#!/system/bin/sh
# Stock settings only. No flashing, root installation, SELinux changes or boot hooks.
set -u
OP=${1:-}
D=/data/local/odin-controls
R=$D/run
fail() { echo "ERROR $*"; exit 1; }
get() { settings --user 0 get system "$1"; }
put() {
 settings --user 0 put system "$1" "$2" >/dev/null 2>&1 || return 1
 [ "$(get "$1")" = "$2" ]
}
restore_key() {
 value=$(cat "$1" 2>/dev/null) || return 1
 if [ "$value" = null ]; then
  settings --user 0 delete system "$2" >/dev/null 2>&1 && [ "$(get "$2")" = null ]
 else put "$2" "$value"; fi
}
now() { cut -d. -f1 /proc/uptime; }
number() { case "$1" in ''|*[!0-9]*) return 1;; esac; }
valid_token() { [ "${#1}" -eq 32 ] || return 1; case "$1" in *[!a-f0-9]*) return 1;; esac; }
gate() {
 [ "$(id -u)" = 0 ] || fail 'OEM service did not provide root'
 model=$(getprop ro.product.model);sdk=$(getprop ro.build.version.sdk)
 case "$model:$sdk" in
  Odin3:35) lighting=rings;;
  Odin2:33)
   [ "$(getprop ro.build.display.id)" = Odin2_Custom_V1.0.0.400 ] || fail 'Odin 2 support currently requires firmware 400'
   lighting=shared;;
  *) fail 'Supported targets: Odin 3 Android 15 or Odin 2 Android 13 firmware 400';;
 esac
 [ "$(am get-current-user 2>/dev/null)" = 0 ] || fail 'Switch to the primary Android user'
 [ ! -L "$D" ] || fail 'Refusing a symlink at the session directory'
 if [ -e "$D" ]; then
  [ -d "$D" ] && [ "$(stat -c %u "$D")" = 0 ] || fail 'Existing session directory is not owned by root'
 fi
}
fan_gate() {
 [ -r /sys/class/gpio5_pwm2/state ] && [ -r /sys/class/gpio5_pwm2/duty ] || fail 'Expected stock fan interface not found'
 [ -w /sys/class/gpio5_pwm2/state ] && [ -w /sys/class/gpio5_pwm2/duty ] || fail 'Fan interface is not writable in this security context'
}
snapshot() { get "$2" > "$1"; [ -s "$1" ]; }
cleanup() {
 result=$?
 trap - EXIT INT TERM
 recovery=0
 if [ -f "$R/armed" ]; then
  if [ "$reason" = stopped ]; then
   if ! { restore_key "$R/speed" fan_speed && restore_key "$R/mode" fan_mode; }; then
    reason=restore-failed;recovery=1
    put fan_mode 4 || reason=restore-and-automatic-failed
   fi
  else
   restore_key "$R/speed" fan_speed || { reason="$reason-speed-restore-failed";recovery=1; }
   put fan_mode 4 || { reason="$reason-automatic-failed";recovery=1; }
  fi
 fi
 printf '%s' "$reason" > "$D/last-result"
 if [ "$recovery" -eq 1 ]; then
  # Keep original values so a later explicit Stop can retry restoration.
  printf '%s' "$reason | recovery required; original settings retained" > "$R/status"
  touch "$R/recovery"
  echo "ERROR $reason; original settings retained. Retry Stop & restore."
  exit 1
 fi
 rm -f "$R/armed" "$R/mode" "$R/speed" "$R/token" "$R/lease" "$R/lease.tmp" "$R/stop" "$R/config" "$R/pid" "$R/status" "$R/recovery"
 rmdir "$R" 2>/dev/null || true
 [ "$OP" != stop ] || echo 'OK Previous fan settings restored'
 exit "$result"
}
gate
case "${1:-}" in
probe)
 fan_gate
 mode=$(get fan_mode);speed=$(get fan_speed)
 case "$mode" in null|0|1|4|5|6) ;; *) fail 'Unrecognized OEM fan mode';; esac
 [ "$(cat /sys/class/gpio5_pwm2/period)" = 50000 ] || fail 'Unexpected fan PWM period'
 echo "OK model=$model sdk=$sdk lighting=$lighting fan_mode=$mode fan_speed=$speed SELinux=$(getenforce)"
 ;;
