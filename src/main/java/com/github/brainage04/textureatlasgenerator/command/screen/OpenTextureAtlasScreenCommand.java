package com.github.brainage04.textureatlasgenerator.command.screen;

import com.github.brainage04.textureatlasgenerator.screen.TextureAtlasScreen;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class OpenTextureAtlasScreenCommand {
    public static int execute(FabricClientCommandSource source) {
        MinecraftClient client = source.getClient();
        client.send(() ->
                client.setScreen(new TextureAtlasScreen(client.currentScreen))
        );

        return 1;
    }

    public static void initialize(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("textureatlasscreen")
                .executes(context ->
                        execute(
                                context.getSource()
                        )
                )
        );
    }
}
