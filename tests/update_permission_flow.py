"""Host-controlled permission grant: Android kills Vela, so instrumentation cannot own the flow."""
import re,subprocess,time,xml.etree.ElementTree as ET

def run_flow(adb):
    adb=list(map(str,adb));pkg='tv.vela.launcher'
    def call(*args):return subprocess.check_output(adb+list(args),timeout=30).decode('utf-8','replace')
    def phase(value):
        text=call('shell','am','instrument','-w','-e','phase',value,'tv.vela.launcher.tests/tv.vela.launcher.AndroidUpdatePermissionTest')
        assert 'INSTRUMENTATION_CODE: -1' in text,text
    def nodes():
        call('shell','uiautomator','dump','/sdcard/vela-permission-test.xml')
        return list(ET.fromstring(call('exec-out','cat','/sdcard/vela-permission-test.xml')).iter('node'))
    def click(label):
        matches=[n for n in nodes() if label in n.get('text','')]
        assert len(matches)==1,f'Expected one control {label}, found {len(matches)}'
        x1,y1,x2,y2=map(int,re.findall(r'\d+',matches[0].get('bounds')))
        call('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
        call('shell','input','keyevent','23')
    def top():return call('shell','dumpsys','activity','activities')
    def wait_top(needle):
        end=time.monotonic()+12
        while time.monotonic()<end:
            if any(needle in line for line in top().splitlines() if 'topResumedActivity' in line or 'mResumedActivity' in line):return
            time.sleep(.15)
        raise AssertionError('Did not resume '+needle+': '+str([line.strip() for line in top().splitlines() if 'topResumedActivity' in line or 'mResumedActivity' in line]))
    def open_updates():
        call('shell','am','start','-n',pkg+'/.SettingsActivity');wait_top('SettingsActivity')
        click('Información y actualizaciones');click('Actualizaciones');wait_top('UpdatesActivity')
    def begin_permission():
        click('Instalar 1.');wait_top('com.android.settings/')
    def wait_installer():
        wait_top('PackageInstallerActivity')
        labels=[n.get('text','') for n in nodes()]
        assert 'Vela' in labels and any(s in ('Instalar','Actualizar','Install','Update') for s in labels),'Missing system install confirmation: '+str(labels)
    def return_from_settings(expected):
        # Recent Android Settings may return through a wrapper/list before the caller.
        for _ in range(4):
            call('shell','input','keyevent','4');time.sleep(.4)
            resumed=[s for s in top().splitlines() if 'topResumedActivity' in s or 'mResumedActivity' in s]
            if not any('com.android.settings/' in s for s in resumed):break
        if expected=='installer':wait_installer()
        else:wait_top(expected)
    initial=call('shell','appops','get',pkg,'REQUEST_INSTALL_PACKAGES')
    match=re.search(r'REQUEST_INSTALL_PACKAGES: (allow|deny|ignore|default)',initial);original=match.group(1) if match else 'default'
    prepared=False
    try:
        call('shell','am','force-stop',pkg);call('shell','appops','set',pkg,'REQUEST_INSTALL_PACKAGES','default')
        phase('prepare');prepared=True;open_updates();begin_permission()
        # Returning without granting must leave a retry, not a permission/installer loop.
        return_from_settings('UpdatesActivity')
        assert any('No se ha concedido el permiso' in n.get('text','') for n in nodes()),'Permission denial was not explained'
        begin_permission();old_pid=call('shell','pidof',pkg+':home').strip();assert old_pid
        switches=[n for n in nodes() if n.get('checkable')=='true'];assert len(switches)==1,'Expected one real permission switch'
        switch=switches[0];x1,y1,x2,y2=map(int,re.findall(r'\d+',switch.get('bounds')))
        call('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
        assert 'allow' in call('shell','appops','get',pkg,'REQUEST_INSTALL_PACKAGES'),'Real Settings did not grant permission'
        # Also force the background process out, even on versions that keep it after a grant.
        call('shell','am','kill',pkg)
        return_from_settings('installer')
        new_pid=call('shell','pidof',pkg+':home').strip();assert new_pid and new_pid!=old_pid,'Test did not exercise process death'
        # Never approve the synthetic future version. Cancellation must not relaunch it.
        call('shell','input','keyevent','4');wait_top('UpdatesActivity');time.sleep(.4);wait_top('UpdatesActivity')
        click('Instalar 1.');wait_installer()
        call('shell','input','keyevent','4');wait_top('UpdatesActivity')
        for entry in ('SettingsActivity','HomeActivity'):
            phase('queue');call('shell','am','start','-n',pkg+'/.'+entry);wait_installer()
            call('shell','input','keyevent','4');wait_top('UpdatesActivity')
        return 'PASS real Settings denial/grant/retry; background process killed during permission flow; recreated process resumed verified APK into Android confirmation once. Cold Settings/Home entry, cancellation and retry work without another download. Synthetic installation never confirmed.'
    finally:
        call('shell','am','force-stop',pkg)
        try:
            if prepared:phase('cleanup')
        finally:
            call('shell','appops','set',pkg,'REQUEST_INSTALL_PACKAGES',original)
            call('shell','rm','-f','/sdcard/vela-permission-test.xml')