start)
 fan_gate
 [ "$(cat /sys/class/gpio5_pwm2/period)" = 50000 ] || fail 'Unexpected fan PWM period'
 [ "$#" -eq 10 ] || fail 'Invalid session parameters'
 token=$2;kind=$3;zone=$4;off=$5;on=$6;full=$7;low=$8;high=$9;manual=${10}
 valid_token "$token" || fail 'Invalid session token'
 for n in "$zone" "$off" "$on" "$full" "$low" "$high" "$manual"; do number "$n" || fail 'Non-numeric policy'; done
 [ "$zone" -le 999 ] && [ "$off" -ge 35 ] && [ "$on" -ge 40 ] && [ "$off" -lt "$on" ] && [ "$on" -lt "$full" ] && [ "$full" -le 78 ] || fail 'Invalid temperature curve'
 [ "$low" -ge 50 ] && [ "$low" -le "$high" ] && [ "$high" -le 70 ] && [ "$manual" -ge 50 ] && [ "$manual" -le 70 ] || fail 'Invalid PWM range'
 case "$kind" in curve|manual) ;; *) fail 'Invalid control mode';; esac
 sensor=/sys/class/thermal/thermal_zone$zone/temp
 [ -r "$sensor" ] || fail 'Selected temperature sensor is unavailable'
 umask 077
 mkdir -p "$D" || fail 'Cannot create session directory'
 chmod 700 "$D" || fail 'Cannot protect session directory'
 mkdir "$R" 2>/dev/null || fail 'A session or recovery journal already exists. Stop and restore it first.'
 reason=start-failed
 trap cleanup EXIT INT TERM
 snapshot "$R/mode" fan_mode && snapshot "$R/speed" fan_speed || fail 'Cannot snapshot stock settings'
 printf '%s' "$token" > "$R/token"
 printf '%s %s %s %s %s %s %s %s\n' "$kind" "$zone" "$off" "$on" "$full" "$low" "$high" "$manual" > "$R/config"
 now > "$R/lease"
 cp "$0" "$D/hardware.sh" && chmod 700 "$D/hardware.sh" || fail 'Cannot stage session controller'
 # The protected root-owned copy is the only background executable.
 nohup sh "$D/hardware.sh" loop "$token" </dev/null >"$D/loop.log" 2>&1 &
 pid=$!;echo "$pid" > "$R/pid"
 kill -0 "$pid" 2>/dev/null || fail 'Controller did not start'
 trap - EXIT INT TERM
 echo 'OK Session started; waiting for sensor validation'
 ;;
lease)
 [ "$#" -eq 2 ] && valid_token "$2" && [ "$(cat "$R/token" 2>/dev/null)" = "$2" ] || fail 'Session ended'
 [ ! -f "$R/stop" ] || fail 'Session is stopping'
 now > "$R/lease.tmp" && mv "$R/lease.tmp" "$R/lease" || fail 'Cannot renew lease'
 echo "OK $(cat "$R/status" 2>/dev/null)"
 ;;
loop)
 [ "$#" -eq 2 ] && valid_token "$2" && [ "$(cat "$R/token" 2>/dev/null)" = "$2" ] || fail 'Invalid background session'
 read -r kind zone off on full low high manual < "$R/config"
 reason=controller-error
 trap cleanup EXIT
 trap 'reason=stopped; exit 0' INT TERM
 touch "$R/armed"
 was=0;last_start=0
 while :; do
  [ ! -f "$R/stop" ] || { reason=stopped; exit 0; }
  [ "$(am get-current-user 2>/dev/null)" = 0 ] || { reason=user-changed; exit 0; }
  time=$(now);lease=$(cat "$R/lease" 2>/dev/null)
  number "$time" && number "$lease" || { reason=lease-invalid; exit 0; }
  [ "$((time-lease))" -le 15 ] && [ "$time" -ge "$lease" ] || { reason=app-lease-expired; exit 0; }
  raw=$(cat "/sys/class/thermal/thermal_zone$zone/temp" 2>/dev/null)
  number "$raw" || { reason=sensor-unavailable; exit 0; }
  temp=$raw;[ "$raw" -lt 1000 ] || temp=$((raw/1000))
  [ "$temp" -gt 0 ] && [ "$temp" -lt 80 ] || { reason=temperature-fallback; exit 0; }
  if [ "$kind" = manual ]; then percent=$manual
  elif [ "$temp" -le "$off" ] || { [ "$was" -eq 0 ] && [ "$temp" -lt "$on" ]; }; then percent=0
  elif [ "$temp" -le "$on" ]; then percent=$low
  elif [ "$temp" -ge "$full" ]; then percent=$high
  else percent=$((low+(high-low)*(temp-on)/(full-on))); fi
  # Once running, avoid cycling the motor for at least 20 seconds.
  if [ "$percent" -eq 0 ] && [ "$was" -eq 1 ] && [ "$((time-last_start))" -lt 20 ]; then percent=$low; fi
  touch "$R/armed"
  if [ "$percent" -eq 0 ]; then
   [ "$(get fan_mode)" = 0 ] || put fan_mode 0 || exit 1
   was=0
  else
   [ "$was" -ne 0 ] || last_start=$time
   duty=$((percent*500))
   [ "$(get fan_speed)" = "$duty" ] || put fan_speed "$duty" || exit 1
   [ "$(get fan_mode)" = 6 ] || put fan_mode 6 || exit 1
   was=1
  fi
  printf '%s°C | requested %s%% PWM | actual duty %s' "$temp" "$percent" "$(cat /sys/class/gpio5_pwm2/duty 2>/dev/null)" > "$R/status"
  sleep 3
 done
 ;;
