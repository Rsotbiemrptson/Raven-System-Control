#!/system/bin/sh
# Stock settings and verified Odin2 LED groups. No flashing or boot hooks.
set -u
OP=${1:-}
D=/data/local/odin-controls
R=$D/run
B=$D/recovery
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
valid_points() {
 printf '%s\n' "$1" | awk -F, 'NF<3||NF>8{exit 1} {last=34;speed=0;for(i=1;i<=NF;i++){if($i !~ /^[0-9]+:[0-9]+$/)exit 1;split($i,p,":");if(p[1]<35||p[1]>78||p[1]<last+1||p[2]<speed||(p[2]!=0&&(p[2]<50||p[2]>70)))exit 1;last=p[1];speed=p[2]}if(speed<50)exit 1}'
}
points_percent() {
 awk -v curve="$points" -v temp="$temp" -v was="$was" 'BEGIN{n=split(curve,a,",");first=0;for(i=1;i<=n;i++){split(a[i],p,":");t[i]=p[1]+0;s[i]=p[2]+0;if(!first&&s[i]>0)first=i}if(!was&&temp<t[first]){print 0;exit}if(temp<=t[1]){print s[1];exit}for(i=2;i<=n;i++)if(temp<=t[i]){if(s[i-1]==0){print (was&&temp>t[i-1]?s[i]:temp<t[i]?0:s[i]);exit}print int(s[i-1]+(s[i]-s[i-1])*(temp-t[i-1])/(t[i]-t[i-1]));exit}print s[n]}'
}
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
save_lighting() {
 mkdir -p "$D" && chmod 700 "$D" || fail 'Cannot protect lighting snapshot'
 if [ ! -d "$D/lights" ]; then
  mkdir "$D/lights.pending" || fail 'An incomplete lighting snapshot exists; inspect before continuing'
  if [ "$lighting" = shared ]; then
   snapshot "$D/lights.pending/colors" led_light_picker_color && snapshot "$D/lights.pending/left" left_joystick_light_enabled && snapshot "$D/lights.pending/right" right_joystick_light_enabled || fail 'Cannot save original lighting'
   mkdir "$D/lights.pending/handles" && snapshot "$D/lights.pending/handles/left" left_handle_light_enabled && snapshot "$D/lights.pending/handles/right" right_handle_light_enabled || fail 'Cannot save original side-light settings'
   echo shared > "$D/lights.pending/format"
  else
   snapshot "$D/lights.pending/colors" joystick_led_light_picker_color && snapshot "$D/lights.pending/enabled" joystick_light_enabled || fail 'Cannot save original lighting'
  fi
  snapshot "$D/lights.pending/brightness" led_light_brightness_percent || fail 'Cannot save original brightness'
  mv "$D/lights.pending" "$D/lights" || fail 'Cannot commit original lighting snapshot'
 fi
 # Older previews did not change side enables or snapshot them. Capture both
 # atomically before the first explicit side-light request in an older journal.
 if [ "$lighting" = shared ] && { [ "$side" != keep ] || [ "$OP" = light-zones ] || [ "$OP" = light-one ]; } && [ ! -d "$D/lights/handles" ]; then
  mkdir "$D/lights/handles.pending" && snapshot "$D/lights/handles.pending/left" left_handle_light_enabled && snapshot "$D/lights/handles.pending/right" right_handle_light_enabled || fail 'Cannot extend original side-light snapshot'
  mv "$D/lights/handles.pending" "$D/lights/handles" || fail 'Cannot commit side-light snapshot'
 fi
}
write_zone() {
  level=${4:-$brightness}
  r=$(( ((0x$3 >> 16) & 255) * level / 100 ))
  g=$(( ((0x$3 >> 8) & 255) * level / 100 ))
  b=$(( (0x$3 & 255) * level / 100 ))
  echo "$2-$r:$g:$b:255" > "$1"
 }

