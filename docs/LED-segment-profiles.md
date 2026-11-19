# Odin 3 ring segments

On compatible Odin 3 hardware, enable **Individual segments** in the LED profile
card. Each stick has four physical positions, viewed from the front of the device:
top-left, top-right, bottom-right and bottom-left. The controller mapping differs
between sticks; Raven translates the positions so corresponding sections line up.

Tap a section to use the existing color wheel, presets, hex field and brightness
control. Complete six-digit hex colors apply to the live preview. Save commits
the change; Cancel restores the previous lighting while Raven still owns it.
Editing a whole stick changes its four sections together. Apply to all lights
changes every section of both rings. The small switches apply immediately and choose which sections a
saved profile enables. A disabled section retains its saved color and brightness
and its RGB driver channels are disabled. Turning it back on restores those values.

Save LED profile stores the eight section settings along with the ring switches.
Selecting the profile in the app or Raven LED quick tile applies it. Existing
whole-ring profiles retain their previous behavior. Segment profiles require
compatible Odin 3 controllers and are rejected on Odin 2. Profile export/import
preserves the optional eight-entry `ringSegments` array in physical position order.

While a segment pattern is active, a notification accompanies the service that
restores the colors after wake. It uses screen and settings events, without a
continuous polling loop. Changing stock LED settings yields control to the stock
driver; Raven does not keep overwriting that choice. With both rings off the
service stops. Force-stopping Raven prevents its wake restoration until Raven is
opened or a profile is applied again.

The feature uses the existing privileged service and detected HTR3212 interfaces.
It does not flash or replace firmware. Exact hardware register readback remains
unavailable; recovery continues to use the protected stock-settings backup.
