"""Shared, checksum-verified JSON dependency for host-side Android tests."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from prepare_media3 import pinned_artifact


def json_jar():
    return pinned_artifact(
        ROOT / '.build-deps' / 'json-20240303.jar',
        'https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar',
        '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed',
    )
