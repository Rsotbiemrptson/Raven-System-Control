"""Generate an Android-shell compatibility fixture; all hardware paths are remapped."""
from pathlib import Path
import sys, uuid
source=Path(__file__).resolve().parents[1]/'assets'
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
base='/data/local/tmp/raven-compat-'+uuid.uuid4().hex
def mapped(s):
    return s.replace('/data/local/odin-controls',base+'/control').replace('/sys/',base+'/sys/')
hardware=mapped((source/'hardware.sh').read_text().split('\ngate\ncase "$OP" in')[0])
perf=mapped((source/'performance.sh').read_text())
head='''#!/system/bin/sh
set -eu
BASE=/data/local/tmp/raven-compat049b
[ ! -e "$BASE" ] || { echo 'Fixture path already exists; choose a fresh path';exit 1; }
umask 077
mkdir -p "$BASE"
'''
body=r'''
model=Odin3;sdk=35;lighting=rings
firmware=Odin3_V1.0.0.187_20260616_193307_user;soc=CQ8725S
getprop(){ case "$1" in ro.build.display.id) echo "$firmware";;ro.soc.model) echo "$soc";;esac; }
get(){ case "$1" in fan_mode) echo 4;;smart_fan_mode_switch) echo 1;;*) echo null;;esac; }
cpu0=$BASE/sys/devices/system/cpu/cpufreq/policy0
cpu6=$BASE/sys/devices/system/cpu/cpufreq/policy6
gpu=$BASE/sys/class/kgsl/kgsl-3d0
fan=$BASE/sys/class/gpio5_pwm2
mkdir -p "$cpu0" "$cpu6" "$gpu" "$fan" "$BASE/sys/class/sn3112l/led" "$BASE/sys/class/sn3112r/led"
echo '0 1 2 3 4 5' > "$cpu0/related_cpus";echo '6 7' > "$cpu6/related_cpus"
echo '384000 1785600 1996800 2227200 2918400 3532800' > "$cpu0/scaling_available_frequencies"
echo '1017600 1958400 2246400 3283200 4204800 4320000' > "$cpu6/scaling_available_frequencies"
echo 384000 > "$cpu0/cpuinfo_min_freq";echo 3532800 > "$cpu0/cpuinfo_max_freq"
echo 1017600 > "$cpu6/cpuinfo_min_freq";echo 4320000 > "$cpu6/cpuinfo_max_freq"
echo '1100000000 1050000000 900000000 660000000 443000000 160000000' > "$gpu/gpu_available_frequencies"
for node in "$cpu0/scaling_min_freq" "$cpu0/scaling_max_freq" "$cpu6/scaling_min_freq" "$cpu6/scaling_max_freq" "$gpu/min_pwrlevel" "$gpu/max_pwrlevel" "$fan/state" "$fan/duty" "$BASE/sys/class/sn3112l/led/brightness" "$BASE/sys/class/sn3112r/led/brightness";do echo 0 > "$node";done
echo 50000 > "$fan/period"
create_backup
before=$(cat "$B/SHA256SUMS")
for firmware in Odin3_V1.0.0.187_20260616_193307_user Odin3_V1.0.0.188_20261114_Raven_user Odin3_V1.0.0.189_20261115_Raven_user_r2 Odin3_Future_190;do
 perf_gate
 validate_backup "$B"
 create_backup
 [ "$(cat "$B/SHA256SUMS")" = "$before" ]
 echo "PASS compatible platform and unchanged original backup: $firmware"
done
soc=OTHER
if (perf_gate) >/dev/null 2>&1;then exit 1;fi
if (validate_backup "$B") >/dev/null 2>&1;then exit 1;fi
soc=CQ8725S
echo 40000 > "$fan/period"
if (validate_backup "$B") >/dev/null 2>&1;then exit 1;fi
echo 50000 > "$fan/period"
echo '6' > "$cpu6/related_cpus"
if (perf_gate) >/dev/null 2>&1;then exit 1;fi
echo '6 7' > "$cpu6/related_cpus"
echo '160000000 660000000 900000000 1050000000 1100000000' > "$gpu/gpu_available_frequencies"
if (perf_gate) >/dev/null 2>&1;then exit 1;fi
echo 5 > "$B/fan_mode"
if (validate_backup "$B") >/dev/null 2>&1;then exit 1;fi
echo 'PASS reject changed SoC, fan period, CPU topology, GPU ordering and corrupt backup'
'''
(out/'compatibility.sh').write_text(head.replace('/data/local/tmp/raven-compat049b',base)+hardware+'\n'+perf+'\n'+body,encoding='utf-8',newline='\n')
print(out/'compatibility.sh')
