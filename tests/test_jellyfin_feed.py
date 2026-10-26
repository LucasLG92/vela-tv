"""Jellyfin metadata and URL validation without a server or account."""
from pathlib import Path
import os,subprocess,hashlib,urllib.request
root=Path(__file__).resolve().parents[1];out=root/'out/jellyfin-tests';out.mkdir(parents=True,exist_ok=True)
library=out/'json.jar'
if not library.exists():urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar',library)
assert hashlib.sha256(library.read_bytes()).hexdigest()=='3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'
jdk=Path(os.environ['JAVA_HOME'])/'bin';sdk=Path(os.environ['ANDROID_HOME']);android=sdk/'platforms/android-34/android.jar';exe='.exe' if os.name=='nt' else ''
sources=['tests/FeedUi.java','app/src/tv/vela/launcher/L10n.java','app/src/tv/vela/launcher/CatalogFilters.java','app/src/tv/vela/launcher/PlexClient.java','app/src/tv/vela/launcher/JellyfinClient.java','tests/JellyfinFeedTest.java']
subprocess.run([str(jdk/('javac'+exe)),'-encoding','UTF-8','-cp',os.pathsep.join([str(library),str(android)]),'-d',str(out),*[str(root/s) for s in sources]],check=True)
subprocess.run([str(jdk/('java'+exe)),'-cp',os.pathsep.join([str(out),str(library),str(android)]),'tv.vela.launcher.JellyfinFeedTest'],check=True)
