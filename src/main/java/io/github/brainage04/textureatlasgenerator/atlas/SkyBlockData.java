package io.github.brainage04.textureatlasgenerator.atlas;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SkyBlockData {
    private static final String RESOURCE = "/assets/textureatlasgenerator/skyblock_items.json";

    private SkyBlockData() {}

    public record Entry(String name, String skinValue) {}

    public static List<Entry> all() {
        return DataHolder.DATA.all;
    }

    public static List<Entry> bazaar() {
        return DataHolder.DATA.bazaar;
    }

    private static final class DataHolder {
        private static final Loaded DATA = load();
    }

    private record Loaded(List<Entry> all, List<Entry> bazaar) {}

    private static Loaded load() {
        JsonObject root;
        try (InputStream stream = SkyBlockData.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing SkyBlock data resource: " + RESOURCE);
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (!parsed.isJsonObject()) throw new IllegalStateException("SkyBlock data root must be an object");
                root = parsed.getAsJsonObject();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof IllegalStateException) throw (IllegalStateException)e;
            throw new IllegalStateException("Unable to load SkyBlock data resource: " + RESOURCE, e);
        }
        JsonElement allElement = root.get("all");
        JsonElement bazaarElement = root.get("bazaar");
        if (allElement == null || !allElement.isJsonArray()) throw new IllegalStateException("SkyBlock data field 'all' must be an array");
        if (bazaarElement == null || !bazaarElement.isJsonArray()) throw new IllegalStateException("SkyBlock data field 'bazaar' must be an array");
        List<Entry> all = parseAll(allElement.getAsJsonArray());
        List<Entry> bazaar = parseBazaar(bazaarElement.getAsJsonArray(), all);
        return new Loaded(List.copyOf(all), List.copyOf(bazaar));
    }

    private static List<Entry> parseAll(JsonArray array) {
        List<Entry> result = new ArrayList<>(array.size());
        Set<String> names = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (!element.isJsonObject()) throw new IllegalStateException("Malformed SkyBlock 'all' entry at index " + i + ": expected object");
            JsonObject object = element.getAsJsonObject();
            String name = requiredString(object, "name", "all", i);
            String skin = requiredString(object, "skinValue", "all", i);
            if (!names.add(name)) throw new IllegalStateException("Duplicate SkyBlock name in 'all': " + name);
            result.add(new Entry(name, skin));
        }
        return result;
    }

    private static List<Entry> parseBazaar(JsonArray array, List<Entry> all) {
        java.util.Map<String, Entry> byName = new java.util.HashMap<>();
        for (Entry entry : all) byName.put(entry.name(), entry);
        List<Entry> result = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) throw new IllegalStateException("Malformed SkyBlock 'bazaar' entry at index " + i + ": expected name string");
            String name = element.getAsString();
            Entry entry = byName.get(name);
            if (entry == null) throw new IllegalStateException("Unknown SkyBlock Bazaar name at index " + i + ": " + name);
            result.add(entry);
        }
        return result;
    }

    private static String requiredString(JsonObject object, String field, String section, int index) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isEmpty()) throw new IllegalStateException("Malformed SkyBlock '" + section + "' entry at index " + index + ": field '" + field + "' must be a non-empty string");
        return value.getAsString();
    }
}
