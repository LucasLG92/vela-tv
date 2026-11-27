"""Validate Plex's JSON metadata mapping without contacting an account or server."""
from pathlib import Path
from host_deps import json_jar
from android_sdk import compile_jar
import os, subprocess
root=Path(__file__).resolve().parents[1]
output=root/'out/plex-tests'
output.mkdir(parents=True,exist_ok=True)
library = json_jar()
jdk=Path(os.environ['JAVA_HOME'])/'bin'
sdk=Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT','')))
android=compile_jar(sdk)
suffix='.exe' if os.name=='nt' else ''
subprocess.run([str(jdk/('javac'+suffix)),'-encoding','UTF-8','-cp',os.pathsep.join([str(library),str(android)]),
    '-d',str(output),str(root/'tests/FeedUi.java'),str(root/'app/src/tv/vela/launcher/L10n.java'),str(root/'app/src/tv/vela/launcher/VelaAccountScope.java'),str(root/'app/src/tv/vela/launcher/PlexClient.java'),str(root/'app/src/tv/vela/launcher/LibraryCollection.java'),str(root/'app/src/tv/vela/launcher/VelaHomeRows.java'),str(root/'app/src/tv/vela/launcher/JellyfinClient.java'),str(root/'app/src/tv/vela/launcher/CatalogFilters.java'),str(root/'tests/PlexFeedTest.java'),str(root/'tests/CatalogFiltersTest.java')],check=True)
for language,country in [('en','US'),('es','ES')]:
    subprocess.run([str(jdk/('java'+suffix)),'-Duser.language='+language,'-Duser.country='+country,'-cp',os.pathsep.join([str(output),str(library),str(android)]),'tv.vela.launcher.PlexFeedTest'],check=True)

subprocess.run([str(jdk/('java'+suffix)),'-cp',os.pathsep.join([str(output),str(library),str(android)]),'tv.vela.launcher.CatalogFiltersTest'],check=True)
