# Texture Atlas Generator

A client-side Fabric and NeoForge mod for Minecraft 26.2 that exports item-model texture atlases from inside Minecraft.

## Features

- Exports every registered vanilla item model, with every potion variant of potions, splash potions, lingering potions and tipped arrows (including the "uncraftable" default stack of each), drawn with whatever resource packs are enabled — for example the built-in Programmer Art pack.
- Exports the enchantment glint as one animated cell — its full 82.5-second loop on a transparent background — for drawing over website icons.
- Exports all 2,545 legacy Hypixel SkyBlock player-head textures, and the 351-item Bazaar subset.
- Supports item sizes from 1×1 through 1024×1024 pixels, independent of GUI scale and window size.
- Writes square, lossless WebP atlases — split into square pages of at most 8192 × 8192 px when larger — with JSON, plain-text and CSS mappings.
- Includes a searchable in-game preview that draws the items exactly as the export will, at the chosen pixel size, and a progress display.
- Renders in bounded GPU pages and encodes the image band by band, so atlases far larger than a render target can be written.

## Usage

Open the generator with the `G` key, through Mod Menu, or with:

```text
/atlas
```

The screen selects the atlas type and pixel size (the `−`/`+` buttons halve and double it; any size can be typed), previews its entries as the export will draw them, and starts the export. The preview draws each visible cell through the export's renderer at the selected pixel size (shown enlarged without smoothing below the preview's own size), so small sizes look as they will be saved. **Export glint** exports the glint loop at the selected size. Commands can also export immediately:

```text
/atlas <pixels>                     vanilla items
/atlas vanilla [<pixels>]
/atlas skyblock-all [<pixels>]
/atlas skyblock-bazaar [<pixels>]
/atlas glint [<pixels>]
```

Pixel sizes must be between 1 and 1024; an atlas without a size exports at 256, the glint at 64. Command exports open the generator screen to show their progress, and the result — or the reason an export failed — is also posted in chat.

## Outputs

Files are written under the Minecraft instance:

```text
texture-atlases/<atlas>_<size>x<size>.webp      one page, or
texture-atlases/<atlas>_<size>x<size>_<n>.webp  pages 1, 2, …
texture-atlases/<atlas>_<size>x<size>.json
texture-atlases/<atlas>_<size>x<size>.txt
texture-atlases/<atlas>_<size>x<size>.css
```

`<atlas>` is `vanilla_items`, `hypixel_skyblock_items` or `hypixel_skyblock_bazaar_items`. Images are square: cells are laid out left to right in ⌈√n⌉ columns and as many rows, with any cells after the last item left transparent; atlas ordering is deterministic. An atlas wider than 8192 px is split into pages of ⌊8192 / size⌋ × ⌊8192 / size⌋ cells, filled in order, the last one square around its remaining cells — 1,720 vanilla items at 1024 px make 27 pages of 8192 × 8192 px instead of one 43,008 px image. Image viewers refuse images that large (it decodes to 7.4 GB; Gwenview stops near 2 GB, and Qt-based viewers by default at 256 MiB, which is 8192 × 8192) and WebP cannot exceed 16,383 px.

Images are lossless WebP, written by the mod's own encoder. On the mod's atlases its files are about half the size of a standard PNG and 68–74% of an `oxipng`-optimised one (for example 1,516,146 bytes against 3,166,803 and 2,060,948 for the 64 px SkyBlock atlas; `cwebp -z 9` reaches 1,312,784). A page is written as PNG instead only if the Java heap cannot hold it for encoding; its JSON then records why in `formatNote`.

Every entry has a unique `name`:

| Atlas | `name` | Example |
|---|---|---|
| Vanilla | namespaced item ID; potion variants use `/give` syntax | `minecraft:stone`, `minecraft:potion[potion_contents="minecraft:swiftness"]` |
| SkyBlock | SkyBlock item ID | `MANDRAA` |

- **JSON** — `pixelSize`, `itemCount` and `pages` (each page's `image`, `columns`, `rows`, `width`, `height`, `firstIndex` and `itemCount`), then per item: `index`, `name`, `displayName` (vanilla), `cssClass`, `page` (from 0), `column`, `row`, `x`, `y`, `width`, `height`.
- **Text** — one line per cell, `Display Name (name): [column, row]` (`name: [column, row]` for SkyBlock), followed by ` page <n>` (from 1) when there are several pages.
- **CSS** — a base class carrying the first page's image (`--tag-image`) and the cell size, and one class per cell setting its offset as `--tag-position` (and its page's image, after the first), with the display name as a comment:

  ```html
  <span class="vanilla_items vanilla_items-minecraft-stone"></span>
  ```

### Rendering

Items are drawn exactly as in an inventory slot, without the enchantment glint, using the first frame of animated textures. Clock and compass models use their ownerless GUI state. Repeated exports are byte-identical for the same resources and size. The client's animation state is restored after each page.

**Programmer Art.** The atlas uses the enabled resource packs, so enabling the built-in Programmer Art pack (Options → Resource Packs) before exporting draws the items it covers with their pre-1.14 textures; newer items keep their current ones.

**Glint overlay.** `enchantment_glint_<size>x<size>.webp` is one cell of the item enchantment glint as it moves over the enchanted book in the inventory: 1,650 frames at 20 frames per second, the 82.5 seconds after which both glint layers line up again at the default Glint Speed, looping forever. It is computed from the glint texture and 26.2's glint transform and blending rather than captured, so the background stays transparent; it matches the glint rendered in game within 0.5/255 on average. Each pixel's colour times its alpha is the light Minecraft adds, so drawing it with `mix-blend-mode: plus-lighter` reproduces the game exactly. `enchantment_glint_<size>x<size>.css` adds a `tag-glint` class that does this, masked to the icon through the atlas stylesheet:

```html
<span class="vanilla_items vanilla_items-minecraft-diamond_sword tag-glint"></span>
```

The 64 px loop is about 9 MB.

**SkyBlock atlases.** Player-head skins are downloaded before rendering. If any skin cannot be loaded, the export stops without writing an atlas and reports which entries failed together with the HTTP result of re-requesting one of them.

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

`/skyblockatlas` has been removed; use `/atlas skyblock-all` or `/atlas skyblock-bazaar`.