backup_keys() {
 if [ "$lighting" = shared ]; then echo 'fan_mode fan_speed smart_fan_mode_switch led_light_picker_color led_light_brightness_percent left_joystick_light_enabled right_joystick_light_enabled left_handle_light_enabled right_handle_light_enabled';else echo 'fan_mode fan_speed smart_fan_mode_switch joystick_led_light_picker_color led_light_brightness_percent joystick_light_enabled';fi
}
validate_backup() {
 path=$1
 [ -d "$path" ] && [ ! -L "$path" ] && [ "$(stat -c %u "$path")" = 0 ] || fail 'Recovery backup is missing or not protected'
 [ "$(cat "$path/model")" = "$model" ] && [ "$(cat "$path/sdk")" = "$sdk" ] && [ "$(cat "$path/build")" = "$(getprop ro.build.display.id)" ] || fail 'Recovery backup belongs to another device or firmware'
 for key in $(backup_keys) created; do [ -s "$path/$key" ] || fail 'Recovery backup is incomplete';done
 (cd "$path" && sha256sum -c SHA256SUMS >/dev/null 2>&1) || fail 'Recovery backup integrity check failed'
 case "$(cat "$path/fan_mode")" in null|0|1|4|5|6) ;; *) fail 'Invalid saved fan mode';; esac
 speed=$(cat "$path/fan_speed");[ "$speed" = null ] || { number "$speed" && [ "$speed" -le 50000 ]; } || fail 'Invalid saved fan speed'
 case "$(cat "$path/smart_fan_mode_switch")" in null|0|1) ;; *) fail 'Invalid saved fan setting';; esac
}
create_backup() {
 if [ -d "$B" ]; then validate_backup "$B";return;fi
 umask 077
 mkdir -p "$D" && chmod 700 "$D" || fail 'Cannot protect recovery storage'
 pending=$B.pending
 mkdir "$pending" || fail 'An incomplete setup backup exists; original records retained'
 for key in $(backup_keys); do snapshot "$pending/$key" "$key" || fail 'Cannot snapshot recovery settings';done
 if [ -d "$R" ]; then
  if [ -f "$R/smart" ]; then cat "$R/smart" > "$pending/smart_fan_mode_switch" || fail 'Cannot preserve original Smart mode';fi
  cat "$R/mode" > "$pending/fan_mode" && cat "$R/speed" > "$pending/fan_speed" || fail 'Cannot preserve original fan session settings'
 fi
 if [ -d "$D/lights" ]; then
  if [ "$lighting" = shared ]; then
   cat "$D/lights/colors" > "$pending/led_light_picker_color" && cat "$D/lights/brightness" > "$pending/led_light_brightness_percent" && cat "$D/lights/left" > "$pending/left_joystick_light_enabled" && cat "$D/lights/right" > "$pending/right_joystick_light_enabled" || fail 'Cannot preserve original lighting settings'
   if [ -d "$D/lights/handles" ]; then cat "$D/lights/handles/left" > "$pending/left_handle_light_enabled" && cat "$D/lights/handles/right" > "$pending/right_handle_light_enabled" || fail 'Cannot preserve original side-light settings';fi
  else
   cat "$D/lights/colors" > "$pending/joystick_led_light_picker_color" && cat "$D/lights/brightness" > "$pending/led_light_brightness_percent" && cat "$D/lights/enabled" > "$pending/joystick_light_enabled" || fail 'Cannot preserve original lighting settings'
  fi
 fi
 printf '%s' "$model" > "$pending/model";printf '%s' "$sdk" > "$pending/sdk";getprop ro.build.display.id > "$pending/build";date -u +%Y-%m-%dT%H:%M:%SZ > "$pending/created"
 (cd "$pending" && sha256sum $(backup_keys) model sdk build created > SHA256SUMS) || fail 'Cannot verify setup backup'
 validate_backup "$pending"
 mv "$pending" "$B" || fail 'Cannot commit setup backup'
}
clear_light_journal() {
 [ -d "$D/lights" ] || return 0
 if [ -d "$D/lights/handles" ]; then rm -f "$D/lights/handles/left" "$D/lights/handles/right";rmdir "$D/lights/handles" || fail 'Unexpected side-light snapshot files; retained';fi
 rm -f "$D/lights/colors" "$D/lights/brightness" "$D/lights/enabled" "$D/lights/left" "$D/lights/right" "$D/lights/format" "$D/lights/direct" "$D/lights/active-l" "$D/lights/active-r"
 rmdir "$D/lights" || fail 'Unexpected lighting snapshot files; retained'
}
restore_backup_lights() {
 validate_backup "$B"
 if [ "$lighting" = shared ]; then
  echo 0 > /sys/class/sn3112l/led/enable && echo 0 > /sys/class/sn3112r/led/enable || fail 'Controller reset failed; backup retained'
  put right_handle_light_enabled 0 && put left_handle_light_enabled 0 && put right_joystick_light_enabled 0 && put left_joystick_light_enabled 0 || fail 'Lighting reset failed; backup retained'
  for key in led_light_picker_color led_light_brightness_percent right_handle_light_enabled left_handle_light_enabled right_joystick_light_enabled left_joystick_light_enabled; do restore_key "$B/$key" "$key" || fail 'Lighting recovery failed; backup retained for retry';done
 else
  put joystick_light_enabled '0,0' || fail 'Lighting reset failed; backup retained'
  for key in joystick_led_light_picker_color led_light_brightness_percent joystick_light_enabled; do restore_key "$B/$key" "$key" || fail 'Lighting recovery failed; backup retained for retry';done
 fi
 clear_light_journal
}
cleanup() {
 result=$?
 trap - EXIT INT TERM
 recovery=0
 if [ -f "$R/armed" ] && [ "$reason" != system-control-changed ]; then
  if [ "$reason" = stopped ]; then
   if ! { restore_key "$R/speed" fan_speed && { [ ! -f "$R/smart" ] || restore_key "$R/smart" smart_fan_mode_switch; } && restore_key "$R/mode" fan_mode; }; then
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
 rm -f "$R/armed" "$R/mode" "$R/speed" "$R/smart" "$R/token" "$R/lease" "$R/lease.tmp" "$R/stop" "$R/config" "$R/config.tmp" "$R/pid" "$R/status" "$R/recovery"
 rmdir "$R" 2>/dev/null || true
 [ "$OP" != stop ] || echo 'OK Previous fan settings restored'
 exit "$result"
}
gate
case "${1:-}" in
oem-sensor)
 # Same ordered discovery as the Odin2 OEM CpuTemperatureUtils. Stop rather
 # than silently using a different source if an unsupported alternate wins.
 for path in /sys/class/thermal/thermal_zone1/temp /sys/devices/system/cpu/cpu0/cpufreq/cpu_temp /sys/devices/system/cpu/cpu0/cpufreq/FakeShmoo_cpu_temp /sys/class/thermal/thermal_zone0/temp /sys/class/i2c-adapter/i2c-4/4-004c/temperature /sys/devices/platform/tegra-i2c.3/i2c-4/4-004c/temperature /sys/devices/platform/omap/omap_temp_sensor.0/temperature /sys/devices/platform/tegra_tmon/temp1_input /sys/kernel/debug/tegra_thermal/temp_tj /sys/devices/platform/s5p-tmu/temperature /sys/class/hwmon/hwmon0/device/temp1_input /sys/class/thermal/thermal_zone3/temp /sys/class/thermal/thermal_zone4/temp; do
  raw=$(cat "$path" 2>/dev/null) || continue
  printf '%s\n' "$raw" | awk '{if($0 !~ /^-?[0-9]+([.][0-9]+)?$/)exit 1;t=$1;if(t < -30 || t > 250)t=t/1000;exit !(t>=-30&&t<=250&&t!=0)}' || continue
  case "$path" in /sys/class/thermal/thermal_zone*/temp) zone=${path%/temp};zone=${zone##*thermal_zone};echo "OK $zone";exit 0;; *) fail 'The OEM temperature source is not a supported thermal-zone interface';; esac
 done
 fail 'The OEM temperature source is unavailable'
 ;;
stock-fan)
 [ "$#" -eq 2 ] || fail 'Invalid stock fan request'
 case "$2" in 0|1|4|5) ;; *) fail 'Unknown stock fan mode';; esac
 fan_gate
 create_backup
 sh "$0" stop > "$D/stock-stop.log" 2>&1 || fail 'Cannot stop custom cooling'
 mode=$2;smart=0;[ "$mode" != 4 ] || smart=1
 put smart_fan_mode_switch "$smart" && put fan_mode "$mode" || fail 'Stock fan mode could not be applied'
 echo 'OK Stock fan mode selected'
 ;;