stop)
 if [ -d "$R" ]; then
  if [ -f "$R/recovery" ]; then reason=stopped;cleanup; fi
  touch "$R/stop"
  pid=$(cat "$R/pid" 2>/dev/null || true);token=$(cat "$R/token" 2>/dev/null || true)
  if number "$pid" && valid_token "$token" && tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null | grep -Fq "$D/hardware.sh loop $token"; then
   kill -TERM "$pid" 2>/dev/null || true
  elif ! number "$pid" || [ ! -d "/proc/$pid" ]; then reason=stopped;cleanup; fi
  count=0
  while [ -d "$R" ] && [ ! -f "$R/recovery" ] && [ "$count" -lt 50 ]; do sleep 0.1;count=$((count+1));done
  [ ! -d "$R" ] || fail "Stop not confirmed: $(cat "$R/status" 2>/dev/null). Original settings retained; retry Stop & restore."
 fi
 echo "OK No active fan session | $(cat "$D/last-result" 2>/dev/null)"
 ;;
status)
 if [ -d "$R" ]; then echo "OK $(cat "$R/status" 2>/dev/null)";else echo "OK Inactive | $(cat "$D/last-result" 2>/dev/null)";fi
 ;;
light)
 [ "$#" -eq 4 ] || fail 'Invalid lighting request'
 left=$2;right=$3;brightness=$4
 [ "${#left}" = 6 ] && [ "${#right}" = 6 ] || fail 'Use six-digit RGB colors'
 case "$left$right" in *[!0-9a-fA-F]*) fail 'Invalid RGB color';; esac
 number "$brightness" && [ "$brightness" -le 100 ] || fail 'Invalid brightness'
 [ "$lighting" != shared ] || [ "$left" = "$right" ] || fail 'Odin 2 firmware 400 uses one shared stock color for both rings'
 [ -e /sys/class/sn3112l/led/brightness ] && [ -e /sys/class/sn3112r/led/brightness ] || fail 'Expected ring-light interfaces not found'
 mkdir -p "$D" && chmod 700 "$D" || fail 'Cannot protect lighting snapshot'
 if [ ! -d "$D/lights" ]; then
  mkdir "$D/lights.pending" || fail 'An incomplete lighting snapshot exists; inspect before continuing'
  if [ "$lighting" = shared ]; then
   snapshot "$D/lights.pending/colors" led_light_picker_color && snapshot "$D/lights.pending/left" left_joystick_light_enabled && snapshot "$D/lights.pending/right" right_joystick_light_enabled || fail 'Cannot save original lighting'
   echo shared > "$D/lights.pending/format"
  else
   snapshot "$D/lights.pending/colors" joystick_led_light_picker_color && snapshot "$D/lights.pending/enabled" joystick_light_enabled || fail 'Cannot save original lighting'
  fi
  snapshot "$D/lights.pending/brightness" led_light_brightness_percent || fail 'Cannot save original brightness'
  mv "$D/lights.pending" "$D/lights" || fail 'Cannot commit original lighting snapshot'
 fi
 # Only two stock ring settings; no undocumented channel/index writes.
 value=$(awk "BEGIN {printf \"%.2f\", $brightness/100}")
 if [ "$lighting" = shared ]; then
  color=$((0xFF$left-4294967296))
  put led_light_picker_color "$color" && put led_light_brightness_percent "$value" && put right_joystick_light_enabled 1 && put left_joystick_light_enabled 1 || fail 'Lighting settings write failed; restore the snapshot'
  echo 'OK Shared ring color applied through Odin 2 stock settings. Shared color/brightness also affect enabled handle lights.'
 else
  put joystick_led_light_picker_color "#$left,#$right" && put led_light_brightness_percent "$value" && put joystick_light_enabled '1,1' || fail 'Lighting settings write failed; restore the snapshot'
  echo 'OK Left and right ring colors applied through stock settings'
 fi
 ;;
restore-light)
 [ -d "$D/lights" ] || { echo 'OK No lighting snapshot to restore';exit 0; }
 if [ "$(cat "$D/lights/format" 2>/dev/null)" = shared ]; then
  restore_key "$D/lights/colors" led_light_picker_color && restore_key "$D/lights/brightness" led_light_brightness_percent && restore_key "$D/lights/right" right_joystick_light_enabled && restore_key "$D/lights/left" left_joystick_light_enabled || fail 'Lighting restore failed; snapshot retained'
 else
  restore_key "$D/lights/colors" joystick_led_light_picker_color && restore_key "$D/lights/brightness" led_light_brightness_percent && restore_key "$D/lights/enabled" joystick_light_enabled || fail 'Lighting restore failed; snapshot retained'
 fi
 rm -f "$D/lights/colors" "$D/lights/brightness" "$D/lights/enabled" "$D/lights/left" "$D/lights/right" "$D/lights/format"
 rmdir "$D/lights" || fail 'Unexpected snapshot files; retained'
 echo 'OK Original lighting restored'
 ;;
*) fail 'Unknown operation';;
esac
