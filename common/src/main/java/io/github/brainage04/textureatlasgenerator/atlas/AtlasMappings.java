package io.github.brainage04.textureatlasgenerator.atlas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/** Serialises an atlas layout as the JSON, plain-text and CSS mappings written beside the images. */
final class AtlasMappings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final AtlasKind kind;
    private final List<AtlasCatalog.Entry> entries;
    private final AtlasLayout layout;
    private final List<String> imageFileNames;
    private final JsonObject extra;

    /**
     * @param imageFileNames the file name of each page's image
     * @param extra additional top-level JSON properties describing how the atlas was drawn
     */
    AtlasMappings(AtlasKind kind, List<AtlasCatalog.Entry> entries, AtlasLayout layout, List<String> imageFileNames, JsonObject extra) {
        this.kind = kind;
        this.entries = entries;
        this.layout = layout;
        this.imageFileNames = imageFileNames;
        this.extra = extra;
    }

    String json() {
        int pixelSize = layout.pixelSize();
        JsonObject root = new JsonObject();
        root.addProperty("atlas", kind.fileStem());
        root.addProperty("pixelSize", pixelSize);
        root.addProperty("itemCount", entries.size());
        JsonArray pages = new JsonArray();
        for (int page = 0; page < layout.pages().size(); page++) {
            AtlasLayout.Page sheet = layout.pages().get(page);
            JsonObject json = new JsonObject();
            json.addProperty("image", imageFileNames.get(page));
            json.addProperty("columns", sheet.columns());
            json.addProperty("rows", sheet.columns());
            json.addProperty("width", sheet.side(pixelSize));
            json.addProperty("height", sheet.side(pixelSize));
            json.addProperty("firstIndex", sheet.firstEntry());
            json.addProperty("itemCount", sheet.entryCount());
            pages.add(json);
        }
        root.add("pages", pages);
        for (var property : extra.entrySet()) {
            root.add(property.getKey(), property.getValue());
        }

        JsonArray items = new JsonArray();
        for (int index = 0; index < entries.size(); index++) {
            AtlasCatalog.Entry entry = entries.get(index);
            AtlasLayout.Cell cell = layout.cell(index);
            JsonObject item = new JsonObject();
            item.addProperty("index", index);
            item.addProperty("name", entry.name());
            if (entry.displayName() != null) {
                item.addProperty("displayName", entry.displayName());
            }
            item.addProperty("cssClass", cssClass(entry));
            item.addProperty("page", cell.page());
            item.addProperty("column", cell.column());
            item.addProperty("row", cell.row());
            item.addProperty("x", cell.column() * pixelSize);
            item.addProperty("y", cell.row() * pixelSize);
            item.addProperty("width", pixelSize);
            item.addProperty("height", pixelSize);
            items.add(item);
        }
        root.add("items", items);
        return GSON.toJson(root) + '\n';
    }

    /**
     * One line per cell, {@code Display Name (key): [column, row]}, followed by
     * {@code page <n>} (from 1) when the atlas has several pages.
     */
    String text() {
        boolean paged = layout.pages().size() > 1;
        StringBuilder text = new StringBuilder(entries.size() * 48);
        for (int index = 0; index < entries.size(); index++) {
            AtlasCatalog.Entry entry = entries.get(index);
            AtlasLayout.Cell cell = layout.cell(index);
            if (entry.displayName() != null) {
                text.append(entry.displayName()).append(" (").append(entry.name()).append(')');
            } else {
                text.append(entry.name());
            }
            text.append(": [").append(cell.column()).append(", ").append(cell.row()).append(']');
            if (paged) {
                text.append(" page ").append(cell.page() + 1);
            }
            text.append('\n');
        }
        return text.toString();
    }

    /**
     * A base class carrying the first page's image and the cell size, plus one class per cell
     * carrying its offset, and its page's image when that is not the first:
     * {@code <span class="vanilla_items vanilla_items-minecraft-stone">}. The image and offset are
     * the {@code --tag-image} and {@code --tag-position} custom properties, and the base class
     * exposes the icon as {@code --tag-mask}, which the glint overlay stylesheet uses to confine
     * the glint to the icon.
     */
    String css() {
        int pixelSize = layout.pixelSize();
        String base = kind.fileStem();
        StringBuilder css = new StringBuilder(entries.size() * 96);
        css.append("/* ").append(String.join(", ", imageFileNames)).append(": ").append(entries.size()).append(" items, ")
                .append(pixelSize).append('x').append(pixelSize).append(" px cells");
        if (layout.pages().size() > 1) {
            css.append(", ").append(layout.pages().size()).append(" pages");
        }
        css.append(". Generated by Texture Atlas Generator. */\n");
        css.append('.').append(base).append(" {\n")
                .append("  --tag-image: url(\"").append(imageFileNames.getFirst()).append("\");\n")
                .append("  --tag-mask: var(--tag-image) var(--tag-position, 0 0) no-repeat;\n")
                .append("  background: var(--tag-image) var(--tag-position, 0 0) no-repeat;\n")
                .append("  display: inline-block;\n")
                .append("  width: ").append(pixelSize).append("px;\n")
                .append("  height: ").append(pixelSize).append("px;\n")
                .append("}\n");
        for (int index = 0; index < entries.size(); index++) {
            AtlasCatalog.Entry entry = entries.get(index);
            AtlasLayout.Cell cell = layout.cell(index);
            css.append('.').append(cssClass(entry)).append(" { ");
            if (cell.page() > 0) {
                css.append("--tag-image: url(\"").append(imageFileNames.get(cell.page())).append("\"); ");
            }
            css.append("--tag-position: ")
                    .append(offset((long) cell.column() * pixelSize)).append(' ')
                    .append(offset((long) cell.row() * pixelSize)).append("; }");
            if (entry.displayName() != null) {
                css.append(" /* ").append(entry.displayName().replace("*/", "* /")).append(" */");
            }
            css.append('\n');
        }
        return css.toString();
    }

    private String cssClass(AtlasCatalog.Entry entry) {
        return kind.fileStem() + '-' + entry.cssClass();
    }

    private static String offset(long pixels) {
        return pixels == 0 ? "0" : "-" + pixels + "px";
    }
}
