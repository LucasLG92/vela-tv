"""Build and run the real-artwork lifecycle test on an explicitly selected device.
Requires Vela already installed and Plex linked privately. Never changes home mode.
"""
from pathlib import Path
import argparse,os,subprocess
parser=argparse.ArgumentParser()
parser.add_argument('--serial',required=True)
parser.add_argument('--adb-port',default='5037')
parser.add_argument('--test',choices=['artwork','ambient','weather','sidebar','reorder','remove','catalog','home-status','account','personalization','live-tv','tv-web','tv-progress','appearance','gallery','apps-grid'],default='artwork')
args=parser.parse_args()
test_class={'artwork':'ArtworkLifecycleTest','ambient':'AndroidAmbientTest','weather':'AndroidWeatherTest','sidebar':'AndroidSidebarTest','reorder':'AndroidSidebarReorderTest','remove':'AndroidSidebarRemoveTest','catalog':'AndroidCatalogTest','home-status':'AndroidHomeStatusTest','account':'AndroidAccountHeaderTest','personalization':'AndroidPersonalizationTest','live-tv':'AndroidLiveTvTest','tv-web':'AndroidTvWebTest','tv-progress':'AndroidTvProgressTest','appearance':'AndroidAppearanceTest','gallery':'AndroidGalleryTest','apps-grid':'AndroidAppsGridTest'}[args.test]
root=Path(__file__).resolve().parents[1]
sdk=Path(os.environ['ANDROID_HOME']);jdk=Path(os.environ['JAVA_HOME'])
bt=max((sdk/'build-tools').iterdir(),key=lambda p:tuple(int(x) for x in p.name.split('.')))
win=os.name=='nt';exe='.exe' if win else '';bat='.bat' if win else ''
out=root/'out/artwork-tests';out.mkdir(parents=True,exist_ok=True);(out/'classes').mkdir(exist_ok=True)
process='tvweb' if args.test=='tv-web' else 'home'
manifest=f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="tv.vela.launcher.tests"><application/><instrumentation android:name="tv.vela.launcher.{test_class}" android:targetPackage="tv.vela.launcher" android:targetProcesses="tv.vela.launcher:{process}"/></manifest>'''
(out/'manifest.xml').write_bytes(manifest.encode('utf-8'))
def run(a):subprocess.run(list(map(str,a)),check=True)
assets=['-A',root/'tests/gallery/assets'] if args.test=='gallery' else []
run([bt/('aapt2'+exe),'link','-o',out/'base.apk','-I',sdk/'platforms/android-34/android.jar','--manifest',out/'manifest.xml','--min-sdk-version','30','--target-sdk-version','33',*assets])
run([jdk/('bin/javac'+exe),'-encoding','UTF-8','-source','8','-target','8','-cp',os.pathsep.join([str(root/'out/classes'),str(sdk/'platforms/android-34/android.jar')]),'-d',out/'classes',root/('tests/'+test_class+'.java')])
run([jdk/('bin/jar'+exe),'cf',out/'classes.jar','-C',out/'classes','.'])
run([bt/('d8'+bat),'--min-api','30','--lib',sdk/'platforms/android-34/android.jar','--output',out,out/'classes.jar'])
run([jdk/('bin/jar'+exe),'uf',out/'base.apk','-C',out,'classes.dex'])
run([bt/('zipalign'+exe),'-f','4',out/'base.apk',out/'aligned.apk'])
run([bt/('apksigner'+bat),'sign','--ks',root/'vela.keystore','--ks-pass','pass:vela-local','--out',out/'tests.apk',out/'aligned.apk'])
adb=[sdk/('platform-tools/adb'+exe),'-P',args.adb_port,'-s',args.serial]
run(adb+['install','-r',out/'tests.apk'])
try:
    result=subprocess.check_output(list(map(str,adb+['shell','am','instrument','-w','tv.vela.launcher.tests/tv.vela.launcher.'+test_class])),timeout=240).decode('utf-8','replace')
    (out/('result-'+args.test+'.txt')).write_text(result,encoding='utf-8')
    print(result)
    assert 'INSTRUMENTATION_CODE: -1' in result,'Artwork lifecycle test failed'
finally:
    subprocess.run(list(map(str,adb+['uninstall','tv.vela.launcher.tests'])),stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
