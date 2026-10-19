# Texture Atlas Generator

A client-side Fabric and NeoForge mod for Minecraft 26.2 that exports item-model texture atlases from inside Minecraft.

## Features

- Exports every registered vanilla item model, with every potion variant of potions, splash potions, lingering potions and tipped arrows (including the "uncraftable" default stack of each), drawn with whatever resource packs are enabled, for example the built-in Programmer Art pack.
- Exports the enchantment glint as one animated cell, its full 82.5-second loop on a transparent background, for drawing over website icons.
- Exports all 2,545 legacy Hypixel SkyBlock player-head textures, and the 351-item Bazaar subset.
- Supports item sizes from 1×1 through 1024×1024 pixels, independent of GUI scale and window size.
- Writes square, lossless WebP atlases, split into square pages of at most 8192 × 8192 px when larger, with JSON, plain-text and CSS mappings.
- Includes a searchable in-game preview that draws the items exactly as the export will, at the chosen pixel size, and a progress display.
- Renders in bounded GPU pages and encodes the image band by band, so atlases far larger than a render target can be written.

## Getting started

- Minecraft 26.2
- Java 25 or newer
- Either Fabric Loader 0.19.3 or newer with Fabric API, or NeoForge 26.2.0.88 or newer.
- Install on the client only.

Open the generator with the `G` key, through Mod Menu, or with `/atlas`.

The screen selects the atlas type and pixel size (the `−`/`+` buttons halve and double it; any size can be typed), previews its entries as the export will draw them, and starts the export. **Export glint** exports the glint loop at the selected size.

## Commands

- `G` - opens the generator screen.
- `/atlas` - opens the generator screen.
- `/atlas <pixels>` - exports the vanilla atlas.
- `/atlas vanilla [<pixels>]` - exports the vanilla atlas.
- `/atlas skyblock-all [<pixels>]` - exports all legacy SkyBlock player heads.
- `/atlas skyblock-bazaar [<pixels>]` - exports the Bazaar subset.
- `/atlas glint [<pixels>]` - exports the enchantment glint loop.

Pixel sizes must be between 1 and 1024; an atlas without a size exports at 256, the glint at 64. Command exports open the generator screen to show their progress, and the result, or the reason an export failed, is also posted in chat.

## Configuration

No configuration file; choose the atlas and pixel size in the generator screen.

## Compatibility

Mod Menu is optional on Fabric.

The atlas uses the enabled resource packs, so enabling the built-in Programmer Art pack (Options → Resource Packs) before exporting draws the items it covers with their pre-1.14 textures; newer items keep their current ones.

Player-head skins are downloaded before rendering. If any skin cannot be loaded, the export stops without writing an atlas and reports which entries failed together with the HTTP result of re-requesting one of them.

## Documentation

- [Export files](docs/EXPORTS.md) - image layout, mappings and glint stylesheets.
- [Release process](docs/RELEASE.md) - maintainer release instructions.
- [Modrinth publishing](docs/MODRINTH.md) - publishing setup.
- [Development](docs/DEVELOPMENT.md) - test review and coverage notes.