start2|update2)
 [ "$#" -eq 2 ] && valid_token "$2" || fail 'Invalid session token'
 read -r kind zone off on full low high manual points extra < "$(dirname "$0")/curve.cfg" || fail 'Curve configuration is missing'
 [ -z "${extra:-}" ] || fail 'Unexpected curve fields'
 case "$kind" in curve|manual) ;; *) fail 'Invalid curve mode';; esac
 for n in "$zone" "$off" "$on" "$full" "$low" "$high" "$manual"; do number "$n" || fail 'Invalid curve configuration';done
 [ "$zone" -le 999 ] && [ "$manual" -ge 50 ] && [ "$manual" -le 70 ] || fail 'Invalid sensor or manual speed'
 valid_points "$points" || fail 'Invalid curve points'
 [ -r "/sys/class/thermal/thermal_zone$zone/temp" ] || fail 'Temperature sensor unavailable'
 if [ "$OP" = start2 ]; then
  started=$(sh "$0" start "$2" "$kind" "$zone" 45 50 75 50 70 "$manual") || fail "$started"
 else
  [ "$(cat "$R/token" 2>/dev/null)" = "$2" ] && [ ! -f "$R/stop" ] || fail 'Fan session ended'
 fi
 printf '%s %s %s %s %s %s %s %s %s\n' "$kind" "$zone" 45 50 75 50 70 "$manual" "$points" > "$R/config.tmp" && mv "$R/config.tmp" "$R/config" || fail 'Cannot update protected curve'
 echo 'OK Cooling profile applied'
 ;;
