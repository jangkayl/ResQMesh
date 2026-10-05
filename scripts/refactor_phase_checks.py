"""Read-only preservation checks for an explicitly authorized structural phase."""
import base64
import hashlib
import re
import subprocess


def public_method_headers(text):
    """Compare facade declarations independently from relocated method bodies."""
    headers = {}
    for match in re.finditer(r'^    (?:(?:internal|public|suspend|override) )*fun (\w+)\(', text, re.M):
        end, depth = match.end(), 1
        while depth:
            if text[end] == '(':
                depth += 1
            elif text[end] == ')':
                depth -= 1
            end += 1
        tail = re.match(r'\s*(?::[^={\n]+)?', text[end:])
        header = text[match.start():end + tail.end()].strip()
        headers[match[1]] = re.sub(r'\s+', ' ', header)
    return headers


def declaration(text, name, brace_end):
    match = re.search(r'^    (?:(?:private|internal) )?fun ' + name + r'\(', text, re.M)
    if not match:
        raise ValueError('Missing declaration: ' + name)
    if name == 'endpointForPeer':
        end = text.index('\n\n    ', match.end())
    else:
        i, depth = match.end(), 1
        while depth:
            if text[i] == '(':
                depth += 1
            elif text[i] == ')':
                depth -= 1
            i += 1
        opening = text.index('{', i)
        end = brace_end(text, opening) + 1
        if text[end:].startswith('.getOrDefault(false)'):
            end += len('.getOrDefault(false)')
    return text[match.start():end]


def check_phase(root, baseline, manifest, brace_end, block_methods, canonical):
    if manifest.get('version') != 1:
        raise ValueError('Unsupported phase manifest')
    files, errors = baseline['Files'], []
    allowed = set(manifest['allowed'])
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    if head != baseline['Head']:
        errors.append('HEAD changed since the phase baseline')
    index = subprocess.check_output(['git', 'diff', '--cached', '--binary'], cwd=root, text=True)
    if index != baseline['Index']:
        errors.append('Staged work changed since the phase baseline')
    inventory = set(subprocess.check_output(
        ['git', '-c', 'core.quotepath=false', 'ls-files', '--cached', '--others', '--exclude-standard'],
        cwd=root, text=True).splitlines())
    protected = 0
    for relative in sorted(set(files) | inventory):
        if relative in allowed:
            continue
        path = root / relative
        digest = hashlib.sha256(path.read_bytes()).hexdigest().upper() if path.is_file() else None
        if relative not in files or digest != files[relative]['Hash']:
            errors.append('Outside phase scope: ' + relative)
        else:
            protected += 1

    source = manifest['source']
    before = base64.b64decode(files[source]['Content']).decode('utf-8').replace('\r\n', '\n')
    after = (root / source).read_text(encoding='utf-8')
    if public_method_headers(before) != public_method_headers(after):
        errors.append('Public facade method signatures/defaults changed')
    if hashlib.sha256(canonical(after).encode()).hexdigest() != manifest['facade_hash']:
        errors.append('Facade differs from the reviewed phase wiring')
    moved = set()
    for move in manifest['moves']:
        name = move['name']
        moved.add(name)
        old = declaration(before, name, brace_end)
        if not move.get('target_private'):
            old = old.replace('    private fun ', '    fun ', 1)
        for old_receiver, new_receiver in move['replacements'].items():
            old = old.replace(old_receiver, new_receiver)
        target = (root / move['target']).read_text(encoding='utf-8')
        new = declaration(target, name, brace_end)
        if canonical(old) != canonical(new):
            errors.append('Relocated declaration differs: ' + name)
        if move.get('facade_removed'):
            if re.search(r'^    private fun ' + name + r'\(', after, re.M):
                errors.append('Unused private facade delegate remains: ' + name)
            continue
        facade = declaration(after, name, brace_end)
        opening = facade.index('{')
        body = canonical(facade[opening + 1:brace_end(facade, opening)])
        expected = move['delegate']
        if body not in [expected, 'return ' + expected]:
            errors.append('Facade delegate differs: ' + name)

    remaining = 0
    old_methods, new_methods = block_methods(before), block_methods(after)
    for name, body in old_methods.items():
        if name in moved:
            continue
        for old_receiver, new_receiver in manifest['remaining_replacements'].items():
            body = body.replace(old_receiver, new_receiver)
        if canonical(body) != canonical(new_methods.get(name, '')):
            errors.append('Remaining orchestration body differs: ' + name)
        remaining += 1

    # Freeze construction/resources around the exact lifted method bodies as reviewed.
    for target, expected in manifest['collaborator_shells'].items():
        text = (root / target).read_text(encoding='utf-8')
        for move in manifest['moves']:
            if move['target'] == target:
                text = text.replace(declaration(text, move['name'], brace_end), '<MOVED:' + move['name'] + '>')
        if hashlib.sha256(canonical(text).encode()).hexdigest() != expected:
            errors.append('Collaborator wiring/state differs: ' + target)
    if errors:
        raise ValueError('\n'.join(errors))
    print(f'Phase {manifest["phase"]} passed: {protected} unrelated files unchanged; '
          f'{len(moved)} relocated declarations and {remaining} remaining orchestration bodies preserved; '
          'public method signatures, reviewed wiring, HEAD and index unchanged.')


