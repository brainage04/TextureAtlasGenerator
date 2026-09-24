package io.github.brainage04.textureatlasgenerator.neoforge;

import io.github.brainage04.textureatlasgenerator.TextureAtlasGenerator;
import io.github.brainage04.textureatlasgenerator.screen.AtlasCommands;
import io.github.brainage04.textureatlasgenerator.screen.TextureAtlasScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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

    /** Registers the {@code /atlas} client command; see {@link AtlasCommands}. */
    private void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(AtlasCommands.<CommandSourceStack>create());
    }
}
