"""Signed synthetic future APKs for an explicitly selected emulator; never distribute them."""
from pathlib import Path
import os,subprocess,zipfile,struct,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1];out=root/'out/update-fixtures';assets=out/'assets';assets.mkdir(parents=True,exist_ok=True)
sdk=Path(os.environ['ANDROID_HOME']);jdk=Path(os.environ['JAVA_HOME']);bt=max((sdk/'build-tools').iterdir(),key=lambda p:tuple(map(int,p.name.split('.'))))
exe='.exe' if os.name=='nt' else '';bat='.bat' if os.name=='nt' else '';ns='{http://schemas.android.com/apk/res/android}'
manifest=(root/'app/AndroidManifest.xml').read_text(encoding='utf-8');parsed=ET.fromstring(manifest)
code=int(parsed.attrib[ns+'versionCode']);current=parsed.attrib[ns+'versionName'];parts=current.split('-')[0].split('.');parts[2]=str(int(parts[2])+1);version='.'.join(parts)+'-preview'
def run(a):subprocess.run(list(map(str,a)),check=True,stdout=subprocess.DEVNULL)
key=out/'test-only.keystore'
if not key.exists():run([jdk/('bin/keytool'+exe),'-genkeypair','-keystore',key,'-storepass','test-only','-keypass','test-only','-alias','test','-dname','CN=Vela Test Only','-keyalg','RSA','-validity','2'])
for kind in ('future','wrong-signer','wrong-package'):
    source=manifest.replace('android:versionCode="'+str(code)+'"','android:versionCode="'+str(code+1)+'"').replace('android:versionName="'+current+'"','android:versionName="'+version+'"')
    if kind=='wrong-package':source=source.replace('package="tv.vela.launcher"','package="tv.vela.updatetest.other"').replace('android:authorities="tv.vela.launcher.updates"','android:authorities="tv.vela.updatetest.other.updates"')
    path=out/(kind+'.xml');path.write_text(source,encoding='utf-8');base=out/(kind+'-base.apk');aligned=out/(kind+'-aligned.apk')
    run([bt/('aapt2'+exe),'link','-o',base,'-I',sdk/'platforms/android-34/android.jar','--manifest',path,root/'out/res.zip','--min-sdk-version','30','--target-sdk-version','33','-A',root/'app/assets','-0','ttf'])
    with zipfile.ZipFile(base,'a') as z:z.write(root/'out/classes.dex','classes.dex')
    # Normalize aapt2 Windows paths exactly as the production build does.
    normalized=out/(kind+'-normal.apk')
    with zipfile.ZipFile(base) as src,zipfile.ZipFile(normalized,'w') as dest:
        for info in src.infolist():
            with base.open('rb') as raw:
                raw.seek(info.header_offset);header=raw.read(30);info.orig_filename=raw.read(struct.unpack_from('<H',header,26)[0]).decode('utf-8')
            dest.writestr(info.filename.replace('\\','/'),src.read(info),compress_type=info.compress_type)
    run([bt/('zipalign'+exe),'-f','4',normalized,aligned])
    run([bt/('apksigner'+bat),'sign','--ks',key if kind=='wrong-signer' else root/'vela.keystore','--ks-pass','pass:test-only' if kind=='wrong-signer' else 'pass:vela-local','--out',assets/(kind+'.apk'),aligned])
(assets/'version.txt').write_text(version,encoding='utf-8');print('Prepared synthetic update fixtures '+version)
