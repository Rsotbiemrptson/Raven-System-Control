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
    replacements={
        'D=/data/local/odin-controls':f'D={root}/control',
        '/sys/class/gpio5_pwm2':str(fan), '/sys/class/thermal':str(root/'thermal'),
        '/sys/class/sn3112l/led/brightness':str(lights/'left'),
        '/sys/class/sn3112r/led/brightness':str(lights/'right'),
        '/proc/uptime':str(uptime), 'sleep 3':'sleep 0.05',
    }
    script=source.read_text()
    for a,b in replacements.items():script=script.replace(a,b)
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
 printf '%s' "$6" > "$D/$5";;
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
        run('light','FF0000','00FF00',50);run('restore-light')
        assert value('joystick_led_light_picker_color')=='#112233,#445566'
        assert value('joystick_light_enabled')=='0,1' and value('led_light_brightness_percent')=='0.30'
        run('light','BAD','00FF00',50,ok=False)
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
        executable('getprop','case "$1" in ro.product.model) echo Odin2;; ro.build.version.sdk) echo 33;; *) echo UnknownFirmware;; esac')
        run('probe',ok=False)
        executable('getprop','case "$1" in ro.product.model) echo Odin3;; *) echo 33;; esac')
        run('probe',ok=False)
        print('PASS Odin2 fan restore, PWM-period guard, shared ARGB lighting, repeat apply, original/null restore and model/build/API guards')
    finally:
        try:run('stop')
        except Exception:pass
