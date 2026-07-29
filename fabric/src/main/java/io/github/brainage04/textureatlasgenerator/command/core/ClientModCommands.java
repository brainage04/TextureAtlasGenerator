package io.github.brainage04.textureatlasgenerator.command.core;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasExporter;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasKind;
import io.github.brainage04.textureatlasgenerator.screen.TextureAtlasScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public final class ClientModCommands {
    private ClientModCommands() {}

    public static void initialize() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("atlas")
                    .executes(context -> openScreen(context.getSource(), AtlasKind.VANILLA, false))
                    .then(ClientCommands.argument(
                                    "pixels",
                                    IntegerArgumentType.integer(
                                            AtlasExporter.MIN_PIXEL_SIZE,
                                            AtlasExporter.MAX_PIXEL_SIZE
                                    )
                            )
                            .executes(context -> openScreen(
                                    context.getSource(),
                                    AtlasKind.VANILLA,
                                    IntegerArgumentType.getInteger(context, "pixels"),
                                    true
                            )))
                    .then(atlasType("vanilla", AtlasKind.VANILLA))
                    .then(atlasType("skyblock-all", AtlasKind.SKYBLOCK_ALL))
                    .then(atlasType("skyblock-bazaar", AtlasKind.SKYBLOCK_BAZAAR))
            );

            dispatcher.register(ClientCommands.literal("skyblockatlas")
                    .executes(context -> openScreen(context.getSource(), AtlasKind.SKYBLOCK_ALL, false))
                    .then(ClientCommands.argument(
                                    "pixels",
                                    IntegerArgumentType.integer(
                                            AtlasExporter.MIN_PIXEL_SIZE,
                                            AtlasExporter.MAX_PIXEL_SIZE
                                    )
                            )
                            .then(ClientCommands.argument("fullAtlas", BoolArgumentType.bool())
                                    .executes(context -> openScreen(
                                            context.getSource(),
                                            BoolArgumentType.getBool(context, "fullAtlas")
                                                    ? AtlasKind.SKYBLOCK_ALL
                                                    : AtlasKind.SKYBLOCK_BAZAAR,
                                            IntegerArgumentType.getInteger(context, "pixels"),
                                            true
                                    )))
                    ));
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> atlasType(
            String literal,
            AtlasKind kind
    ) {
        return ClientCommands.literal(literal)
                .executes(context -> openScreen(context.getSource(), kind, false))
                .then(ClientCommands.argument(
                                "pixels",
                                IntegerArgumentType.integer(
                                        AtlasExporter.MIN_PIXEL_SIZE,
                                        AtlasExporter.MAX_PIXEL_SIZE
                                )
                        )
                        .executes(context -> openScreen(
                                context.getSource(),
                                kind,
                                IntegerArgumentType.getInteger(context, "pixels"),
                                true
                        )));
    }

    private static int openScreen(
            FabricClientCommandSource source,
            AtlasKind kind,
            boolean exportImmediately
    ) {
        return openScreen(source, kind, AtlasExporter.DEFAULT_PIXEL_SIZE, exportImmediately);
    }

    private static int openScreen(
            FabricClientCommandSource source,
            AtlasKind kind,
            int pixelSize,
            boolean exportImmediately
    ) {
        TextureAtlasScreen screen = new TextureAtlasScreen(source.getClient().gui.screen());
        screen.setSelection(kind, pixelSize);
        if (exportImmediately) {
            screen.startExportOnOpen();
        }
        source.getClient().gui.setScreen(screen);
        return 1;
    }
}