backup-create)
 create_backup
 catalog=$D/stock-fan-v1
 if [ ! -d "$catalog" ]; then
  umask 077;mkdir "$catalog.pending" || fail 'Incomplete stock fan catalog retained'
  printf 'model=%s\nbuild=%s\nQuiet=1\nSmart=4\nSport=5\n' "$model" "$(getprop ro.build.display.id)" > "$catalog.pending/modes"
  if [ "$model" = Odin2 ]; then printf 'period=50000\nQuiet duty=5000\nSport duty=25000\nSmart uses stock temperature algorithm; no editable firmware curve file\n' >> "$catalog.pending/modes";fi
  (cd "$catalog.pending" && sha256sum modes > SHA256SUMS) && mv "$catalog.pending" "$catalog" || fail 'Cannot retain stock fan catalog'
 fi
 (cd "$catalog" && sha256sum -c SHA256SUMS >/dev/null 2>&1) || fail 'Stock fan catalog integrity check failed'
 echo "OK Recovery backup ready: $(cat "$B/created")"
 ;;
backup-status)
 validate_backup "$B"
 echo "OK Recovery backup ready: $(cat "$B/created")"
 ;;
fan-recover)
 validate_backup "$B"
 sh "$0" stop > "$D/recovery-stop.log" 2>&1 || fail 'Stop the current fan session before recovery'
 [ ! -d "$R" ] || fail 'Fan session stop is not confirmed'
 if ! { restore_key "$B/fan_speed" fan_speed && restore_key "$B/smart_fan_mode_switch" smart_fan_mode_switch && restore_key "$B/fan_mode" fan_mode; }; then put fan_mode 4 || true;fail 'Fan recovery failed; automatic cooling requested and backup retained';fi
 echo 'OK Fan settings restored from setup backup'
 ;;
