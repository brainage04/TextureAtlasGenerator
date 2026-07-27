package io.github.brainage04.textureatlasgenerator.neoforge;

import io.github.brainage04.textureatlasgenerator.TextureAtlasGenerator;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasExporter;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasKind;
import io.github.brainage04.textureatlasgenerator.screen.TextureAtlasScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = TextureAtlasGenerator.MOD_ID, dist = Dist.CLIENT)
public final class TextureAtlasGeneratorNeoForge {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(TextureAtlasGenerator.MOD_ID, "general")
    );
    private static final KeyMapping OPEN_ATLAS_SCREEN = new KeyMapping(
            "key.textureatlasgenerator.open_screen", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY
    );

    public TextureAtlasGeneratorNeoForge(IEventBus modBus) {
        modBus.addListener(this::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        TextureAtlasGenerator.initialize();
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_ATLAS_SCREEN);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        while (OPEN_ATLAS_SCREEN.consumeClick()) {
            client.gui.setScreen(new TextureAtlasScreen(client.gui.screen()));
        }
    }

    private void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("atlas")
                .executes(context -> open(AtlasKind.VANILLA, AtlasExporter.DEFAULT_PIXEL_SIZE, false))
                .then(Commands.argument("pixels", IntegerArgumentType.integer(AtlasExporter.MIN_PIXEL_SIZE, AtlasExporter.MAX_PIXEL_SIZE))
                        .executes(context -> open(AtlasKind.VANILLA, IntegerArgumentType.getInteger(context, "pixels"), true)))
                .then(atlasType("vanilla", AtlasKind.VANILLA))
                .then(atlasType("skyblock-all", AtlasKind.SKYBLOCK_ALL))
                .then(atlasType("skyblock-bazaar", AtlasKind.SKYBLOCK_BAZAAR)));
        event.getDispatcher().register(Commands.literal("skyblockatlas")
                .executes(context -> open(AtlasKind.SKYBLOCK_ALL, AtlasExporter.DEFAULT_PIXEL_SIZE, false))
                .then(Commands.argument("pixels", IntegerArgumentType.integer(AtlasExporter.MIN_PIXEL_SIZE, AtlasExporter.MAX_PIXEL_SIZE))
                        .then(Commands.argument("fullAtlas", BoolArgumentType.bool()).executes(context -> open(
                                BoolArgumentType.getBool(context, "fullAtlas") ? AtlasKind.SKYBLOCK_ALL : AtlasKind.SKYBLOCK_BAZAAR,
                                IntegerArgumentType.getInteger(context, "pixels"), true)))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> atlasType(String literal, AtlasKind kind) {
        return Commands.literal(literal).executes(context -> open(kind, AtlasExporter.DEFAULT_PIXEL_SIZE, false))
                .then(Commands.argument("pixels", IntegerArgumentType.integer(AtlasExporter.MIN_PIXEL_SIZE, AtlasExporter.MAX_PIXEL_SIZE))
                        .executes(context -> open(kind, IntegerArgumentType.getInteger(context, "pixels"), true)));
    }

    private static int open(AtlasKind kind, int pixels, boolean exportImmediately) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            TextureAtlasScreen screen = new TextureAtlasScreen(client.gui.screen());
            screen.setSelection(kind, pixels);
            if (exportImmediately) screen.startExportOnOpen();
            client.gui.setScreen(screen);
        });
        return 1;
    }
}
