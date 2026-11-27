Configure routine status notifications separately from recovery alerts.

- Settings now offers independent Fan status, LED status, CPU/GPU status and Recovery alerts controls using Android notification settings.
- Custom fan and CPU/GPU profiles work with routine status notifications hidden. Foreground monitoring, fan leases, independent watchdogs, temperature limits and conservative recovery remain enabled.
- Guided Setup requests notification permission optionally for recovery alerts, rather than requiring visible fan notifications.
- LED apply and segment-restoration status share the LED status category. Control/recovery failures use separate recovery alerts.

Verified on the exact signed APK on Odin 3, Android 15, modified AYN firmware 1.0.0.187 (Raven build label 190):
- Hidden-status manual and curve fan control; heartbeat-loss Smart fallback and a separate recovery alert.
- Hidden-status conservative CPU/GPU profile, retained caps and watchdog recovery to conservative limits; separate recovery alert.
- Saved mixed-disabled segment profile applied from the app with LED status hidden; standby/wake returned the correct six sections with both disabled positions dark and no flash.
- Original saved profiles, editing draft, settings, notification preferences and green Backup App lighting restored. Protected recovery backup checksums unchanged; test profiles and tools removed.

This is a preview release. CPU/GPU tuning, Game Assistant integration and individual ring segments remain in preview. This candidate's notification changes were tested on Odin 3; a new Odin 2 regression was not performed.

README changes preserved. No screenshots included.
