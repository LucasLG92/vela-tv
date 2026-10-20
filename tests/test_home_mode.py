"""Run HomeMode against a simulated TV shell; no device is contacted."""
from pathlib import Path
import hashlib
import os
import shutil
import subprocess
import urllib.request

root = Path(__file__).resolve().parents[1]
output = root / 'out' / 'home-mode-tests'
output.mkdir(parents=True, exist_ok=True)
json_jar = output / 'json.jar'
url = 'https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar'
if not json_jar.exists():
    urllib.request.urlretrieve(url, json_jar)
assert hashlib.sha256(json_jar.read_bytes()).hexdigest() == '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'
jdk = Path(os.environ.get('JAVA_HOME', '')) / 'bin'
suffix = '.exe' if os.name == 'nt' else ''
javac = str(jdk / ('javac' + suffix)) if (jdk / ('javac' + suffix)).exists() else shutil.which('javac')
java = str(jdk / ('java' + suffix)) if (jdk / ('java' + suffix)).exists() else shutil.which('java')
assert java and javac, 'Set JAVA_HOME to a JDK 17 or newer'
sources = list((root / 'tests' / 'stubs').rglob('*.java'))
sources += [root / 'app/src/tv/vela/launcher/HomeMode.java', root / 'tests/HomeModeTest.java']
subprocess.run([javac, '-encoding', 'UTF-8', '-cp', str(json_jar), '-d', str(output), *map(str, sources)], check=True)
subprocess.run([java, '-Djava.io.tmpdir=' + str(output), '-cp', os.pathsep.join([str(output), str(json_jar)]), 'tv.vela.launcher.HomeModeTest'], check=True)
