# Build Raven System Control

The app is a small Java/Android project built without Gradle. The checked-in
PowerShell script packages the Java classes, resources and shell helper,
runs the fan-policy tests, aligns the APK, signs it and verifies the result.

## Requirements

- Windows with PowerShell.
- A JDK with javac, java, jar and keytool.
- Android platform android-35/android.jar.
- Android build tools providing aapt.exe, zipalign.exe, lib/d8.jar and
  lib/apksigner.jar. The build uses the Workbench toolchain layout below.
- Android NDK r30 with the Windows x86_64 LLVM toolchain (clang.exe), used to build the ARM64 ring-segment helper.
- Optional WSL Ubuntu with Python 3 for mocked shell integration tests.

## Toolchain layout

Choose a workspace directory and arrange these tools beneath it:

```text
<workspace>/
  Toolchain/
    Java/bin/{javac,java,jar,keytool}.exe
    android-ndk-r30/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe
    Android-SDK/platforms/android-35/android.jar
    Android-Build-Tools/aapt.exe
    Android-Build-Tools/zipalign.exe
    Android-Build-Tools/lib/d8.jar
    Android-Build-Tools/lib/apksigner.jar
  Signing-Keys/Odin-Controls/       # local only, never commit or distribute
```

Obtain the JDK and Android SDK/build tools from their official distributors.
For a standard SDK installation, copy the selected build-tools directory to
`Toolchain/Android-Build-Tools` and the android-35 platform to the path above.
No OEM APKs or device images are required to build this repository.

From this repository:

```powershell
.\Source\build.ps1 -Workspace 'D:\AndroidWorkspace'
```

The script uses a separate `Outputs/build-<id>` staging directory and writes the
signed APK to `Outputs/Release staging/0.2.54-preview/`, along with SHA256SUMS.txt. Build output
is ignored by Git. It does not install the APK, access a device or publish a release.

## Development signing

On a fresh workspace the script creates a dedicated development keystore under
`Signing-Keys/Odin-Controls`. Its password is the conventional local-development
value `android`; this is not a secret for public-release signing. Keep the private
key out of source control. The name of this legacy folder is retained for build
compatibility; the application name is Raven System Control.

The maintainer's existing signing key is not part of this repository. An APK built
with your own key cannot update the published APK in place. Do not uninstall an
installed copy until you have stopped its fan session and restored its lighting.

## Host-only tests

The build script runs `FanCurveTest.java`, `CurvePointsTest.java` and
`AppearanceColorsTest.java` (including 4,096 accent-color contrast checks)
from `Source/tests/`.

For the shell helper, run these inside Linux/WSL from the repository directory:

```sh
sh -n Source/assets/hardware.sh
python3 Source/tests/hardware_test.py
```

The Python harness replaces device paths and commands with temporary fixtures.
It does not read or modify an Android device. Tests cover model/API/build guards,
PWM-period validation, policy behavior, heartbeat fallback, failed restoration
and retry, four-zone colors/brightness and independent analog/side masks,
shared/split lighting formats, ARGB conversion, immutable setup backups and
original/null-setting restoration. Passing these tests does not validate real hardware.

## Publishing updates

Fetch and fast-forward this checkout from GitHub before editing a release.
Preserve remote README edits and update version details in place. The owner
has removed the fan-screen screenshot: do not add screenshots or restore its
README embed unless explicitly requested. `docs/images/app.png` is ignored.
Publish the signed APK and checksum with a source tag; exclude private keys,
firmware, OEM extraction results and device evidence.

## Firmware compatibility fixtures

`python3 Source/tests/firmware_compatibility_test.py <output-directory>` generates an Android-shell fixture with all hardware paths remapped into a fresh temporary test tree. Run the generated `compatibility.sh` only on an authorized Android test device with the required privileges. It exercises platform/table compatibility and immutable backup validation without writing real hardware controls. This fixture differs from the host-only `hardware_test.py` harness.

## Performance reentry fixtures

The generator "Source/tests/performance_reentry_test.py" takes an output directory and creates Android-shell fixtures with all hardware paths remapped. Push the generated files into /data/local/tmp/raven-reentry-source and run init.sh only on an authorized Android test device with the required privileges. The fixture checks repeated custom/stock transitions, obsolete recovery tokens, preservation of a newer guard and conservative recovery without writing real hardware controls.

The native ring helper is built from Source/native/ring-segments.c into the generated, ignored Source/assets/ring-segments asset. No prebuilt OEM binaries are required.

The build also runs SegmentColorsTest.java for disabled output, saved color/brightness, accumulated edits and peer preservation. The native helper includes Source/native/segment-frame.h. Its register-frame logic can be tested without hardware on Linux/WSL:

```sh
cc -std=c11 -Wall -Wextra -Werror Source/tests/segment_frame_test.c -o /tmp/raven-segment-frame-test
/tmp/raven-segment-frame-test
```

Run the isolated stock-mask regression with python3 Source/tests/segment_mask_test.py. It exercises the real shell branch against temporary settings and a fake native writer, covering empty rings, mixed output, explicit off and original-snapshot preservation. SegmentColorsTest also checks the matching Java mask rules.

Before publishing changes to segment behavior, test both mixed disabled sections and all sections disabled through standby/wake on the exact candidate APK. Observe the first instant of wake for flashes, verify stick-toggle state, and restore original lighting, profiles and recovery data. Live on/off tests alone do not cover standby.

The shell segment regression also checks the dark OEM wake fallback, restoration of default/explicit stock colors, rejection of invalid colors, and preservation of a newer stock color selection. Saved disabled-segment profiles must be applied from both the app and actual Raven LED quick tile before their respective standby checks.
