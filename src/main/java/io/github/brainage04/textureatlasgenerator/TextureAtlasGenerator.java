package io.github.brainage04.textureatlasgenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TextureAtlasGenerator {
    public static final String MOD_ID = "textureatlasgenerator";
    public static final String MOD_NAME = "Texture Atlas Generator";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static volatile boolean initialized;

    private TextureAtlasGenerator() {}

    public static void initialize() {
        initialized = true;
        LOGGER.info("{} client initialised.", MOD_NAME);
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
