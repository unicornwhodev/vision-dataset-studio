import base64
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('validator',Path(__file__).parents[1]/'hf_validate_and_convert.py')
validator=importlib.util.module_from_spec(spec);spec.loader.exec_module(validator)
class ValidatorTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);self.dir=self.root/'batches/batch-000001';(self.dir/'images').mkdir(parents=True)
        self.image=self.dir/'images/a.png'
        self.image.write_bytes(base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5w0AAAAASUVORK5CYII='))
        self.record={'sample_id':'a','review_status':'VALIDATED','media':{'filename':'a.png','width':1,'height':1,'sha256':validator.compute_sha256(self.image)},'annotations':{'boxes':[{'xmin':.1,'ymin':.2,'xmax':.7,'ymax':.8,'label':'object','isHumanVerified':True}]}}
        self.write_record();self.manifest()
    def write_record(self):
        (self.dir/'annotations.jsonl').write_text(json.dumps(self.record)+'\n')
    def manifest(self):
        files=[{'path':str(p.relative_to(self.root)), 'size':p.stat().st_size, 'sha256':validator.compute_sha256(p)} for p in [self.image,self.dir/'annotations.jsonl']]
        (self.dir/'manifest.json').write_text(json.dumps({'schema_version':2,'sample_count':1,'files':files}))
    def test_valid_fixture(self):self.assertTrue(validator.validate_batch(self.root))
    def test_tampered_image(self):
        self.image.write_bytes(b'changed')
        with self.assertRaises(ValueError):validator.validate_batch(self.root)
    def test_traversal_manifest(self):
        m=json.loads((self.dir/'manifest.json').read_text());m['files'][0]['path']='../../private';(self.dir/'manifest.json').write_text(json.dumps(m))
        with self.assertRaises(ValueError):validator.validate_batch(self.root)
    def test_duplicate_ids(self):
        with (self.dir/'annotations.jsonl').open('a') as f:f.write(json.dumps(self.record)+'\n')
        with self.assertRaises(ValueError):validator.validate_batch(self.root)
    def test_invalid_coordinates(self):
        self.record['annotations']['boxes'][0]['xmax']=1.2;self.write_record();self.manifest()
        with self.assertRaises(ValueError):validator.validate_batch(self.root)
    def test_coco_preserves_pixels_and_path(self):
        target=self.root/'coco.json';validator.convert_to_coco(self.root,target);r=json.loads(target.read_text())
        self.assertEqual(r['images'][0]['file_name'],'batches/batch-000001/images/a.png')
        self.assertAlmostEqual(r['annotations'][0]['bbox'][2],.6)
        self.assertEqual(r['categories'][0]['name'],'object')
if __name__=='__main__':unittest.main(verbosity=2)
