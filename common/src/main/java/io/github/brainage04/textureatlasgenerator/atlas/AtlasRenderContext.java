package io.github.brainage04.textureatlasgenerator.atlas;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Selects a canonical animation frame without advancing or resetting the client's animations. */
final class AtlasRenderContext implements AutoCloseable {
    private final List<TextureAtlas> atlases = new ArrayList<>();
    private final List<AnimationSnapshot> animations = new ArrayList<>();
    private final OptionsRenderState options;
    private final double previousGlintSpeed;
    private final GpuBuffer previousGlobals = RenderSystem.getGlobalSettingsUniform();
    private final GlobalSettingsUniform globals;

    AtlasRenderContext(Minecraft client) {
        options = client.gameRenderer.gameRenderState().optionsRenderState;
        previousGlintSpeed = options.glintSpeed;
        client.getAtlasManager().forEach((id, atlas) -> {
            if (!atlas.animatedTexturesStates.isEmpty()) {
                atlases.add(atlas);
                for (SpriteContents.AnimationState state : atlas.animatedTexturesStates) {
                    animations.add(new AnimationSnapshot(state, state.frame, state.subFrame, state.isDirty));
                }
            }
        });
        globals = new GlobalSettingsUniform();
    }

    void apply(int width, int height) {
        // Glint uses wall-clock milliseconds times this speed; shader effects use GameTime.
        options.glintSpeed = 0;
        globals.update(width, height, 0.75, 0, DeltaTracker.ZERO, 0, Vec3.ZERO, false);
        for (AnimationSnapshot snapshot : animations) {
            snapshot.state.frame = 0;
            snapshot.state.subFrame = 0;
            snapshot.state.isDirty = true;
        }
        for (TextureAtlas atlas : atlases) {
            atlas.uploadAnimationFrames();
        }
    }

    @Override
    public void close() {
        try {
            for (AnimationSnapshot snapshot : animations) {
                snapshot.state.frame = snapshot.frame;
                snapshot.state.subFrame = snapshot.subFrame;
                snapshot.state.isDirty = true;
            }
            for (TextureAtlas atlas : atlases) {
                atlas.uploadAnimationFrames();
            }
        } finally {
            for (AnimationSnapshot snapshot : animations) {
                snapshot.state.isDirty = snapshot.dirty;
            }
            options.glintSpeed = previousGlintSpeed;
            RenderSystem.setGlobalSettingsUniform(previousGlobals);
            globals.close();
        }
    }

    private record AnimationSnapshot(SpriteContents.AnimationState state, int frame, int subFrame, boolean dirty) {}
}
