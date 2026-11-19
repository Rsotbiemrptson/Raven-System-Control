# Raven System Control

<img src="docs/images/icon.png?v=0.2.53-preview" alt="Raven System Control" width="112">

**Your system. Your way.**

Fan curves, independent lighting profiles and quick controls for AYN Odin 2 and Odin 3 while preserving the stock OS.

**Current release: 0.2.53-preview** · Package: `dev.odin.controls` · Version code: `78` · License: **GPL-3.0-only**

[Download the APK](https://github.com/Rsotbiemrptson/Raven-System-Control/releases/download/v0.2.53-preview/Raven-System-Control-0.2.53-preview.apk) · [Release notes and checksums](https://github.com/Rsotbiemrptson/Raven-System-Control/releases/tag/v0.2.53-preview)

**Preview release:** CPU/GPU tuning, Game Assistant integration and Odin 3 individual ring segments remain in preview.

**Hardware tested:** Odin 2 firmware 400 and Odin 3 firmware 1.0.0.187; the current CPU/GPU, fan and recovery changes were tested on Raven Odin 3 firmware 1.0.0.189 r2.

## Features

- Individual Odin 3 stick-ring segments: edit color, brightness and enabled state for each of the eight sections, with matching physical positions on both rings. Segment switches apply immediately, disable the selected RGB channels when off, and retain color and brightness for re-enabling. Save them in LED profiles and apply them from the app or Raven LED tile. An event-driven service restores active segment patterns after standby and yields to stock LED changes. See [segment controls](docs/LED-segment-profiles.md).

- Performance tab on compatible Odin 3 devices: CPU-cluster and GPU minimum/maximum sliders with supported frequency steps, three protected stock modes and separate custom profiles. Stock settings can be edited as drafts but must be saved under a custom name.
- Custom performance profiles use a foreground safety monitor and independent watchdog. At 78°C, invalid sensors or monitoring loss, recovery lowers ceilings to conservative limits; stock restoration after recovery requires valid temperatures below 60°C. Platform thermal protections remain enabled.
- Odin 3 compatibility uses live platform, frequency-table and control-interface checks instead of a firmware-number whitelist. After an update, Guided Setup verifies the existing original recovery backup without replacing it with an active custom profile.
- Odin 3 LED editing previews color and brightness live, and saved profiles can enable either stick ring independently. Raven tile subtitles observe stock settings changes without waiting for hardware reads before opening.
- A restricted Game Assistant bridge exposes saved fan, LED and CPU profiles to compatible system Game Assistant builds. It reuses guarded application paths; support depends on the installed Game Assistant.

- Fan profile selector with protected OEM Quiet, Smart and Sport references, displayed without stock suffixes; save custom profiles separately without overwriting the stock names.
- Editable temperature-curve graph and optional manual speed. Standard custom operation requests off or 50–70% PWM. An optional extended range allows 10–100% while running and retains the fan safeguards. The live temperature uses the controller’s reading during a custom session. Odin 2 follows OEM sensor discovery and Odin 3 uses the CPU thermal sensor average.
- On Odin 2 firmware 400, four independently controlled zones: left side, left stick, right stick and right side. Each zone has its own color and brightness.
- Color-wheel selectors, hex entry, presets, an icon-orange default, Apply to all lights and direct Save-to-selected-light behavior on Odin 2.
- Separate saved fan and LED profiles. Selecting an LED profile applies it immediately.
- Raven Fan and Raven LED Quick Settings tiles. Tap for profiles; long-press opens the corresponding app tab. Selections leave the chooser open. Only its profile list scrolls; controls stay fixed. Back or an outside tap dismisses it.
- Separate analog/side-light switches in the Odin 2 LED tile chooser. Switch controls and selection indicators follow the app accent; popup titles, profile names and light-switch labels use neutral text. Redundant on/off buttons are removed from the Fan chooser and Odin 2 LED chooser.
- Responsive analog/side switching: one switch no longer locks the other. Shared-color profiles skip the extra observer wait; independent colors retain it to preserve per-LED brightness and color. Group on/off settings persist through standby.
- Orange Raven icon with flat #1F2228 charcoal and rounded corners matching Raven Backup, shared with GitHub, adjustable accent, light/dark appearance and compact About popup.
- First-launch guided setup with a fan-control warning, an explanation and permission prompt for custom fan notifications, and an option to skip fan control. Original recovery backups are retained outside app storage. Settings → Advanced contains setup and recovery actions.

## Device support

| Device | Accepted software | Status |
| --- | --- | --- |
| Odin 2 (`Odin2`) | Android 13 / API 33, firmware 400 | Live fan, four-zone LED, profile, recovery and tile checks performed. Physical colors and independent brightness were also confirmed by the owner during development. |
| Odin 3 (`Odin3`) | Android 15 / API 35 with expected stock interfaces | Firmware 1.0.0.187 tested for cooling, two-ring LEDs, standby and Quick Settings; Raven 1.0.0.189 r2 tested for stock/custom CPU/GPU profiles, conservative recovery, fan control and firmware-update backup verification. CPU/GPU tuning also requires the expected CQ8725S platform and stock/recovery frequency steps. |
| Other devices | — | Hardware controls are blocked; compatibility is not assumed. |

Hardware controls currently require the primary Android user (user 0).

**Fan safety:** manipulating fan settings can cause overheating and device damage. Use supervised tests and keep platform thermal protections enabled. Custom fan control requests OEM Smart cooling on temperature reaching 80°C, sensor loss, a missed heartbeat or user switch; its safeguards do not guarantee protection. A stock fan-mode change ends Raven's custom control. There is no automatic boot start.

### Quick Settings popup permissions

The app uses AYN's existing `PServerBinder` privileged service for hardware commands. On Odin 2/API 33, it also attempts to launch a temporary **UID 0 window renderer** through that service so the chooser can appear above the open shade. The main app keeps its normal Android UID and validates profile requests. The renderer closes on dismissal, screen-off/lock, parent-app exit or inactivity.

This does not install root software, unlock the bootloader, modify SystemUI or firmware, disable SELinux, or add a boot hook. Access still depends on the OEM service and runtime policy. If startup is denied, the helper exits or startup times out after eight seconds, Raven falls back to Android's normal tile dialog, which closes the shade.

Odin 2 above-shade rendering and forced helper-exit/timeout fallbacks were tested. Odin 3 uses Android’s normal tile dialog directly, which closes the shade.

### Lighting limits

- Odin 2 four-zone writes use the verified stock controller groups. Individual segments inside a stick ring are not exposed.
- Odin 3 supports separate left/right stick ring colors and brightness. The Odin 2 side-light switches are specific to Odin 2.
- RGB charging-indicator control is not exposed in this release.
- OEM lighting changes can override Raven colors. The app observes stock settings to reflect external changes; driver-only changes may not be readable.

## Getting started

1. Install the APK on the supported device and complete the first-launch guided setup. Fan control can be skipped.
2. Allow fan notifications before applying a custom curve. Begin with a short supervised test; **Use Smart** hands control to OEM Smart cooling.
3. In **LEDs**, select a zone, choose a color/brightness and press **Save**. On Odin 2, Save applies the selected light immediately. On Odin 3, color and brightness edits preview live; Save retains the selected edit. Each ring can also be enabled or disabled in a saved profile. **Apply to all lights** applies it to all supported zones.
4. Save fan and LED profiles in their respective tabs. Add **Raven Fan** and **Raven LED** from Android's Quick Settings tile editor.
5. Use **Settings → Advanced → Rerun Guided Setup** to repeat checks. Recovery actions are in the same section.

## Recovery and uninstalling

**Factory Restore Fan** and **Factory Restore LEDs** restore the original settings retained during setup, not a universal firmware default. Repeating setup does not replace a valid original backup with an active Raven profile. The root-owned recovery backup under `/data/local/odin-controls/recovery` is retained across normal uninstall/reinstall; damaged or incompatible backups are rejected.

Before uninstalling, choose **Use Smart** or the appropriate recovery action and confirm the result. Uninstall cannot run app cleanup. LED restoration is not automatic after uninstall or force-stop. An existing compatible recovery backup can be used after reinstalling; this is recovery support, not a guarantee that an uninstall callback runs.

## Build and verification

See [BUILDING.md](BUILDING.md). Java sources, Android resources, shell helper, GPL text and host tests are included in [Source](Source). OEM APKs, firmware images, private signing keys, raw device logs and personal data are not distributed.

The build runs fan-policy, editable-curve and 4,096 appearance-contrast checks, and protected Performance profile checks, then verifies APK signing/alignment. Host shell fixtures cover fan leases, takeover/recovery, four-zone colors and brightness, independent group masks, invalid input and immutable setup backups. Odin 2 checks include saved-profile application, single-zone saves, separate analog/side toggles, native fallback dialogs, above-shade popups and dismissal. Odin 3 checks include CPU-average cooling, Smart handoff, separate ring colors and brightness, standby and standard tile dialogs. The first-launch notification prompt was tested on Odin 2.

The APK retains the previous release's signing identity, so it can update 0.1.6-preview in place. A build signed with your own development key cannot update the published APK directly.

The 0.2.49-preview Odin 3 checks covered all three stock performance modes, guarded conservative CPU/GPU caps, conservative recovery, Quiet/Smart/Sport, manual and curve cooling, Smart handoff, and the firmware-change notice. Saved profiles/settings and the original recovery backup were preserved. Compatibility fixtures accept matching interfaces across firmware labels and reject changed topology, GPU ordering, PWM periods and corrupted backups.

The 0.2.50-preview update fixes late CPU-session cleanup changing restored stock controls back to read-only, which could block the next custom profile when reentering a game. Cleanup for a disarmed or replaced token now leaves the current controls unchanged. Live Odin 3 firmware 189 r2 checks passed for repeated game entry/exit, custom fan/LED/CPU overrides, restoration of active Raven global profiles, and Follow global selections. Isolated and live recovery checks confirm that current-session safety recovery still applies conservative limits and stock permissions remain intact afterward. Original saved profiles, settings and recovery backups were retained. These new checks were performed on Odin 3 with the compatible custom Game Assistant; Odin 2 regression checks were completed on 0.2.49-preview.

The 0.2.51-preview update adds mapped individual stick-ring segments on compatible Odin 3 hardware. Live single-section previews, Save/profile reload, off/on, standby restoration, stock takeover, the actual Raven LED chooser and separate left-ring switching were checked on Raven firmware 189 r2. Existing profiles and the recovery backup were preserved; test profiles were removed and both rings restored off. The compact Power spacing is included in the app fallback chooser; the matching firmware-hosted popup change is prepared for a later firmware update.

The 0.2.52-preview update makes individual segment switches apply live, merges successive switch changes, and preserves neighboring sections. Disabled sections now have zero PWM and their RGB driver channels disabled; their saved colors and brightness return when switched on. Live on/off and rapid-switch checks passed on Odin 3 firmware 189 r2. Original lighting, the four existing saved profiles and the recovery backup were preserved.

The 0.2.53-preview update fixes the brief wake flash when all ring sections are disabled by also clearing the stock ring-enable flags for empty rings. Each stick toggle switches off with its last enabled section and back on when a section is re-enabled. Both mixed-disabled and all-disabled standby/wake checks passed without flashes on the exact final APK on Odin 3 firmware 189 r2 before publication. Original rainbow lighting, all four saved profiles and the recovery backup were verified restored.

## License and attribution

Created by **Rsotbiemrptson**. Source and project artwork are licensed under **GNU GPL version 3 only**; see [LICENSE](LICENSE) and [NOTICE](NOTICE).

The icon, portions of the interface, color wheel and contrast helpers are adapted from [Raven Backup](https://github.com/Rsotbiemrptson/RavenBackup), also GPL-3.0-only. Raven Backup, Raven App Control and Raven Gamepad are separate applications.
