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
    def test_draft_without_a_git_tag_is_resolved(self):
        draft={'tag_name':'v1.35.1-preview','draft':True,'id':42}
        with patch.object(p,'api',side_effect=[None,[draft]]):self.assertEqual(p.find_release('v1.35.1-preview'),draft)
    def test_missing_release_is_not_confused_with_another_draft(self):
        with patch.object(p,'api',side_effect=[None,[{'tag_name':'v0.0.0-preview','draft':True,'id':1}]]):self.assertIsNone(p.find_release('v1.35.1-preview'))
    def test_draft_lookup_reaches_later_pages(self):
        draft={'tag_name':self.item['tag'],'draft':True,'id':42}
        with patch.object(p,'api',side_effect=[None,[{'tag_name':'other'}]*100,[draft]]) as api:
            self.assertEqual(p.find_release(self.item['tag']),draft)
            self.assertEqual(api.call_args.args,('releases?per_page=100&page=2',))
    def test_draft_creation_keeps_the_response_identity(self):
        item={**self.item,'title':'A reviewed preview','notes':'guide/releases/synthetic.md'}
        draft={'id':42,'tag_name':item['tag'],'draft':True}
        with patch.object(Path,'read_text',return_value='Exact reviewed notes\n'),patch.object(p,'gh',return_value=json.dumps(draft).encode()) as gh:
            self.assertEqual(p.create_draft(item,'0'*40),draft)
            self.assertEqual(gh.call_args.kwargs['body']['body'],'Exact reviewed notes\n')
            self.assertTrue(gh.call_args.kwargs['body']['draft'])
    def test_unexpected_created_draft_is_rejected(self):
        item={**self.item,'title':'Preview','notes':'guide/releases/synthetic.md'}
        with patch.object(Path,'read_text',return_value='Notes'),patch.object(p,'gh',return_value=b'null'):
            with self.assertRaises(ValueError):p.create_draft(item,'0'*40)
    def test_new_draft_is_not_rediscovered_through_a_tag_or_list(self):
        source='0'*40;draft={'id':42,'target_commitish':source,'draft':True,'assets':[]}
        published={**draft,'draft':False,'prerelease':True,'html_url':'https://github.com/example/release'}
        with patch.object(p,'find_release',return_value=None) as find,patch.object(p,'create_draft',return_value=draft),patch.object(p,'gh'),patch.object(p,'verify_assets'),patch.object(p,'api',side_effect=[draft,published,{'object':{'type':'commit','sha':source}}]):
            self.assertEqual(p.publish(self.item,Path('synthetic.apk'),source),published)
            find.assert_called_once_with(self.item['tag'])
    def test_published_assets_are_never_overwritten(self):
        release={'draft':False,'assets':[{'name':self.item['file'],'state':'uploaded','id':1},{'name':self.item['file']+'.sha256','state':'uploaded','id':2}]}
        with patch.object(p,'api',side_effect=[release,b'tampered']),patch.object(p,'gh') as mutations:
            with self.assertRaises(ValueError):p.publish(self.item,Path('synthetic.apk'),'0'*40)
            mutations.assert_not_called()

if __name__=='__main__':unittest.main()
