"""Build and run the real-artwork lifecycle test on an explicitly selected device.
Requires Vela already installed. Real-artwork checks use a privately linked Plex account;
Jellyfin checks use an owned loopback fixture on a clean emulator. Never changes home mode.
"""
from pathlib import Path
import argparse,os,subprocess
parser=argparse.ArgumentParser()
parser.add_argument('--serial',required=True)
parser.add_argument('--adb-port',default='5037')
parser.add_argument('--no-captures',action='store_true',help='Skip optional live-video captures on emulators whose compositor cannot capture a decoder surface')
parser.add_argument('--test',choices=['tv-personalization','channel-controls','artwork','up-next','ambient','weather','sidebar','reorder','remove','catalog','home-status','account','personalization','live-tv','tv-progress','continue-watching','appearance','gallery','apps-grid','updates','update-permission','jellyfin','watched','catalog-filters','xtream','m3u','tv-logos','tv-redesign','tv-timeline','tv-fallback','plex-link','home-tv','integrated-settings','inputs','integrated-update-permission'],default='artwork')
parser.add_argument('--jellyfin-fixture',help='Private JSON for an owned loopback Jellyfin fixture; emulator only')
parser.add_argument('--xtream-fixture',help='Private JSON for an owned loopback Xtream fixture; emulator only')
args=parser.parse_args()
if args.test in ('xtream','m3u','tv-fallback') and not args.xtream_fixture:parser.error('--xtream-fixture is required')
if args.test=='jellyfin' and not args.jellyfin_fixture:parser.error('--jellyfin-fixture is required')
test_class={'tv-personalization':'AndroidTvPersonalizationTest','channel-controls':'AndroidTvNavigationTest','up-next':'AndroidUpNextTest','artwork':'ArtworkLifecycleTest','ambient':'AndroidAmbientTest','weather':'AndroidWeatherTest','sidebar':'AndroidSidebarTest','reorder':'AndroidSidebarReorderTest','remove':'AndroidSidebarRemoveTest','catalog':'AndroidCatalogTest','home-status':'AndroidHomeStatusTest','account':'AndroidAccountHeaderTest','personalization':'AndroidPersonalizationTest','live-tv':'AndroidLiveTvTest','tv-progress':'AndroidTvProgressTest','continue-watching':'AndroidContinueWatchingTest','appearance':'AndroidAppearanceTest','gallery':'AndroidGalleryTest','apps-grid':'AndroidAppsGridTest','updates':'AndroidUpdatesTest','update-permission':'AndroidUpdatePermissionTest','jellyfin':'AndroidJellyfinTest','watched':'AndroidWatchedTest','catalog-filters':'AndroidCatalogFiltersTest','xtream':'AndroidXtreamTest','m3u':'AndroidM3uTest','tv-logos':'AndroidTvLogoTest','tv-redesign':'AndroidTvRedesignTest','tv-timeline':'AndroidTvTimelineTest','tv-fallback':'AndroidTvFallbackTest','plex-link':'AndroidPlexLinkTest','integrated-update-permission':'AndroidUpdatePermissionTest','inputs':'AndroidInputsTest','integrated-settings':'AndroidIntegratedSettingsTest','home-tv':'AndroidHomeLiveTvTest'}[args.test]
root=Path(__file__).resolve().parents[1]
sdk=Path(os.environ['ANDROID_HOME']);jdk=Path(os.environ['JAVA_HOME'])
bt=max((sdk/'build-tools').iterdir(),key=lambda p:tuple(int(x) for x in p.name.split('.')))
win=os.name=='nt';exe='.exe' if win else '';bat='.bat' if win else ''
out=root/'out/artwork-tests';out.mkdir(parents=True,exist_ok=True);(out/'classes').mkdir(exist_ok=True)
process='home'
manifest=f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="tv.vela.launcher.tests"><application/><instrumentation android:name="tv.vela.launcher.{test_class}" android:targetPackage="tv.vela.launcher" android:targetProcesses="tv.vela.launcher:{process}"/></manifest>'''
(out/'manifest.xml').write_bytes(manifest.encode('utf-8'))
def run(a):subprocess.run(list(map(str,a)),check=True)
assets=['-A',root/'tests/gallery/assets'] if args.test=='gallery' else ['-A',root/'out/update-fixtures/assets'] if args.test in ('updates','update-permission','integrated-update-permission') else []
run([bt/('aapt2'+exe),'link','-o',out/'base.apk','-I',sdk/'platforms/android-34/android.jar','--manifest',out/'manifest.xml','--min-sdk-version','30','--target-sdk-version','33',*assets])
run([jdk/('bin/javac'+exe),'-encoding','UTF-8','-source','8','-target','8','-cp',os.pathsep.join([str(root/'out/classes'),str(sdk/'platforms/android-34/android.jar')]),'-d',out/'classes',root/('tests/'+test_class+'.java')])
run([jdk/('bin/jar'+exe),'cf',out/'classes.jar','-C',out/'classes','.'])
run([bt/('d8'+bat),'--min-api','30','--lib',sdk/'platforms/android-34/android.jar','--output',out,out/'classes.jar'])
run([jdk/('bin/jar'+exe),'uf',out/'base.apk','-C',out,'classes.dex'])
run([bt/('zipalign'+exe),'-f','4',out/'base.apk',out/'aligned.apk'])
run([bt/('apksigner'+bat),'sign','--ks',root/'vela.keystore','--ks-pass','pass:vela-local','--out',out/'tests.apk',out/'aligned.apk'])
adb=[sdk/('platform-tools/adb'+exe),'-P',args.adb_port,'-s',args.serial]
if args.test in ('tv-personalization','channel-controls','up-next','jellyfin','watched','catalog-filters','updates','update-permission','xtream','m3u','tv-fallback','plex-link','home-tv','integrated-settings','inputs','integrated-update-permission'):
    assert subprocess.check_output(list(map(str,adb+['shell','getprop','ro.kernel.qemu']))).decode().strip()=='1','This isolated fixture requires an emulator'
if args.test in ('watched','catalog-filters'):run(adb+['shell','am','force-stop','tv.vela.launcher'])
run(adb+['install','-r',out/'tests.apk'])
if args.test in ('xtream','m3u','tv-fallback'):run(adb+['push',Path(args.xtream_fixture),'/sdcard/Android/data/tv.vela.launcher/files/xtream-fixture.json'])
if args.test=='jellyfin':run(adb+['push',Path(args.jellyfin_fixture),'/sdcard/Android/data/tv.vela.launcher/files/jellyfin-fixture.json'])
install_mode=None
if args.test=='updates':
    import re
    appops=subprocess.check_output(list(map(str,adb+['shell','appops','get','tv.vela.launcher','REQUEST_INSTALL_PACKAGES']))).decode()
    match=re.search(r'REQUEST_INSTALL_PACKAGES: (allow|deny|ignore|default)',appops)
    install_mode=match.group(1) if match else 'default'
    run(adb+['shell','appops','set','tv.vela.launcher','REQUEST_INSTALL_PACKAGES','allow'])
try:
    if args.test in ('update-permission','integrated-update-permission'):
        from update_permission_flow import run_flow
        result=run_flow(adb,integrated=args.test=='integrated-update-permission')+'\nINSTRUMENTATION_CODE: -1\n'
    else:result=subprocess.check_output(list(map(str,adb+['shell','am','instrument','-w']+(['-e','captures','false'] if args.no_captures else [])+['tv.vela.launcher.tests/tv.vela.launcher.'+test_class])),timeout=240).decode('utf-8','replace')
    (out/('result-'+args.test+'.txt')).write_text(result,encoding='utf-8')
    print(result)
    assert 'INSTRUMENTATION_CODE: -1' in result,'Artwork lifecycle test failed'
finally:
    if install_mode is not None:subprocess.run(list(map(str,adb+['shell','appops','set','tv.vela.launcher','REQUEST_INSTALL_PACKAGES',install_mode])),stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    if args.test in ('watched','catalog-filters'):subprocess.run(list(map(str,adb+['shell','am','force-stop','tv.vela.launcher'])),stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    if args.test in ('xtream','m3u','tv-fallback'):subprocess.run(list(map(str,adb+['shell','rm','-f','/sdcard/Android/data/tv.vela.launcher/files/xtream-fixture.json'])),stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    if args.test=='jellyfin':subprocess.run(list(map(str,adb+['shell','rm','-f','/sdcard/Android/data/tv.vela.launcher/files/jellyfin-fixture.json'])),stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    subprocess.run(list(map(str,adb+['uninstall','tv.vela.launcher.tests'])),stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