probe)
 fan_gate
 mode=$(get fan_mode);speed=$(get fan_speed)
 case "$mode" in null|0|1|4|5|6) ;; *) fail 'Unrecognized OEM fan mode';; esac
 [ "$(cat /sys/class/gpio5_pwm2/period)" = 50000 ] || fail 'Unexpected fan PWM period'
 echo "OK model=$model sdk=$sdk lighting=$lighting fan_mode=$mode fan_speed=$speed side_left=$(get left_handle_light_enabled) side_right=$(get right_handle_light_enabled) SELinux=$(getenforce)"
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
 snapshot "$R/mode" fan_mode && snapshot "$R/speed" fan_speed && snapshot "$R/smart" smart_fan_mode_switch || fail 'Cannot snapshot stock settings'
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
 was=0;last_start=0;expected_mode=;expected_speed=
 while :; do
  [ ! -f "$R/stop" ] || { reason=stopped; exit 0; }
  [ "$(am get-current-user 2>/dev/null)" = 0 ] || { reason=user-changed; exit 0; }
  if [ -n "$expected_mode" ] && { [ "$(get fan_mode)" != "$expected_mode" ] || [ "$(get fan_speed)" != "$expected_speed" ] || [ "$(get smart_fan_mode_switch)" = 1 ]; }; then reason=system-control-changed;exit 0;fi
  read -r kind zone off on full low high manual points < "$R/config"
  time=$(now);lease=$(cat "$R/lease" 2>/dev/null)
  number "$time" && number "$lease" || { reason=lease-invalid; exit 0; }
  [ "$((time-lease))" -le 15 ] && [ "$time" -ge "$lease" ] || { reason=app-lease-expired; exit 0; }
  raw=$(cat "/sys/class/thermal/thermal_zone$zone/temp" 2>/dev/null)
  number "$raw" || { reason=sensor-unavailable; exit 0; }
  temp=$raw;[ "$raw" -lt 1000 ] || temp=$((raw/1000))
  [ "$temp" -gt 0 ] && [ "$temp" -lt 80 ] || { reason=temperature-fallback; exit 0; }
  if [ "$kind" = manual ]; then percent=$manual
  elif [ -n "${points:-}" ]; then percent=$(points_percent)
  elif [ "$temp" -le "$off" ] || { [ "$was" -eq 0 ] && [ "$temp" -lt "$on" ]; }; then percent=0
  elif [ "$temp" -le "$on" ]; then percent=$low
  elif [ "$temp" -ge "$full" ]; then percent=$high
  else percent=$((low+(high-low)*(temp-on)/(full-on))); fi
  # Once running, avoid cycling the motor for at least 20 seconds.
  if [ "$percent" -eq 0 ] && [ "$was" -eq 1 ] && [ "$((time-last_start))" -lt 20 ]; then percent=$low; fi
  touch "$R/armed"
  put smart_fan_mode_switch 0 || exit 1
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
  if [ "$percent" -eq 0 ]; then expected_mode=0;expected_speed=$(get fan_speed);else expected_mode=6;expected_speed=$duty;fi
  printf '%s°C · fan speed %s%%' "$temp" "$percent" > "$R/status"
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
 [ "$#" -eq 4 ] || [ "$#" -eq 5 ] || fail 'Invalid lighting request'
 left=$2;right=$3;brightness=$4;side=${5:-keep}
 case "$side" in keep|on|off) ;; *) fail 'Invalid side-light request';; esac
 [ "$lighting" = shared ] || [ "$side" = keep ] || fail 'Side-light control is supported on Odin 2 firmware 400 only'
 [ "${#left}" = 6 ] && [ "${#right}" = 6 ] || fail 'Use six-digit RGB colors'
 case "$left$right" in *[!0-9a-fA-F]*) fail 'Invalid RGB color';; esac
 number "$brightness" && [ "$brightness" -le 100 ] || fail 'Invalid brightness'
 [ "$lighting" != shared ] || [ "$left" = "$right" ] || fail 'Odin 2 firmware 400 uses one shared stock color for both rings'
 [ -e /sys/class/sn3112l/led/brightness ] && [ -e /sys/class/sn3112r/led/brightness ] || fail 'Expected ring-light interfaces not found'
 save_lighting
 # Only two stock ring settings; no undocumented channel/index writes.
 value=$(awk "BEGIN {printf \"%.2f\", $brightness/100}")
 if [ "$lighting" = shared ]; then
  color=$((0xFF$left-4294967296))
  put led_light_picker_color "$color" && put led_light_brightness_percent "$value" && put right_joystick_light_enabled 1 && put left_joystick_light_enabled 1 || fail 'Lighting settings write failed; restore the snapshot'
  case "$side" in
   on) put right_handle_light_enabled 1 && put left_handle_light_enabled 1 || fail 'Side-light enable failed; restore the snapshot';;
   off) put right_handle_light_enabled 0 && put left_handle_light_enabled 0 || fail 'Side-light disable failed; restore the snapshot';;
  esac
  echo "OK Shared ring color applied through stock settings. Side lights: $side. Enabled side lights share the stick color and brightness. side_left=$(get left_handle_light_enabled) side_right=$(get right_handle_light_enabled)"
 else
  put joystick_led_light_picker_color "#$left,#$right" && put led_light_brightness_percent "$value" && put joystick_light_enabled '1,1' || fail 'Lighting settings write failed; restore the snapshot'
  echo 'OK Left and right ring colors applied through stock settings'
 fi
 ;;
