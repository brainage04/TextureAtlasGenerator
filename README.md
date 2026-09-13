# Texture Atlas Generator

A client-side Fabric and NeoForge mod for Minecraft 26.2 that exports item-model texture atlases from inside Minecraft.

## Features

- Exports every registered vanilla item model.
- Exports all 2,545 legacy Hypixel SkyBlock player-head textures.
- Exports the 351-item Hypixel SkyBlock Bazaar subset.
- Supports item sizes from 8×8 through 256×256 pixels, independent of GUI scale and window size.
- Includes a searchable in-game preview and progress display.
- Writes a JSON coordinate mapping beside every PNG.
- Splits GPU rendering into bounded pages, so large atlases are assembled without exceeding the render-target limit.

## Usage

Open the generator with the `G` key, through Mod Menu, or with:

```text
/atlas
```

The screen selects the atlas type and pixel size, previews its entries, and starts the export. Commands can also export immediately:

```text
/atlas <pixels>
/atlas vanilla <pixels>
/atlas skyblock-all <pixels>
/atlas skyblock-bazaar <pixels>
```

The legacy SkyBlock command remains available:

```text
/skyblockatlas <pixels> <fullAtlas>
```

`fullAtlas=true` selects all SkyBlock entries; `false` selects the Bazaar subset. Pixel sizes must be between 8 and 256.

Outputs are written under the Minecraft instance:

```text
texture-atlases/<atlas>_<pixel-size>x<pixel-size>.png
texture-atlases/<atlas>_<pixel-size>x<pixel-size>.json
```

The JSON file records the item name, index, row, column, and pixel rectangle for every entry. Atlas ordering is deterministic. SkyBlock exports download the embedded player-head textures first; individual download failures are reported and rendered with Minecraft's fallback skin rather than aborting the entire atlas.

Exports use the first frame of animated textures and a fixed shader/glint time. Clock and compass models use their ownerless GUI state. This makes repeated exports byte-identical for the same resources and size, rather than capturing the current player's position or animation tick. The client's animation state is restored after each page. GPU pages are at most 4096×4096; the assembled PNG can be larger.

## Requirements

- Minecraft 26.2
- Java 25 or newer
- Either Fabric Loader 0.19.3 or newer with Fabric API, or NeoForge 26.2.0.23-beta or newer.

Mod Menu is optional on Fabric.

## Migrating from the Fabric-only release

Install exactly one loader-specific JAR: `textureatlasgenerator-<version>.jar` for Fabric or
`textureatlasgenerator-neoforge-<version>.jar` for NeoForge. Remove the old JAR before
switching loaders; this is a client-only mod and belongs only in the client instance's `mods`
directory. Fabric additionally requires Fabric API; NeoForge has no extra mod dependency.

The mod ID remains `textureatlasgenerator`, and generated atlas files keep their existing
`texture-atlases/` instance path. A root `./gradlew build` produces both loader artifacts under
`build/libs`.
