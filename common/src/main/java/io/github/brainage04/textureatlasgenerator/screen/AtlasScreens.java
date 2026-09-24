package io.github.brainage04.textureatlasgenerator.screen;

import io.github.brainage04.textureatlasgenerator.atlas.AtlasKind;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/** Opens the generator from a chat command. */
public final class AtlasScreens {
    private AtlasScreens() {}

    /**
     * Opens the generator screen, exporting {@code kind} at {@code pixelSize} straight away when
     * {@code kind} is not {@code null}.
     *
     * @return {@code 1}, the Brigadier success count
     */
    public static int open(Minecraft client, @Nullable AtlasKind kind, int pixelSize) {
        return openLater(client, screen -> {
            if (kind != null) {
                screen.setSelection(kind, pixelSize);
                screen.startExportOnOpen();
            }
        });
    }

    /** Opens the generator screen and exports the enchantment glint loop at {@code pixelSize}. */
    public static int openGlint(Minecraft client, int pixelSize) {
        return openLater(client, screen -> screen.startGlintExportOnOpen(pixelSize));
    }

    /**
     * Client commands run inside {@code ChatScreen}'s Enter handler, which closes the current
     * screen after the command returns. A screen set synchronously is therefore closed again at
     * once, so opening is queued until the next client task pass.
     */
    private static int openLater(Minecraft client, Consumer<TextureAtlasScreen> setup) {
        client.schedule(() -> {
            TextureAtlasScreen screen = new TextureAtlasScreen(client.gui.screen());
            setup.accept(screen);
            client.gui.setScreen(screen);
        });
        return 1;
    }
}
