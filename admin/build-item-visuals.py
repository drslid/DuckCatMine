"""Build local item thumbnails and French labels from the official 26.3 client assets.

Run from the repository root. The official 26.3 client is downloaded on the
first run. The French language file comes from the same release's asset index.
"""
import hashlib
import json
import re
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CLIENT = ROOT.parent / '_client-26.3.jar'
CLIENT_URL = 'https://piston-data.mojang.com/v1/objects/e877b6a07acd633fb3bb475002175cec036e7b87/client.jar'
OUT = ROOT / 'public' / 'icons'
FR_HASH = '342131fd71813e80c7f29ac8d23304599b3c5fe6'
CAT = {
    'featured': 'À découvrir', 'blocks': 'Blocs', 'tools': 'Outils & armes',
    'food': 'Nourriture', 'transport': 'Transport', 'nature': 'Nature',
    'creatures': 'Créatures', 'decor': 'Décoration', 'other': 'Autres',
}


def category(key, block):
    if any(x in key for x in ('minecart', 'boat', 'rail', 'saddle', 'elytra', 'horse_armor')):
        return 'transport'
    if key.endswith('_spawn_egg') or key in ('armor_stand', 'name_tag', 'lead'):
        return 'creatures'
    if any(key.endswith(x) for x in ('_sword', '_axe', '_pickaxe', '_shovel', '_hoe', '_helmet', '_chestplate', '_leggings', '_boots')) or key in ('bow', 'crossbow', 'trident', 'mace', 'shield', 'arrow', 'spectral_arrow'):
        return 'tools'
    if any(x in key for x in ('apple', 'bread', 'stew', 'soup', 'cookie', 'cake', 'pie', 'carrot', 'potato', 'beef', 'porkchop', 'chicken', 'mutton', 'rabbit', 'cod', 'salmon', 'melon_slice', 'berries', 'bottle', 'potion')):
        return 'food'
    if any(x in key for x in ('flower', 'sapling', 'leaves', 'seed', 'mushroom', 'coral', 'kelp', 'vine', 'bamboo', 'cactus')):
        return 'nature'
    if any(x in key for x in ('banner', 'bed', 'painting', 'pot', 'candle', 'carpet', 'sign', 'frame', 'head', 'skull')):
        return 'decor'
    return 'blocks' if block else 'other'


def candidates(key):
    yield ('item', key)
    yield ('block', key)
    for suffix in ('_stairs', '_slab', '_wall', '_fence_gate', '_fence', '_button', '_pressure_plate', '_carpet', '_pane'):
        if key.endswith(suffix):
            stem = key[:-len(suffix)]
            for name in (stem, stem + '_planks', stem + '_wool', stem + '_concrete', stem + '_bricks', stem + '_brick', stem + '_side', stem + '_top'):
                yield ('block', name)
    for suffix in ('_bed', '_banner', '_candle', '_boat', '_chest_boat', '_spawn_egg'):
        if key.endswith(suffix):
            stem = key[:-len(suffix)]
            for name in (stem + '_wool', stem + '_planks', stem + '_log', suffix.strip('_')):
                yield ('block', name)
                yield ('item', name)
    if key.endswith('_wood'):
        yield ('block', key[:-5] + '_log')
    for suffix in ('_side', '_front', '_top', '_bottom', '_end'):
        yield ('block', key + suffix)
    # A recognisable texture is preferable to an empty tile for model-only items.
    for token in reversed(key.split('_')):
        yield ('item', token)
        yield ('block', token)
    yield ('item', 'barrier')


def main():
    expected = CLIENT_URL.split('/objects/')[1].split('/')[0]
    if not CLIENT.exists() or hashlib.sha1(CLIENT.read_bytes()).hexdigest() != expected:
        with urllib.request.urlopen(CLIENT_URL, timeout=120) as response:
            payload = response.read(1024 * 1024 * 1024)
        if hashlib.sha1(payload).hexdigest() != expected:
            raise RuntimeError('Official client checksum mismatch')
        CLIENT.write_bytes(payload)
    items_path = ROOT / 'items.json'
    catalog = json.loads(items_path.read_text(encoding='utf-8'))
    fr = json.load(urllib.request.urlopen(
        f'https://resources.download.minecraft.net/{FR_HASH[:2]}/{FR_HASH}', timeout=30))
    OUT.mkdir(exist_ok=True)
    metadata = {}
    exact = 0
    fallback = 0
    with zipfile.ZipFile(CLIENT) as client:
        files = set(client.namelist())
        en = json.loads(client.read('assets/minecraft/lang/en_us.json'))
        for item in catalog['items']:
            key = item.removeprefix('minecraft:')
            label_key = next((x for x in (f'item.minecraft.{key}', f'block.minecraft.{key}') if x in fr), None)
            name = fr.get(label_key) or en.get(label_key) or key.replace('_', ' ').title()
            source = next(((kind, texture) for kind, texture in candidates(key)
                           if f'assets/minecraft/textures/{kind}/{texture}.png' in files), None)
            if source:
                kind, texture = source
                (OUT / f'{key}.png').write_bytes(client.read(f'assets/minecraft/textures/{kind}/{texture}.png'))
                exact += texture == key
                fallback += texture != key
            metadata[item] = {
                'name': name,
                'category': category(key, label_key is not None and label_key.startswith('block.')),
                'icon': bool(source),
            }
    catalog['meta'] = metadata
    items_path.write_text(json.dumps(catalog, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')
    print(f'{len(metadata)} items, {exact} exact textures, {fallback} representative textures')


if __name__ == '__main__':
    main()
