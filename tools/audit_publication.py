"""Check the files in a public source copy, without reading device data or printing secret values."""
from pathlib import Path
import ipaddress,json,re,subprocess,sys

root=Path(__file__).resolve().parents[1]
listed=subprocess.check_output(['git','ls-files','-z','--cached','--others','--exclude-standard'],cwd=root).decode().split('\0')
files=sorted(set(name for name in listed if name and (root/name).is_file()))
issues=[]
private_names={'config.properties','local.properties','adbkey','adbkey.pub','.env'}
private_suffixes={'.keystore','.jks','.p12','.pem','.key','.apk','.idsig','.log','.zip'}
networks=[ipaddress.ip_network(x) for x in ('10.0.0.0/8','172.16.0.0/12','192.168.0.0/16')]
for name in files:
    path=Path(name)
    if path.name in private_names or path.suffix.lower() in private_suffixes or path.name.startswith('.env.') or name.startswith(('out/','outputs/','Recuperacion-')):
        issues.append((name,'private or generated file'))
    if name.startswith('docs/') or name=='.github/workflows/pages.yml':issues.append((name,'inherited website is not part of the public app copy'))
    raw=(root/name).read_bytes()
    # Native screenshots need pixels and their colour profile, never personal EXIF/XMP.
    if name.startswith('gallery/') and path.suffix.lower()=='.webp':
        if raw[:4]!=b'RIFF' or raw[8:12]!=b'WEBP':issues.append((name,'invalid screenshot format'))
        offset=12
        while offset+8<=len(raw):
            tag=raw[offset:offset+4];size=int.from_bytes(raw[offset+4:offset+8],'little')
            if tag in (b'EXIF',b'XMP '):issues.append((name,'screenshot includes EXIF or XMP metadata'))
            if offset+8+size>len(raw):issues.append((name,'truncated screenshot chunk'));break
            offset+=8+size+(size%2)

    try:text=raw.decode('utf-8')
    except UnicodeDecodeError:continue
    if re.search(r'(?i)[a-z]:[\\/]+Users[\\/]+[^\\/\s]+',text):issues.append((name,'personal Windows user path'))
    for candidate in re.findall(r'(?<![\d.])(?:\d{1,3}\.){3}\d{1,3}(?![\d.])',text):
        try:address=ipaddress.ip_address(candidate)
        except ValueError:continue
        if any(address in network and address!=network.network_address for network in networks):issues.append((name,'private LAN address'));break
    if re.search(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----\s*[A-Za-z0-9+/]{30,}',text):issues.append((name,'private key material'))
properties={}
for line in (root/'config.example.properties').read_text(encoding='utf-8').splitlines():
    if '=' in line and not line.lstrip().startswith('#'):
        key,value=line.split('=',1);properties[key.strip()]=value.strip()
for key in ('tmdb_key','city','recovery_home'):
    if properties.get(key)!='':issues.append(('config.example.properties','non-neutral '+key))
for key in ('latitude','longitude'):
    if properties.get(key)!='0':issues.append(('config.example.properties','non-neutral '+key))
if properties.get('disable_apps')!='false':issues.append(('config.example.properties','package disabling enabled'))
manifest=root/'gallery/index.json'
if manifest.exists():
    pictures=json.loads(manifest.read_text(encoding='utf-8'))
    expected={item['file'] for item in pictures}
    actual={p.name for p in (root/'gallery').glob('*.webp')}
    if expected!=actual or any(item.get('personal_data') is not False for item in pictures):issues.append(('gallery/index.json','unreviewed or missing gallery image'))
if issues:
    for name,reason in issues:print(f'FAIL {name}: {reason}')
    sys.exit(1)
print(f'PASS {len(files)} public files: no private filenames, LAN addresses, user paths or key material; neutral defaults and reviewed gallery inventory')
