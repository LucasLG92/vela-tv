"""Publish already signed, reviewed APKs. No signing key is used by GitHub Actions."""
from pathlib import Path
import argparse,base64,hashlib,json,os,re,subprocess,xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
REPO='LucasLG92/vela-tv'
SIGNER='351be7ac4d954b7edc3d763593ca622c2afde578cb34c1a667f0253889b31cb2'
ANDROID='{http://schemas.android.com/apk/res/android}'

def require(ok,message):
    if not ok:raise ValueError(message)

def manifest(path):
    doc=json.loads(path.read_text(encoding='utf-8'))
    require(doc.get('repository')==REPO,'Unexpected repository')
    releases=doc.get('releases',[])
    require(len(releases)==2,'Exactly Full and Lite must be reviewed together')
    packages=set()
    for item in releases:
        package=item['package'];lite=package=='tv.vela.launcher.lite'
        require(package in ('tv.vela.launcher','tv.vela.launcher.lite') and package not in packages,'Unexpected or duplicate package');packages.add(package)
        version=item['version'];require(re.fullmatch(r'\d+\.\d+\.\d+-preview',version) is not None,'Invalid version')
        require(item['tag']==('lite-v' if lite else 'v')+version,'Tag/version mismatch')
        require(item['file']==('Vela-Lite-' if lite else 'Vela-')+version+'.apk','Unexpected APK filename')
        require(0<item['size']<=64*1024*1024,'Invalid APK size')
        require(re.fullmatch('[a-f0-9]{40}',item['blob_sha']) is not None and re.fullmatch('[a-f0-9]{64}',item['sha256']) is not None,'Invalid content digest')
        source=ET.parse(ROOT/('lite/AndroidManifest.xml' if lite else 'app/AndroidManifest.xml')).getroot()
        require(source.attrib['package']==package and source.attrib[ANDROID+'versionName']==version and int(source.attrib[ANDROID+'versionCode'])==item['code'],'Source/APK version mismatch')
        chunks=item.get('chunks',[]);require(0<len(chunks)<=128,'Invalid APK parts');require(sum(c['size'] for c in chunks)==item['size'],'APK part sizes mismatch')
        for c in chunks:require(re.fullmatch('[a-f0-9]{40}',c['sha']) is not None and 0<c['size']<=512*1024,'Invalid APK part')
        notes=ROOT/item['notes'];require(notes.resolve().is_relative_to((ROOT/'guide/releases').resolve()) and notes.is_file(),'Invalid release notes path')
    return releases

def verify_bytes(data,item):
    require(len(data)==item['size'],'APK size mismatch')
    require(hashlib.sha256(data).hexdigest()==item['sha256'],'APK SHA-256 mismatch')
    require(hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()==item['blob_sha'],'Git blob mismatch')

def sdk_tools():
    sdk=Path(os.environ['ANDROID_HOME'])
    return max((sdk/'build-tools').iterdir(),key=lambda p:tuple(map(int,p.name.split('.'))))

def verify_apk(path,item):
    verify_bytes(path.read_bytes(),item);bt=sdk_tools();bat='.bat' if os.name=='nt' else '';exe='.exe' if os.name=='nt' else ''
    certificate=subprocess.check_output([str(bt/('apksigner'+bat)),'verify','--print-certs',str(path)],text=True)
    signers=re.findall(r'certificate SHA-256 digest: ([a-f0-9]+)',certificate)
    require(signers==[SIGNER],'Unexpected APK signing identity')
    badging=subprocess.check_output([str(bt/('aapt2'+exe)),'dump','badging',str(path)],text=True)
    found=re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",badging,re.M)
    require(found is not None and found.groups()==(item['package'],str(item['code']),item['version']),'Compiled APK identity/version mismatch')

def gh(*args,missing=False,body=None):
    done=subprocess.run(['gh',*args],capture_output=True,input=None if body is None else json.dumps(body).encode('utf-8'))
    if done.returncode:
        if missing and b'HTTP 404' in done.stderr:return None
        raise RuntimeError('GitHub command failed: '+done.stderr.decode('utf-8','replace')[:1200])
    return done.stdout

def api(path,missing=False,binary=False):
    result=gh('api','-H','Accept: application/octet-stream' if binary else 'Accept: application/vnd.github+json',f'repos/{REPO}/{path}',missing=missing)
    return result if binary or result is None else json.loads(result)

def find_release(tag):
    release=api('releases/tags/'+tag,missing=True)
    if release is None:
        # Draft releases need not have a Git tag yet; resolve them by release ID.
        for page in range(1,21):
            rows=api('releases?per_page=100'+('' if page==1 else '&page='+str(page)))
            release=next((r for r in rows if r['tag_name']==tag),None)
            if release is not None or len(rows)<100:break
    return release

