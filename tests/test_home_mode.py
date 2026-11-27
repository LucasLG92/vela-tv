"""Run HomeMode against a simulated TV shell; no device is contacted."""
from pathlib import Path
from host_deps import json_jar
import os
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
output = root / 'out' / 'home-mode-tests'
output.mkdir(parents=True, exist_ok=True)
library = json_jar()
jdk = Path(os.environ.get('JAVA_HOME', '')) / 'bin'
suffix = '.exe' if os.name == 'nt' else ''
javac = str(jdk / ('javac' + suffix)) if (jdk / ('javac' + suffix)).exists() else shutil.which('javac')
java = str(jdk / ('java' + suffix)) if (jdk / ('java' + suffix)).exists() else shutil.which('java')
assert java and javac, 'Set JAVA_HOME to a JDK 17 or newer'
sources = list((root / 'tests' / 'stubs').rglob('*.java'))
sources += [root / 'app/src/tv/vela/launcher/HomeMode.java', root / 'tests/HomeModeTest.java']
subprocess.run([javac, '-encoding', 'UTF-8', '-cp', str(library), '-d', str(output), *map(str, sources)], check=True)
subprocess.run([java, '-Djava.io.tmpdir=' + str(output), '-cp', os.pathsep.join([str(output), str(library)]), 'tv.vela.launcher.HomeModeTest'], check=True)
