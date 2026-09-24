package io.github.brainage04.textureatlasgenerator.command.core;

import io.github.brainage04.textureatlasgenerator.screen.AtlasCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/** Registers the {@code /atlas} client command; see {@link AtlasCommands}. */
public final class ClientModCommands {
    private ClientModCommands() {}

    public static void initialize() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(AtlasCommands.<FabricClientCommandSource>create()));
    }
}
