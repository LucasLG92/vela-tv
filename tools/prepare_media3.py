"""Prepare the optional Full-only player from checksum-pinned official Maven artifacts.

No Gradle daemon, library services or native codecs are included. R8 removes unused code.
"""
from pathlib import Path
import hashlib
import json
import urllib.request
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def main():
    cache = ROOT / '.build-deps'
    target = ROOT / 'out' / 'media3'
    cache.mkdir(exist_ok=True)
    (target / 'libs').mkdir(parents=True, exist_ok=True)
    (target / 'res' / 'values').mkdir(parents=True, exist_ok=True)
    (target / 'res' / 'values' / 'empty.xml').write_text('<resources/>', encoding='utf-8')
    rules = ['-keep class tv.vela.launcher.** { *; }', '-keep class io.nayuki.qrcodegen.** { *; }',
             '-dontobfuscate', '-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod',
             '-dontwarn javax.annotation.**', '-dontwarn org.checkerframework.**',
             '-dontwarn com.google.errorprone.annotations.**', '-dontwarn com.google.j2objc.annotations.**']
    for artifact in json.loads((ROOT / 'tools' / 'media3-deps.json').read_text(encoding='utf-8'))['artifacts']:
        name = '_'.join([artifact['group'].replace('.', '_'), artifact['artifact'], artifact['version']])
        filename = name + '_' + artifact['url'].rsplit('/', 1)[1]
        path = cache / filename
        if not path.exists() or hashlib.sha256(path.read_bytes()).hexdigest() != artifact['sha256']:
            with urllib.request.urlopen(artifact['url'], timeout=60) as response:
                content = response.read(32 * 1024 * 1024 + 1)
            if len(content) > 32 * 1024 * 1024 or hashlib.sha256(content).hexdigest() != artifact['sha256']:
                raise RuntimeError('Dependency checksum mismatch: ' + artifact['artifact'])
            path.write_bytes(content)
        output = target / 'libs' / (name + '.jar')
        if artifact['type'] == 'jar':
            output.write_bytes(path.read_bytes())
            continue
        with zipfile.ZipFile(path) as aar:
            output.write_bytes(aar.read('classes.jar'))
            for member in aar.namelist():
                if member.startswith('res/') and not member.endswith('/'):
                    relative = Path(member)
                    if '..' in relative.parts or relative.is_absolute():
                        raise RuntimeError('Invalid AAR path')
                    dest = target / relative.parent / (artifact['artifact'].replace('-', '_') + '_' + relative.name)
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    if dest.exists() and dest.read_bytes() != aar.read(member):
                        raise RuntimeError('Library resource collision: ' + member)
                    dest.write_bytes(aar.read(member))
            if 'proguard.txt' in aar.namelist():
                rules.append(aar.read('proguard.txt').decode('utf-8'))
            symbols = aar.read('R.txt').decode('utf-8').splitlines() if 'R.txt' in aar.namelist() else []
            if symbols:
                package = ET.fromstring(aar.read('AndroidManifest.xml')).attrib['package']
                groups = {}
                for symbol in symbols:
                    typ, group, field, value = symbol.split(maxsplit=3)
                    if typ != 'int':
                        raise RuntimeError('Unsupported library resource symbol')
                    groups.setdefault(group, []).append(field)
                source = ['package ' + package + ';', 'public final class R {']
                for group, fields in groups.items():
                    source.append('public static final class ' + group + ' {')
                    source.extend('public static final int ' + field + '=tv.vela.launcher.R.' + group + '.' + field + ';' for field in fields)
                    source.append('}')
                source.append('}')
                dest = ROOT / 'out' / 'gen' / Path(package.replace('.', '/')) / 'R.java'
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_text('\n'.join(source), encoding='utf-8')
    (target / 'rules.pro').write_text('\n'.join(rules), encoding='utf-8')
    print('Prepared pinned optional Full player dependencies.')


if __name__ == '__main__':
    main()
