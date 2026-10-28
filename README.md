# Raven System Control

<img src="docs/images/icon.png" alt="Raven System Control" width="112">

**Your system. Your way.**

Fan curves, independent lighting profiles and quick controls for AYN Odin 2 and Odin 3 while preserving the stock OS.

**Current release: 0.2.7-preview** · Package: `dev.odin.controls` · Version code: `30` · License: **GPL-3.0-only**

[Download the APK](https://github.com/Rsotbiemrptson/Raven-System-Control/releases/download/v0.2.7-preview/Raven-System-Control-0.2.7-preview.apk) · [Release notes and checksums](https://github.com/Rsotbiemrptson/Raven-System-Control/releases/tag/v0.2.7-preview)

## Features

- Fan profile selector with protected OEM Quiet, Smart and Sport references; save custom profiles separately without overwriting the stock names.
- Editable temperature-curve graph and optional manual speed. Custom operation uses the verified off/50–70% requested PWM range, not measured RPM. The temperature source follows the OEM discovery order automatically.
- On Odin 2 firmware 400, four independently controlled zones: left side, left stick, right stick and right side. Each zone has its own color and brightness.
- Color-wheel selectors, hex entry, presets, an icon-orange default, Apply to all lights and direct Save-to-selected-light behavior.
- Separate saved fan and LED profiles. Selecting an LED profile applies it immediately.
- Raven Fan and Raven LED Quick Settings tiles. Tap for profiles; long-press opens the corresponding app tab. Selections leave the chooser open. Only its profile list scrolls; controls stay fixed. Back or an outside tap dismisses it.
- Separate analog/side-light switches in the Odin 2 LED tile chooser. Popup switches, selection indicators and power buttons follow the app accent; titles and profile names use neutral text.
- Orange Raven icon, adjustable accent, light/dark appearance and compact About popup.
- First-launch guided setup with a fan-control warning and an option to skip fan control. Original recovery backups are retained outside app storage. Settings → Advanced contains setup and recovery actions.

## Device support

| Device | Accepted software | Status |
| --- | --- | --- |
| Odin 2 (`Odin2`) | Android 13 / API 33 | Live fan, four-zone LED, profile, recovery and tile checks performed. Physical colors and independent brightness were also confirmed by the owner during development. |
| Odin 3 (`Odin3`) | Android 15 / API 35 with expected stock interfaces | Implemented from local firmware 107/151 evidence. **No Odin 3 hardware test has been performed.** |
| Other devices | — | Hardware controls are blocked; compatibility is not assumed. |

Hardware controls currently require the primary Android user (user 0).

**Experimental preview:** manipulating fan settings can cause overheating and device damage. Use supervised tests and keep platform thermal protections enabled. Custom fan control requests OEM Smart cooling on temperature reaching 80°C, sensor loss, a missed heartbeat or user switch; its safeguards do not guarantee protection. A stock fan-mode change ends Raven's custom control. There is no automatic boot start.

### Quick Settings popup permissions

The app uses AYN's existing `PServerBinder` privileged service for hardware commands. On Odin 2/API 33 and Odin 3/API 35, it also attempts to launch a temporary **UID 0 window renderer** through that service so the chooser can appear above the open shade. The main app keeps its normal Android UID and validates profile requests. The renderer closes on dismissal, screen-off/lock, parent-app exit or inactivity.

This does not install root software, unlock the bootloader, modify SystemUI or firmware, disable SELinux, or add a boot hook. Access still depends on the OEM service and runtime policy. If startup is denied, the helper exits or startup times out after eight seconds, Raven falls back to Android's normal tile dialog, which closes the shade.

Odin 2 above-shade rendering and forced helper-exit/timeout fallbacks were tested. Both Odin 3 firmware versions contain a root-run pservice and system dialog window type 2017; this is compatibility evidence, **not a successful Odin 3 runtime test**.

### Lighting limits

- Odin 2 four-zone writes use the verified stock controller groups. Individual segments inside a stick ring are not exposed.
- Odin 3 retains its existing separate left/right ring setting format; actual behavior remains untested. Odin 2-only side-light switches are not shown there.
- RGB charging-indicator control is not exposed in this release.
- OEM lighting changes can override Raven colors. The app observes stock settings to reflect external changes; driver-only changes may not be readable.

## Getting started

1. Install the APK on the supported device and complete the first-launch guided setup. Fan control can be skipped.
2. Allow fan notifications before applying a custom curve. Begin with a short supervised test; **Use Smart** hands control to OEM Smart cooling.
3. In **LEDs**, select a zone, choose a color/brightness and press **Save**. Save applies that light immediately. **Apply to all lights** applies it to all supported zones.
4. Save fan and LED profiles in their respective tabs. Add **Raven Fan** and **Raven LED** from Android's Quick Settings tile editor.
5. Use **Settings → Advanced → Rerun Guided Setup** to repeat checks. Recovery actions are in the same section.

## Recovery and uninstalling

**Factory Restore Fan** and **Factory Restore LEDs** restore the original settings retained during setup, not a universal firmware default. Repeating setup does not replace a valid original backup with an active Raven profile. The root-owned recovery backup under `/data/local/odin-controls/recovery` is retained across normal uninstall/reinstall; damaged or incompatible backups are rejected.

Before uninstalling, choose **Use Smart** or the appropriate recovery action and confirm the result. Uninstall cannot run app cleanup. LED restoration is not automatic after uninstall or force-stop. An existing compatible recovery backup can be used after reinstalling; this is recovery support, not a guarantee that an uninstall callback runs.

## Build and verification

See [BUILDING.md](BUILDING.md). Java sources, Android resources, shell helper, GPL text and host tests are included in [Source](Source). OEM APKs, firmware images, private signing keys, raw device logs and personal data are not distributed.

The build runs fan-policy, editable-curve and 4,096 appearance-contrast checks, then verifies APK signing/alignment. Host shell fixtures cover fan leases, takeover/recovery, four-zone colors and brightness, independent group masks, invalid input and immutable setup backups. Odin 2 checks include saved-profile application, single-zone saves, separate analog/side toggles, native fallback dialogs, above-shade popups and dismissal. These do not certify Odin 3 or other firmware.

The preview APK retains the previous release's signing identity, so it can update 0.1.6-preview in place. A build signed with your own development key cannot update the published APK directly.

## License and attribution

Created by **Rsotbiemrptson**. Source and project artwork are licensed under **GNU GPL version 3 only**; see [LICENSE](LICENSE) and [NOTICE](NOTICE).

The icon, portions of the interface, color wheel and contrast helpers are adapted from [Raven Backup](https://github.com/Rsotbiemrptson/RavenBackup), also GPL-3.0-only. Raven Backup, Raven App Control and Raven Gamepad are separate applications.
