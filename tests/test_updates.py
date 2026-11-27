"""Public release selection, entirely synthetic metadata and no GitHub account."""
from pathlib import Path
from host_deps import json_jar
import os,subprocess
root=Path(__file__).resolve().parents[1]
out=root/'out/update-tests';out.mkdir(parents=True,exist_ok=True)
library = json_jar()
jdk=Path(os.environ['JAVA_HOME'])/'bin';exe='.exe' if os.name=='nt' else ''
subprocess.run([str(jdk/('javac'+exe)),'-encoding','UTF-8','-cp',str(library),'-d',str(out),str(root/'app/src/tv/vela/launcher/ReleaseFeed.java'),str(root/'tests/ReleaseFeedTest.java')],check=True)
subprocess.run([str(jdk/('java'+exe)),'-cp',os.pathsep.join([str(out),str(library)]),'tv.vela.launcher.ReleaseFeedTest'],check=True)
