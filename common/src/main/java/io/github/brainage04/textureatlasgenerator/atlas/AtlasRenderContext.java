package io.github.brainage04.textureatlasgenerator.atlas;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.phys.Vec3;

final class AtlasRenderContext implements AutoCloseable {
    private final List<TextureAtlas> atlases = new ArrayList<>();
    private final List<AnimationSnapshot> animations = new ArrayList<>();
    private final GpuBuffer previousGlobals = RenderSystem.getGlobalSettingsUniform();
    private final GlobalSettingsUniform globals;

    AtlasRenderContext(Minecraft client) {
        client.getAtlasManager()
                .forEach(
                        (id, atlas) -> {
                            if (!atlas.animatedTexturesStates.isEmpty()) {
                                atlases.add(atlas);
                                for (SpriteContents.AnimationState state :
                                        atlas.animatedTexturesStates) {
                                    animations.add(
                                            new AnimationSnapshot(
                                                    state,
                                                    state.frame,
                                                    state.subFrame,
                                                    state.isDirty));
                                }
                            }
                        });
        globals = new GlobalSettingsUniform();
    }

    void apply(int width, int height) {
        globals.update(width, height, 0.75, 0, 0.0F, 0, Vec3.ZERO, false);
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
            RenderSystem.setGlobalSettingsUniform(previousGlobals);
            globals.close();
        }
    }

    private record AnimationSnapshot(
            SpriteContents.AnimationState state, int frame, int subFrame, boolean dirty) {}
}
