"""UI smoke checks for a pinned APK; does not test a VPN server or network leaks."""
import json
import pathlib
import subprocess
import time
import xml.etree.ElementTree as ET

OUT = pathlib.Path('emulator-evidence')
OUT.mkdir(exist_ok=True)
PACKAGE = 'de.blinkt.openvpn'
DASHBOARD = PACKAGE + '/.activities.TvDashboardActivity'
results = []
serial = 0

def adb(*args, check=True):
    return subprocess.run(['adb', '-e', *args], check=check, capture_output=True,
                          timeout=45).stdout

def snapshot(label):
    global serial
    serial += 1
    prefix = OUT / f'{serial:02d}-{label}'
    adb('shell', 'uiautomator', 'dump', '/sdcard/window.xml')
    xml = adb('shell', 'cat', '/sdcard/window.xml')
    prefix.with_suffix('.xml').write_bytes(xml)
    prefix.with_suffix('.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    return ET.fromstring(xml)

def contains(root, text):
    return any(text in n.get('text', '') or text in n.get('content-desc', '')
               for n in root.iter('node'))

def expect(root, text):
    assert contains(root, text), f'Expected UI text: {text}'

def key(code, label):
    adb('shell', 'input', 'keyevent', str(code))
    time.sleep(0.5)
    return snapshot(label)

def launch():
    response = adb('shell', 'am', 'start', '-W', '-n', DASHBOARD).decode()
    assert 'Error:' not in response, response
    for attempt in range(5):
        root = snapshot(f'dashboard-{attempt}')
        if contains(root, 'Home VPN') or contains(root, 'System VPN protection settings'):
            # Phone emulators initially use touch mode. A D-pad event enters
            # keyboard navigation; verify focus rather than assuming it exists.
            if not any(n.get('focused') == 'true' for n in root.iter('node')):
                root = key(19, 'enter-dpad-mode')
            return root
        time.sleep(1)
    raise AssertionError('Dashboard did not appear')

def focused(root, label):
    assert any(n.get('focused') == 'true' and n.get('text') == label
               for n in root.iter('node')), f'D-pad focus did not reach {label}'

def passed(name):
    results.append({'test': name, 'result': 'passed'})
    print('PASS:', name, flush=True)

try:
    apk, = pathlib.Path('apk').rglob('*.apk')
    install = adb('install', '-r', str(apk)).decode()
    assert 'Success' in install, install
    passed('APK installation on Android 11 x86_64')
    reset = adb('shell', 'pm', 'clear', PACKAGE).decode()
    assert 'Success' in reset, reset
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('logcat', '-c')
    adb('shell', 'wm', 'size', '1080x1920')
    adb('shell', 'wm', 'density', '320')
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    adb('shell', 'settings', 'put', 'system', 'user_rotation', '1')
    adb('shell', 'input', 'keyevent', '82')
    time.sleep(2)  # Let display configuration settle before the first activity.
    root = launch()
    expect(root, 'Internet blocking is NOT verified')
    focused(root, 'Connect a saved profile')
    passed('TV dashboard launch and protection disclaimer')

    root = key(23, 'connect-empty')
    expect(root, 'Import a profile first')
    passed('Connect without a profile shows guidance')
    root = key(4, 'back-from-connect')
    expect(root, 'Home VPN')
    root = key(20, 'focus-prepare')
    focused(root, 'Prepare a TV profile copy')
    root = key(23, 'prepare-empty')
    expect(root, 'Import a profile first')
    passed('D-pad navigation and empty-profile preparation')
    root = key(4, 'back-from-prepare')
    root = key(20, 'focus-import')
    focused(root, 'Import / edit profiles')
    root = key(23, 'profile-screen')
    activity = adb('shell', 'dumpsys', 'activity', 'activities').decode()
    (OUT / 'profile-activity.txt').write_text(activity)
    assert any('MainActivity' in line and ('ResumedActivity' in line or 'topResumedActivity' in line)
               for line in activity.splitlines()), 'Profile activity is not resumed'
    passed('Profile management screen opens')

    # New activity instance resets initial D-pad focus deterministically.
    adb('shell', 'am', 'force-stop', PACKAGE)
    root = launch()
    for index in range(3):
        root = key(20, f'focus-settings-{index}')
    focused(root, 'System VPN protection settings')
    root = key(23, 'vpn-settings')
    assert any(n.get('package') == 'com.android.settings' for n in root.iter('node')) \
        or contains(root, 'VPN settings unavailable'), 'Neither settings nor fallback appeared'
    passed('System VPN settings or explicit unsupported-device fallback')

    root = key(4, 'back-from-vpn-settings')
    expect(root, 'System VPN protection settings')
    passed('Back from VPN settings returns to dashboard')
    root = key(3, 'home')
    root = launch()
    expect(root, 'System VPN protection settings')
    assert adb('shell', 'pidof', PACKAGE).strip(), 'App process missing'
    passed('Return from Home and relaunch')
    crash = adb('logcat', '-b', 'crash', '-d').decode(errors='replace')
    (OUT / 'crash-buffer.txt').write_text(crash)
    assert PACKAGE not in crash, 'App appears in crash buffer'
    passed('No app crash recorded during tested flows')
except Exception as error:
    results.append({'test': 'execution', 'result': 'failed', 'detail': str(error)})
    try:
        snapshot('failure')
    except Exception:
        pass
    raise
finally:
    (OUT / 'results.json').write_text(json.dumps({
        'apk_commit': '7eca7b8910c871a7b657b066e282f632a2ac5716',
        'environment': 'Android 11 Google APIs x86_64; not Fire OS',
        'results': results,
        'not_tested': ['VPN connection', 'profile file import', 'leaks', 'kill switch',
                       'long-running stability', 'Fire OS', 'updates']}, indent=2))
    (OUT / 'logcat.txt').write_bytes(adb('logcat', '-d', check=False))
