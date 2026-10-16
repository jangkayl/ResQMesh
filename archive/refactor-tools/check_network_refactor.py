"""Verify this refactor against its pre-edit, local workspace snapshot (stdlib only).

Unlike the presentation-only guard, this allows exactly the reviewed network
extractions, checks relocated bodies, and protects existing untracked work.
It never captures or replaces a baseline and never writes workspace files.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

sys.dont_write_bytecode = True

SOURCE = 'app/src/main/java/com/example/testresqmesh/'
ALLOWED = {
    SOURCE + p for p in [
        'core/network/NativeBleManager.kt', 'core/network/MeshNetworkGateway.kt',
        'core/network/NativeBleGateway.kt', 'core/network/NativePayloadEvents.kt',
        'core/network/dispatch/PayloadHandlers.kt', 'core/network/dispatch/PresenceHandlers.kt',
        'core/network/dispatch/DeliveryHandlers.kt', 'core/network/dispatch/LinkControlHandlers.kt',
        'core/network/dispatch/LiveAudioHandler.kt', 'core/network/dispatch/IncidentEventHandlers.kt',
        'core/network/dispatch/NativePayloadCallbacks.kt',
        'core/network/bluetooth/BlePresencePublisher.kt',
        'core/network/bluetooth/gatt/GattClientManager.kt',
        'core/network/bluetooth/gatt/GattServerManager.kt',
        'core/network/bluetooth/gatt/GattHost.kt', 'core/network/bluetooth/gatt/GattClientHost.kt',
        'core/network/bluetooth/gatt/GattServerHost.kt', 'core/network/bluetooth/gatt/NativeGattHost.kt',
        'data/repository/MeshRepository.kt', 'data/repository/MeshNetworkEventBinder.kt',
    ]
} | {
    'app/src/test/java/com/example/testresqmesh/core/network/bluetooth/BlePresencePublisherTest.kt',
    'app/src/test/java/com/example/testresqmesh/core/network/dispatch/NativePayloadCallbacksTest.kt',
    'scripts/check_network_refactor.py', 'docs/status.md', 'docs/architecture.md',
    'docs/validation.md', 'docs/decisions.md',
    'docs/plans/network-maintainability-refactor.md',
    'docs/testing/network-maintainability-test-card.md',
    'archive/docs-superseded-2026-10-05/README.md',
    'archive/docs-superseded-2026-10-05/validation-before-network-refactor.md',
}


def brace_end(text, start):
    depth, i = 0, start
    while i < len(text):
        if text.startswith('//', i):
            i = text.find('\n', i)
            if i == -1:
                break
        elif text.startswith('/*', i):
            i = text.index('*/', i + 2) + 2
        elif text[i] in '\"\'':
            quote = text[i]
            i += 1
            while i < len(text):
                if text[i] == '\\':
                    i += 2
                    continue
                if text[i] == quote:
                    i += 1
                    break
                i += 1
        else:
            if text[i] == '{':
                depth += 1
            elif text[i] == '}':
                depth -= 1
                if depth == 0:
                    return i
            i += 1
    raise ValueError('Unmatched source block')


def canonical(text):
    return '\n'.join(line.strip() for line in text.splitlines() if line.strip())


def block_methods(text):
    result = {}
    for match in re.finditer(r'^    (?:(?:private|internal|suspend) )*fun (\w+)\(', text, re.M):
        i, depth = match.end(), 1
        while depth:
            if text[i] == '(':
                depth += 1
            elif text[i] == ')':
                depth -= 1
            i += 1
        opening = text.find('{', i)
        if opening < 0 or '=' in text[i:opening] or '\n    ' in text[i:opening]:
            continue
        result[match[1]] = text[opening + 1:brace_end(text, opening)]
    return result


def check(root, baseline):
    errors = []
    files = baseline['Files']
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    if head != baseline['Head']:
        errors.append('HEAD changed since the pre-edit snapshot')
    inventory = set(subprocess.check_output(
        ['git', '-c', 'core.quotepath=false', 'ls-files', '--cached', '--others', '--exclude-standard'],
        cwd=root, text=True).splitlines())
    protected = 0
    for relative in sorted(set(files) | inventory):
        if relative in ALLOWED:
            continue
        path = root / relative
        digest = hashlib.sha256(path.read_bytes()).hexdigest().upper() if path.is_file() else None
        if relative not in files or digest != files[relative]['Hash']:
            errors.append('Outside refactor scope: ' + relative)
        else:
            protected += 1

    def original(relative):
        return base64.b64decode(files[SOURCE + relative]['Content']).decode('utf-8').replace('\r\n', '\n')

    def current(relative):
        return (root / (SOURCE + relative)).read_text(encoding='utf-8')

    def same(label, before, after):
        if canonical(before) != canonical(after):
            errors.append('Relocated body differs: ' + label)

    # Handler class bodies, annotations and dispatch registration order are preserved.
    before = original('core/network/dispatch/PayloadHandlers.kt')
    matches = list(re.finditer(r'^(?:@OptIn\([^\n]+\)\n)?class (\w+)\b', before, re.M))
    handler_count = 0
    for i, match in enumerate(matches):
        block = before[match.start():matches[i + 1].start() if i + 1 < len(matches) else len(before)]
        found = []
        for path in (root / SOURCE / 'core/network/dispatch').glob('*.kt'):
            text = path.read_text(encoding='utf-8')
            candidate = re.search(r'^(?:@OptIn\([^\n]+\)\n)?class ' + match[1] + r'\b', text, re.M)
            if candidate:
                opening = text.index('{', candidate.start())
                found.append(text[candidate.start():brace_end(text, opening) + 1])
        if len(found) != 1:
            errors.append('Handler missing or duplicated: ' + match[1])
        else:
            opening = block.index('{')
            same(match[1], block[:brace_end(block, opening) + 1], found[0])
            handler_count += 1
    gateway = original('core/network/MeshNetworkGateway.kt')
    marker = '/** Keeps NativeBleManager available'
    same('gateway interface', gateway[:gateway.index(marker)], current('core/network/MeshNetworkGateway.kt'))
    adapter = current('core/network/NativeBleGateway.kt')
    same('gateway adapter', gateway[gateway.index(marker):], adapter[adapter.index(marker):])

    for role, marker in [('Client', '    private val setups'), ('Server', '    // The elected client')]:
        relative = 'core/network/bluetooth/gatt/Gatt' + role + 'Manager.kt'
        old, new = original(relative), current(relative)
        old = old[old.index(marker):]
        if role == 'Client':
            old = old.replace('bluetoothAdapter.getRemoteDevice(macAddress)', 'getRemoteDevice(macAddress)')
            old = old.replace('private fun NativeBleManager.notifyScanState', 'private fun GattClientHost.notifyScanState')
        same('GATT ' + role + ' callbacks', old, new[new.index(marker):])

    preserved_methods = 0
    for relative in ['core/network/NativeBleManager.kt', 'data/repository/MeshRepository.kt']:
        old, new = block_methods(original(relative)), block_methods(current(relative))
        for name, body in old.items():
            if name in ['sendSystemPulse', 'setupCallbacks']:
                continue
            if name == 'startTransport':
                body = body.replace('lastSystemPulseHash = 0\n        lastSystemPulseTime = 0L', 'presencePublisher.resetTransport()')
            same(name, body, new.get(name, ''))
            preserved_methods += 1

    # The binder owns assignments only; each policy callback stays in the repository.
    repo = original('data/repository/MeshRepository.kt')
    start = repo.index('    private fun setupCallbacks()')
    opening = repo.index('{', start)
    section = repo[opening + 1:brace_end(repo, opening)]
    order = []
    methods = block_methods(current('data/repository/MeshRepository.kt'))
    for match in re.finditer(r'networkManager\.(\w+)\s*=\s*\{', section):
        name = match[1]
        order.append(name)
        opening = match.end() - 1
        body = section[opening + 1:brace_end(section, opening)]
        if '->' in body.split('\n', 1)[0]:
            body = body.split('->', 1)[1]
        method = 'onNetwork' + name[2:] if name.startswith('on') else {
            'checkRouteExists': 'networkRouteExists', 'canRetireForBridge': 'canNetworkRetireForBridge',
            'stpNeighborsProvider': 'networkSpanningTreeNeighbors',
        }[name]
        # These extracted handlers have expression bodies (= run { ... }).
        text = current('data/repository/MeshRepository.kt')
        opening = text.index('{', text.index('    private fun ' + method + '('))
        same(name, body, text[opening + 1:brace_end(text, opening)])
    binder = current('data/repository/MeshNetworkEventBinder.kt')
    if order != re.findall(r'^    gateway\.(\w+) = callbacks\.\w+$', binder, re.M):
        errors.append('Gateway callback assignment order changed')

    pulse = block_methods(original('core/network/NativeBleManager.kt'))['sendSystemPulse']
    replacements = {
        'sessionLifecycle.peers(distinctReadyLinkCount())': 'updatePeerCount()',
        'System.currentTimeMillis()': 'clock()',
        'java.util.UUID.randomUUID().toString()': 'newPulseId()',
        'senderName = myDeviceName,': 'senderName = myDeviceName(),',
        'senderName = myDeviceName\n': 'senderName = myDeviceName()\n',
        'senderNodeId = myNodeId,': 'senderNodeId = myNodeId(),',
        'com.example.testresqmesh.core.network.CryptoManager.getMyPublicKeyBase64()': 'publicKey()',
        'com.example.testresqmesh.core.network.MeshPayload': 'MeshPayload',
        'kotlinx.serialization.protobuf.ProtoBuf': 'ProtoBuf',
        'com.example.testresqmesh.core.utils.TerminalLogCategory': 'TerminalLogCategory',
        'now - lastSystemPulseTime < 30000': 'now - lastSystemPulseTime < FULL_REFRESH_MS',
        '// Topology changed or 60s passed! Send full 141-byte SYSTEM sync.':
            '// Topology changed or 30 seconds passed; send a full SYSTEM snapshot.',
    }
    for old, new in replacements.items():
        pulse = pulse.replace(old, new)
    publisher = current('core/network/bluetooth/BlePresencePublisher.kt')
    same('presence publisher', pulse, block_methods(publisher)['publish'])
    if 'const val FULL_REFRESH_MS = 30_000L' not in publisher:
        errors.append('Presence refresh interval changed')
    same('presence reset', 'lastSystemPulseHash = 0\nlastSystemPulseTime = 0L',
         block_methods(publisher)['resetTransport'])

    for path in (root / SOURCE).rglob('*.kt'):
        match = re.search(r'^package ([\w.]+)', path.read_text(encoding='utf-8-sig'), re.M)
        if not match or not path.parent.as_posix().endswith('/' + match[1].replace('.', '/')):
            errors.append('Package/folder mismatch: ' + str(path.relative_to(root)))
    if errors:
        raise ValueError('\n'.join(errors))
    print(f'Network refactor scope passed: {protected} unrelated files unchanged; '
          f'{handler_count} handler bodies, {preserved_methods} orchestration bodies, '
          f'GATT callbacks and {len(order)} repository bindings preserved.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', required=True, type=Path)
    parser.add_argument('--phase-manifest', type=Path,
                        help='Use an explicit structural-phase scope; historical default is unchanged.')
    parser.add_argument('--architecture-only', action='store_true',
                        help='Inspect packages, declarations, import cycles, shared UI boundaries and source sizes.')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    baseline = json.loads(args.baseline.read_text(encoding='utf-8-sig'))
    if args.architecture_only:
        from refactor_phase_checks import check_architecture
        check_architecture(root, baseline)
    elif args.phase_manifest:
        from refactor_phase_checks import check_phase, check_architecture
        manifest = json.loads(args.phase_manifest.read_text(encoding='utf-8-sig'))
        check_phase(root, baseline, manifest, brace_end, block_methods, canonical)
        check_architecture(root, baseline)
    else:
        check(root, baseline)
