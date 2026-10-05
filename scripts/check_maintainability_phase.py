"""Validate an explicit later-phase mapping without replacing historical baselines."""
import argparse
import base64
import hashlib
import json
import re
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from check_network_refactor import canonical, brace_end
from refactor_phase_checks import check_architecture, public_method_headers


def check(root, baseline, manifest):
    errors = []
    if manifest['version'] != 2:
        raise ValueError('Expected later-phase manifest version 2')
    allowed = set(manifest['allowed'])
    inventory = set(subprocess.check_output(
        ['git', '-c', 'core.quotepath=false', 'ls-files', '--cached', '--others', '--exclude-standard'],
        cwd=root, text=True).splitlines())
    protected = 0
    for relative in sorted(set(baseline['Files']) | inventory):
        if relative in allowed:
            continue
        path = root / relative
        digest = hashlib.sha256(path.read_bytes()).hexdigest().upper() if path.is_file() else None
        if relative not in baseline['Files'] or digest != baseline['Files'][relative]['Hash']:
            errors.append('Outside phase scope: ' + relative)
        else:
            protected += 1
    for key, command in [('Head', ['git', 'rev-parse', 'HEAD']),
                         ('Index', ['git', 'diff', '--cached', '--binary'])]:
        value = subprocess.check_output(command, cwd=root, text=True)
        if (value.strip() if key == 'Head' else value) != baseline[key]:
            errors.append(key + ' changed during the phase')
    for source in manifest['public_facades']:
        before = base64.b64decode(baseline['Files'][source]['Content']).decode('utf-8-sig')
        after = (root / source).read_text(encoding='utf-8-sig')
        class_name = manifest.get('public_facade_classes', {}).get(source)
        if class_name:
            def class_body(text):
                start = re.search(r'\bclass ' + re.escape(class_name) + r'\(', text).end()
                pos, depth = start, 1
                while depth:
                    if text[pos] == '(':
                        depth += 1
                    elif text[pos] == ')':
                        depth -= 1
                    pos += 1
                opening = text.index('{', pos)
                return text[opening + 1:brace_end(text, opening)]
            before, after = class_body(before), class_body(after)
        if source in manifest.get('top_level_facades', []):
            # Screen APIs are top-level declarations; local functions belong to mapped bodies.
            for target in manifest.get('top_level_api_targets', {}).get(source, []):
                after += '\n' + (root / target).read_text(encoding='utf-8-sig')
            before = '\n'.join('    ' + line for line in before.splitlines())
            after = '\n'.join('    ' + line for line in after.splitlines())
        if public_method_headers(before) != public_method_headers(after):
            errors.append('Public method signatures/defaults changed: ' + source)
    for move in manifest['moves']:
        original = base64.b64decode(baseline['Files'][move['source']]['Content']).decode('utf-8-sig')
        if canonical(move['before']) not in canonical(original):
            errors.append('Moved declaration absent from baseline: ' + move['name'])
        expected = move['before']
        for before, after in move.get('replacements', {}).items():
            expected = expected.replace(before, after)
        if canonical(expected) != canonical(move['after']):
            errors.append('Unmapped declaration change: ' + move['name'])
        target = (root / move['target']).read_text(encoding='utf-8-sig')
        if canonical(move['after']) not in canonical(target):
            errors.append('Moved declaration changed: ' + move['name'])
    for relative, digest in manifest['reviewed_sources'].items():
        path = root / relative
        if hashlib.sha256(path.read_bytes()).hexdigest().upper() != digest:
            errors.append('Source differs from reviewed wiring: ' + relative)
    if errors:
        raise ValueError('\n'.join(errors))
    check_architecture(root, baseline)
    print(f'Phase {manifest["phase"]}: {protected} unrelated files byte-identical; '
          f'{len(manifest["moves"])} mapped declarations, public methods, reviewed wiring and Git state preserved.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--manifest', type=Path, required=True)
    args = parser.parse_args()
    check(Path(__file__).resolve().parent.parent,
          json.loads(args.baseline.read_text(encoding='utf-8-sig')),
          json.loads(args.manifest.read_text(encoding='utf-8-sig')))
