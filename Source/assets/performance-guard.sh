# Performance watchdog and fail-closed recovery. Sourced after the device gate.
guard_file() {
 [ ! -L "$perf_folder/$1" ] || return 1
 echo "$2" > "$perf_folder/$1.pending" && mv "$perf_folder/$1.pending" "$perf_folder/$1"
}
guard_current_boot() {
 [ "$(perf_read "$perf_folder/guard-boot" 2>/dev/null)" = "$(perf_read /proc/sys/kernel/random/boot_id)" ]
}
guard_status() {
 if ! guard_current_boot; then echo stopped;return;fi
 cat "$perf_folder/guard-status" 2>/dev/null || echo stopped
}
guard_sensors() {
 guard_cpu=0;guard_gpu=0
 for guard_path in /sys/class/thermal/thermal_zone*/type; do
  read -r guard_type < "$guard_path" || return 1
  case "$guard_type" in
   *cpu*|*CPU*) guard_cpu=$((guard_cpu+1));;
   *gpu*|*GPU*) guard_gpu=$((guard_gpu+1));;
   *) continue;;
  esac
  echo "${guard_path%type}temp"
 done
 [ "$guard_cpu" -gt 0 ] && [ "$guard_gpu" -gt 0 ]
}
guard_temperature() {
 guard_peak=0;guard_count=0
 while IFS= read -r guard_sensor; do
  case "$guard_sensor" in /sys/class/thermal/thermal_zone[0-9]*/temp) ;; *) return 1;; esac
  read -r guard_sample < "$guard_sensor" || return 1
  number "$guard_sample" || return 1
  [ "$guard_sample" -gt 250 ] || guard_sample=$((guard_sample*1000))
  [ "$guard_sample" -gt 0 ] && [ "$guard_sample" -le 150000 ] || return 1
  [ "$guard_sample" -le "$guard_peak" ] || guard_peak=$guard_sample
  guard_count=$((guard_count+1))
 done < "$perf_folder/guard-sensors"
 [ "$guard_count" -gt 0 ] || return 1
 echo "$guard_peak"
}
guard_gpu_permissions() {
 if [ -f "$perf_folder/guard-gpu-permissions" ]; then
  read -r guard_gpu_boot guard_gpu_min_mode guard_gpu_max_mode < "$perf_folder/guard-gpu-permissions" || return 1
  if [ "$guard_gpu_boot" = "$(perf_read /proc/sys/kernel/random/boot_id)" ]; then
   perf_modes_valid "$guard_gpu_min_mode" "$guard_gpu_max_mode" 644 644 || return 1
   return 0
  fi
 fi
 guard_gpu_min_mode=$(stat -c %a "$perf_gpu/min_pwrlevel") && guard_gpu_max_mode=$(stat -c %a "$perf_gpu/max_pwrlevel") || return 1
 perf_modes_valid "$guard_gpu_min_mode" "$guard_gpu_max_mode" 644 644 || return 1
 [ "$((8#$guard_gpu_min_mode & 146))" -ne 0 ] && [ "$((8#$guard_gpu_max_mode & 146))" -ne 0 ] || return 1
 guard_file guard-gpu-permissions "$(perf_read /proc/sys/kernel/random/boot_id) $guard_gpu_min_mode $guard_gpu_max_mode"
}
guard_restore_gpu_permissions() {
 [ -f "$perf_folder/guard-gpu-permissions" ] || return 0
 guard_gpu_permissions || return 1
 chmod "$guard_gpu_min_mode" "$perf_gpu/min_pwrlevel" && chmod "$guard_gpu_max_mode" "$perf_gpu/max_pwrlevel" || return 1
 [ "$(stat -c %a "$perf_gpu/min_pwrlevel")" = "$guard_gpu_min_mode" ] && [ "$(stat -c %a "$perf_gpu/max_pwrlevel")" = "$guard_gpu_max_mode" ] || return 1
 rm -f "$perf_folder/guard-gpu-permissions"
}
guard_alive() {
 guard_pid=$(perf_read "$perf_folder/guard-pid" 2>/dev/null) || return 1
 number "$guard_pid" && kill -0 "$guard_pid" 2>/dev/null || return 1
 tr '\000' ' ' < "/proc/$guard_pid/cmdline" 2>/dev/null | grep -Fq "$D/performance-hardware.sh performance-watch $1" || return 1
 guard_sample_time=$(perf_read "$perf_folder/guard-sample" 2>/dev/null) || return 1
 number "$guard_sample_time" || return 1
 guard_time=$(now)
 [ "$guard_time" -ge "$guard_sample_time" ] && [ "$((guard_time-guard_sample_time))" -le 4 ]
}
guard_copy_scripts() {
 # Durable scripts survive app termination and scoped-storage removal.
 for guard_asset in hardware.sh performance.sh performance-guard.sh; do
  guard_destination=$guard_asset
  [ "$guard_asset" != hardware.sh ] || guard_destination=performance-hardware.sh
  [ ! -L "$D/$guard_destination" ] && [ ! -L "$D/$guard_destination.pending" ] || return 1
  cp "$(dirname "$0")/$guard_asset" "$D/$guard_destination.pending" && chmod 600 "$D/$guard_destination.pending" && mv "$D/$guard_destination.pending" "$D/$guard_destination" || return 1
 done
}
guard_prepare() {
 guard_new_token=$1;shift
 valid_token "$guard_new_token" && perf_validate "$@" || return 1
 if [ "$(guard_status)" = recovery ]; then guard_allow_stock || return 1;fi
 guard_paths=$(guard_sensors) || return 1
 guard_file guard-sensors "$guard_paths" || return 1
 guard_heat=$(guard_temperature) || return 1
 [ "$guard_heat" -lt 78000 ] || return 1
 guard_gpu_permissions && guard_copy_scripts || return 1
 guard_file guard-config "$*" && guard_file guard-boot "$(perf_read /proc/sys/kernel/random/boot_id)" && guard_file guard-lease "$(now)" && guard_file guard-temperature "$guard_heat" && guard_file guard-reason none && guard_file guard-status starting || return 1
 guard_file guard-token "$guard_new_token" || return 1
 nohup sh "$D/performance-hardware.sh" performance-watch "$guard_new_token" </dev/null > "$perf_folder/guard.log" 2>&1 &
 guard_started=$!
 guard_file guard-pid "$guard_started" || return 1
 for guard_attempt in 1 2 3 4 5 6 7 8 9 10; do
  if [ "$(perf_read "$perf_folder/guard-ready" 2>/dev/null)" = "$guard_new_token" ] && kill -0 "$guard_started" 2>/dev/null; then return 0;fi
  sleep .1
 done
 return 1
}
guard_disarm() {
 guard_file guard-status stopped && guard_file guard-token stopped
}
guard_allow_stock() {
 [ "$(guard_status)" = recovery ] || return 0
 if ! guard_heat=$(guard_temperature); then
  guard_paths=$(guard_sensors) && guard_file guard-sensors "$guard_paths" || return 1
  guard_heat=$(guard_temperature) || return 1
 fi
 [ "$guard_heat" -lt 60000 ]
}
guard_write_pair() {
 guard_min_path=$1;guard_max_path=$2;guard_min_value=$3;guard_max_value=$4;guard_owner_index=$5
 set -- $(perf_owner "$guard_owner_index")
 guard_min_uid=$1;guard_min_gid=$2;guard_min_mode=$(printf '%03o' "$((8#$3 & 365))")
 set -- $(perf_owner "$((guard_owner_index+1))")
 guard_max_uid=$1;guard_max_gid=$2;guard_max_mode=$(printf '%03o' "$((8#$3 & 365))")
 # The Odin bridge cannot write a read-only sysfs control. Open private handles
 # while root owns the nodes, then reinstate protection and ownership BEFORE
 # issuing any frequency writes. System-UID writers stay blocked throughout.
 chown 0:0 "$guard_min_path" "$guard_max_path" && chmod 600 "$guard_min_path" "$guard_max_path" || return 1
 exec 7> "$guard_min_path" 8> "$guard_max_path" || return 1
 chmod "$guard_min_mode" "$guard_min_path" && chmod "$guard_max_mode" "$guard_max_path" || return 1
 chown "$guard_min_uid:$guard_min_gid" "$guard_min_path" && chown "$guard_max_uid:$guard_max_gid" "$guard_max_path" || return 1
 echo "$guard_min_value" >&7 && echo "$guard_max_value" >&8 || return 1
 exec 7>&- 8>&-
}
guard_seal_controls() {
 [ -n "${perf_saved_owners:-}" ] || return 0
 set -- $perf_saved_owners
 guard_seal_result=0
 for guard_node in "$perf_cpu0/scaling_min_freq" "$perf_cpu0/scaling_max_freq" "$perf_cpu6/scaling_min_freq" "$perf_cpu6/scaling_max_freq" "$perf_gpu/min_pwrlevel" "$perf_gpu/max_pwrlevel"; do
  # Failure cleanup must restore protection before restoring ownership. This
  # prevents an interrupted private-handle setup giving vendor writers access.
  chmod "$(printf '%03o' "$((8#$3 & 365))")" "$guard_node" || guard_seal_result=1
  chown "$1:$2" "$guard_node" || guard_seal_result=1
  shift 3
 done
 return "$guard_seal_result"
}
guard_recover() {
 guard_token=$1;guard_reason=$2
 valid_token "$guard_token" || return 1
 [ "$(perf_read "$perf_folder/guard-token" 2>/dev/null)" = "$guard_token" ] && guard_current_boot || return 0
 [ "$(guard_status)" != stopped ] || return 0
 case "$guard_reason" in overheat|sensor-failure|app-unavailable|guard-stopped|limits-changed|apply-failed|monitor-stopped|legacy-session) ;; *) return 1;; esac
 guard_config=$(cat "$perf_folder/guard-config") || return 1
 set -- $guard_config
 perf_validate "$@" || return 1
 # Never change the system performance mode, widen a GPU ceiling, or make a
 # frequency control writable while recovering. Each new cap is no higher than
 # both the requested cap and its current effective ceiling.
 guard_cap0=$3;[ "$guard_cap0" -le 1785600 ] || guard_cap0=1785600
 guard_cap6=$5;[ "$guard_cap6" -le 1958400 ] || guard_cap6=1958400
 guard_cap_gpu=$7;[ "$guard_cap_gpu" -le 443000000 ] || guard_cap_gpu=443000000
 guard_actual0=$(perf_read "$perf_cpu0/scaling_max_freq") && guard_actual6=$(perf_read "$perf_cpu6/scaling_max_freq") || return 1
 guard_actual_gpu=$(perf_gpu_frequency "$(perf_read "$perf_gpu/max_pwrlevel")") || return 1
 perf_member "$guard_actual0" "$perf_cpu0_frequencies" && perf_member "$guard_actual6" "$perf_cpu6_frequencies" && perf_member "$guard_actual_gpu" "$perf_gpu_frequencies" || return 1
 [ "$guard_cap0" -le "$guard_actual0" ] || guard_cap0=$guard_actual0
 [ "$guard_cap6" -le "$guard_actual6" ] || guard_cap6=$guard_actual6
 [ "$guard_cap_gpu" -le "$guard_actual_gpu" ] || guard_cap_gpu=$guard_actual_gpu
 guard_write_pair "$perf_cpu0/scaling_min_freq" "$perf_cpu0/scaling_max_freq" "$perf_cpu0_low" "$guard_cap0" 0 || return 1
 guard_write_pair "$perf_cpu6/scaling_min_freq" "$perf_cpu6/scaling_max_freq" "$perf_cpu6_low" "$guard_cap6" 2 || return 1
 guard_gpu_index=$(perf_gpu_index "$guard_cap_gpu") || return 1
 guard_gpu_slowest=$(echo "$perf_gpu_frequencies" | awk '{print NF-1}') || return 1
 guard_write_pair "$perf_gpu/min_pwrlevel" "$perf_gpu/max_pwrlevel" "$guard_gpu_slowest" "$guard_gpu_index" 4 || return 1
 # Retain read permissions, remove write permissions, and preserve original
 # modes in the existing boot-scoped journals for explicit Stock restoration.
 for guard_node in "$perf_cpu0/scaling_min_freq" "$perf_cpu0/scaling_max_freq" "$perf_cpu6/scaling_min_freq" "$perf_cpu6/scaling_max_freq" "$perf_gpu/min_pwrlevel" "$perf_gpu/max_pwrlevel"; do
  guard_mode=$(stat -c %a "$guard_node") || return 1
  case "$guard_mode" in [0-7][0-7][0-7]) ;; *) return 1;; esac
  chmod "$(printf '%03o' "$((8#$guard_mode & 365))")" "$guard_node" || return 1
 done
 perf_verify_cpu "$perf_cpu0" "$perf_cpu0_low" "$guard_cap0" && perf_verify_cpu "$perf_cpu6" "$perf_cpu6_low" "$guard_cap6" || return 1
 [ "$(perf_read "$perf_gpu/max_pwrlevel")" = "$guard_gpu_index" ] || return 1
 sh "$D/performance-hardware.sh" stock-fan 4 >/dev/null 2>&1 || return 1
 guard_file guard-reason "$guard_reason" && guard_file guard-status recovery
}
guard_watch_cleanup() {
 if [ "$guard_clean_exit" = 0 ]; then
  sh "$D/performance-hardware.sh" performance-recover "$guard_watch_token" guard-stopped >> "$perf_folder/guard.log" 2>&1 || true
 fi
}
guard_watch() {
 [ "$#" -eq 1 ] && valid_token "$1" || fail 'Invalid safety monitor token'
 guard_watch_token=$1;guard_clean_exit=0
 trap 'guard_watch_cleanup' 0
 trap 'exit 1' HUP INT TERM
 guard_file guard-ready "$guard_watch_token" || exit 1
 while [ "$(perf_read "$perf_folder/guard-token" 2>/dev/null)" = "$guard_watch_token" ] && guard_current_boot; do
  guard_state=$(guard_status)
  case "$guard_state" in stopped|recovery) guard_clean_exit=1;exit 0;; esac
  guard_why=
  guard_heat=$(guard_temperature) || guard_why=sensor-failure
  if [ -z "$guard_why" ]; then
   guard_file guard-temperature "$guard_heat" || guard_why=sensor-failure
   guard_file guard-sample "$(now)" || guard_why=sensor-failure
   [ "$guard_heat" -lt 78000 ] || guard_why=overheat
  fi
  guard_lease=$(perf_read "$perf_folder/guard-lease" 2>/dev/null) || guard_lease=0
  guard_now=$(now)
  if ! number "$guard_lease" || [ "$guard_now" -lt "$guard_lease" ] || [ "$((guard_now-guard_lease))" -gt 12 ]; then guard_why=app-unavailable;fi
  if [ "$guard_state" = armed ] && [ -z "$guard_why" ]; then
   guard_configuration=$(cat "$perf_folder/guard-config") || guard_why=limits-changed
   set -- $guard_configuration
   if [ "$#" -ne 7 ] || [ "$(perf_read "$perf_cpu0/scaling_max_freq")" != "$3" ] || [ "$(perf_read "$perf_cpu6/scaling_max_freq")" != "$5" ] || [ "$(perf_gpu_frequency "$(perf_read "$perf_gpu/max_pwrlevel")")" != "$7" ]; then guard_why=limits-changed;fi
  fi
  if [ -n "$guard_why" ]; then
   if sh "$D/performance-hardware.sh" performance-recover "$guard_watch_token" "$guard_why" >> "$perf_folder/guard.log" 2>&1; then guard_clean_exit=1;exit 0;fi
  fi
  sleep 1
 done
 guard_clean_exit=1
}
