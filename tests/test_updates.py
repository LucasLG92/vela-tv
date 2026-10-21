"""Public release selection, entirely synthetic metadata and no GitHub account."""
from pathlib import Path
import os,subprocess,hashlib,urllib.request
root=Path(__file__).resolve().parents[1]
out=root/'out/update-tests';out.mkdir(parents=True,exist_ok=True)
library=out/'json.jar'
if not library.exists():urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar',library)
assert hashlib.sha256(library.read_bytes()).hexdigest()=='3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'
jdk=Path(os.environ['JAVA_HOME'])/'bin';exe='.exe' if os.name=='nt' else ''
subprocess.run([str(jdk/('javac'+exe)),'-encoding','UTF-8','-cp',str(library),'-d',str(out),str(root/'app/src/tv/vela/launcher/ReleaseFeed.java'),str(root/'tests/ReleaseFeedTest.java')],check=True)
subprocess.run([str(jdk/('java'+exe)),'-cp',os.pathsep.join([str(out),str(library)]),'tv.vela.launcher.ReleaseFeedTest'],check=True)
