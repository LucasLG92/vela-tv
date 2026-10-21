"""Build and run the real-artwork lifecycle test on an explicitly selected device.
Requires Vela already installed. Real-artwork checks use a privately linked Plex account;
Jellyfin checks use an owned loopback fixture on a clean emulator. Never changes home mode.
"""
from pathlib import Path
import argparse,os,subprocess
parser=argparse.ArgumentParser()
parser.add_argument('--serial',required=True)
parser.add_argument('--adb-port',default='5037')
parser.add_argument('--test',choices=['artwork','ambient','weather','sidebar','reorder','remove','catalog','home-status','account','personalization','live-tv','tv-web','tv-progress','continue-watching','appearance','gallery','apps-grid','updates','jellyfin','watched'],default='artwork')
parser.add_argument('--jellyfin-fixture',help='Private JSON for an owned loopback Jellyfin fixture; emulator only')
args=parser.parse_args()
if args.test=='jellyfin' and not args.jellyfin_fixture:parser.error('--jellyfin-fixture is required')
test_class={'artwork':'ArtworkLifecycleTest','ambient':'AndroidAmbientTest','weather':'AndroidWeatherTest','sidebar':'AndroidSidebarTest','reorder':'AndroidSidebarReorderTest','remove':'AndroidSidebarRemoveTest','catalog':'AndroidCatalogTest','home-status':'AndroidHomeStatusTest','account':'AndroidAccountHeaderTest','personalization':'AndroidPersonalizationTest','live-tv':'AndroidLiveTvTest','tv-web':'AndroidTvWebTest','tv-progress':'AndroidTvProgressTest','continue-watching':'AndroidContinueWatchingTest','appearance':'AndroidAppearanceTest','gallery':'AndroidGalleryTest','apps-grid':'AndroidAppsGridTest','updates':'AndroidUpdatesTest','jellyfin':'AndroidJellyfinTest','watched':'AndroidWatchedTest'}[args.test]
root=Path(__file__).resolve().parents[1]
sdk=Path(os.environ['ANDROID_HOME']);jdk=Path(os.environ['JAVA_HOME'])
bt=max((sdk/'build-tools').iterdir(),key=lambda p:tuple(int(x) for x in p.name.split('.')))
win=os.name=='nt';exe='.exe' if win else '';bat='.bat' if win else ''
out=root/'out/artwork-tests';out.mkdir(parents=True,exist_ok=True);(out/'classes').mkdir(exist_ok=True)
process='tvweb' if args.test=='tv-web' else 'home'
manifest=f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="tv.vela.launcher.tests"><application/><instrumentation android:name="tv.vela.launcher.{test_class}" android:targetPackage="tv.vela.launcher" android:targetProcesses="tv.vela.launcher:{process}"/></manifest>'''
(out/'manifest.xml').write_bytes(manifest.encode('utf-8'))
def run(a):subprocess.run(list(map(str,a)),check=True)
assets=['-A',root/'tests/gallery/assets'] if args.test=='gallery' else ['-A',root/'out/update-fixtures/assets'] if args.test=='updates' else []
run([bt/('aapt2'+exe),'link','-o',out/'base.apk','-I',sdk/'platforms/android-34/android.jar','--manifest',out/'manifest.xml','--min-sdk-version','30','--target-sdk-version','33',*assets])
run([jdk/('bin/javac'+exe),'-encoding','UTF-8','-source','8','-target','8','-cp',os.pathsep.join([str(root/'out/classes'),str(sdk/'platforms/android-34/android.jar')]),'-d',out/'classes',root/('tests/'+test_class+'.java')])
run([jdk/('bin/jar'+exe),'cf',out/'classes.jar','-C',out/'classes','.'])
run([bt/('d8'+bat),'--min-api','30','--lib',sdk/'platforms/android-34/android.jar','--output',out,out/'classes.jar'])
run([jdk/('bin/jar'+exe),'uf',out/'base.apk','-C',out,'classes.dex'])
run([bt/('zipalign'+exe),'-f','4',out/'base.apk',out/'aligned.apk'])
run([bt/('apksigner'+bat),'sign','--ks',root/'vela.keystore','--ks-pass','pass:vela-local','--out',out/'tests.apk',out/'aligned.apk'])
adb=[sdk/('platform-tools/adb'+exe),'-P',args.adb_port,'-s',args.serial]
if args.test in ('jellyfin','watched'):
    assert subprocess.check_output(list(map(str,adb+['shell','getprop','ro.kernel.qemu']))).decode().strip()=='1','Jellyfin fixture requires an emulator'
if args.test=='watched':run(adb+['shell','am','force-stop','tv.vela.launcher'])
run(adb+['install','-r',out/'tests.apk'])
if args.test=='jellyfin':run(adb+['push',Path(args.jellyfin_fixture),'/sdcard/Android/data/tv.vela.launcher/files/jellyfin-fixture.json'])
try:
    result=subprocess.check_output(list(map(str,adb+['shell','am','instrument','-w','tv.vela.launcher.tests/tv.vela.launcher.'+test_class])),timeout=240).decode('utf-8','replace')
    (out/('result-'+args.test+'.txt')).write_text(result,encoding='utf-8')
    print(result)
    assert 'INSTRUMENTATION_CODE: -1' in result,'Artwork lifecycle test failed'
finally:
    if args.test=='watched':subprocess.run(list(map(str,adb+['shell','am','force-stop','tv.vela.launcher'])),stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    if args.test=='jellyfin':subprocess.run(list(map(str,adb+['shell','rm','-f','/sdcard/Android/data/tv.vela.launcher/files/jellyfin-fixture.json'])),stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    subprocess.run(list(map(str,adb+['uninstall','tv.vela.launcher.tests'])),stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
