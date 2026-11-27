"""Jellyfin metadata and URL validation without a server or account."""
from pathlib import Path
from host_deps import json_jar
from android_sdk import compile_jar
import os,subprocess
root=Path(__file__).resolve().parents[1];out=root/'out/jellyfin-tests';out.mkdir(parents=True,exist_ok=True)
library = json_jar()
jdk=Path(os.environ['JAVA_HOME'])/'bin';sdk=Path(os.environ['ANDROID_HOME']);android=compile_jar(sdk);exe='.exe' if os.name=='nt' else ''
sources=['tests/FeedUi.java','app/src/tv/vela/launcher/L10n.java','app/src/tv/vela/launcher/CatalogFilters.java','app/src/tv/vela/launcher/VelaAccountScope.java','app/src/tv/vela/launcher/PlexClient.java','app/src/tv/vela/launcher/LibraryCollection.java','app/src/tv/vela/launcher/VelaHomeRows.java','app/src/tv/vela/launcher/JellyfinClient.java','tests/JellyfinFeedTest.java']
subprocess.run([str(jdk/('javac'+exe)),'-encoding','UTF-8','-cp',os.pathsep.join([str(library),str(android)]),'-d',str(out),*[str(root/s) for s in sources]],check=True)
subprocess.run([str(jdk/('java'+exe)),'-cp',os.pathsep.join([str(out),str(library),str(android)]),'tv.vela.launcher.JellyfinFeedTest'],check=True)
