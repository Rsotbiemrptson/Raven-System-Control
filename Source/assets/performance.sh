# Sourced by hardware.sh after its privileged-device gate.
# Writes only enumerated CPU/GPU limits. Thermal controls remain with the OEM.
perf_read() { read -r perf_value < "$1" || return 1; printf '%s\n' "$perf_value"; }
perf_member() {
 number "$1" || return 1
 case " $2 " in *" $1 "*) return 0;; *) return 1;; esac
}
perf_gpu_frequency() {
 perf_index=$1;perf_count=0
 for perf_frequency in $perf_gpu_frequencies; do
  if [ "$perf_count" = "$perf_index" ]; then echo "$perf_frequency";return 0;fi
  perf_count=$((perf_count+1))
 done
 return 1
}
perf_gpu_index() {
 perf_count=0
 for perf_frequency in $perf_gpu_frequencies; do
  if [ "$perf_frequency" = "$1" ]; then echo "$perf_count";return 0;fi
  perf_count=$((perf_count+1))
 done
 return 1
}
perf_gate() {
 [ "$model" = Odin3 ] || fail 'Performance tuning requires Odin 3'
 # Firmware labels change across OTA updates without changing these controls.
 # Validate the platform and live interfaces instead of a build-name whitelist.
 [ "$sdk" = 35 ] && [ "$(getprop ro.soc.model)" = CQ8725S ] || fail 'Unsupported Odin 3 performance platform'
 perf_cpu0=/sys/devices/system/cpu/cpufreq/policy0
 perf_cpu6=/sys/devices/system/cpu/cpufreq/policy6
 perf_gpu=/sys/class/kgsl/kgsl-3d0
 [ "$(perf_read "$perf_cpu0/related_cpus")" = '0 1 2 3 4 5' ] && [ "$(perf_read "$perf_cpu6/related_cpus")" = '6 7' ] || fail 'CPU cluster layout changed'
 perf_cpu0_frequencies=$(perf_read "$perf_cpu0/scaling_available_frequencies") || fail 'CPU frequency table unavailable'
 perf_cpu6_frequencies=$(perf_read "$perf_cpu6/scaling_available_frequencies") || fail 'CPU frequency table unavailable'
 perf_gpu_frequencies=$(perf_read "$perf_gpu/gpu_available_frequencies") || fail 'GPU frequency table unavailable'
 perf_cpu0_low=$(perf_read "$perf_cpu0/cpuinfo_min_freq")
 perf_cpu0_high=$(perf_read "$perf_cpu0/cpuinfo_max_freq")
 perf_cpu6_low=$(perf_read "$perf_cpu6/cpuinfo_min_freq")
 perf_cpu6_high=$(perf_read "$perf_cpu6/cpuinfo_max_freq")
 perf_gpu_high=$(perf_gpu_frequency 0) || fail 'GPU power-level table unavailable'
 number "$perf_cpu0_low" && number "$perf_cpu0_high" && number "$perf_cpu6_low" && number "$perf_cpu6_high" || fail 'Invalid CPU frequency bounds'
 [ "$perf_cpu0_low" -gt 0 ] && [ "$perf_cpu0_low" -lt "$perf_cpu0_high" ] && [ "$perf_cpu6_low" -gt 0 ] && [ "$perf_cpu6_low" -lt "$perf_cpu6_high" ] || fail 'Invalid CPU frequency bounds'
 # KGSL uses reversed power-level indices. Reject a changed ordering rather
 # than accidentally writing a minimum where a maximum was intended.
 perf_previous=;perf_levels=0
 for perf_frequency in $perf_gpu_frequencies; do
  number "$perf_frequency" && [ "$perf_frequency" -gt 0 ] || fail 'Invalid GPU frequency'
  [ -z "$perf_previous" ] || [ "$perf_frequency" -lt "$perf_previous" ] || fail 'Unsupported GPU power-level ordering'
  perf_previous=$perf_frequency;perf_levels=$((perf_levels+1))
 done
 [ "$perf_levels" -gt 1 ] || fail 'GPU power-level table unavailable'
 for perf_node in "$perf_cpu0/scaling_min_freq" "$perf_cpu0/scaling_max_freq" "$perf_cpu6/scaling_min_freq" "$perf_cpu6/scaling_max_freq" "$perf_gpu/min_pwrlevel" "$perf_gpu/max_pwrlevel"; do
  [ -f "$perf_node" ] && [ -r "$perf_node" ] || fail 'Performance limit interface unavailable'
 done
 # These stock floors were measured across all three OEM modes on firmware 187.
 # Validate them against the live table before allowing any changes.
 # Conservative recovery steps must remain available as well as stock floors.
 for perf_floor in "$perf_cpu0_low" "$perf_cpu0_high" 1785600 1996800 2227200 2918400; do perf_member "$perf_floor" "$perf_cpu0_frequencies" || fail 'Stock or recovery CPU table changed';done
 for perf_floor in "$perf_cpu6_low" "$perf_cpu6_high" 1958400 2246400 3283200 4204800; do perf_member "$perf_floor" "$perf_cpu6_frequencies" || fail 'Stock or recovery CPU table changed';done
 for perf_floor in 443000000 660000000 900000000 1050000000; do perf_member "$perf_floor" "$perf_gpu_frequencies" || fail 'Stock or recovery GPU table changed';done
}
perf_stock() {
 case "$1" in
  0) echo "0 1996800 $perf_cpu0_high 2246400 $perf_cpu6_high 660000000 $perf_gpu_high";;
  1) echo "1 2227200 $perf_cpu0_high 3283200 $perf_cpu6_high 900000000 $perf_gpu_high";;
  2) echo "2 2918400 $perf_cpu0_high 4204800 $perf_cpu6_high 1050000000 $perf_gpu_high";;
  *) return 1;;
 esac
}
perf_current() {
 perf_mode=$(getprop persist.vendor.debug.mode)
 perf_low0=$(perf_read "$perf_cpu0/scaling_min_freq") && perf_high0=$(perf_read "$perf_cpu0/scaling_max_freq") && perf_low6=$(perf_read "$perf_cpu6/scaling_min_freq") && perf_high6=$(perf_read "$perf_cpu6/scaling_max_freq") || return 1
 perf_low_gpu_index=$(perf_read "$perf_gpu/min_pwrlevel") && perf_high_gpu_index=$(perf_read "$perf_gpu/max_pwrlevel") || return 1
 perf_low_gpu=$(perf_gpu_frequency "$perf_low_gpu_index") && perf_high_gpu=$(perf_gpu_frequency "$perf_high_gpu_index") || return 1
 echo "$perf_mode $perf_low0 $perf_high0 $perf_low6 $perf_high6 $perf_low_gpu $perf_high_gpu"
}
perf_validate() {
 [ "$#" -eq 7 ] || return 1
 case "$1" in 0|1|2) ;; *) return 1;; esac
 perf_member "$2" "$perf_cpu0_frequencies" && perf_member "$3" "$perf_cpu0_frequencies" && [ "$2" -le "$3" ] && [ "$3" -le "$perf_cpu0_high" ] || return 1
 perf_member "$4" "$perf_cpu6_frequencies" && perf_member "$5" "$perf_cpu6_frequencies" && [ "$4" -le "$5" ] && [ "$5" -le "$perf_cpu6_high" ] || return 1
 perf_member "$6" "$perf_gpu_frequencies" && perf_member "$7" "$perf_gpu_frequencies" && [ "$6" -le "$7" ] && [ "$7" -le "$perf_gpu_high" ]
}
perf_modes_valid() {
 [ "$#" -eq 4 ] || return 1
 for perf_mode_bits in "$@"; do
  case "$perf_mode_bits" in [0-7][0-7][0-7]) ;; *) return 1;; esac
 done
}
perf_modes() {
 for perf_permission_node in "$perf_cpu0/scaling_min_freq" "$perf_cpu0/scaling_max_freq" "$perf_cpu6/scaling_min_freq" "$perf_cpu6/scaling_max_freq"; do
  stat -c %a "$perf_permission_node" || return 1
 done
}
perf_ownership_valid() {
 [ "$#" -eq 18 ] || return 1
 while [ "$#" -gt 0 ]; do
  number "$1" && number "$2" && [ "$1" -le 100000 ] && [ "$2" -le 100000 ] || return 1
  case "$3" in [0-7][0-7][0-7]) ;; *) return 1;; esac
  shift 3
 done
}
perf_save_ownership() {
 perf_owner_boot=$(perf_read /proc/sys/kernel/random/boot_id) || return 1
 if [ -f "$perf_folder/ownership" ]; then
  read -r perf_stored_boot perf_saved_owners < "$perf_folder/ownership" || return 1
  perf_ownership_valid $perf_saved_owners || return 1
  [ "$perf_stored_boot" != "$perf_owner_boot" ] || return 0
 fi
 perf_saved_owners=
 for perf_owner_node in "$perf_cpu0/scaling_min_freq" "$perf_cpu0/scaling_max_freq" "$perf_cpu6/scaling_min_freq" "$perf_cpu6/scaling_max_freq" "$perf_gpu/min_pwrlevel" "$perf_gpu/max_pwrlevel"; do
  perf_owner_row=$(stat -c '%u %g %a' "$perf_owner_node") || return 1
  perf_saved_owners="$perf_saved_owners $perf_owner_row"
 done
 perf_ownership_valid $perf_saved_owners || return 1
 guard_file ownership "$perf_owner_boot $perf_saved_owners"
}
perf_owner() {
 perf_owner_skip=$((3*$1))
 set -- $perf_saved_owners
 shift "$perf_owner_skip"
 echo "$1 $2 $3"
}
perf_restore_ownership() {
 [ -n "${perf_saved_owners:-}" ] || return 0
 set -- $perf_saved_owners
 for perf_owner_node in "$perf_cpu0/scaling_min_freq" "$perf_cpu0/scaling_max_freq" "$perf_cpu6/scaling_min_freq" "$perf_cpu6/scaling_max_freq" "$perf_gpu/min_pwrlevel" "$perf_gpu/max_pwrlevel"; do
  if [ "$(stat -c '%u %g' "$perf_owner_node")" != "$1 $2" ]; then chown "$1:$2" "$perf_owner_node" || return 1;fi
  shift 3
 done
}
perf_save_permissions() {
 perf_boot=$(perf_read /proc/sys/kernel/random/boot_id) || return 1
 if [ -f "$perf_folder/permissions" ]; then
  read -r perf_saved_boot perf_saved_modes < "$perf_folder/permissions" || return 1
  perf_modes_valid $perf_saved_modes || return 1
  if [ "$perf_saved_boot" = "$perf_boot" ]; then return 0;fi
 fi
 # A reboot recreates sysfs. Never replay permissions from another boot.
 perf_saved_modes=$(perf_modes) || return 1
 set -- $perf_saved_modes
 perf_modes_valid "$@" || return 1
 for perf_mode_bits in "$@"; do
  [ "$((8#$perf_mode_bits & 146))" -ne 0 ] || return 1
 done
 perf_saved_modes="$*"
 echo "$perf_boot $perf_saved_modes" > "$perf_folder/permissions.pending" && mv "$perf_folder/permissions.pending" "$perf_folder/permissions"
}
perf_write_cpu() {
 # Keep the minimum protected before setting the maximum: the vendor service
 # otherwise raises the floor and ceiling together during gaming boosts.
 perf_min_writable=$(printf '%03o' "$((8#$5 | 128))")
 perf_max_writable=$(printf '%03o' "$((8#$6 | 128))")
 chmod "$perf_min_writable" "$1/scaling_min_freq" && chmod "$perf_max_writable" "$1/scaling_max_freq" || return 1
 echo "$4" > "$1/scaling_min_freq" && echo "$2" > "$1/scaling_min_freq" && chmod "$5" "$1/scaling_min_freq" || return 1
 echo "$3" > "$1/scaling_max_freq" && chmod "$6" "$1/scaling_max_freq" || return 1
 [ "$(stat -c %a "$1/scaling_min_freq")" = "$5" ] && [ "$(stat -c %a "$1/scaling_max_freq")" = "$6" ]
}
perf_verify_cpu() {
 perf_effective_min=$(perf_read "$1/scaling_min_freq") && perf_effective_max=$(perf_read "$1/scaling_max_freq") || return 1
 number "$perf_effective_min" && number "$perf_effective_max" || return 1
 # scaling_min_freq reports the aggregate QoS floor, including independent
 # system boosts. A successful minimum request can have a higher effective
 # floor. The requested ceiling must still match exactly and bound that floor.
 [ "$perf_effective_min" -ge "$2" ] && [ "$perf_effective_min" -le "$3" ] && [ "$perf_effective_max" = "$3" ]
}
perf_apply() {
 perf_validate "$@" || return 1
 setprop persist.vendor.debug.mode "$1" || return 1
 sleep 1
 [ "$(getprop persist.vendor.debug.mode)" = "$1" ] || return 1
 perf_write_cpu "$perf_cpu0" "$2" "$3" "$perf_cpu0_low" "$perf_mode0_min" "$perf_mode0_max" && perf_write_cpu "$perf_cpu6" "$4" "$5" "$perf_cpu6_low" "$perf_mode6_min" "$perf_mode6_max" || return 1
 perf_gpu_min_index=$(perf_gpu_index "$6") && perf_gpu_max_index=$(perf_gpu_index "$7") || return 1
 # Adreno indices run in reverse frequency order. Expand the ceiling first.
 chmod "$(printf '%03o' "$((8#$perf_gpu_mode_min | 128))")" "$perf_gpu/min_pwrlevel" && chmod "$(printf '%03o' "$((8#$perf_gpu_mode_max | 128))")" "$perf_gpu/max_pwrlevel" || return 1
 echo 0 > "$perf_gpu/max_pwrlevel" && echo "$perf_gpu_min_index" > "$perf_gpu/min_pwrlevel" && echo "$perf_gpu_max_index" > "$perf_gpu/max_pwrlevel" || return 1
 chmod "$perf_gpu_mode_min" "$perf_gpu/min_pwrlevel" && chmod "$perf_gpu_mode_max" "$perf_gpu/max_pwrlevel" || return 1
 perf_verify_cpu "$perf_cpu0" "$2" "$3" && perf_verify_cpu "$perf_cpu6" "$4" "$5" || return 1
 [ "$(getprop persist.vendor.debug.mode)" = "$1" ] && [ "$(perf_read "$perf_gpu/min_pwrlevel")" = "$perf_gpu_min_index" ] && [ "$(perf_read "$perf_gpu/max_pwrlevel")" = "$perf_gpu_max_index" ]
}
perf_finish() {
 if [ -n "${perf_recover_reason:-}" ]; then guard_seal_controls || echo 'Recovery controls still need protection';fi
 perf_restore_ownership || echo 'Original frequency-control ownership needs recovery'
 if [ "$perf_transaction" = 1 ]; then
  perf_original=$(cat "$perf_folder/before" 2>/dev/null)
  read -r perf_mode0_min perf_mode0_max perf_mode6_min perf_mode6_max < "$perf_folder/before-permissions"
  read -r perf_gpu_mode_min perf_gpu_mode_max < "$perf_folder/before-gpu-permissions"
  if perf_modes_valid "$perf_mode0_min" "$perf_mode0_max" "$perf_mode6_min" "$perf_mode6_max" && perf_apply $perf_original; then
   echo 'Previous performance limits restored after the failed change'
  else
   echo 'Recovery required. Previous performance limits are retained in the protected performance journal. Apply a stock mode to recover.'
  fi
 fi
 flock -u 9 9>&9 2>/dev/null || true
}
performance_main() {
 perf_gate
 perf_folder=$D/performance
 . "$(dirname "$0")/performance-guard.sh"
 case "$1" in
  performance-watch) shift;guard_watch "$@";return;;
  performance-heartbeat)
   [ "$#" -eq 2 ] && valid_token "$2" || fail 'Invalid performance heartbeat'
   if [ "$(perf_read "$perf_folder/guard-token" 2>/dev/null)" != "$2" ] || ! guard_current_boot; then echo 'OK stopped';return;fi
   case "$(guard_status)" in
    recovery) echo "OK recovery $(cat "$perf_folder/guard-reason")";return;;
    armed)
     if guard_alive "$2"; then guard_file guard-lease "$(now)" || fail 'Cannot renew performance safety lease';echo "OK armed $(cat "$perf_folder/guard-temperature")";return;fi
     performance_main performance-recover "$2" guard-stopped
     return;;
    *) echo 'OK stopped';return;;
   esac;;
 esac
 if [ "$1" = performance-status ]; then
  [ "$#" -eq 1 ] || fail 'Invalid performance read'
  perf_row=$(perf_current) || fail 'Cannot read performance limits'
  set -- $perf_row
  [ "$#" -eq 7 ] || fail 'Incomplete performance limits'
  case "$1" in 0|1|2) ;; *) fail 'Unknown OEM performance mode';; esac
  echo OK
  echo "mode=$1"
  echo "frequencies.0=$perf_cpu0_frequencies"
  echo "frequencies.1=$perf_cpu6_frequencies"
  echo "frequencies.2=$perf_gpu_frequencies"
  echo "minimum.0=$2";echo "maximum.0=$3"
  echo "minimum.1=$4";echo "maximum.1=$5"
  echo "minimum.2=$6";echo "maximum.2=$7"
  echo "current.0=$(perf_read "$perf_cpu0/scaling_cur_freq")"
  echo "current.1=$(perf_read "$perf_cpu6/scaling_cur_freq")"
  echo "current.2=$(perf_read "$perf_gpu/devfreq/cur_freq")"
  for perf_mode in 0 1 2; do perf_values=$(perf_stock "$perf_mode");echo "stock.$perf_mode=${perf_values#* }";done
  echo "guard=$(guard_status)"
  echo "guardReason=$(cat "$perf_folder/guard-reason" 2>/dev/null)"
  echo "guardTemperature=$(cat "$perf_folder/guard-temperature" 2>/dev/null)"
  return 0
 fi
 perf_guard_token=
 perf_recover_reason=
 perf_bootstrap=0
 case "$1" in
  performance-bootstrap)
   [ -f "$perf_folder/permissions" ] || { echo 'OK stopped';return; }
   read -r perf_bootstrap_boot perf_bootstrap_modes < "$perf_folder/permissions" || fail 'Invalid performance recovery journal'
   [ "$perf_bootstrap_boot" = "$(perf_read /proc/sys/kernel/random/boot_id)" ] || { echo 'OK stopped';return; }
   case "$(guard_status)" in
    recovery) echo 'OK recovery';return;;
    armed)
     perf_existing_token=$(perf_read "$perf_folder/guard-token")
     if guard_alive "$perf_existing_token"; then echo 'OK armed';return;fi
     performance_main performance-recover "$perf_existing_token" app-unavailable
     return;;
   esac
   perf_guard_token=$(tr -d '-' < /proc/sys/kernel/random/uuid)
   valid_token "$perf_guard_token" || fail 'Cannot create a recovery token'
   perf_bootstrap=1;perf_recover_reason=legacy-session;perf_request=;perf_restore_stock=0;;
  performance-stock)
   [ "$#" -eq 2 ] || fail 'Invalid stock performance request'
   perf_request=$(perf_stock "$2") || fail 'Unknown stock performance mode'
   perf_restore_stock=1;;
  performance-guard)
   [ "$#" -eq 9 ] && valid_token "$2" || fail 'Invalid guarded performance request'
   perf_guard_token=$2;shift 2;perf_request="$*";perf_restore_stock=0;;
  performance-recover)
   [ "$#" -eq 3 ] && valid_token "$2" || fail 'Invalid performance recovery request'
   perf_guard_token=$2;perf_recover_reason=$3;perf_request=;perf_restore_stock=0;;
  performance-apply) fail 'Custom performance profiles require the Performance safety guard';;
  *) fail 'Unknown performance operation';;
 esac
 [ -n "$perf_recover_reason" ] || perf_validate $perf_request || fail 'Use supported minimum and maximum frequencies'
 for perf_node in "$perf_cpu0/scaling_min_freq" "$perf_cpu0/scaling_max_freq" "$perf_cpu6/scaling_min_freq" "$perf_cpu6/scaling_max_freq" "$perf_gpu/min_pwrlevel" "$perf_gpu/max_pwrlevel"; do
  [ -f "$perf_node" ] && [ -r "$perf_node" ] || fail 'Performance limits are unavailable'
 done
 mkdir -p "$D" && chmod 700 "$D" || fail 'Cannot protect the performance journal'
 [ ! -L "$D/performance.lock" ] || fail 'Invalid performance lock'
 exec 9> "$D/performance.lock" || fail 'Cannot open the performance lock'
 # Android mksh marks persistent descriptors close-on-exec. Explicitly pass
 # this descriptor to flock while retaining the same open file in this shell.
 flock -n 9 9>&9 || fail 'Another performance change is in progress. Try again when it finishes.'
 perf_transaction=0
 trap 'perf_finish' 0
 trap 'exit 1' HUP INT TERM
 [ ! -L "$perf_folder" ] || fail 'Invalid performance journal'
 mkdir -p "$perf_folder" && chmod 700 "$perf_folder" || fail 'Cannot create performance journal'
 for perf_file in before before.pending original original.pending permissions permissions.pending before-permissions before-permissions.pending before-gpu-permissions before-gpu-permissions.pending ownership ownership.pending; do [ ! -L "$perf_folder/$perf_file" ] || fail 'Invalid performance journal file';done
 perf_save_ownership || fail 'Cannot preserve frequency-control ownership'
 if [ -n "$perf_recover_reason" ]; then
  if [ "$perf_bootstrap" = 1 ]; then
   perf_save_permissions && guard_gpu_permissions && guard_copy_scripts || fail 'Cannot prepare conservative recovery'
   guard_old_config=$(perf_current) && perf_validate $guard_old_config || fail 'Cannot read previous performance limits'
   guard_paths=$(guard_sensors) || guard_paths=
   guard_file guard-sensors "$guard_paths" && guard_file guard-config "$guard_old_config" && guard_file guard-boot "$(perf_read /proc/sys/kernel/random/boot_id)" && guard_file guard-token "$perf_guard_token" && guard_file guard-status starting || fail 'Cannot record conservative recovery'
  fi
  guard_recover "$perf_guard_token" "$perf_recover_reason" || fail 'Safe recovery could not be verified. Conservative limits and the recovery journal are retained.'
  echo "OK recovery $(cat "$perf_folder/guard-reason" 2>/dev/null)"
  return 0
 fi
 if [ "$perf_restore_stock" = 1 ]; then
  guard_allow_stock || fail 'Safe recovery remains active. Wait for valid temperatures below 60 C before restoring a stock mode.'
 fi
 perf_save_permissions || fail 'Cannot preserve CPU frequency permissions. Another tuning app may be holding the controls.'
 perf_previous_modes=$(perf_modes) && perf_modes_valid $perf_previous_modes || fail 'Cannot read CPU frequency permissions'
 set -- $perf_previous_modes
 echo "$*" > "$perf_folder/before-permissions.pending" && mv "$perf_folder/before-permissions.pending" "$perf_folder/before-permissions" || fail 'Cannot save previous CPU permissions'
 perf_gpu_mode_min=$(stat -c %a "$perf_gpu/min_pwrlevel") && perf_gpu_mode_max=$(stat -c %a "$perf_gpu/max_pwrlevel") || fail 'Cannot preserve GPU permissions'
 perf_modes_valid "$perf_gpu_mode_min" "$perf_gpu_mode_max" 644 644 || fail 'Invalid GPU permissions'
 guard_file before-gpu-permissions "$perf_gpu_mode_min $perf_gpu_mode_max" || fail 'Cannot save previous GPU permissions'
 set -- $perf_saved_modes
 if [ "$perf_restore_stock" = 0 ]; then
  # Remove only write bits, retaining the original read/execute permissions.
  perf_target_modes=
  for perf_mode_bits in "$@"; do perf_target_modes="$perf_target_modes $(printf '%03o' "$((8#$perf_mode_bits & 365))")";done
  set -- $perf_target_modes
 fi
 perf_modes_valid "$@" || fail 'Invalid target CPU permissions'
 perf_mode0_min=$1;perf_mode0_max=$2;perf_mode6_min=$3;perf_mode6_max=$4
 perf_before=$(perf_current) && perf_validate $perf_before || fail 'Current performance limits cannot be preserved safely'
 echo "$perf_before" > "$perf_folder/before.pending" && mv "$perf_folder/before.pending" "$perf_folder/before" || fail 'Cannot save previous performance limits'
 if [ ! -f "$perf_folder/original" ]; then
  cp "$perf_folder/before" "$perf_folder/original.pending" && mv "$perf_folder/original.pending" "$perf_folder/original" || fail 'Cannot save original performance limits'
 fi
 if [ -n "$perf_guard_token" ]; then
  guard_prepare "$perf_guard_token" $perf_request || fail 'Safety monitor could not start. Valid CPU/GPU sensors below 78 C are required.'
  perf_gpu_mode_min=$(printf '%03o' "$((8#$guard_gpu_min_mode & 365))")
  perf_gpu_mode_max=$(printf '%03o' "$((8#$guard_gpu_max_mode & 365))")
 elif [ -f "$perf_folder/guard-gpu-permissions" ]; then
  guard_gpu_permissions || fail 'Cannot recover original GPU permissions'
  perf_gpu_mode_min=$guard_gpu_min_mode;perf_gpu_mode_max=$guard_gpu_max_mode
 fi
 perf_transaction=1
 perf_apply $perf_request || fail 'Performance limits did not apply. Restoring the previous limits.'
 perf_transaction=0
 if [ "$perf_restore_stock" = 1 ]; then
  guard_restore_gpu_permissions || fail 'Stock limits applied, but GPU permission restoration is incomplete'
  guard_disarm || fail 'Stock limits applied, but the safety monitor could not be stopped'
  perf_restore_ownership || fail 'Original frequency-control ownership could not be restored'
  rm -f "$perf_folder/original" "$perf_folder/permissions" "$perf_folder/ownership" || fail 'Stock mode applied, but the previous journal could not be cleared'
 else
  guard_file guard-status armed || fail 'Performance limits applied, but safety status could not be committed'
 fi
 echo 'OK Performance profile applied and verified'
}