def import_cycles(sources):
    """Return cyclic file components from resolvable project imports.

    This is a source-navigation check, not a compiler dependency graph. Existing
    cycles are retained for review; new cycles fail the phased size inspection.
    """
    symbols, packages = {}, {}
    for path, text in sources.items():
        package = re.search(r'^package ([\w.]+)', text, re.M)
        if not package:
            continue
        packages.setdefault(package[1], set()).add(path)
        for match in re.finditer(
                r'^(?:(?:private|internal|public|data|sealed|enum|abstract|open|value|suspend|inline|const) )*'
                r'(?:class|interface|object|fun|val|typealias) (\w+)', text, re.M):
            symbols.setdefault(package[1] + '.' + match[1], set()).add(path)
    graph = {path: set() for path in sources}
    for path, text in sources.items():
        for match in re.finditer(r'^import ([\w.*]+)', text, re.M):
            name = match[1]
            if name.endswith('.*'):
                targets = packages.get(name[:-2], set())
            else:
                targets = symbols.get(name, set())
                while not targets and '.' in name:
                    name = name.rsplit('.', 1)[0]
                    targets = symbols.get(name, set())
            graph[path].update(targets - {path})
    indices, low, stack, active, cycles = {}, {}, [], set(), set()

    def visit(path):
        indices[path] = low[path] = len(indices)
        stack.append(path)
        active.add(path)
        for target in sorted(graph[path]):
            if target not in indices:
                visit(target)
                low[path] = min(low[path], low[target])
            elif target in active:
                low[path] = min(low[path], indices[target])
        if low[path] == indices[path]:
            component = set()
            while True:
                target = stack.pop()
                active.remove(target)
                component.add(target)
                if target == path:
                    break
            if len(component) > 1:
                cycles.add(frozenset(component))

    for path in sorted(graph):
        if path not in indices:
            visit(path)
    return cycles


def check_architecture(root, baseline, size_limit=500):
    source_root = root / 'app/src/main/java/com/example/testresqmesh'
    errors, oversized, types, sources = [], [], {}, {}
    if baseline is not None and 'Files' not in baseline:
        raise ValueError('Architecture size checks require a full phase snapshot supplied with BaselinePath.')
    baseline_files = baseline['Files'] if baseline else {}
    for path in sorted(source_root.rglob('*.kt')):
        text = path.read_text(encoding='utf-8-sig')
        relative = path.relative_to(root).as_posix()
        sources[relative] = text
        package = re.search(r'^package ([\w.]+)', text, re.M)
        if not package or not path.parent.as_posix().endswith('/' + package[1].replace('.', '/')):
            errors.append('Package/folder mismatch: ' + relative)
        if '/core/ui/' in '/' + relative and re.search(r'^import .*\.feature\.', text, re.M):
            errors.append('Shared UI imports feature: ' + relative)
        for match in re.finditer(r'^(?:(?:private|internal|public|data|sealed|enum|abstract|open|value) )*(?:class|interface|object) (\w+)', text, re.M):
            key = (package[1] if package else '') + '.' + match[1]
            if key in types:
                errors.append('Duplicate declaration: ' + key)
            types[key] = relative
        lines = len(text.splitlines())
        if lines > size_limit:
            old = baseline_files.get(relative)
            prior = len(base64.b64decode(old['Content']).decode('utf-8-sig').splitlines()) if old else 0
            oversized.append((relative, lines, prior))
            if baseline is not None and (prior <= size_limit or lines > prior):
                errors.append(f'New or growing oversized file: {relative} ({prior} -> {lines})')
    current_cycles = import_cycles(sources)
    if baseline is not None:
        old_sources = {
            relative: base64.b64decode(record['Content']).decode('utf-8-sig')
            for relative, record in baseline_files.items()
            if relative.startswith('app/src/main/java/com/example/testresqmesh/') and relative.endswith('.kt')
        }
        old_cycles = import_cycles(old_sources)
        for cycle in current_cycles:
            if not any(cycle <= old_cycle for old_cycle in old_cycles):
                errors.append('New import dependency cycle: ' + ', '.join(sorted(cycle)))
    if errors:
        raise ValueError('\n'.join(errors))
    print(f'Architecture inspection passed: package/folder and shared UI boundaries valid; '
          f'{len(current_cycles)} existing import-cycle components, no new cycles; '
          f'{len(oversized)} existing oversized files remain in the phase ledger; no new oversized files.')
    return oversized
