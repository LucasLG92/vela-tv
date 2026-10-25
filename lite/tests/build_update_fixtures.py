"""Tiny signed APKs used only by the owned-emulator updater test; never installed."""
from pathlib import Path
import os,subprocess

def build(root,sdk,jdk,bt,out,adb):
    win=os.name=='nt';exe='.exe' if win else '';bat='.bat' if win else ''
    def run(a):subprocess.run(list(map(str,a)),check=True,stdout=subprocess.DEVNULL)
    folder=out/'update-fixtures';folder.mkdir(exist_ok=True)
    wrong=folder/'wrong.keystore'
    if not wrong.exists():run([jdk/('bin/keytool'+exe),'-genkeypair','-keystore',wrong,'-alias','test','-keyalg','RSA','-keysize','2048','-validity','3650','-storepass','test-local','-keypass','test-local','-dname','CN=Isolated test'])
    for name,package,key,password in [('update','tv.vela.launcher.lite',root.parent/'vela.keystore','vela-local'),('wrong-package','tv.vela.launcher',root.parent/'vela.keystore','vela-local'),('wrong-signer','tv.vela.launcher.lite',wrong,'test-local')]:
        manifest=folder/(name+'.xml');manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="'+package+'" android:versionCode="999999" android:versionName="99.0.0-fixture"><application android:label="Updater test fixture" android:hasCode="false"/></manifest>',encoding='utf-8')
        run([bt/('aapt2'+exe),'link','-o',folder/(name+'-base.apk'),'-I',sdk/'platforms/android-34/android.jar','--manifest',manifest,'--min-sdk-version','26','--target-sdk-version','33'])
        run([bt/('zipalign'+exe),'-f','4',folder/(name+'-base.apk'),folder/(name+'-aligned.apk')])
        run([bt/('apksigner'+bat),'sign','--ks',key,'--ks-pass','pass:'+password,'--out',folder/(name+'.apk'),folder/(name+'-aligned.apk')])
        target='/sdcard/Android/data/tv.vela.launcher.lite/files/update-test/'
        run(adb+['shell','mkdir','-p',target]);run(adb+['push',folder/(name+'.apk'),target+name+'.apk'])
