package io.github.brainage04.textureatlasgenerator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestRecorder;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestServers;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasCatalog;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasExporter;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasKind;
import io.github.brainage04.textureatlasgenerator.atlas.SkyBlockData;
import io.github.brainage04.textureatlasgenerator.screen.TextureAtlasScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("UnstableApiUsage")
public final class TextureAtlasGeneratorClientGameTest implements FabricClientGameTest {
    private static final int PIXEL_SIZE = 8;

    @Override
    public void runTest(ClientGameTestContext context) {
        Properties serverProperties = ClientGameTestServers.flatServerProperties();
        try (TestDedicatedServerContext server = context.worldBuilder().createServer(serverProperties)) {
            ClientGameTestServers.connectToDedicatedServer(context, server, "Texture Atlas Generator GameTest");
            try {
                runConnectedTest(context);
            } finally {
                cleanupOutputs(context);
                ClientGameTestServers.disconnectFromDedicatedServer(context);
            }
        }
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
                "texture_atlas.screen",
                "Texture Atlas Generator",
                "Choose an atlas, preview its items, and export a texture atlas with its JSON mapping"
        );
        openScreen(context, AtlasKind.VANILLA);
        context.waitForScreen(TextureAtlasScreen.class);
        context.waitTicks(50);
        context.takeScreenshot("texture-atlas-generator-screen");

        if (SkyBlockData.all().size() != 2545 || SkyBlockData.bazaar().size() != 351) {
            throw new AssertionError("Legacy SkyBlock data counts changed");
        }

        AtlasExporter.Result vanilla = export(context, AtlasKind.VANILLA, 1200);
        if (vanilla.itemCount() < 1000) {
            throw new AssertionError(
                    "Expected a complete vanilla catalog, found only " + vanilla.itemCount() + " items"
            );
        }
        validateResult(vanilla, PIXEL_SIZE);

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

    private static AtlasExporter.Result export(
            ClientGameTestContext context,
            AtlasKind kind,
            int timeoutTicks
    ) {
        ClientGameTestRecorder.showStep(
                context,
                "texture_atlas." + kind.fileStem() + ".exporting",
                kind.displayName() + " export",
                "The selected atlas is rendered and its progress is shown in the generator screen"
        );
        openExportScreen(context, kind);
        context.waitForScreen(TextureAtlasScreen.class);
        context.waitFor(client -> AtlasExporter.isExporting(), 100);
        context.waitTicks(50);
        context.takeScreenshot("texture-atlas-" + kind.fileStem() + "-exporting");

        AtomicReference<Path> outputDirectory = new AtomicReference<>();
        context.runOnClient(client -> outputDirectory.set(client.gameDirectory.toPath().resolve("texture-atlases")));
        Path directory = outputDirectory.get();
        String fileBase = kind.fileStem() + '_' + PIXEL_SIZE + 'x' + PIXEL_SIZE;
        Path png = directory.resolve(fileBase + ".png");
        Path mapping = directory.resolve(fileBase + ".json");

        context.waitFor(client -> !AtlasExporter.isExporting() && Files.isRegularFile(png) && Files.isRegularFile(mapping), timeoutTicks);
        int itemCount = AtlasCatalog.create(kind).size();
        AtlasExporter.Result result = new AtlasExporter.Result(
                kind,
                PIXEL_SIZE,
                itemCount,
                AtlasExporter.COLUMNS * PIXEL_SIZE,
                Math.ceilDiv(itemCount, AtlasExporter.COLUMNS) * PIXEL_SIZE,
                0,
                png,
                mapping
        );

        ClientGameTestRecorder.showStep(
                context,
                "texture_atlas." + kind.fileStem() + ".saved",
                kind.displayName() + " atlas saved",
                "The generator confirms the PNG atlas and JSON item mapping have been written"
        );
        context.waitTicks(50);
        context.takeScreenshot("texture-atlas-" + kind.fileStem() + "-saved");
        return result;
    }

    private static void openScreen(ClientGameTestContext context, AtlasKind kind) {
        context.runOnClient(client -> {
            TextureAtlasScreen screen = new TextureAtlasScreen(client.gui.screen());
            screen.setSelection(kind, PIXEL_SIZE);
            client.gui.setScreen(screen);
        });
    }

    private static void openExportScreen(ClientGameTestContext context, AtlasKind kind) {
        context.runOnClient(client -> {
            TextureAtlasScreen screen = new TextureAtlasScreen(client.gui.screen());
            screen.setSelection(kind, PIXEL_SIZE);
            screen.startExportOnOpen();
            client.gui.setScreen(screen);
        });
    }

    private static void cleanupOutputs(ClientGameTestContext context) {
        context.runOnClient(client -> {
            Path outputDirectory = client.gameDirectory.toPath().resolve("texture-atlases");
            for (AtlasKind kind : AtlasKind.values()) {
                String fileBase = kind.fileStem() + '_' + PIXEL_SIZE + 'x' + PIXEL_SIZE;
                try {
                    Files.deleteIfExists(outputDirectory.resolve(fileBase + ".png"));
                    Files.deleteIfExists(outputDirectory.resolve(fileBase + ".json"));
                    Files.deleteIfExists(outputDirectory.resolve(fileBase + ".tmp.png"));
                    Files.deleteIfExists(outputDirectory.resolve(fileBase + ".tmp.json"));
                } catch (Exception error) {
                    throw new AssertionError("Unable to clean atlas GameTest output for " + kind, error);
                }
            }
        });
    }

    private static void validateResult(AtlasExporter.Result result, int expectedPixelSize) {
        if (result.width() != AtlasExporter.COLUMNS * expectedPixelSize) {
            throw new AssertionError("Unexpected atlas width: " + result.width());
        }
        if (!Files.isRegularFile(result.png()) || !Files.isRegularFile(result.mapping())) {
            throw new AssertionError("Expected both PNG and mapping outputs to exist");
        }

        try (InputStream stream = Files.newInputStream(result.png()); NativeImage image = NativeImage.read(stream)) {
            if (image.getWidth() != result.width() || image.getHeight() != result.height()) {
                throw new AssertionError(
                        "PNG dimensions do not match export result: " + image.getWidth() + 'x' + image.getHeight()
                );
            }
            boolean hasVisiblePixel = false;
            for (int pixel : image.getPixels()) {
                if ((pixel >>> 24) != 0) {
                    hasVisiblePixel = true;
                    break;
                }
            }
            if (!hasVisiblePixel) {
                throw new AssertionError("Rendered atlas is fully transparent");
            }
        } catch (Exception error) {
            throw new AssertionError("Unable to validate the rendered PNG", error);
        }

        try {
            JsonObject mapping = JsonParser.parseString(
                    Files.readString(result.mapping(), StandardCharsets.UTF_8)
            ).getAsJsonObject();
            if (mapping.get("items").getAsJsonArray().size() != result.itemCount()) {
                throw new AssertionError("Mapping item count does not match the rendered atlas");
            }
            if (mapping.get("pixelSize").getAsInt() != expectedPixelSize) {
                throw new AssertionError("Mapping did not preserve the requested pixel size");
            }
            Files.deleteIfExists(result.png());
            Files.deleteIfExists(result.mapping());
        } catch (Exception error) {
            throw new AssertionError("Unable to validate the atlas mapping", error);
        }
    }
}
