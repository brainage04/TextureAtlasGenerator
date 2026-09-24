package io.github.brainage04.textureatlasgenerator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestRecorder;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestServers;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasCatalog;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasExporter;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasKind;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasLayout;
import io.github.brainage04.textureatlasgenerator.atlas.GlintExporter;
import io.github.brainage04.textureatlasgenerator.atlas.SkyBlockData;
import io.github.brainage04.textureatlasgenerator.screen.TextureAtlasScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import org.lwjgl.glfw.GLFW;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("UnstableApiUsage")
public final class TextureAtlasGeneratorClientGameTest implements FabricClientGameTest {
    private static final int PIXEL_SIZE = 8;
    private static final List<String> EXTENSIONS = List.of(".webp", ".json", ".txt", ".css");
    private static final List<String> GLINT_EXTENSIONS = List.of(".webp", ".json", ".css");

    @Override
    public void runTest(ClientGameTestContext context) {
        Properties serverProperties = ClientGameTestServers.flatServerProperties();
        ClientGameTestServers.withDedicatedServer(context, serverProperties, "Texture Atlas Generator GameTest", server -> { try {
            runConnectedTest(context);
        } finally {
            cleanupOutputs(context);
        } });
    }

    private void runConnectedTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            if (!TextureAtlasGenerator.isInitialized()) {
                throw new AssertionError("Expected the client initializer to run before the client GameTest");
            }
        });
        ClientGameTestServers.assertClientWorldAndPlayerAvailable(context);
        context.waitTicks(20);

        ClientGameTestRecorder.startRecording(context);
        ClientGameTestRecorder.showStep(
                context,
                "texture_atlas.command_opens_screen",
                "Texture Atlas Generator",
                "/atlas typed into chat opens the generator screen"
        );
        runChatCommand(context, "/atlas");
        context.waitForScreen(TextureAtlasScreen.class);
        context.waitTicks(50);
        context.takeScreenshot("texture-atlas-generator-screen");

        if (SkyBlockData.all().size() != 2545 || SkyBlockData.bazaar().size() != 351) {
            throw new AssertionError("Legacy SkyBlock data counts changed");
        }

        List<AtlasCatalog.Entry> vanillaCatalog = AtlasCatalog.create(AtlasKind.VANILLA);
        int potionVariants = BuiltInRegistries.POTION.size() + 1;
        long potionStacks = vanillaCatalog.stream().filter(entry -> entry.name().startsWith("minecraft:potion")).count();
        if (potionStacks != potionVariants) {
            throw new AssertionError("Expected " + potionVariants + " potion stacks (every potion plus the uncraftable default), found " + potionStacks);
        }
        JsonObject vanilla = export(context, AtlasKind.VANILLA, 1200);
        if (vanilla.get("items").getAsJsonArray().size() != vanillaCatalog.size()) {
            throw new AssertionError("Vanilla mapping does not list every catalog entry");
        }

        // Atlases too large for image viewers are split into square pages of at most 8192 px.
        AtlasLayout paged = AtlasLayout.of(vanillaCatalog.size(), AtlasExporter.MAX_PIXEL_SIZE);
        if (paged.pages().size() < 2 || paged.entryCount() != vanillaCatalog.size()
                || paged.pages().stream().anyMatch(page -> page.side(AtlasExporter.MAX_PIXEL_SIZE) > AtlasLayout.MAX_PAGE_SIDE)) {
            throw new AssertionError("Unexpected 1024 px vanilla layout " + paged);
        }

        exportGlint(context);

        if (AtlasCatalog.create(AtlasKind.SKYBLOCK_BAZAAR).size() != 351) {
            throw new AssertionError("Expected all 351 legacy Bazaar entries in the deterministic catalog");
        }
        ClientGameTestRecorder.showStep(
                context,
                "texture_atlas.hypixel_skyblock_bazaar_items.catalog",
                "Hypixel SkyBlock Bazaar catalog",
                "The 351-item Bazaar catalog is available for preview; its remote player-head export is intentionally not recorded"
        );
        openScreen(context, AtlasKind.SKYBLOCK_BAZAAR);
        context.waitForScreen(TextureAtlasScreen.class);
        context.waitTicks(50);
        context.takeScreenshot("texture-atlas-hypixel-skyblock-bazaar-items-catalog");
    }

    /** Types {@code command} into the chat screen and submits it with Enter, as a player would. */
    private static void runChatCommand(ClientGameTestContext context, String command) {
        context.setScreen(() -> new ChatScreen(command, false));
        context.waitTicks(2);
        context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
    }

    /** Exports {@code kind} through its chat command and returns the validated JSON mapping. */
    private static JsonObject export(ClientGameTestContext context, AtlasKind kind, int timeoutTicks) {
        String command = "/atlas " + kind.commandName() + " " + PIXEL_SIZE;
        ClientGameTestRecorder.showStep(
                context,
                "texture_atlas." + kind.fileStem() + ".exporting",
                kind.displayName() + " export",
                command + " renders the atlas and shows its progress"
        );
        List<Path> outputs = outputs(context, AtlasExporter.fileBase(kind, PIXEL_SIZE), EXTENSIONS);

        runChatCommand(context, command);
        context.waitForScreen(TextureAtlasScreen.class);
        context.waitFor(client -> AtlasExporter.isExporting(), 100);
        context.takeScreenshot("texture-atlas-" + kind.fileStem() + "-exporting");
        context.waitFor(client -> !AtlasExporter.isExporting() && outputs.stream().allMatch(Files::isRegularFile), timeoutTicks);

        ClientGameTestRecorder.showStep(
                context,
                "texture_atlas." + kind.fileStem() + ".saved",
                kind.displayName() + " atlas saved",
                "The WebP and its JSON, text and CSS mappings have been written"
        );
        context.waitTicks(20);
        context.takeScreenshot("texture-atlas-" + kind.fileStem() + "-saved");
        return validate(kind, outputs);
    }

    private static List<Path> outputs(ClientGameTestContext context, String fileBase, List<String> extensions) {
        AtomicReference<Path> outputDirectory = new AtomicReference<>();
        context.runOnClient(client -> outputDirectory.set(client.gameDirectory.toPath().resolve("texture-atlases")));
        List<Path> outputs = new ArrayList<>();
        for (String extension : extensions) {
            outputs.add(outputDirectory.get().resolve(fileBase + extension));
        }
        return outputs;
    }

    private static JsonObject validate(AtlasKind kind, List<Path> outputs) {
        try {
            JsonObject mapping = JsonParser.parseString(Files.readString(outputs.get(1), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray items = mapping.get("items").getAsJsonArray();
            int itemCount = AtlasCatalog.create(kind).size();
            int columns = AtlasLayout.columnsFor(itemCount);
            JsonArray pages = mapping.getAsJsonArray("pages");
            if (pages.size() != 1 || !pages.get(0).getAsJsonObject().get("image").getAsString().equals(outputs.get(0).getFileName().toString())) {
                throw new AssertionError(kind + " mapping should describe one page, " + outputs.get(0).getFileName() + ": " + pages);
            }
            if (items.size() != itemCount || mapping.get("pixelSize").getAsInt() != PIXEL_SIZE) {
                throw new AssertionError(kind + " mapping has " + items.size() + " items at "
                        + mapping.get("pixelSize") + " px; expected " + itemCount + " at " + PIXEL_SIZE);
            }

            WebPInfo image = WebPInfo.read(outputs.get(0));
            if (image.width() != columns * PIXEL_SIZE || image.height() != columns * PIXEL_SIZE || image.animated()) {
                throw new AssertionError("Unexpected " + kind + " image " + image + "; expected a square still "
                        + columns * PIXEL_SIZE + " px wide");
            }

            List<String> lines = Files.readAllLines(outputs.get(2), StandardCharsets.UTF_8);
            String css = Files.readString(outputs.get(3), StandardCharsets.UTF_8);
            Set<String> classes = new HashSet<>();
            for (int index = 0; index < items.size(); index++) {
                JsonObject item = items.get(index).getAsJsonObject();
                String expectedLine = item.get("displayName").getAsString() + " (" + item.get("name").getAsString()
                        + "): [" + index % columns + ", " + index / columns + "]";
                if (!lines.get(index).equals(expectedLine)) {
                    throw new AssertionError(kind + " text line " + index + " is '" + lines.get(index) + "', expected '" + expectedLine + "'");
                }
                String cssClass = item.get("cssClass").getAsString();
                if (!cssClass.matches("[A-Za-z_][A-Za-z0-9_-]*") || !classes.add(cssClass)
                        || !css.contains("." + cssClass + " { --tag-position: ")) {
                    throw new AssertionError(kind + " CSS class for " + item.get("name") + " is missing, invalid or duplicated: " + cssClass);
                }
            }
            return mapping;
        } catch (Exception error) {
            throw new AssertionError("Unable to validate the " + kind + " atlas outputs", error);
        }
    }

    /** {@code /atlas glint} writes the whole 82.5 s loop as one animated cell. */
    private static void exportGlint(ClientGameTestContext context) {
        String command = "/atlas glint " + PIXEL_SIZE;
        ClientGameTestRecorder.showStep(context, "texture_atlas.glint.exporting", "Enchantment glint loop",
                command + " renders the glint loop as an animated WebP");
        List<Path> outputs = outputs(context, GlintExporter.FILE_STEM + '_' + PIXEL_SIZE + 'x' + PIXEL_SIZE, GLINT_EXTENSIONS);
        runChatCommand(context, command);
        context.waitForScreen(TextureAtlasScreen.class);
        context.waitFor(client -> !AtlasExporter.isExporting() && outputs.stream().allMatch(Files::isRegularFile), 1200);
        try {
            WebPInfo image = WebPInfo.read(outputs.get(0));
            if (image.width() != PIXEL_SIZE || image.height() != PIXEL_SIZE || !image.animated()
                    || image.frames() != GlintExporter.FRAMES || image.frameMillis() != (long) GlintExporter.FRAMES * GlintExporter.FRAME_MILLIS) {
                throw new AssertionError("Unexpected glint animation " + image);
            }
            JsonObject description = JsonParser.parseString(Files.readString(outputs.get(1), StandardCharsets.UTF_8)).getAsJsonObject();
            if (description.get("loopSeconds").getAsDouble() != 82.5) {
                throw new AssertionError("The glint loop is not 82.5 s: " + description);
            }
        } catch (Exception error) {
            throw new AssertionError("Unable to validate the glint outputs", error);
        }
    }

    /** The size and animation of a WebP file, read from its RIFF chunks. */
    private record WebPInfo(int width, int height, boolean animated, int frames, long frameMillis) {
        static WebPInfo read(Path path) throws Exception {
            ByteBuffer data = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN);
            if (data.getInt(0) != 0x46464952 || data.getInt(8) != 0x50424557 || data.getInt(4) + 8 != data.capacity()) {
                throw new AssertionError(path + " is not a complete WebP file");
            }
            int width = 0;
            int height = 0;
            boolean animated = false;
            int frames = 0;
            long frameMillis = 0;
            for (int at = 12; at + 8 <= data.capacity(); ) {
                String type = new String(new byte[] {data.get(at), data.get(at + 1), data.get(at + 2), data.get(at + 3)}, StandardCharsets.US_ASCII);
                int size = data.getInt(at + 4);
                int payload = at + 8;
                switch (type) {
                    case "VP8X" -> {
                        animated = (data.get(payload) & 0x02) != 0;
                        width = uint24(data, payload + 4) + 1;
                        height = uint24(data, payload + 7) + 1;
                    }
                    case "VP8L" -> {
                        if (data.get(payload) != 0x2F) {
                            throw new AssertionError(path + " has a bad VP8L signature");
                        }
                        int bits = data.getInt(payload + 1);
                        width = (bits & 0x3FFF) + 1;
                        height = ((bits >>> 14) & 0x3FFF) + 1;
                    }
                    case "ANMF" -> {
                        frames++;
                        frameMillis += uint24(data, payload + 12);
                    }
                    default -> { }
                }
                at = payload + size + (size & 1);
            }
            return new WebPInfo(width, height, animated, frames, frameMillis);
        }

        private static int uint24(ByteBuffer data, int at) {
            return (data.get(at) & 0xFF) | (data.get(at + 1) & 0xFF) << 8 | (data.get(at + 2) & 0xFF) << 16;
        }
    }

    private static void openScreen(ClientGameTestContext context, AtlasKind kind) {
        context.waitTicks(1);
        context.runOnClient(client -> {
            TextureAtlasScreen screen = new TextureAtlasScreen(client.gui.screen());
            screen.setSelection(kind, PIXEL_SIZE);
            client.gui.setScreen(screen);
        });
    }

    private static void cleanupOutputs(ClientGameTestContext context) {
        context.runOnClient(client -> {
            Path outputDirectory = client.gameDirectory.toPath().resolve("texture-atlases");
            List<String> fileBases = new ArrayList<>();
            for (AtlasKind kind : AtlasKind.values()) {
                fileBases.add(AtlasExporter.fileBase(kind, PIXEL_SIZE));
            }
            fileBases.add(GlintExporter.FILE_STEM + '_' + PIXEL_SIZE + 'x' + PIXEL_SIZE);
            try {
                for (String fileBase : fileBases) {
                    for (String extension : List.of(".webp", ".png", ".json", ".txt", ".css")) {
                        Files.deleteIfExists(outputDirectory.resolve(fileBase + extension));
                        Files.deleteIfExists(outputDirectory.resolve(fileBase + extension + ".tmp"));
                        Files.deleteIfExists(outputDirectory.resolve(fileBase + ".tmp" + extension));
                    }
                }
            } catch (Exception error) {
                throw new AssertionError("Unable to clean atlas GameTest output", error);
            }
        });
    }
}
