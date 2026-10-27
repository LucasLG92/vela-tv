"""Offline publication guard tests; never create a release or use an account."""
from pathlib import Path
import copy,hashlib,importlib.util,json,types,unittest
from unittest.mock import patch

root=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('publisher',root/'tools/publish_releases.py')
p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)

class PublicationSafety(unittest.TestCase):
    def setUp(self):
        self.doc=json.loads((root/'.github/release-candidates.json').read_text())
        self.data=b'reviewed synthetic bytes'
        self.item={'file':'Vela-test.apk','package':'tv.vela.launcher','code':69,'version':'1.35.1-preview','size':len(self.data),'sha256':hashlib.sha256(self.data).hexdigest(),'blob_sha':hashlib.sha1(b'blob '+str(len(self.data)).encode()+b'\0'+self.data).hexdigest(),'tag':'v1.35.1-preview'}
    def parse(self):return p.manifest(types.SimpleNamespace(read_text=lambda **kw:json.dumps(self.doc)))
    def test_reviewed_manifest_matches_both_source_packages(self):self.assertEqual(len(self.parse()),2)
    def test_another_repository_is_rejected(self):
        self.doc['repository']='example/unrelated'
        with self.assertRaises(ValueError):self.parse()
    def test_package_duplicates_are_rejected(self):
        self.doc['releases'][1]=copy.deepcopy(self.doc['releases'][0])
        with self.assertRaises(ValueError):self.parse()
    def test_filename_cannot_escape_output(self):
        self.doc['releases'][0]['file']='../private.apk'
        with self.assertRaises(ValueError):self.parse()
    def test_unchanged_source_version_is_required(self):
        self.doc['releases'][0]['code']+=1
        with self.assertRaises(ValueError):self.parse()
    def test_missing_part_is_rejected(self):
        self.doc['releases'][0]['chunks'].pop()
        with self.assertRaises(ValueError):self.parse()
    def test_correct_exact_bytes_pass(self):p.verify_bytes(self.data,self.item)
    def test_truncated_bytes_are_rejected(self):
        with self.assertRaises(ValueError):p.verify_bytes(self.data[:-1],self.item)
    def test_same_size_tampering_is_rejected(self):
        with self.assertRaises(ValueError):p.verify_bytes(b'X'+self.data[1:],self.item)
    def test_unexpected_git_object_is_rejected(self):
        self.item['blob_sha']='0'*40
        with self.assertRaises(ValueError):p.verify_bytes(self.data,self.item)
    def test_different_signing_key_is_rejected(self):
        with patch.object(Path,'read_bytes',return_value=self.data),patch.object(p,'sdk_tools',return_value=Path('sdk')),patch.object(p.subprocess,'check_output',return_value='Signer #1 certificate SHA-256 digest: '+'0'*64):
            with self.assertRaises(ValueError):p.verify_apk(Path('synthetic.apk'),self.item)
    def test_compiled_package_cannot_impersonate_reviewed_package(self):
        responses=['Signer #1 certificate SHA-256 digest: '+p.SIGNER,"package: name='example.other' versionCode='69' versionName='1.35.1-preview'"]
        with patch.object(Path,'read_bytes',return_value=self.data),patch.object(p,'sdk_tools',return_value=Path('sdk')),patch.object(p.subprocess,'check_output',side_effect=responses):
            with self.assertRaises(ValueError):p.verify_apk(Path('synthetic.apk'),self.item)
    def test_published_assets_are_never_overwritten(self):
        release={'draft':False,'assets':[{'name':self.item['file'],'state':'uploaded','id':1},{'name':self.item['file']+'.sha256','state':'uploaded','id':2}]}
        with patch.object(p,'api',side_effect=[release,b'tampered']),patch.object(p,'gh') as mutations:
            with self.assertRaises(ValueError):p.publish(self.item,Path('synthetic.apk'),'0'*40)
            mutations.assert_not_called()

if __name__=='__main__':unittest.main()
