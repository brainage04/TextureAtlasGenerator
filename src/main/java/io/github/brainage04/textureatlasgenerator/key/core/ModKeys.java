package io.github.brainage04.textureatlasgenerator.key.core;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.brainage04.textureatlasgenerator.TextureAtlasGenerator;
import io.github.brainage04.textureatlasgenerator.screen.TextureAtlasScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ModKeys {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(TextureAtlasGenerator.MOD_ID, "general")
    );

    private static final KeyMapping OPEN_ATLAS_SCREEN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.textureatlasgenerator.open_screen",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    ));

    private ModKeys() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_ATLAS_SCREEN.consumeClick()) {
                client.gui.setScreen(new TextureAtlasScreen(client.gui.screen()));
            }
        });
    }
}
