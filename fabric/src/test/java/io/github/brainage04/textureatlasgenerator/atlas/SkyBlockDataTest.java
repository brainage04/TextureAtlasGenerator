package io.github.brainage04.textureatlasgenerator.atlas;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkyBlockDataTest {
    @Test
    void bundledDataLoadsAndEveryBazaarItemIsInTheFullList() {
        List<SkyBlockData.Entry> all = SkyBlockData.all();
        List<SkyBlockData.Entry> bazaar = SkyBlockData.bazaar();
        assertFalse(all.isEmpty());
        assertFalse(bazaar.isEmpty());
        Set<SkyBlockData.Entry> known = new HashSet<>(all);
        for (SkyBlockData.Entry entry : bazaar) {
            assertTrue(known.contains(entry), "Bazaar item missing from the full list: " + entry.name());
        }
    }

    /// The exporter hands each skin value to Minecraft as a profile's textures property, and on failure
    /// `PlayerHeadSkins` reads textures.SKIN.url from it, so every value must decode to that shape.
    @Test
    void everySkinValueDecodesToASkinTextureUrl() {
        for (SkyBlockData.Entry entry : SkyBlockData.all()) {
            JsonObject decoded = JsonParser.parseString(new String(
                    Base64.getDecoder().decode(entry.skinValue()), StandardCharsets.UTF_8)).getAsJsonObject();
            String url = decoded.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
            assertTrue(url.matches("https?://textures\\.minecraft\\.net/texture/[0-9a-f]+"),
                    entry.name() + " has skin URL " + url);
        }
    }
}