light-zones)
 [ "$lighting" = shared ] || fail 'Four-zone lighting requires Odin 2 firmware 400'
 [ "$#" -eq 7 ] || [ "$#" -eq 10 ] || [ "$#" -eq 11 ] || fail 'Invalid four-zone lighting request'
 left=$2;right=$3;handle_left=$4;handle_right=$5;brightness=$6
 rings=on
 if [ "$#" -eq 11 ]; then rings=${11};fi
 case "$rings" in on|off) ;; *) fail 'Invalid analog-light request';; esac
 if [ "$#" -ge 10 ]; then right_b=$7;handle_left_b=$8;handle_right_b=$9;side=${10};else right_b=$brightness;handle_left_b=$brightness;handle_right_b=$brightness;side=$7;fi
 for color in "$left" "$right" "$handle_left" "$handle_right"; do
  [ "${#color}" = 6 ] || fail 'Use six-digit RGB colors'
  case "$color" in *[!0-9a-fA-F]*) fail 'Invalid RGB color';; esac
 done
 for level in "$brightness" "$right_b" "$handle_left_b" "$handle_right_b"; do number "$level" && [ "$level" -le 100 ] || fail 'Invalid brightness';done
 case "$side" in keep|on|off) ;; *) fail 'Invalid side-light request';; esac
 [ -w /sys/class/sn3112l/led/brightness ] && [ -w /sys/class/sn3112r/led/brightness ] || fail 'Expected writable lighting interfaces not found'
 save_lighting
 touch "$D/lights/direct" || fail 'Cannot retain direct-lighting recovery marker'
 value=$(awk "BEGIN {printf \"%.2f\", $brightness/100}")
 color=$((0xFF$left-4294967296))
 ring_enable=0;[ "$rings" = on ] && ring_enable=1
 put led_light_picker_color "$color" && put led_light_brightness_percent "$value" && put right_joystick_light_enabled "$ring_enable" && put left_joystick_light_enabled "$ring_enable" || fail 'Lighting enable failed; original settings retained for Restore'
 case "$side" in
  on) put right_handle_light_enabled 1 && put left_handle_light_enabled 1 || fail 'Side-light enable failed; original settings retained for Restore';;
  off) put right_handle_light_enabled 0 && put left_handle_light_enabled 0 || fail 'Side-light disable failed; original settings retained for Restore';;
 esac
 # Allow the OEM setting observers to finish before setting independent colors.
 sleep 1
 [ "$rings" = on ] || { left=000000;right=000000; }
 [ "$(get left_handle_light_enabled)" = 1 ] || handle_left=000000
 [ "$(get right_handle_light_enabled)" = 1 ] || handle_right=000000
 write_zone /sys/class/sn3112l/led/brightness 1 "$handle_left" "$handle_left_b" && write_zone /sys/class/sn3112l/led/brightness 2 "$left" "$brightness" && write_zone /sys/class/sn3112r/led/brightness 1 "$handle_right" "$handle_right_b" && write_zone /sys/class/sn3112r/led/brightness 2 "$right" "$right_b" || fail 'A color write failed; original settings retained. Use Restore lighting to recover'
 echo 1 > "$D/lights/active-l";echo 1 > "$D/lights/active-r"
 echo "OK Four-zone lighting on. side_left=$(get left_handle_light_enabled) side_right=$(get right_handle_light_enabled)"
 ;;
