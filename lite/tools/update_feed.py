"""Build the public auxiliary feed from a published release and its exact APK."""
import argparse
import hashlib
import json
from pathlib import Path
import re


def prepare(release, apk):
    tag = release.get('tag_name', '')
    if release.get('draft', True) or not re.fullmatch(
            r'lite-v(?:0|[1-9][0-9]{0,8})\.(?:0|[1-9][0-9]{0,8})\.(?:0|[1-9][0-9]{0,8})(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?', tag):
        raise ValueError('Expected a published Lite release')
    version = tag[6:]
    name = 'Vela-Lite-' + version + '.apk'
    url = 'https://github.com/LucasLG92/vela-tv/releases/download/' + tag + '/' + name
    assets = [a for a in release.get('assets', []) if a.get('name') == name]
    if len(assets) != 1:
        raise ValueError('Expected exactly one Lite APK')
    asset = assets[0]
    digest = 'sha256:' + hashlib.sha256(apk.read_bytes()).hexdigest()
    size = apk.stat().st_size
    if not (1024 <= size <= 16 * 1024 * 1024) or asset.get('size') != size or asset.get('digest') != digest or asset.get('browser_download_url') != url or asset.get('state') != 'uploaded':
        raise ValueError('Published APK metadata does not match the local artifact')
    return [{'tag_name': tag, 'draft': False,
             'prerelease': bool(release.get('prerelease') or '-' in version),
             'assets': [{'name': name, 'state': 'uploaded', 'size': size,
                         'digest': digest, 'browser_download_url': url}]}]


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--release', required=True, type=Path)
    parser.add_argument('--apk', required=True, type=Path)
    args = parser.parse_args()
    feed = prepare(json.loads(args.release.read_text(encoding='utf-8')), args.apk)
    destination = Path(__file__).resolve().parents[1] / 'updates.json'
    destination.write_text(json.dumps(feed, indent=2) + '\n', encoding='utf-8')
    print('Prepared public Lite metadata for ' + feed[0]['tag_name'])
