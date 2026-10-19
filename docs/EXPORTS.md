# Export files

Files are written under the Minecraft instance:

```text
texture-atlases/<atlas>_<size>x<size>.webp      one page, or
texture-atlases/<atlas>_<size>x<size>_<n>.webp  pages 1, 2, …
texture-atlases/<atlas>_<size>x<size>.json
texture-atlases/<atlas>_<size>x<size>.txt
texture-atlases/<atlas>_<size>x<size>.css
```

`<atlas>` is `vanilla_items`, `hypixel_skyblock_items` or `hypixel_skyblock_bazaar_items`. Images are square: cells are laid out left to right in ⌈√n⌉ columns and as many rows, with any cells after the last item left transparent; atlas ordering is deterministic. An atlas wider than 8192 px is split into pages of ⌊8192 / size⌋ × ⌊8192 / size⌋ cells, filled in order, the last one square around its remaining cells.

Images are lossless WebP, written by the mod's own encoder. A page is written as PNG instead only if the Java heap cannot hold it for encoding; its JSON then records why in `formatNote`.

Every entry has a unique `name`:

- Vanilla: namespaced item ID; potion variants use `/give` syntax, for example `minecraft:stone` or `minecraft:potion[potion_contents="minecraft:swiftness"]`.
- SkyBlock: SkyBlock item ID, for example `MANDRAA`.

- **JSON**: `pixelSize`, `itemCount` and `pages` (each page's `image`, `columns`, `rows`, `width`, `height`, `firstIndex` and `itemCount`), then per item: `index`, `name`, `displayName` (vanilla), `cssClass`, `page` (from 0), `column`, `row`, `x`, `y`, `width`, `height`.
- **Text**: one line per cell, `Display Name (name): [column, row]` (`name: [column, row]` for SkyBlock), followed by ` page <n>` (from 1) when there are several pages.
- **CSS**: a base class carrying the first page's image (`--tag-image`) and the cell size, and one class per cell setting its offset as `--tag-position` (and its page's image, after the first), with the display name as a comment:

  ```html
  <span class="vanilla_items vanilla_items-minecraft-stone"></span>
  ```

## Rendering

Items are drawn exactly as in an inventory slot, without the enchantment glint, using the first frame of animated textures. Clock and compass models use their ownerless GUI state. The client's animation state is restored after each page.

## Glint overlay

`enchantment_glint_<size>x<size>.webp` is one cell of the item enchantment glint as it moves over the enchanted book in the inventory: 1,650 frames at 20 frames per second, the 82.5 seconds after which both glint layers line up again at the default Glint Speed, looping forever. It is computed from the glint texture and 26.2's glint transform and blending rather than captured, so the background stays transparent. Each pixel's colour times its alpha is the light Minecraft adds, so drawing it with `mix-blend-mode: plus-lighter` reproduces the game exactly. `enchantment_glint_<size>x<size>.css` adds a `tag-glint` class that does this, masked to the icon through the atlas stylesheet:

```html
<span class="vanilla_items vanilla_items-minecraft-diamond_sword tag-glint"></span>
```
