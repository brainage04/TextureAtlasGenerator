package io.github.brainage04.textureatlasgenerator.atlas;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AtlasMappingsTest {
    @Test
    void mappingFormatsAgreeAtPageBoundary() {
        List<AtlasCatalog.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            entries.add(new AtlasCatalog.Entry(null, "item:" + i, "Item " + i, "item-" + i, "", null));
        }
        AtlasMappings mappings = new AtlasMappings(AtlasKind.VANILLA, entries, AtlasLayout.of(65, 1024),
                List.of("first.webp", "second.webp"), new JsonObject());
        JsonObject json = JsonParser.parseString(mappings.json()).getAsJsonObject();
        JsonObject last = json.getAsJsonArray("items").get(64).getAsJsonObject();
        assertEquals(1, last.get("page").getAsInt());
        assertEquals(0, last.get("x").getAsInt());
        assertEquals(0, last.get("y").getAsInt());
        JsonObject second = json.getAsJsonArray("pages").get(1).getAsJsonObject();
        assertEquals("second.webp", second.get("image").getAsString());
        assertEquals(64, second.get("firstIndex").getAsInt());
        assertEquals(1, second.get("itemCount").getAsInt());
        assertEquals(1024, second.get("width").getAsInt());
        assertEquals("Item 63 (item:63): [7, 7] page 1", mappings.text().lines().toList().get(63));
        assertEquals("Item 64 (item:64): [0, 0] page 2", mappings.text().lines().toList().get(64));
        assertTrue(mappings.css().contains(".vanilla_items-item-64 { --tag-image: url(\"second.webp\"); --tag-position: 0 0; }"));
    }

    @Test
    void headMappingsOmitDisplayNameAndProtectCssComments() {
        AtlasMappings heads = new AtlasMappings(AtlasKind.SKYBLOCK_ALL,
                List.of(new AtlasCatalog.Entry(null, "HEAD", null, "head", "", null)),
                AtlasLayout.of(1, 16), List.of("heads.webp"), new JsonObject());
        assertFalse(JsonParser.parseString(heads.json()).getAsJsonObject().getAsJsonArray("items")
                .get(0).getAsJsonObject().has("displayName"));
        assertEquals("HEAD: [0, 0]\n", heads.text());
        AtlasMappings named = new AtlasMappings(AtlasKind.VANILLA,
                List.of(new AtlasCatalog.Entry(null, "item", "Unsafe */ name", "item", "", null)),
                AtlasLayout.of(1, 16), List.of("item.webp"), new JsonObject());
        assertTrue(named.css().contains("/* Unsafe * / name */"));
    }
}
