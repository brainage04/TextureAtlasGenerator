package io.github.brainage04.textureatlasgenerator.screen;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasExporter;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasKind;
import io.github.brainage04.textureatlasgenerator.atlas.GlintExporter;
import net.minecraft.client.Minecraft;

/**
 * The {@code /atlas} tree, shared by the Fabric and NeoForge client command registrations.
 *
 * <ul>
 *   <li>{@code /atlas} opens the generator.
 *   <li>{@code /atlas <pixels>} exports the vanilla atlas.
 *   <li>{@code /atlas <type> [<pixels>]} exports that atlas, at {@value AtlasExporter#DEFAULT_PIXEL_SIZE}
 *       pixels when no size is given.
 *   <li>{@code /atlas glint [<pixels>]} exports the enchantment glint loop, at
 *       {@value GlintExporter#DEFAULT_PIXEL_SIZE} pixels when no size is given.
 * </ul>
 */
public final class AtlasCommands {
    private AtlasCommands() {}

    public static <S> LiteralArgumentBuilder<S> create() {
        LiteralArgumentBuilder<S> atlas = LiteralArgumentBuilder.<S>literal("atlas")
                .executes(context -> AtlasScreens.open(client(), null, 0))
                .then(AtlasCommands.<S>pixels().executes(context -> AtlasScreens.open(
                        client(), AtlasKind.VANILLA, IntegerArgumentType.getInteger(context, "pixels"))));
        for (AtlasKind kind : AtlasKind.values()) {
            atlas.then(LiteralArgumentBuilder.<S>literal(kind.commandName())
                    .executes(context -> AtlasScreens.open(client(), kind, AtlasExporter.DEFAULT_PIXEL_SIZE))
                    .then(AtlasCommands.<S>pixels().executes(context -> AtlasScreens.open(
                            client(), kind, IntegerArgumentType.getInteger(context, "pixels")))));
        }
        atlas.then(LiteralArgumentBuilder.<S>literal("glint")
                .executes(context -> AtlasScreens.openGlint(client(), GlintExporter.DEFAULT_PIXEL_SIZE))
                .then(AtlasCommands.<S>pixels().executes(context -> AtlasScreens.openGlint(
                        client(), IntegerArgumentType.getInteger(context, "pixels")))));
        return atlas;
    }

    private static Minecraft client() {
        return Minecraft.getInstance();
    }

    private static <S> RequiredArgumentBuilder<S, Integer> pixels() {
        return RequiredArgumentBuilder.argument(
                "pixels", IntegerArgumentType.integer(AtlasExporter.MIN_PIXEL_SIZE, AtlasExporter.MAX_PIXEL_SIZE));
    }
}
