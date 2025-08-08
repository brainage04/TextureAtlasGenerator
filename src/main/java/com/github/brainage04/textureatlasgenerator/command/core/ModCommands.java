package com.github.brainage04.textureatlasgenerator.command.core;

import com.github.brainage04.textureatlasgenerator.command.screen.OpenTextureAtlasScreenCommand;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public class ModCommands {
    public static void initialize() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            OpenTextureAtlasScreenCommand.initialize(dispatcher);
        });
    }
}
