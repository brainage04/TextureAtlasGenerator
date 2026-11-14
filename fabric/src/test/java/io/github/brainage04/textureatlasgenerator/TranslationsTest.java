package io.github.brainage04.textureatlasgenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasKind;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TranslationsTest {
    private static final Path ROOT = projectRoot();
    private static final Path EN_US =
            ROOT.resolve("common/src/main/resources/assets/textureatlasgenerator/lang/en_us.json");
    private static final List<Path> MAIN_SOURCES =
            List.of(
                    ROOT.resolve("common/src/main/java"),
                    ROOT.resolve("fabric/src/main/java"),
                    ROOT.resolve("neoforge/src/main/java"));
    private static final Pattern KEY_LITERAL =
            Pattern.compile("\"((?:key\\.)?textureatlasgenerator\\.[a-z0-9_.]+)\"");
    // A key mapping category is labelled Identifier.toLanguageKey("key.category"), so its key never
    // appears as a literal (client-26.3 net/minecraft/client/KeyMapping.java:226).
    private static final Pattern KEY_CATEGORY =
            Pattern.compile(
                    "Category\\.register\\(\\s*Identifier\\.fromNamespaceAndPath\\("
                            + "\\s*(?:\\w+\\.)?MOD_ID,\\s*\"([a-z0-9_./-]+)\"\\s*\\)");
    // The only placeholders TranslatableContents accepts; anything else makes the game print the
    // raw template (client-26.3 TranslatableContents.java:53,126).
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?s|%%");
    private static final Pattern LITERAL_WORDS = Pattern.compile("Component\\.literal\\(\\s*\"[^\"]*\\p{L}");

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (!Files.exists(dir.resolve("settings.gradle"))) dir = dir.getParent();
        return dir;
    }

    private static Map<String, String> enUs() {
        try {
            JsonObject json = JsonParser.parseString(Files.readString(EN_US)).getAsJsonObject();
            Map<String, String> translations = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                translations.put(entry.getKey(), entry.getValue().getAsString());
            }
            return translations;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> mainSources() {
        List<String> sources = new ArrayList<>();
        for (Path root : MAIN_SOURCES) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    sources.add(Files.readString(file));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return sources;
    }

    private static Set<String> keysInCode() {
        Set<String> keys = new TreeSet<>();
        for (String source : mainSources()) {
            Matcher literal = KEY_LITERAL.matcher(source);
            while (literal.find()) keys.add(literal.group(1));
            Matcher category = KEY_CATEGORY.matcher(source);
            while (category.find()) {
                keys.add("key.category.textureatlasgenerator." + category.group(1));
            }
        }
        return keys;
    }

    @AfterEach
    void restoreLanguage() {
        Language.inject(Language.DEFAULT_INSTANCE);
    }

    @Test
    void everyKeyTheCodeUsesIsInEnUs() {
        Set<String> missing = new TreeSet<>(keysInCode());
        missing.removeAll(enUs().keySet());
        assertEquals(Set.of(), missing);
    }

    @Test
    void everyEnUsKeyIsUsedByTheCode() {
        Set<String> unused = new TreeSet<>(enUs().keySet());
        unused.removeAll(keysInCode());
        assertEquals(Set.of(), unused);
    }

    @Test
    void noWordsAreWrittenIntoALiteralComponent() {
        for (String source : mainSources()) {
            assertFalse(
                    LITERAL_WORDS.matcher(source).find(),
                    "player-visible text must use Component.translatable");
        }
    }

    @Test
    void translationsUseOnlyPlaceholdersTheGameFormats() {
        enUs().forEach(
                        (key, value) ->
                                assertTrue(
                                        PLACEHOLDER.matcher(value).replaceAll("").indexOf('%') < 0,
                                        key + ": " + value));
    }

    @Test
    void atlasNamesReadFromEnUs() {
        Map<String, String> translations = enUs();
        Language.inject(
                new Language() {
                    @Override
                    public String getOrDefault(String key, String defaultValue) {
                        return translations.getOrDefault(key, defaultValue);
                    }

                    @Override
                    public boolean has(String key) {
                        return translations.containsKey(key);
                    }

                    @Override
                    public boolean isDefaultRightToLeft() {
                        return false;
                    }

                    @Override
                    public FormattedCharSequence getVisualOrder(FormattedText text) {
                        return FormattedCharSequence.EMPTY;
                    }
                });
        assertEquals("Vanilla items", AtlasKind.VANILLA.displayName().getString());
        assertEquals(
                "Hypixel SkyBlock – all items", AtlasKind.SKYBLOCK_ALL.displayName().getString());
        assertEquals(
                "Hypixel SkyBlock – Bazaar items",
                AtlasKind.SKYBLOCK_BAZAAR.displayName().getString());
    }
}
