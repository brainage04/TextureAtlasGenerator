package io.github.brainage04.textureatlasgenerator;

import io.github.brainage04.textureatlasgenerator.command.core.ClientModCommands;
import io.github.brainage04.textureatlasgenerator.key.core.ModKeys;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TextureAtlasGenerator implements ClientModInitializer {
    public static final String MOD_ID = "textureatlasgenerator";
    public static final String MOD_NAME = "Texture Atlas Generator";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static volatile boolean initialized;

    @Override
    public void onInitializeClient() {
        ClientModCommands.initialize();
        ModKeys.initialize();
        initialized = true;

        LOGGER.info("{} client initialised.", MOD_NAME);
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
