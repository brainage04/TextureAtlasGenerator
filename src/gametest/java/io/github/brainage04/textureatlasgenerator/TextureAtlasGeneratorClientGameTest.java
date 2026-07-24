package io.github.brainage04.textureatlasgenerator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasExporter;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasKind;
import io.github.brainage04.textureatlasgenerator.atlas.SkyBlockData;
import io.github.brainage04.textureatlasgenerator.screen.TextureAtlasScreen;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestServers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("UnstableApiUsage")
public final class TextureAtlasGeneratorClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Properties serverProperties = ClientGameTestServers.flatServerProperties();
        try (TestDedicatedServerContext server = context.worldBuilder().createServer(serverProperties)) {
            ClientGameTestServers.connectToDedicatedServer(context, server, "Texture Atlas Generator GameTest");
            try {
                runConnectedTest(context);
            } finally {
                ClientGameTestServers.disconnectFromDedicatedServer(context);
            }
        }
    }

    private void runConnectedTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            if (!TextureAtlasGenerator.isInitialized()) {
                throw new AssertionError("Expected the client initializer to run before the client GameTest");
            }
            client.gui.setScreen(new TextureAtlasScreen(client.gui.screen()));
        });
        context.waitForScreen(TextureAtlasScreen.class);
        context.waitTicks(5);
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
        validateResult(vanilla, 8);

        AtlasExporter.Result bazaar = export(context, AtlasKind.SKYBLOCK_BAZAAR, 2400);
        if (bazaar.itemCount() != 351) {
            throw new AssertionError(
                    "Expected all 351 legacy Bazaar entries, found " + bazaar.itemCount()
            );
        }
        validateResult(bazaar, 8);
    }

    private static AtlasExporter.Result export(
            ClientGameTestContext context,
            AtlasKind kind,
            int timeoutTicks
    ) {
        AtomicReference<AtlasExporter.Result> completed = new AtomicReference<>();
        AtomicReference<Throwable> failed = new AtomicReference<>();

        context.runOnClient(client -> {
            boolean started = AtlasExporter.start(client, kind, 8, new AtlasExporter.Listener() {
                @Override
                public void onProgress(AtlasExporter.Progress progress) {}

                @Override
                public void onSuccess(AtlasExporter.Result result) {
                    completed.set(result);
                }

                @Override
                public void onFailure(Throwable error) {
                    failed.set(error);
                }
            });
            if (!started) {
                throw new AssertionError("Expected the " + kind + " atlas export to start");
            }
        });

        context.waitFor(client -> completed.get() != null || failed.get() != null, timeoutTicks);
        if (failed.get() != null) {
            throw new AssertionError(kind + " atlas export failed", failed.get());
        }

        AtlasExporter.Result result = completed.get();
        if (result == null) {
            throw new AssertionError(kind + " atlas export did not return a result");
        }
        return result;
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
