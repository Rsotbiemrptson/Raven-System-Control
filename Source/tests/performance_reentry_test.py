"""Generate isolated Android-shell fixtures; no real hardware paths are used."""
from pathlib import Path
import sys,uuid
source=Path(__file__).resolve().parents[1]/'assets'
base='/data/local/tmp/raven-reentry-'+uuid.uuid4().hex
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
freqs=['384000 556800 748800 960000 1152000 1363200 1555200 1785600 1996800 2227200 2400000 2745600 2918400 3072000 3321600 3532800','1017600 1209600 1401600 1689600 1958400 2246400 2438400 2649600 2841600 3072000 3283200 3513600 3801600 4089600 4204800 4320000','1100000000 1050000000 967000000 900000000 832000000 734000000 660000000 607000000 525000000 443000000 389000000 342000000 222000000 160000000']
mocks='''getprop(){ case "$1" in ro.product.model) echo Odin3;;ro.build.version.sdk) echo 35;;ro.soc.model) echo CQ8725S;;ro.build.display.id) echo QA189;;persist.vendor.debug.mode) cat BASE/mode;;esac; }
setprop(){ echo "$2" > BASE/mode; }
am(){ echo 0; }
settings(){ case "$3" in get) if [ -f BASE/settings/$5 ];then cat BASE/settings/$5;else echo null;fi;;put) echo "$6" > BASE/settings/$5;;delete) rm -f BASE/settings/$5;;esac; }
'''.replace('BASE',base)
for name in ['hardware.sh','performance.sh','performance-guard.sh']:
 s=(source/name).read_text().replace('/data/local/odin-controls',base+'/session').replace('/sys/',base+'/sys/').replace('/proc'+base+'/sys/','/proc/sys/')
 if name=='hardware.sh':s=s.replace('set -u','set -u\n'+mocks,1)
 (out/name).write_text(s,encoding='utf-8',newline='\n')
init=['#!/system/bin/sh','set -eu',f'B={base}',f'mkdir -p "$B/settings" "$B/session"',f'cp /data/local/tmp/raven-reentry-source/*.sh "$B/"','echo 0 > "$B/mode"']
def write(path,value,mode='644',owner=None):
 init.extend([f'mkdir -p "$B/{path.rsplit("/",1)[0]}"',f"echo '{value}' > \"$B/{path}\"",f'chmod {mode} "$B/{path}"'])
 if owner:init.append(f'chown {owner} "$B/{path}"')
for i,cluster in enumerate([0,6]):
 p=f'sys/devices/system/cpu/cpufreq/policy{cluster}'
 for name,val in [('related_cpus','0 1 2 3 4 5' if i==0 else '6 7'),('scaling_available_frequencies',freqs[i]),('cpuinfo_min_freq',freqs[i].split()[0]),('cpuinfo_max_freq',freqs[i].split()[-1]),('scaling_min_freq','1996800' if i==0 else '2246400'),('scaling_max_freq',freqs[i].split()[-1]),('scaling_cur_freq','1996800' if i==0 else '2246400')]:write(p+'/'+name,val,'664','1000:1000')
p='sys/class/kgsl/kgsl-3d0'
for name,val in [('gpu_available_frequencies',freqs[2]),('min_pwrlevel','6'),('max_pwrlevel','0'),('devfreq/cur_freq','660000000')]:write(p+'/'+name,val)
for n in ['state','duty','period']:write('sys/class/gpio5_pwm2/'+n,'50000' if n=='period' else '1' if n=='state' else '10000')
for side in ['l','r']:write(f'sys/class/sn3112{side}/led/brightness','0')
for i,kind in enumerate(['cpu-test','gpu-test']):
 write(f'sys/class/thermal/thermal_zone{i}/type',kind);write(f'sys/class/thermal/thermal_zone{i}/temp','40000')
init+=['echo 4 > "$B/settings/fan_mode"','echo 1 > "$B/settings/smart_fan_mode_switch"',r'''
run(){ sh "$B/hardware.sh" "$@"; }
modes(){ stat -c %a "$B/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq" "$B/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq" "$B/sys/class/kgsl/kgsl-3d0/min_pwrlevel" "$B/sys/class/kgsl/kgsl-3d0/max_pwrlevel" | tr '\n' ' '; }
t1=11111111111111111111111111111111
t2=22222222222222222222222222222222
run performance-guard "$t1" 0 384000 3072000 1017600 3801600 160000000 967000000
run performance-stock 0
[ "$(modes)" = '664 664 644 644 ' ] || { echo 'FAIL first stock permission restore';exit 1; }
run performance-recover "$t1" monitor-stopped
[ "$(modes)" = '664 664 644 644 ' ] || { echo "FAIL stale recovery changed stock permissions: $(modes)";exit 1; }
[ ! -f "$B/session/performance/ownership" ] || { echo 'FAIL stale recovery created ownership';exit 1; }
run performance-guard "$t2" 0 384000 3072000 1017600 3801600 160000000 967000000
run performance-recover "$t1" monitor-stopped
[ "$(cat "$B/session/performance/guard-token")" = "$t2" ]
run performance-recover "$t2" sensor-failure
[ "$(cat "$B/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq")" = 1785600 ]
[ "$(cat "$B/sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq")" = 1958400 ]
[ "$(cat "$B/sys/class/kgsl/kgsl-3d0/max_pwrlevel")" = 9 ]
run performance-stock 0
[ "$(modes)" = '664 664 644 644 ' ]
echo 'PASS repeated guard/stock, stale-token no-op, current guard preserved and conservative recovery'
''']
(out/'init.sh').write_text('\n'.join(init)+'\n',encoding='utf-8',newline='\n');print(base)
