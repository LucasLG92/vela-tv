"""Run deterministic frame-timestamp policy tests without Android or network access."""
from pathlib import Path
import os, subprocess

root = Path(__file__).resolve().parents[1]
jdk = Path(os.environ['JAVA_HOME']) / 'bin'
suffix = '.exe' if os.name == 'nt' else ''
out = root / 'out' / 'cadence-tests'
out.mkdir(parents=True, exist_ok=True)
subprocess.run([str(jdk / ('javac' + suffix)), '-encoding', 'UTF-8', '-d', str(out),
                str(root / 'app/src/tv/vela/launcher/VideoCadence.java'),
                str(root / 'tests/VideoCadenceTest.java')], check=True)
subprocess.run([str(jdk / ('java' + suffix)), '-cp', str(out),
                'tv.vela.launcher.VideoCadenceTest'], check=True)