def create_draft(item,source):
    body={'tag_name':item['tag'],'target_commitish':source,'name':item['title'],
          'body':(ROOT/item['notes']).read_text(encoding='utf-8'),
          'draft':True,'prerelease':True,'make_latest':'false'}
    release=json.loads(gh('api','--method','POST','-H','Accept: application/vnd.github+json',
                          f'repos/{REPO}/releases','--input','-',body=body))
    require(isinstance(release,dict) and isinstance(release.get('id'),int)
            and release.get('tag_name')==item['tag'] and release.get('draft') is True,
            'GitHub did not return the requested release draft')
    return release

def asset_bytes(asset):
    require(asset.get('state')=='uploaded' and isinstance(asset.get('id'),int),'Incomplete release asset')
    return api('releases/assets/'+str(asset['id']),binary=True)

def verify_assets(release,item):
    assets={a['name']:a for a in release['assets']}
    require(item['file'] in assets and item['file']+'.sha256' in assets,'Release is missing a reviewed asset')
    verify_bytes(asset_bytes(assets[item['file']]),item)
    expected=(item['sha256']+'  '+item['file']+'\n').encode()
    require(asset_bytes(assets[item['file']+'.sha256'])==expected,'Release checksum file mismatch')

def publish(item,path,source):
    release=find_release(item['tag'])
    if release is not None and not release['draft']:
        verify_assets(release,item);print('Already published and verified:',release['html_url']);return release
    if release is None:
        # Use the creation response, not an immediately repeated list/tag lookup.
        release=create_draft(item,source)
    if release['target_commitish']!=source:
        require(not release['assets'],'An existing draft with assets belongs to a different source commit')
        gh('release','edit',item['tag'],'--repo',REPO,'--target',source)
        release=api('releases/'+str(release['id']))
    require(release['target_commitish']==source,'Draft belongs to a different source commit')
    expected={item['file'],item['file']+'.sha256'}
    require({a['name'] for a in release['assets']}<=expected,'Draft contains unexpected assets')
    present={a['name']:a for a in release['assets']}
    for file in (path,Path(str(path)+'.sha256')):
        if file.name in present:require(asset_bytes(present[file.name])==file.read_bytes(),'Existing draft asset differs; refusing overwrite')
        else:gh('release','upload',item['tag'],str(file),'--repo',REPO)
    verify_assets(api('releases/'+str(release['id'])),item)
    gh('release','edit',item['tag'],'--repo',REPO,'--draft=false','--prerelease','--latest=false')
    published=api('releases/'+str(release['id']));require(not published['draft'] and published['prerelease'],'Release was not published as a preview')
    verify_assets(published,item)
    ref=api('git/ref/tags/'+item['tag'])['object']
    if ref['type']=='tag':ref=api('git/tags/'+ref['sha'])['object']
    require(ref['type']=='commit' and ref['sha']==source,'Published tag points at another source commit')
    print('Published and downloaded back for verification:',published['html_url']);return published

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--manifest',type=Path,default=ROOT/'.github/release-candidates.json');parser.add_argument('--verify-only',action='store_true');parser.add_argument('--local-dir',type=Path);parser.add_argument('--source',default='');args=parser.parse_args()
    items=manifest(args.manifest)
    if not args.verify_only:
        require(os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('GITHUB_REPOSITORY')==REPO,'Publication is restricted to this repository in GitHub Actions')
        require(args.source==os.environ.get('GITHUB_SHA') and re.fullmatch('[a-f0-9]{40}',args.source) is not None,'Invalid source commit')
        require(args.local_dir is None,'Publication only accepts reviewed Git objects')
    folder=ROOT/'out/release-publication';folder.mkdir(parents=True,exist_ok=True)
    prepared=[]
    for item in items:
        if args.local_dir:path=args.local_dir/item['file']
        else:
            previous=find_release(item['tag'])
            existing=next((a for a in previous['assets'] if a['name']==item['file']),None) if previous else None
            if existing:data=asset_bytes(existing)
            else:
                parts=[]
                for part in item['chunks']:
                    blob=api('git/blobs/'+part['sha']);require(blob.get('encoding')=='base64','Unsupported Git blob encoding')
                    raw=base64.b64decode(''.join(blob['content'].split()),validate=True)
                    require(len(raw)==part['size'] and hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==part['sha'],'APK part mismatch');parts.append(raw)
                data=b''.join(parts)
            verify_bytes(data,item);path=folder/item['file'];path.write_bytes(data)
        verify_apk(path,item);prepared.append((item,path));print('Verified package, version, official signer and bytes:',item['file'])
    if args.verify_only:return
    # Verify both packages before creating or publishing either release.
    for item,path in prepared:Path(str(path)+'.sha256').write_text(item['sha256']+'  '+item['file']+'\n',encoding='utf-8')
    releases=[publish(item,path,args.source) for item,path in prepared]
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'],'a',encoding='utf-8') as out:
            out.write('## Verified preview releases\n\n'+''.join('- ['+r['tag_name']+']('+r['html_url']+')\n' for r in releases))

if __name__=='__main__':main()
