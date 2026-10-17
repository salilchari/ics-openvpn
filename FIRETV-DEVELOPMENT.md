# Home VPN TV — development patch, not a finished VPN product

Based on schwabe/ics-openvpn commit 14445f1cf9c06195a5c9f04f2bee8f412e73e3cb.
This is a development snapshot, not a verified stable upstream release.
Existing GPL terms and dependency licenses remain applicable; see doc/LICENSE.txt.

## Implemented

- TV launcher opens a dashboard with large focusable remote controls.
- Saved profile connection, upstream import/edit screens, logs and disconnect.
- Optional separate TV profile copy requests full IPv4/IPv6 routes, removes LAN
  and app bypasses, blocks unused address families, enables persist-tun and
  unlimited connection retries with a 2–60 second retry interval.
- Custom profile/connection options require review instead of silent removal.
- Startup reconnect uses the upstream boot receiver and default profile setting.
- System VPN settings shortcut handles devices that do not expose that screen.
- Protection is explicitly unverified; connected is never labeled leak-free.
- GitHub Actions development build and three profile-policy regression tests.

## Not implemented or verified

No APK has been built. No Android runtime, Fire Stick, router or leak tests ran.
Local Gradle launch failed because Java could not load libjli.so; an Android SDK,
NDK and native dependency checkout are also required. Ramus rejected the user's
original APK with app_not_allowed. This restriction was not bypassed.

The complete kill-switch requirement is NOT met by this patch. Android system
lockdown must be available and configured to cover crashes, force-stop and boot.
If the Fire OS device cannot support it, use firewall enforcement on the local
gateway serving the Fire Stick. A second VPN-based firewall cannot run alongside
the OpenVPN tunnel as an independent Android VpnService.

Server-pushed routes, DNS routing and IPv6 require packet-level verification.
Profile preparation alone does not prove that all traffic follows the VPN.
Startup reconnect can be delayed or blocked by OS policy. Import/edit screens
are inherited from upstream and their D-pad usability remains untested.

No signed release, in-app updater, upstream update watcher, automatic deployment,
or production signing key has been created. The workflow produces debug builds
only. This retains upstream's application ID and is not a side-by-side install:
it cannot update the user's APK because its signing key differs. Export profiles
before any later installation process. A separate application ID requires an
audit of upstream's hardcoded component/API references before release.

## Apply and build

```sh
git clone https://github.com/schwabe/ics-openvpn.git
cd ics-openvpn
git checkout 14445f1cf9c06195a5c9f04f2bee8f412e73e3cb
git switch -c firetv-development
git apply /path/to/firetv-development.patch
git submodule update --init --recursive
# Install the Java/Android SDK/NDK versions declared by the pinned project.
./gradlew :main:testUiOvpn2DebugUnitTest :main:assembleUiOvpn2Debug
```

Alternatively push the branch to an authorized fork and run the included
Fire TV development APK workflow. The workflow has not been executed.

## Release gates

1. Target: Fire TV Stick 4K Max (2nd generation), Fire OS 8.1.8.2
   (RS182/3811), connecting over UDP to an Archer AX53 in India. Router hardware
   revision and firmware number are unavailable; the user reports current firmware.
2. Select and pin a compatible stable upstream release; rebase and compile.
3. Test import, permission prompts, authentication and all controls with D-pad.
4. Capture traffic at the local gateway during connection, tunnel/server failure,
   Wi-Fi loss/recovery, app process death, force-stop, sleep/wake, reboot and upgrade.
   Check IPv4, IPv6, DNS and existing streaming connections for bypass.
5. Test a sustained connection to the user's router with a test profile delivered
   privately. Do not commit profiles, private keys, credentials or raw logs.
6. Configure persistent release signing, version increments and signed update
   verification. Upstream stable releases should trigger a review/build/test
   pipeline; do not install untested upstream code automatically. Android user
   confirmation may be required for APK updates.
7. Only claim blocking for the tested OS/device/failure cases. A VPN client cannot
   guarantee acceptance by streaming platforms or prevent account restrictions.

GitHub destination: https://github.com/salilchari/ics-openvpn.
Development changes are submitted on a separate branch; consult its Actions
results for the latest build status. No production release is authorized by a
successful development build alone.
