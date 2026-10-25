"""Run Lite UI integration checks only on an explicitly selected owned emulator."""
from pathlib import Path
import argparse,os,subprocess
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--adb-port',default='5038');p.add_argument('--test',choices=['tv-personalization','integration','settings-gallery','antenna','xmltv','updates','epg','release','probe'],default='integration');p.add_argument('--stage',default='download');p.add_argument('--expected',default='');a=p.parse_args();test_class={'tv-personalization':'AndroidTvPersonalizationTest','settings-gallery':'LiteSettingsGalleryTest','probe':'AntennaProbe','antenna':'AntennaIntegrationTest','xmltv':'XmlTvIntegrationTest','epg':'XmlTvDeviceProbe','updates':'LiteUpdateTest','release':'LiteReleaseDeviceTest','integration':'LiteIntegrationTest'}[a.test]
root=Path(__file__).resolve().parents[1];repo=root.parent;sdk=Path(os.environ['ANDROID_HOME']);jdk=Path(os.environ['JAVA_HOME']);bt=max((sdk/'build-tools').iterdir(),key=lambda x:tuple(map(int,x.name.split('.'))));out=root/'out/tests';out.mkdir(exist_ok=True);(out/'classes').mkdir(exist_ok=True)
win=os.name=='nt';exe='.exe' if win else '';bat='.bat' if win else '';adb=[sdk/('platform-tools/adb'+exe),'-P',a.adb_port,'-s',a.serial]
def run(args):subprocess.run(list(map(str,args)),check=True)
if a.test not in ('probe','epg','release'):assert subprocess.check_output(list(map(str,adb+['shell','getprop','ro.kernel.qemu']))).decode().strip()=='1','Owned emulator required'
if a.test=='updates':
 import build_update_fixtures
 build_update_fixtures.build(root,sdk,jdk,bt,out,adb)
provider='<provider android:name="tv.vela.launcher.AntennaIntegrationTest$TestTvProvider" android:authorities="android.media.tv" android:exported="true"/>' if a.test=='antenna' else ''
(out/'manifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="tv.vela.launcher.lite.tests"><uses-permission android:name="android.permission.INTERNET"/><application>'+provider+'</application><instrumentation android:name="tv.vela.launcher.'+test_class+'" android:targetPackage="tv.vela.launcher.lite"/></manifest>',encoding='utf-8')
run([bt/('aapt2'+exe),'link','-o',out/'base.apk','-I',sdk/'platforms/android-34/android.jar','--manifest',out/'manifest.xml','--min-sdk-version','26','--target-sdk-version','33'])
run([jdk/('bin/javac'+exe),'-encoding','UTF-8','-source','8','-target','8','-cp',os.pathsep.join([str(root/'out/classes'),str(sdk/'platforms/android-34/android.jar')]),'-d',out/'classes',(repo if a.test=='tv-personalization' else root)/('tests/'+test_class+'.java')])
run([jdk/('bin/jar'+exe),'cf',out/'classes.jar','-C',out/'classes','.']);run([bt/('d8'+bat),'--min-api','26','--lib',sdk/'platforms/android-34/android.jar','--output',out,out/'classes.jar']);run([jdk/('bin/jar'+exe),'uf',out/'base.apk','-C',out,'classes.dex']);run([bt/('zipalign'+exe),'-f','4',out/'base.apk',out/'aligned.apk']);run([bt/('apksigner'+bat),'sign','--ks',repo/'vela.keystore','--ks-pass','pass:vela-local','--out',out/'tests.apk',out/'aligned.apk']);run(adb+['install','-r',out/'tests.apk'])
result=subprocess.check_output(list(map(str,adb+['shell','am','instrument','-w']+(['-e','stage',a.stage,'-e','expected',a.expected] if a.test=='release' else [])+['tv.vela.launcher.lite.tests/tv.vela.launcher.'+test_class])),timeout=180).decode('utf-8','replace');(out/('result-'+a.test+'.txt')).write_text(result,encoding='utf-8');print(result.encode("ascii","backslashreplace").decode());assert 'INSTRUMENTATION_CODE: -1' in result,'Lite check failed'

if a.test=="updates":run(adb+["shell","appops","set","tv.vela.launcher.lite","REQUEST_INSTALL_PACKAGES","default"])
