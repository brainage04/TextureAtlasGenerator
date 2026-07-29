package io.github.brainage04.textureatlasgenerator.fabric;

import io.github.brainage04.textureatlasgenerator.TextureAtlasGenerator;
import io.github.brainage04.textureatlasgenerator.command.core.ClientModCommands;
import io.github.brainage04.textureatlasgenerator.key.core.ModKeys;
import net.fabricmc.api.ClientModInitializer;

public final class TextureAtlasGeneratorFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientModCommands.initialize();
        ModKeys.initialize();
        TextureAtlasGenerator.initialize();
    }
}