light-one)
 [ "$lighting" = shared ] || fail 'Individual Save requires Odin 2 firmware 400'
 [ "$#" -eq 4 ] || fail 'Invalid single-light request'
 target=$2;color=$3;brightness=$4;side=keep
 case "$target" in 0) controller=l;group=2;; 1) controller=r;group=2;; 2) controller=l;group=1;; 3) controller=r;group=1;; *) fail 'Invalid light target';; esac
 [ "${#color}" = 6 ] || fail 'Use six-digit RGB colors'
 case "$color" in *[!0-9a-fA-F]*) fail 'Invalid RGB color';; esac
 number "$brightness" && [ "$brightness" -le 100 ] || fail 'Invalid brightness'
 node=/sys/class/sn3112$controller/led
 [ -w "$node/brightness" ] && [ -w "$node/enable" ] || fail 'Expected writable lighting interfaces not found'
 save_lighting
 touch "$D/lights/direct" || fail 'Cannot retain direct-lighting recovery marker'
 active=0
 if [ "$(get left_joystick_light_enabled)" = 1 ] || [ "$(get left_handle_light_enabled)" = 1 ] || [ "$(cat "$D/lights/active-$controller" 2>/dev/null)" = 1 ]; then active=1;fi
 if [ "$active" = 0 ]; then
  # Re-enabling an off controller must not revive its other old color.
  echo 1 > "$node/enable" || fail 'Selected-light enable failed; original settings retained'
  other=$((3-group))
  write_zone "$node/brightness" "$other" 000000 || fail 'Cannot keep the other light off; original settings retained'
 fi
 write_zone "$node/brightness" "$group" "$color" || fail 'Selected-light write failed; original settings retained for Restore'
 echo 1 > "$D/lights/active-$controller" || fail 'Cannot retain selected-light state'
 echo 'OK Selected light color applied'
 ;;
light-off)
 [ "$#" -eq 1 ] || fail 'Invalid lighting-off request'
 side=off
 save_lighting
 if [ "$lighting" = shared ]; then
  put right_handle_light_enabled 0 && put left_handle_light_enabled 0 && put right_joystick_light_enabled 0 && put left_joystick_light_enabled 0 || fail 'Lighting disable failed; original settings retained for Restore'
 else
  put joystick_light_enabled '0,0' || fail 'Lighting disable failed; original settings retained for Restore'
 fi
 if [ "$lighting" = shared ] && [ -f "$D/lights/direct" ]; then
  echo 0 > /sys/class/sn3112l/led/enable && echo 0 > /sys/class/sn3112r/led/enable || fail 'Controller disable failed; original settings retained'
  echo 0 > "$D/lights/active-l";echo 0 > "$D/lights/active-r"
 fi
 echo "OK Lighting off. Your selected colors and brightness are kept. side_left=$(get left_handle_light_enabled) side_right=$(get right_handle_light_enabled)"
 ;;
restore-light)
 if [ -d "$B" ]; then restore_backup_lights;echo "OK Lighting restored from setup backup. side_left=$(get left_handle_light_enabled) side_right=$(get right_handle_light_enabled)";exit 0;fi
 [ -d "$D/lights" ] || { echo "OK No lighting snapshot to restore side_left=$(get left_handle_light_enabled) side_right=$(get right_handle_light_enabled)";exit 0; }
 if [ "$(cat "$D/lights/format" 2>/dev/null)" = shared ]; then
  if [ -f "$D/lights/direct" ]; then
   if [ ! -d "$D/lights/handles" ]; then side=on;save_lighting;fi
   echo 0 > /sys/class/sn3112l/led/enable && echo 0 > /sys/class/sn3112r/led/enable || fail 'Controller reset failed; snapshot retained'
   # Force a stock observer transition even if the original shared color and
   # enable settings already equal the settings beneath our direct colors.
   put right_handle_light_enabled 0 && put left_handle_light_enabled 0 && put right_joystick_light_enabled 0 && put left_joystick_light_enabled 0 || fail 'Lighting reset failed; snapshot retained'
  fi
  if [ -d "$D/lights/handles" ]; then
   restore_key "$D/lights/handles/right" right_handle_light_enabled && restore_key "$D/lights/handles/left" left_handle_light_enabled || fail 'Side-light restore failed; snapshot retained'
  fi
  restore_key "$D/lights/colors" led_light_picker_color && restore_key "$D/lights/brightness" led_light_brightness_percent && restore_key "$D/lights/right" right_joystick_light_enabled && restore_key "$D/lights/left" left_joystick_light_enabled || fail 'Lighting restore failed; snapshot retained'
 else
  restore_key "$D/lights/colors" joystick_led_light_picker_color && restore_key "$D/lights/brightness" led_light_brightness_percent && restore_key "$D/lights/enabled" joystick_light_enabled || fail 'Lighting restore failed; snapshot retained'
 fi
 clear_light_journal
 echo "OK Original lighting restored side_left=$(get left_handle_light_enabled) side_right=$(get right_handle_light_enabled)"
 ;;
*) fail 'Unknown operation';;
esac
