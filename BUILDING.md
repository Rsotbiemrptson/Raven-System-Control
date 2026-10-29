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
- Optional WSL Ubuntu with Python 3 for mocked shell integration tests.

## Toolchain layout

Choose a workspace directory and arrange these tools beneath it:

```text
<workspace>/
  Toolchain/
    Java/bin/{javac,java,jar,keytool}.exe
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
signed APK to `Outputs/0.2.17-preview/`, along with SHA256SUMS.txt. Build output
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
