from pathlib import Path
import hashlib
import json

from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parent
Image.MAX_IMAGE_PIXELS = 110_000_000  # Verified 8192x12288 native export.
SOURCE = ROOT / 'raw-export/vanilla_items_256x256.png'
ICON = ROOT / 'textureatlasgenerator-same-devutils-1024.png'
LEGACY_ICON = ROOT / 'reference/devutils-legacy-blocks-1024.png'
MODERN_IDS = {
    'GRASS': 'grass_block',
    'DIRT': 'dirt',
    'STONE': 'stone',
    'COBBLESTONE': 'cobblestone',
    'PLANKS': 'oak_planks',
    'LOG': 'oak_log',
    'LEAVES': 'oak_leaves',
    'GLASS': 'glass',
    'SAND': 'sand',
    'GRAVEL': 'gravel',
    'BRICK_BLOCK': 'bricks',
    'BOOKSHELF': 'bookshelf',
    'CRAFTING_TABLE': 'crafting_table',
    'FURNACE': 'furnace',
    'CHEST': 'chest',
    'OBSIDIAN': 'obsidian',
}


def sha256(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def rgba_sha256(image):
    assert image.mode == 'RGBA'
    return hashlib.sha256(image.tobytes()).hexdigest()


def save_json(name, value):
    (ROOT / name).write_text(json.dumps(value, indent=2) + '\n')


legacy_proof = json.loads((ROOT / 'reference/devutils-icon-proof.json').read_text())
fixed_proof = json.loads((ROOT / 'reference/fixed-export-icon-verification.json').read_text())
mapping = json.loads(SOURCE.with_suffix('.json').read_text())
selections = legacy_proof['selections']
items = {item['name']: item for item in mapping['items']}
assert len(items) == len(mapping['items']) == 1536
assert list(MODERN_IDS) == [selection['block'] for selection in selections]
assert mapping['pixelSize'] == 256
assert sha256(SOURCE) == fixed_proof['sourceSha256']
assert sha256(LEGACY_ICON) == legacy_proof['iconSha256']

(ROOT / 'cells').mkdir(exist_ok=True)
cells = []
with Image.open(SOURCE) as atlas, Image.open(LEGACY_ICON) as legacy:
    assert atlas.mode == legacy.mode == 'RGBA'
    assert atlas.size == (mapping['width'], mapping['height']) == (8192, 12288)
    assert legacy.size == (1024, 1024)
    icon = Image.new('RGBA', (1024, 1024))
    for index, selection in enumerate(selections):
        modern_id = 'minecraft:' + MODERN_IDS[selection['block']]
        item = items[modern_id]
        x, y, width, height = (item[key] for key in ('x', 'y', 'width', 'height'))
        assert width == height == 256
        assert x % 256 == y % 256 == 0
        source_box = (x, y, x + width, y + height)
        icon_box = tuple(selection['iconBox'])
        dx, dy = index % 4 * 256, index // 4 * 256
        assert icon_box == (dx, dy, dx + 256, dy + 256)
        legacy_cell = legacy.crop(icon_box)
        assert rgba_sha256(legacy_cell) == selection['pixelsSha256']
        cell = atlas.crop(source_box)
        assert cell.getchannel('A').getbbox() is not None
        icon.paste(cell, (dx, dy))  # Unmasked paste: preserve every RGBA byte.
        cell_path = ROOT / f'cells/{index + 1:02d}-{MODERN_IDS[selection["block"]]}.png'
        cell.save(cell_path)
        cells.append({
            'index': index,
            'row': index // 4,
            'column': index % 4,
            'legacyBlock': selection['block'],
            'legacyLabel': selection['label'],
            'modernId': modern_id,
            'legacyIconBox': icon_box,
            'legacyPixelsSha256': rgba_sha256(legacy_cell),
            'legacyProofHashMatches': True,
            'sourceExport': str(SOURCE.relative_to(ROOT)),
            'sourceExportSha256': sha256(SOURCE) if index == 0 else cells[0]['sourceExportSha256'],
            'sourceItemIndex': item['index'],
            'sourceRect': [x, y, width, height],
            'sourceBox': source_box,
            'iconBox': icon_box,
            'sourcePixelsSha256': rgba_sha256(cell),
            'alphaBounds': cell.getchannel('A').getbbox(),
            'cellFile': str(cell_path.relative_to(ROOT)),
            'cellFileSha256': sha256(cell_path),
            'scaleFactor': 1.0,
        })
    icon.save(ICON)
    with Image.open(ICON) as saved_icon:
        assert saved_icon.mode == 'RGBA' and saved_icon.size == (1024, 1024)
        for record in cells:
            source_cell = atlas.crop(record['sourceBox'])
            saved_icon_cell = saved_icon.crop(record['iconBox'])
            with Image.open(ROOT / record['cellFile']) as saved_cell:
                assert saved_cell.mode == 'RGBA' and saved_cell.size == (256, 256)
                assert source_cell.tobytes() == saved_cell.tobytes() == saved_icon_cell.tobytes()
                difference = ImageChops.difference(source_cell, saved_icon_cell)
                assert difference.getextrema() == ((0, 0),) * 4
                record.update({
                    'savedCellPixelsSha256': rgba_sha256(saved_cell),
                    'savedIconCellPixelsSha256': rgba_sha256(saved_icon_cell),
                    'savedCellByteIdenticalToSource': True,
                    'savedIconCellByteIdenticalToSource': True,
                    'differentRgbaBytes': 0,
                    'maxChannelDifference': 0,
                    'comparedBytes': len(source_cell.tobytes()),
                })

proof = {
    'icon': ICON.name,
    'iconSha256': sha256(ICON),
    'size': [1024, 1024],
    'layout': '4x4',
    'cellSize': [256, 256],
    'scaleFactor': 1.0,
    'resampling': 'none',
    'compositing': 'Unmasked RGBA paste; no resizing, recoloring, alpha blending, margins, or gutters added.',
    'hashSemantics': 'PNG file hashes cover encoded files; pixel hashes cover row-major decoded 8-bit RGBA bytes. Cell identity means decoded RGBA byte identity, including fully transparent pixels.',
    'rectSemantics': 'sourceRect is [x,y,width,height]; sourceBox and iconBox are [left,top,right,bottom], right/bottom exclusive.',
    'source': str(SOURCE.relative_to(ROOT)),
    'sourceSha256': sha256(SOURCE),
    'sourceSize': [8192, 12288],
    'sourceMetadataSha256': sha256(SOURCE.with_suffix('.json')),
    'legacyReference': str(LEGACY_ICON.relative_to(ROOT)),
    'legacyReferenceSha256': sha256(LEGACY_ICON),
    'legacyProof': 'reference/devutils-icon-proof.json',
    'legacyProofSha256': sha256(ROOT / 'reference/devutils-icon-proof.json'),
    'all16LegacyProofHashesMatchSavedReference': True,
    'all16IdentitiesAndPositionsMatchLegacy': True,
    'all16SavedCellsByteIdenticalToSource': True,
    'all16SavedIconCellsByteIdenticalToSource': True,
    'totalComparedIconRgbaBytes': sum(cell['comparedBytes'] for cell in cells),
    'differentRgbaBytes': 0,
    'selections': cells,
}
save_json('icon-proof.json', proof)
print(json.dumps({key: value for key, value in proof.items() if key != 'selections'}, indent=2))
print(json.dumps([cell['modernId'] for cell in cells]))
