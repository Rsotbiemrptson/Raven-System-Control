"""Native event-ack regression using isolated files and a simulated OEM writer."""
import os, re, subprocess, tempfile, textwrap
from pathlib import Path

source = Path(__file__).resolve().parents[1] / 'native/odin2-zones.c'
keys = ['left_joystick_light_enabled','right_joystick_light_enabled','left_handle_light_enabled','right_handle_light_enabled']
with tempfile.TemporaryDirectory(prefix='raven-zone-native-') as tmp:
    root=Path(tmp); binary=root/'zones'
    subprocess.run(['cc','-DZONE_HOST_TEST','-O2','-Wall','-Wextra','-Werror',str(source),'-o',str(binary)],check=True)
    mock=root/'settings'
    mock.write_text('#!/usr/bin/env python3\n'+textwrap.dedent('''
        import os,sys,subprocess,time,fcntl
        from pathlib import Path
        root=Path(os.environ['RAVEN_ZONE_TEST_ROOT'])
        if sys.argv[1]=='callback':
            key=sys.argv[2]
            if os.environ.get('MODE')=='late' and key=='left_joystick_light_enabled':time.sleep(.15)
            with (root/'lock').open('w') as lock:
                fcntl.flock(lock,fcntl.LOCK_EX)
                on=any((root/k).read_text()=='1' for k in ['left_joystick_light_enabled','left_handle_light_enabled'])
                for node in ['left-enable','right-enable']:
                    (root/node).write_text('1' if on else '0');time.sleep(.002)
                if on:
                    for group in [2,1]:
                        for side in ['left','right']:
                            (root/(side+'-brightness')).write_text(str(group)+'-0:0:0:255');time.sleep(.002)
                if os.environ.get('MODE')=='takeover':(root/'led_light_picker_color').write_text('-16776961')
            sys.exit(0)
        op,key=sys.argv[3],sys.argv[5]
        if op=='get':print((root/key).read_text() if (root/key).exists() else 'null')
        elif op=='put':
            (root/key).write_text(sys.argv[6])
            if key.startswith('left_') and os.environ.get('MODE')!='missing':
                subprocess.Popen([sys.executable,__file__,'callback',key],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,close_fds=True)
        else:sys.exit(1)
    '''));mock.chmod(0o700)
    def run(mode,oldmask,newmask,ok=True):
        case=root/(mode+'-'+oldmask+'-'+newmask);case.mkdir()
        for node in ['left-enable','right-enable','left-brightness','right-brightness']:(case/node).write_text('0')
        for key,value in zip(keys,oldmask):(case/key).write_text('null' if value=='n' else value)
        (case/'led_light_picker_color').write_text('-16777216');(case/'led_light_brightness_percent').write_text('1.00')
        env=dict(os.environ,RAVEN_ZONE_TEST_ROOT=str(case),RAVEN_ZONE_TEST_SETTINGS=str(mock),MODE=mode)
        result=subprocess.run([str(binary),'FF0000','0000FF','00FF00','FF8000','100','50','25','75',newmask,'1.00'],env=env,text=True,capture_output=True,timeout=5)
        assert (result.returncode==0)==ok,(result.stdout,result.stderr)
        assert ''.join('1' if (case/k).read_text()=='1' else '0' for k in keys)==newmask
        if ok:
            assert (case/'left-brightness').read_text().strip()==('1-0:63:0:255' if newmask[2]=='1' else '1-0:0:0:255')
            assert (case/'right-brightness').read_text().strip()==('1-191:96:0:255' if newmask[3]=='1' else '1-0:0:0:255')
        return result.stdout
    for old,new in [('0000','1111'),('1111','0011'),('0011','1111'),('1111','1100'),('1100','1111'),('1111','0000')]:
        assert 'settle=event' in run('normal',old,new)
    late=run('late','0000','1111');assert 'settle=event' in late and int(re.search(r'wait=(\d+)',late)[1])>=80,late
    missing=run('missing','0000','1111');assert 'settle=fallback' in missing and int(re.search(r'wait=(\d+)',missing)[1])>=950,missing
    assert 'settle=unchanged' in run('none','1111','1111')
    assert 'settle=event' in run('defaults','nnnn','1000')
    assert 'Stock lighting changed' in run('takeover','0000','1111',False)
    result=subprocess.run([str(binary),'bad'],text=True,capture_output=True);assert result.returncode!=0
    print('PASS native completion acknowledgments, late callback counting, timeout fallback, off/on brightness retention, unchanged masks, stock takeover and input rejection')
