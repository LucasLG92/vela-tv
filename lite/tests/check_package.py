"""Verify that the actual Lite APK is independent and contains no full Vela runtime."""
from pathlib import Path
import hashlib,json,os,re,subprocess,zipfile
root=Path(__file__).resolve().parents[1];sdk=Path(os.environ.get('ANDROID_HOME',str(Path(os.environ.get('LOCALAPPDATA',Path.home()))/'Android/Sdk')));bt=max((sdk/'build-tools').iterdir(),key=lambda p:tuple(map(int,p.name.split('.'))));apk=root/'Vela-Lite.apk'
data=apk.read_bytes();badging=subprocess.check_output([str(bt/('aapt2.exe' if os.name=='nt' else 'aapt2')),'dump','badging',str(apk)]).decode()
assert "name='tv.vela.launcher.lite'" in badging and re.search(r"(?:minSdkVersion|sdkVersion):'26'",badging) and "targetSdkVersion:'33'" in badging
permissions=re.findall(r"^uses-permission: name='([^']+)'",badging,re.M)
assert set(permissions)=={'android.permission.READ_TV_LISTINGS','android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE','android.permission.REQUEST_INSTALL_PACKAGES','android.permission.POST_NOTIFICATIONS','android.permission.RECEIVE_BOOT_COMPLETED'},'Only the declared TV, network, update and opt-in programme alert permissions may be requested'
with zipfile.ZipFile(apk) as z:
 names=z.namelist();assert not any('fixture' in n.lower() or n.endswith(('.keystore','.key','.pem')) or 'config.properties' in n for n in names)
 dex=z.read('classes.dex')
 for forbidden in [b'PlexClient;',b'JellyfinClient;',b'XtreamClient;',b'CinemaHomeView;',b'LiveTvPlayer;',b'Updates;',b'AmbientService;',b'VelaDvrPlayer;',b'VelaHlsBuffer;',b'androidx/media3/']:assert forbidden not in dex,forbidden
 assert 'assets/fonts/InterVariable.ttf' in names
 assert z.read('assets/fonts/InterVariable.ttf')==(root.parent/'app/assets/fonts/InterVariable.ttf').read_bytes(),'Font differs from Vela'
 assert 'assets/licenses/LICENSE.txt' in names and 'assets/licenses/Inter-OFL.txt' in names
 components=subprocess.check_output([str(bt/('aapt2.exe' if os.name=='nt' else 'aapt2')),'dump','xmltree',str(apk),'--file','AndroidManifest.xml']).decode()
 assert not re.search(r'E: service\b',components),'Unexpected background service'
 assert len(re.findall(r'E: receiver\b',components))==1 and 'VelaReminderReceiver' in components,'Only the cached programme reminder receiver may be declared'
 assert len(re.findall(r'E: provider\b',components))==1 and 'LiteUpdateProvider' in components,'Only the private APK provider may be declared'
 assert len(data)<2375*1024,'Lite package grew beyond its profile, voice, sleep, watchlist, six-language, visual styles, programme artwork, temporary measurements, local-font, optional header/weather-widget and foreground remote-binding budget'
print(json.dumps({'result':'PASS','package':'tv.vela.launcher.lite','minimum_android':'8.0','bytes':len(data),'sha256':hashlib.sha256(data).hexdigest(),'permissions':permissions,'background_services':0,'cached_reminder_receivers':1,'private_apk_providers':1,'full_vela_clients_in_apk':False,'font_identical_to_vela':True},indent=2))
