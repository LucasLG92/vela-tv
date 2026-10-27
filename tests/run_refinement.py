"""Native player/row reuse checks on an explicitly selected owned emulator."""
from pathlib import Path
import argparse,os,subprocess,shutil,re

p=argparse.ArgumentParser()
p.add_argument('--serial',required=True);p.add_argument('--adb-port',default='5038')
p.add_argument('--lite',action='store_true');p.add_argument('--test',choices=['shared','library','uiux','navigation','tv-fixes','identity'],default='shared')
p.add_argument('--art',type=Path,required=True);p.add_argument('--fixture',default='');p.add_argument('--language',default='es')
a=p.parse_args();assert not(a.lite and a.test=='library')
repo=Path(__file__).resolve().parents[1];root=repo/'lite' if a.lite else repo
sdk=Path(os.environ['ANDROID_HOME']);jdk=Path(os.environ['JAVA_HOME'])
bt=max((sdk/'build-tools').iterdir(),key=lambda d:tuple(map(int,d.name.split('.'))))
exe='.exe' if os.name=='nt' else '';bat='.bat' if os.name=='nt' else ''
adb=[sdk/('platform-tools/adb'+exe),'-P',a.adb_port,'-s',a.serial]
def run(args):return subprocess.run(list(map(str,args)),check=True)
assert subprocess.check_output(list(map(str,adb+['shell','getprop','ro.kernel.qemu']))).strip()==b'1','Owned emulator required'
cls={'shared':'AndroidRefinementTest','library':'AndroidLibraryDesignTest','uiux':'AndroidUiUxTest','navigation':'AndroidNavigationTest','tv-fixes':'AndroidTvRegressionTest','identity':'AndroidIdentityTest'}[a.test]
if a.lite and a.test=='tv-fixes':cls='LiteTvRegressionTest'
out=root/('out/refinement-'+a.test);(out/'classes').mkdir(parents=True,exist_ok=True)
assets=out/'assets';assets.mkdir(exist_ok=True)
if a.art:
 for path in a.art.iterdir():
  if re.fullmatch(r'(?:poster-[0-9]+|backdrop-[0-9]+)\.jpg|catalog\.json',path.name):
   assert path.stat().st_size<16*1024*1024;shutil.copyfile(path,assets/path.name)

pkg='tv.vela.launcher'+('.lite' if a.lite else '');testpkg=pkg+'.refinement.tests'
provider='<provider android:name="tv.vela.launcher.LiteTvRegressionTest$Provider" android:authorities="android.media.tv" android:exported="true"/>' if a.lite and a.test=='tv-fixes' else ''
(out/'manifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="'+testpkg+'"><uses-permission android:name="android.permission.INTERNET"/><application>'+provider+'</application><instrumentation android:name="tv.vela.launcher.'+cls+'" android:targetPackage="'+pkg+'"'+('' if a.lite else ' android:targetProcesses="tv.vela.launcher:home"')+'/></manifest>',encoding='utf8')
run([bt/('aapt2'+exe),'link','-o',out/'base.apk','-I',sdk/'platforms/android-34/android.jar','--manifest',out/'manifest.xml','-A',assets,'--min-sdk-version','26' if a.lite else '30','--target-sdk-version','33'])
run([jdk/('bin/javac'+exe),'-encoding','UTF-8','-source','8','-target','8','-cp',os.pathsep.join([str(root/'out/classes'),str(sdk/'platforms/android-34/android.jar')]),'-d',out/'classes',(root if a.lite and a.test=='tv-fixes' else repo)/('tests/'+cls+'.java'),repo/'tests/RefinementHttp.java'])
run([jdk/('bin/jar'+exe),'cf',out/'classes.jar','-C',out/'classes','.'])
run([bt/('d8'+bat),'--min-api','26' if a.lite else '30','--lib',sdk/'platforms/android-34/android.jar','--output',out,out/'classes.jar'])
run([jdk/('bin/jar'+exe),'uf',out/'base.apk','-C',out,'classes.dex'])
run([bt/('zipalign'+exe),'-f','4',out/'base.apk',out/'aligned.apk'])
run([bt/('apksigner'+bat),'sign','--ks',repo/'vela.keystore','--ks-pass','pass:vela-local','--out',out/'tests.apk',out/'aligned.apk'])
if a.lite and a.test=='tv-fixes':subprocess.run(list(map(str,adb+['uninstall','tv.vela.launcher.lite.tests'])),stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
run(adb+['install','-r',out/'tests.apk'])
try:
 args=adb+['shell','am','instrument','-w','-e','reuse','true','-e','language',a.language]+(['-e','fixture',a.fixture] if a.fixture else [])+[testpkg+'/tv.vela.launcher.'+cls]
 result=subprocess.check_output(list(map(str,args)),timeout=240).decode('utf8','replace')
 (out/'result.txt').write_text(result,encoding='utf8');print(result.encode('ascii','backslashreplace').decode())
 assert 'INSTRUMENTATION_CODE: -1' in result,'Native optimization check failed'
finally:
 run(adb+['shell','am','force-stop',pkg])
 run(adb+['uninstall',testpkg])
