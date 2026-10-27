"""Validate Plex's JSON metadata mapping without contacting an account or server."""
from pathlib import Path
import os, subprocess, hashlib, urllib.request
root=Path(__file__).resolve().parents[1]
output=root/'out/plex-tests'
output.mkdir(parents=True,exist_ok=True)
library=output/'json.jar'
if not library.exists(): urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar',library)
assert hashlib.sha256(library.read_bytes()).hexdigest()=='3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'
jdk=Path(os.environ['JAVA_HOME'])/'bin'
sdk=Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT','')))
android=sdk/'platforms/android-34/android.jar'
suffix='.exe' if os.name=='nt' else ''
subprocess.run([str(jdk/('javac'+suffix)),'-encoding','UTF-8','-cp',os.pathsep.join([str(library),str(android)]),
    '-d',str(output),str(root/'tests/FeedUi.java'),str(root/'app/src/tv/vela/launcher/L10n.java'),str(root/'app/src/tv/vela/launcher/VelaAccountScope.java'),str(root/'app/src/tv/vela/launcher/PlexClient.java'),str(root/'app/src/tv/vela/launcher/LibraryCollection.java'),str(root/'app/src/tv/vela/launcher/JellyfinClient.java'),str(root/'app/src/tv/vela/launcher/CatalogFilters.java'),str(root/'tests/PlexFeedTest.java'),str(root/'tests/CatalogFiltersTest.java')],check=True)
for language,country in [('en','US'),('es','ES')]:
    subprocess.run([str(jdk/('java'+suffix)),'-Duser.language='+language,'-Duser.country='+country,'-cp',os.pathsep.join([str(output),str(library),str(android)]),'tv.vela.launcher.PlexFeedTest'],check=True)

subprocess.run([str(jdk/('java'+suffix)),'-cp',os.pathsep.join([str(output),str(library),str(android)]),'tv.vela.launcher.CatalogFiltersTest'],check=True)
