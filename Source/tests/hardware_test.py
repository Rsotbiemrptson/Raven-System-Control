"""Host-only integration tests. All device paths/commands use temporary fixtures."""
from pathlib import Path
import os, subprocess, tempfile, time, shutil

source=Path(__file__).resolve().parents[1]/'assets/hardware.sh'
with tempfile.TemporaryDirectory(prefix='odin-controls-test-') as folder:
    root=Path(folder);bin=root/'bin';bin.mkdir();state=root/'settings';state.mkdir()
    fan=root/'fan';fan.mkdir();(fan/'state').write_text('1');(fan/'duty').write_text('25000');(fan/'period').write_text('50000')
    thermal=root/'thermal/thermal_zone0';thermal.mkdir(parents=True);(thermal/'temp').write_text('49000')
    uptime=root/'uptime';uptime.write_text('100.00 0.00')
    lights=root/'lights';lights.mkdir();(lights/'left').touch();(lights/'right').touch()
    for side,name in [('l','left'),('r','right')]:
        (lights/side).mkdir();(lights/side/'enable').write_text('1');(lights/side/'brightness').symlink_to('../'+name)
    replacements={
        'D=/data/local/odin-controls':f'D={root}/control',
        '/sys/class/gpio5_pwm2':str(fan), '/sys/class/thermal':str(root/'thermal'),
        '/sys/class/sn3112l/led/brightness':str(lights/'left'),
        '/sys/class/sn3112r/led/brightness':str(lights/'right'),
        '/sys/class/sn3112$controller/led':str(lights)+'/$controller',
        '/sys/class/sn3112l/led/enable':str(lights/'l/enable'),
        '/sys/class/sn3112r/led/enable':str(lights/'r/enable'),
        '/proc/uptime':str(uptime), 'sleep 3':'sleep 0.05',
    }
    script=source.read_text()
    for a,b in replacements.items():script=script.replace(a,b)
    writes=root/'zone-writes'
    script=script.replace('  echo "$2-$r:$g:$b:255" > "$1"', '  echo "$1|$2-$r:$g:$b:255" >> "'+str(writes)+'"; echo "$2-$r:$g:$b:255" > "$1"')
    helper=root/'hardware.sh';helper.write_text(script)
    def executable(name,body):
        p=bin/name;p.write_text('#!/bin/sh\n'+body+'\n');p.chmod(0o700)
    executable('id','echo 0')
    executable('am','echo 0')
    executable('getprop','case "$1" in ro.product.model) echo Odin3;; *) echo 35;; esac')
    executable('getenforce','echo Enforcing')
    executable('stat','echo 0') # Mocked root-owned session, even for an unprivileged host user.
    executable('settings',f'''D={state}
case "$3" in
get) if [ -f "$D/$5" ]; then cat "$D/$5";else echo null;fi;;
put) [ ! -f "$D/fail-$5-$6" ] || exit 1
 printf '%s' "$6" > "$D/$5"
 echo "$5=$6" >> "$D/put-log";;
delete) [ -f "$D/ignore-delete-$5" ] || rm -f "$D/$5";;
*) exit 1;;
esac''')
    env=dict(os.environ,PATH=str(bin)+':'+os.environ['PATH'])
    def run(*args,ok=True):
        p=subprocess.run(['sh',str(helper),*map(str,args)],env=env,capture_output=True,text=True,timeout=5)
        if ok:assert p.returncode==0 and p.stdout.startswith('OK'),(args,p.stdout,p.stderr)
        else:assert p.returncode!=0,(args,p.stdout,p.stderr)
        return p.stdout
    def value(key):return (state/key).read_text() if (state/key).exists() else 'null'
    def waitfor(predicate):
        deadline=time.monotonic()+4
        while time.monotonic()<deadline:
            if predicate():return
            time.sleep(.02)
        raise AssertionError('Timed out waiting for mocked hardware state')
    token='1'*32;control=root/'control';runpath=control/'run'
    def start(temp=49000,kind='curve'):
        (state/'fan_mode').write_text('5');(state/'fan_speed').write_text('30000')
        (thermal/'temp').write_text(str(temp));uptime.write_text('100.00 0.00')
        run('start',token,kind,0,45,50,75,50,70,55)
        waitfor(lambda:(runpath/'status').exists())
    try:
        run('probe');assert not control.exists(),'Read-only probe wrote files'
        assert run('oem-sensor').strip()=='OK 0'
        preferred=root/'thermal/thermal_zone1';preferred.mkdir();(preferred/'temp').write_text('51000')
        assert run('oem-sensor').strip()=='OK 1'
        (preferred/'temp').write_text('0');assert run('oem-sensor').strip()=='OK 0'
        (preferred/'temp').write_text('invalid');assert run('oem-sensor').strip()=='OK 0'
        previous=(thermal/'temp').read_text();(thermal/'temp').write_text('0');run('oem-sensor',ok=False);(thermal/'temp').write_text(previous)
        print('PASS automatic OEM temperature discovery, priority, invalid/zero fallback and unavailable-source rejection')

        run('start','bad','curve',0,45,50,75,50,70,55,ok=False)
        run('start',token,'curve',0,50,45,75,50,70,55,ok=False)
        start();waitfor(lambda:value('fan_mode')=='0')
        (thermal/'temp').write_text('62000');waitfor(lambda:value('fan_speed')=='29500' and value('fan_mode')=='6')
        (thermal/'temp').write_text('44000');time.sleep(.15);assert value('fan_mode')=='6','Minimum run time not enforced'
        uptime.write_text('115.00 0.00');run('lease',token)
        uptime.write_text('125.00 0.00');run('lease',token);waitfor(lambda:value('fan_mode')=='0')
        run('stop');waitfor(lambda:not runpath.exists());assert value('fan_mode')=='5' and value('fan_speed')=='30000'
        print('PASS start, curve, minimum run time, heartbeat, stop and original-setting restore')
        start(60000);waitfor(lambda:value('fan_mode')=='6')
        (state/'fail-fan_mode-5').touch()
        run('stop',ok=False)
        assert (runpath/'recovery').exists() and (runpath/'mode').read_text().strip()=='5'
        assert value('fan_mode')=='4','Failed restore did not request automatic cooling'
        (state/'fail-fan_mode-5').unlink()
        run('stop');assert not runpath.exists() and value('fan_mode')=='5'
        assert value('fan_speed')=='30000'
        print('PASS failed fan restoration retains original settings and supports retry')
        start(60000,'manual');waitfor(lambda:value('fan_speed')=='27500')
        uptime.write_text('116.00 0.00');waitfor(lambda:not runpath.exists());assert value('fan_mode')=='4' and value('fan_speed')=='30000'
        print('PASS missing app heartbeat requests stock automatic cooling')
        start(60000);waitfor(lambda:value('fan_mode')=='6')
        (state/'fail-fan_mode-4').touch()
        uptime.write_text('116.00 0.00');waitfor(lambda:(runpath/'recovery').exists())
        assert (runpath/'mode').read_text().strip()=='5'
        (state/'fail-fan_mode-4').unlink()
        run('stop');assert not runpath.exists() and value('fan_mode')=='5'
        print('PASS failed automatic fallback retains a recoverable fan journal')
        start(60000);(thermal/'temp').write_text('80000');waitfor(lambda:not runpath.exists());assert value('fan_mode')=='4'
        print('PASS temperature fallback requests stock automatic cooling')
        start(60000);(thermal/'temp').write_text('unavailable');waitfor(lambda:not runpath.exists());assert value('fan_mode')=='4'
        print('PASS unreadable sensor requests stock automatic cooling')
        start(60000);(thermal/'temp').write_text('0');waitfor(lambda:not runpath.exists());assert value('fan_mode')=='4'
        print('PASS zero sensor reading requests stock automatic cooling')
        run('start',token,'manual',0,45,50,75,50,70,49,ok=False)
        executable('getprop','echo UnsupportedDevice')
        run('probe',ok=False)
        assert not runpath.exists()
        executable('getprop','case "$1" in ro.product.model) echo Odin3;; *) echo 35;; esac')
        print('PASS unsupported model and out-of-range manual control are blocked')
        run('light','FF0000','00FF00',50)
        assert value('joystick_led_light_picker_color')=='#FF0000,#00FF00'
        run('light','0000FF','FFFFFF',100)
        (state/'ignore-delete-joystick_led_light_picker_color').touch()
        run('restore-light',ok=False)
        assert (control/'lights/colors').exists(),'Failed null restoration lost snapshot'
        (state/'ignore-delete-joystick_led_light_picker_color').unlink()
        run('restore-light');assert value('joystick_led_light_picker_color')=='null'
        assert value('joystick_light_enabled')=='null' and value('led_light_brightness_percent')=='null'
        (state/'joystick_led_light_picker_color').write_text('#112233,#445566')
        (state/'joystick_light_enabled').write_text('0,1')
        (state/'led_light_brightness_percent').write_text('0.30')
        run('light-off')
        assert value('joystick_light_enabled')=='0,0'
        assert value('joystick_led_light_picker_color')=='#112233,#445566'
        run('light','FF0000','00FF00',50);run('light-off');run('restore-light')
        assert value('joystick_led_light_picker_color')=='#112233,#445566'
        assert value('joystick_light_enabled')=='0,1' and value('led_light_brightness_percent')=='0.30'
        run('light','BAD','00FF00',50,ok=False)
        run('light','112233','445566',50,'on',ok=False)
        run('light-zones','FF0000','0000FF','00FF00','FF8000',25,'on',ok=False)
        run('light-one',0,'FF0000',50,ok=False)
        assert not (control/'lights').exists()
        print('PASS lighting snapshot, repeat apply, null restoration and RGB validation')
        executable('getprop','case "$1" in ro.product.model) echo Odin2;; ro.build.version.sdk) echo 33;; *) echo Odin2_Custom_V1.0.0.400;; esac')
        assert 'lighting=shared' in run('probe')
        (fan/'period').write_text('100000');run('probe',ok=False)
        run('start',token,'manual',0,45,50,75,50,70,55,ok=False)
        (fan/'period').write_text('50000')
        start(60000,'manual');waitfor(lambda:value('fan_speed')=='27500')
        run('stop');assert value('fan_mode')=='5' and value('fan_speed')=='30000'
        run('light','15A5FC','B68CFF',50,ok=False)
        assert not (control/'lights').exists()
        for k in ['led_light_picker_color','led_light_brightness_percent','left_joystick_light_enabled','right_joystick_light_enabled']:
            (state/k).unlink(missing_ok=True)
        run('light','15A5FC','15A5FC',50)
        assert value('led_light_picker_color')==str(int('FF15A5FC',16)-2**32)
        assert value('left_joystick_light_enabled')=='1' and value('right_joystick_light_enabled')=='1'
        run('light','2244CC','2244CC',25);run('restore-light')
        for k in ['led_light_picker_color','led_light_brightness_percent','left_joystick_light_enabled','right_joystick_light_enabled']:
            assert value(k)=='null',(k,value(k))
        (state/'led_light_picker_color').write_text('-12345')
        (state/'led_light_brightness_percent').write_text('0.30')
        (state/'left_joystick_light_enabled').write_text('0');(state/'right_joystick_light_enabled').write_text('0')
        run('light','112233','112233',30);run('restore-light')
        assert value('led_light_picker_color')=='-12345' and value('led_light_brightness_percent')=='0.30'
        assert value('left_joystick_light_enabled')=='0' and value('right_joystick_light_enabled')=='0'
        (state/'left_handle_light_enabled').write_text('1');(state/'right_handle_light_enabled').write_text('0')
        run('light','112233','112233',30)
        assert value('left_handle_light_enabled')=='1' and value('right_handle_light_enabled')=='0','Older/default requests changed side enables'
        assert 'side_left=1 side_right=1' in run('light','2244CC','2244CC',50,'on')
        assert value('left_handle_light_enabled')=='1' and value('right_handle_light_enabled')=='1'
        assert 'side_left=0 side_right=0' in run('light','2244CC','2244CC',50,'off')
        assert value('left_handle_light_enabled')=='0' and value('right_handle_light_enabled')=='0'
        run('restore-light')
        assert value('left_handle_light_enabled')=='1' and value('right_handle_light_enabled')=='0','Original asymmetric side state lost after repeat apply'
        assert value('led_light_picker_color')=='-12345' and value('led_light_brightness_percent')=='0.30'
        for k in ['left_handle_light_enabled','right_handle_light_enabled']:(state/k).unlink()
        run('light','112233','112233',30,'on');run('restore-light')
        assert value('left_handle_light_enabled')=='null' and value('right_handle_light_enabled')=='null'
        (state/'left_handle_light_enabled').write_text('0');(state/'right_handle_light_enabled').write_text('1')
        run('light','112233','112233',30,'on')
        (state/'fail-left_handle_light_enabled-0').touch();run('restore-light',ok=False)
        assert (control/'lights/handles/left').read_text().strip()=='0','Failed side restore discarded snapshot'
        (state/'fail-left_handle_light_enabled-0').unlink();run('restore-light')
        assert value('left_handle_light_enabled')=='0' and value('right_handle_light_enabled')=='1'
        (state/'right_handle_light_enabled').write_text('0')
        (state/'fail-left_handle_light_enabled-1').touch();run('light','112233','112233',30,'on',ok=False)
        assert (control/'lights/handles').exists()
        (state/'fail-left_handle_light_enabled-1').unlink();run('restore-light')
        assert value('left_handle_light_enabled')=='0' and value('right_handle_light_enabled')=='0'
        # Simulate an original snapshot left by 0.1.6, which had no handle journal.
        old=control/'lights';old.mkdir()
        for k,v in {'colors':'-12345','brightness':'0.30','left':'0','right':'0','format':'shared'}.items():(old/k).write_text(v)
        (state/'right_handle_light_enabled').write_text('1')
        run('light','112233','112233',30,'on')
        assert (old/'handles/right').read_text().strip()=='1'
        run('restore-light');assert value('left_handle_light_enabled')=='0' and value('right_handle_light_enabled')=='1'
        run('light','112233','112233',30,'invalid',ok=False)
        assert not (control/'lights').exists()
        # Off has its own journal even when used before On, and keeps color/brightness.
        (state/'left_joystick_light_enabled').write_text('1');(state/'right_joystick_light_enabled').write_text('0')
        run('light-off')
        for k in ['left_joystick_light_enabled','right_joystick_light_enabled','left_handle_light_enabled','right_handle_light_enabled']:assert value(k)=='0'
        assert value('led_light_picker_color')=='-12345' and value('led_light_brightness_percent')=='0.30'
        run('light','15A5FC','15A5FC',50,'on');run('light-off');run('restore-light')
        assert value('left_joystick_light_enabled')=='1' and value('right_joystick_light_enabled')=='0'
        assert value('left_handle_light_enabled')=='0' and value('right_handle_light_enabled')=='1'
        assert value('led_light_picker_color')=='-12345' and value('led_light_brightness_percent')=='0.30'
        run('light-off','extra',ok=False)
        assert not (control/'lights').exists()
        # A partial Off failure must keep the original enable journal for recovery.
        (state/'fail-left_joystick_light_enabled-0').touch();run('light-off',ok=False)
        assert (control/'lights/left').read_text().strip()=='1'
        (state/'fail-left_joystick_light_enabled-0').unlink();run('restore-light')
        assert value('left_joystick_light_enabled')=='1' and value('right_handle_light_enabled')=='1'
        print('PASS dedicated Off preserves colors, snapshots before Off, repeated On/Off restores originals and failed Off can recover')
        print('PASS side match on/off, preserve old/default behavior, asymmetric/null restore, partial failure/retry and legacy snapshot extension')
        before={k:value(k) for k in ['led_light_picker_color','led_light_brightness_percent','left_joystick_light_enabled','right_joystick_light_enabled','left_handle_light_enabled','right_handle_light_enabled']}
        run('light-zones','bad','0000FF','00FF00','FF8000',25,'on',ok=False)
        run('light-zones','FF0000','0000FF','00FF00','FF8000',101,'on',ok=False)
        run('light-zones','FF0000','0000FF','00FF00','FF8000',25,'invalid',ok=False)
        assert not (control/'lights').exists()
        run('light-zones','FF0000','0000FF','00FF00','FF8000',25,'on')
        lines=writes.read_text().splitlines()[-4:]
        assert lines==[f'{lights}/left|1-0:63:0:255',f'{lights}/left|2-63:0:0:255',f'{lights}/right|1-63:32:0:255',f'{lights}/right|2-0:0:63:255'],lines
        assert (lights/'left').read_text()=='2-63:0:0:255\n' and (lights/'right').read_text()=='2-0:0:63:255\n'
        run('light-zones','FFFFFF','112233','ABCDEF','123456',100,'off')
        lines=writes.read_text().splitlines()[-4:]
        assert lines==[f'{lights}/left|1-0:0:0:255',f'{lights}/left|2-255:255:255:255',f'{lights}/right|1-0:0:0:255',f'{lights}/right|2-17:34:51:255'],lines
        run('light-off');run('restore-light')
        assert all(value(k)==v for k,v in before.items())
        run('light-zones','FFFFFF','FFFFFF','FFFFFF','FFFFFF',0,'keep')
        assert all(line.endswith('-0:0:0:255') for line in writes.read_text().splitlines()[-4:])
        run('restore-light')
        run('light-zones','FFFFFF','FFFFFF','00FF00','0000FF',100,'keep')
        assert writes.read_text().splitlines()[-4].endswith('1-0:0:0:255') # original left side off
        assert writes.read_text().splitlines()[-2].endswith('1-0:0:255:255') # original right side on
        run('restore-light')
        (lights/'right').unlink();(lights/'right').mkdir()
        run('light-zones','FF0000','0000FF','00FF00','FF8000',25,'on',ok=False)
        assert (control/'lights/colors').exists(),'Rejected direct write lost original snapshot'
        (lights/'right').rmdir();(lights/'right').touch();run('restore-light')
        assert all(value(k)==v for k,v in before.items())
        # Restoring identical stock values still needs an off/on observer transition
        # to replace custom direct colors with the original shared stock color.
        for k in ['left_joystick_light_enabled','right_joystick_light_enabled','left_handle_light_enabled','right_handle_light_enabled']:(state/k).write_text('1')
        (state/'led_light_picker_color').write_text('-65536');(state/'led_light_brightness_percent').write_text('0.50')
        run('light-zones','FF0000','0000FF','00FF00','FF8000',50,'on')
        assert (control/'lights/direct').exists()
        (state/'put-log').write_text('');run('restore-light')
        events=(state/'put-log').read_text().splitlines()
        for k in ['left_joystick_light_enabled','right_joystick_light_enabled','left_handle_light_enabled','right_handle_light_enabled']:
            assert events.index(k+'=0')<events.index(k+'=1'),events
            assert value(k)=='1'
        assert value('led_light_picker_color')=='-65536' and value('led_light_brightness_percent')=='0.50'
        assert not (control/'lights').exists()
        print('PASS restore forces stock observer transition even with unchanged original stock values')
        print('PASS four-zone mapping, RGB scaling 0/25/100%, side masks, invalid inputs, repeated On/Off and direct-write failure recovery')
        for target in range(4):
            run('light-one',target,'123456',50)
            assert writes.read_text().splitlines()[-1].endswith(f'{2 if target<2 else 1}-9:26:43:255')
            assert all(value(k)=='1' for k in ['left_joystick_light_enabled','right_joystick_light_enabled','left_handle_light_enabled','right_handle_light_enabled'])
        run('light-off')
        assert (lights/'l/enable').read_text().strip()=='0' and (lights/'r/enable').read_text().strip()=='0'
        writes.write_text('');right_before=(lights/'right').read_text()
        run('light-one',2,'FF8000',25)
        lines=writes.read_text().splitlines()
        assert len(lines)==2 and lines[0].endswith('2-0:0:0:255') and lines[1].endswith('1-63:32:0:255'),lines
        assert (lights/'right').read_text()==right_before and (lights/'r/enable').read_text().strip()=='0'
        assert (lights/'l/enable').read_text().strip()=='1'
        writes.write_text('');run('light-one',0,'0000FF',100)
        assert len(writes.read_text().splitlines())==1,'Saving active controller rewrote another light'
        run('restore-light');assert not (control/'lights').exists()
        run('light-one',4,'FF0000',50,ok=False);run('light-one',0,'FF0000',101,ok=False);run('light-one',0,'bad',50,ok=False)
        assert not (control/'lights').exists()
        for k in ['left_joystick_light_enabled','right_joystick_light_enabled','left_handle_light_enabled','right_handle_light_enabled']:(state/k).write_text('0')
        run('light-one',2,'00FF00',50);run('restore-light')
        assert (lights/'l/enable').read_text().strip()=='0' and (lights/'r/enable').read_text().strip()=='0','Restore left a directly-enabled controller on'
        # A legacy shared snapshot needs handle originals before direct restore resets them.
        old=control/'lights';old.mkdir()
        for k,v in {'colors':'-65536','brightness':'0.50','left':'0','right':'0','format':'shared'}.items():(old/k).write_text(v)
        (state/'right_handle_light_enabled').write_text('1')
        run('light-one',0,'FF0000',25)
        assert (old/'handles/right').read_text().strip()=='1'
        run('restore-light');assert value('right_handle_light_enabled')=='1' and value('left_handle_light_enabled')=='0'
        (lights/'left').unlink();(lights/'left').mkdir()
        run('light-one',0,'FF0000',50,ok=False)
        assert (control/'lights/direct').exists(),'Single-write failure lost recovery state'
        (lights/'left').rmdir();(lights/'left').touch();run('restore-light')
        assert value('right_handle_light_enabled')=='1'
        print('PASS direct Save restore from off, legacy journal extension and failed selected-write recovery')
        saved={k:value(k) for k in ['led_light_picker_color','led_light_brightness_percent','left_joystick_light_enabled','right_joystick_light_enabled','left_handle_light_enabled','right_handle_light_enabled']}
        writes.write_text('');run('light-zones','FFFFFF','FFFFFF','FFFFFF','FFFFFF',10,25,50,100,'on')
        lines=writes.read_text().splitlines()
        assert lines==[f'{lights}/left|1-127:127:127:255',f'{lights}/left|2-25:25:25:255',f'{lights}/right|1-255:255:255:255',f'{lights}/right|2-63:63:63:255'],lines
        writes.write_text('');run('light-zones','FFFFFF','FFFFFF','FFFFFF','FFFFFF',100,0,25,50,'on')
        lines=writes.read_text().splitlines()
        assert lines[1].endswith('2-255:255:255:255') and lines[3].endswith('2-0:0:0:255')
        run('restore-light');assert all(value(k)==v for k,v in saved.items())
        run('light-zones','FFFFFF','FFFFFF','FFFFFF','FFFFFF',50,101,50,50,'on',ok=False)
        run('light-zones','FFFFFF','FFFFFF','FFFFFF','FFFFFF',50,50,50,'bad','on',ok=False)
        assert not (control/'lights').exists()
        # Group toggles leave the other group's colors/brightness intact.
        for analog,side in [('off','on'),('on','off'),('off','off'),('on','on')]:
            writes.write_text('');run('light-zones','FF0000','0000FF','00FF00','FFFF00',25,50,75,100,side,analog)
            lines=writes.read_text().splitlines()
            assert value('left_joystick_light_enabled')==('1' if analog=='on' else '0')
            assert value('right_joystick_light_enabled')==('1' if analog=='on' else '0')
            assert value('left_handle_light_enabled')==('1' if side=='on' else '0')
            assert value('right_handle_light_enabled')==('1' if side=='on' else '0')
            assert lines[0].endswith('1-0:191:0:255' if side=='on' else '1-0:0:0:255'),lines
            assert lines[1].endswith('2-63:0:0:255' if analog=='on' else '2-0:0:0:255'),lines
            assert lines[2].endswith('1-255:255:0:255' if side=='on' else '1-0:0:0:255'),lines
            assert lines[3].endswith('2-0:0:127:255' if analog=='on' else '2-0:0:0:255'),lines
        run('restore-light');assert all(value(k)==v for k,v in saved.items())
        before={k:value(k) for k in saved};run('light-zones','FF0000','0000FF','00FF00','FFFF00',25,50,75,100,'on','invalid',ok=False)
        assert all(value(k)==v for k,v in before.items())
        print('PASS analog/side independent group toggles, per-zone color/brightness retention, invalid mask rejection and original restore')
        print('PASS independent brightness 0/10/25/50/100%, legacy brightness compatibility, validation and exact restoration')
        print('PASS single-light targets, brightness, no stock setting changes, off-to-single activation and other-zone preservation')
        # Setup backup preserves existing original journals instead of Raven's live changes.
        originals={'fan_mode':'4','fan_speed':'null','smart_fan_mode_switch':'1','led_light_picker_color':'-65536','led_light_brightness_percent':'0.25','left_joystick_light_enabled':'0','right_joystick_light_enabled':'0','left_handle_light_enabled':'0','right_handle_light_enabled':'0'}
        for k,v in originals.items():
            if v=='null':(state/k).unlink(missing_ok=True)
            else:(state/k).write_text(v)
        run('light-zones','0000FF','0000FF','0000FF','0000FF',100,'on')
        runpath.mkdir();(runpath/'mode').write_text('4');(runpath/'speed').write_text('null');(runpath/'armed').touch()
        (state/'fan_mode').write_text('5');(state/'fan_speed').write_text('27500')
        live={k:value(k) for k in originals}
        run('backup-create');backup=control/'recovery'
        assert all((backup/k).read_text().strip()==v for k,v in originals.items())
        assert all(value(k)==v for k,v in live.items()),'Backup creation changed hardware settings'
        checksum=(backup/'SHA256SUMS').read_text();created=(backup/'created').read_text()
        run('backup-create');run('backup-status');assert (backup/'SHA256SUMS').read_text()==checksum and (backup/'created').read_text()==created
        run('restore-light')
        for k in originals:
            if k.startswith(('led_','left_','right_')):assert value(k)==originals[k],(k,value(k))
        assert backup.exists() and not (control/'lights').exists()
        run('fan-recover');assert all(value(k)==originals[k] for k in ['fan_mode','fan_speed','smart_fan_mode_switch'])
        # Repeat recovery is persistent; failed restores retain the baseline for retry.
        (state/'fan_mode').write_text('5');(state/'fail-fan_mode-4').touch();run('fan-recover',ok=False)
        assert backup.exists();(state/'fail-fan_mode-4').unlink();run('fan-recover');assert value('fan_mode')=='4'
        (state/'fail-left_joystick_light_enabled-0').touch();run('restore-light',ok=False)
        assert (backup/'SHA256SUMS').read_text()==checksum
        (state/'fail-left_joystick_light_enabled-0').unlink();run('restore-light');run('restore-light')
        # Corrupt backups cannot alter controls or replace their original contents.
        (backup/'fan_mode').write_text('6');before={k:value(k) for k in originals}
        run('backup-status',ok=False);run('restore-light',ok=False);run('backup-create',ok=False)
        assert all(value(k)==v for k,v in before.items())
        (backup/'fan_mode').write_text('4');run('backup-status')
        assert (backup/'SHA256SUMS').read_text()==checksum
        print('PASS setup backup is read-only, atomic, immutable, checksum checked, preserves original journals and supports repeat recovery/retry')
        # New multipoint configuration uses protected atomic updates and yields to OEM changes.
        (state/'fan_mode').write_text('4');(state/'smart_fan_mode_switch').write_text('1')
        (state/'fan_speed').unlink(missing_ok=True)
        (thermal/'temp').write_text('62000');uptime.write_text('100.00 0.00')
        config=root/'curve.cfg';config.write_text('curve 0 45 50 75 50 70 55 35:0,50:50,60:60,78:70\n')
        run('start2',token);waitfor(lambda:value('fan_mode')=='6' and value('fan_speed')=='30500')
        assert value('smart_fan_mode_switch')=='0'
        (thermal/'temp').write_text('49000');waitfor(lambda:value('fan_speed')=='25000')
        (thermal/'temp').write_text('62000');waitfor(lambda:value('fan_speed')=='30500')
        config.write_text('manual 0 45 50 75 50 70 65 35:0,50:50,60:60,78:70\n')
        run('update2',token);waitfor(lambda:value('fan_speed')=='32500')
        config.write_text('curve 0 45 50 75 50 70 55 35:0,50:50,78:99\n')
        run('update2',token,ok=False);assert value('fan_speed')=='32500'
        (state/'fan_mode').write_text('1');(state/'smart_fan_mode_switch').write_text('0')
        waitfor(lambda:not runpath.exists());assert value('fan_mode')=='1'
        assert (control/'last-result').read_text()=='system-control-changed'
        config.write_text('manual 0 45 50 75 50 70 55 35:0,50:50,60:60,78:70\n')
        run('start2',token);waitfor(lambda:(runpath/'status').exists() and '55%' in (runpath/'status').read_text() and value('fan_speed')=='27500')
        (state/'fan_speed').write_text('33333');waitfor(lambda:not runpath.exists());assert value('fan_mode')=='6' and value('fan_speed')=='33333'
        run('start2',token);waitfor(lambda:value('fan_mode')=='6' and value('fan_speed')=='27500')
        run('stock-fan',4);assert value('fan_mode')=='4' and value('smart_fan_mode_switch')=='1' and not runpath.exists()
        run('start2',token);waitfor(lambda:(runpath/'status').exists())
        run('stock-fan',0);assert value('fan_mode')=='0' and value('smart_fan_mode_switch')=='0' and not runpath.exists()
        run('stock-fan',0);run('stock-fan',4);assert value('fan_mode')=='4' and value('smart_fan_mode_switch')=='1'
        run('stock-fan',99,ok=False)
        run('backup-create');catalog=(control/'stock-fan-v1/modes').read_text();run('backup-create');assert (control/'stock-fan-v1/modes').read_text()==catalog
        print('PASS multipoint start, live update, invalid curve rejection, system takeover without restoration, stock Smart and immutable stock catalog')
        executable('getprop','case "$1" in ro.product.model) echo Odin2;; ro.build.version.sdk) echo 33;; *) echo UnknownFirmware;; esac')
        run('probe',ok=False)
        executable('getprop','case "$1" in ro.product.model) echo Odin3;; *) echo 33;; esac')
        run('probe',ok=False)
        print('PASS Odin2 fan restore, PWM-period guard, shared ARGB lighting, repeat apply, original/null restore and model/build/API guards')
    finally:
        try:run('stop')
        except Exception:pass
