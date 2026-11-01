"""Validate the exact bundled assets without running Minecraft or external tools."""
from pathlib import Path
import hashlib,json,unittest,zipfile

ROOT=Path(__file__).resolve().parent.parent
class PackTest(unittest.TestCase):
    def test_bundled_pack_is_self_contained_and_matches_manifest(self):
        root=ROOT/'admin/public/packs'
        manifest=json.loads((root/'manifest.json').read_text(encoding='utf-8'))
        data=(root/manifest['filename']).read_bytes()
        self.assertEqual(hashlib.sha1(data).hexdigest(),manifest['sha1']);self.assertEqual(len(data),manifest['size'])
        self.assertLess(len(data),1024*1024)
        with zipfile.ZipFile(root/manifest['filename']) as z:
            names=set(z.namelist());self.assertEqual(z.testzip(),None)
            self.assertIn('LICENSE.txt',names)
            self.assertTrue(all('..' not in Path(n).parts and not n.startswith('/') for n in names))
            self.assertFalse(any(n.startswith('assets/minecraft/') for n in names),'Never replace vanilla assets globally')
            meta=json.loads(z.read('pack.mcmeta'));self.assertEqual(meta['pack']['min_format'],[97,1])
            for kind in ['spitter','jumper','crawler','stalker','bloated','breaker']:
                self.assertIn('<title>',(ROOT/'admin/public/horror'/f'{kind}.svg').read_text(encoding='utf-8'))
                for part in ['body','left','right']:
                    definition=json.loads(z.read(f'assets/duckcatmine/items/{kind}_{part}.json'))
                    model=definition['model']['model'].split(':')[1]
                    body=json.loads(z.read(f'assets/duckcatmine/models/{model}.json'))
                    for texture in body['textures'].values():
                        self.assertTrue(z.read('assets/duckcatmine/textures/'+texture.split(':')[1]+'.png').startswith(b'\x89PNG'))
                    for box in body['elements']:
                        for lo,hi in zip(box['from'],box['to']):self.assertTrue(-16<=lo<hi<=32)
            sounds=json.loads(z.read('assets/duckcatmine/sounds.json'));self.assertEqual(len(sounds),6)
            for sound in sounds.values():
                self.assertTrue(z.read('assets/duckcatmine/sounds/'+sound['sounds'][0]['name'].split(':')[1]+'.ogg').startswith(b'OggS'))

if __name__=='__main__':unittest.main()
