# TextureAtlasGenerator icon

## What this is

`docs/icon/icon.png` — the mod's icon: 1024x1024 PNG, 8-bit RGBA, non-interlaced,
148,764 bytes, sha256
`07d54e88ff01ebf37e0a15da57e811de26c00aa87341fdcef2607fc3b5b6e838`.

## How it was made

Deterministic Pillow **12.3.0** export composition — no rendering, no ML, no retouching:
`provenance/compose.py` crops sixteen **native 256x256 RGBA cells** out of a real
TextureAtlasGenerator export (8192x12288, 1536 mapped items, 256 px per item) and pastes
them unmasked into a 4x4 grid at exactly 1:1. No resizing, recolouring, alpha blending,
borders or gutters. Every saved icon cell is decoded-RGBA byte-identical to its source
rectangle (16 cells, 4,194,304 bytes compared, 0 differing bytes, max channel difference 0;
two runs produced the identical file hash).

How the atlas itself was produced (real, not synthetic): the **TextureAtlasGenerator** mod
at commit `51ce360` exported it from a **real Minecraft 26.2 client** run
(`AtlasExporter.start`, 256 px per item, live dedicated server, Mesa llvmpipe OpenGL), and
the export is GUI-scale/window independent — the 64 px export hashes identically at
854x480/gui1, 1280x720/gui2 and 1920x1080/gui3. The raw 256 px export used here has sha256
`c9baa75bac9a498b4d3a5be106ca75163e4d3e33aaa599618f7aba4485cdd681`; the same file
survives in the round-3 tree at
`round3/atlas3/textureatlasgenerator/examples/vanilla_items_256x256.png`.

Cell order (4x4, left to right, top to bottom), as listed in `BLOCKS` in `compose.py`:
grass block, dirt, stone, cobblestone, oak planks, oak log, oak leaves, glass, sand, gravel,
bricks, bookshelf, crafting table, furnace, chest, obsidian.

There is no Minecraft capture and no shader pack involved in this file: it is a 1:1 export of
an atlas that in turn came from real client-side item rendering.

## Provenance files

`provenance/` holds the script, its inputs and the records of the round-3 authoring run:

| file | what it is |
|---|---|
| `compose.py` | the author script; crops, pastes, saves and re-verifies |
| `01-…16-*.png` | the sixteen 256x256 cells exactly as cropped from the export (per-cell sha256 in `icon-proof.json`) |
| `icon-proof.json` | full per-cell proof: source rectangles, saved-cell RGBA hashes, byte-equality results |
| `manifest.json` | round-3 entry: method, sources, notes, reproduce command, discrepancy resolution |
| `source-provenance.json` | how the raw export was recovered and hash-verified (it is byte-identical to the archive copy of the fixed export) |
| `verification-run.json` | both composition runs, their output hashes, and the decoded-pixel comparison summary |
| `raw-export/vanilla_items_256x256.json` | the export's own item mapping (1536 items, 256 px cells, x/y/width/height per item) |
| `reference/fixed-export-icon-verification.json` | the recorded hash of the missing fixed export |
| `blockers.json`, `cleanup-report.json` | round-3 records (no blockers; write scope, retained files) |

## How to regenerate

From `docs/icon/provenance` with Pillow 12.3.0:

```
python3 compose.py
```

`compose.py` needs `raw-export/vanilla_items_256x256.png`, which is **not shipped here**
(13.4 MB — over the 5 MB single-file limit for this provenance copy). Restore it first, from
either source:

```
# a) the byte-identical surviving copy in the round-3 tree (sha256 must match c9baa75b…)
cp round3/atlas3/textureatlasgenerator/examples/vanilla_items_256x256.png \
   docs/icon/provenance/raw-export/vanilla_items_256x256.png
# b) or re-run the mod's exporter (commit 51ce360) in a Minecraft 26.2 client at 256 px per item
```

Then re-run `compose.py`; it rewrites `icon.png`, the sixteen cell PNGs and `icon-proof.json`,
and asserts that every cell is byte-identical to its source rectangle. `raw-export/vanilla_items_256x256.json` is
shipped so the item ids and cell rectangles are still auditable without the PNG.

## Notes

- The raw export PNG is deliberately excluded for size; `source-provenance.json` records its
  sha256 and why the surviving archive copy is provably the same file.
- Not copied from the round-3 tree: `round3/atlas4/raw-export/vanilla_items_256x256.png`.

## Working-tree note

The round-3 working tree that produced this icon was cleaned up after integration. Every file needed to regenerate the icon was copied into `provenance/`; the copies live under `provenance/from-round3/` when they came from the working tree. Any remaining `round3/...` mention records where something came from, not a path that still exists.
