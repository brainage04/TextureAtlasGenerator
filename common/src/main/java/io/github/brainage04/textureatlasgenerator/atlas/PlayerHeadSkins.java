package io.github.brainage04.textureatlasgenerator.atlas;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.github.brainage04.textureatlasgenerator.TextureAtlasGenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Util;
import net.minecraft.world.item.component.ResolvableProfile;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Loads player-head skins through {@link PlayerSkinRenderCache}, the cache the item renderer
 * reads, and refuses to render a head that is not showing its own downloaded skin.
 *
 * <p>Warming {@code SkinManager} alone is not enough: its entries expire 15 seconds after last
 * access, and the head renderer resolves skins through {@code PlayerSkinRenderCache}, which asks
 * {@code SkinManager} again. A head whose skin finished loading more than 15 seconds before its
 * page was drawn therefore started a fresh asynchronous load and was drawn with a default skin,
 * without any error. Exports are now checked immediately before each page is rendered.
 */
final class PlayerHeadSkins {
    private static final int MAX_CONCURRENT_LOADS = 8;
    private static final int REPORTED_FAILURES = 5;

    private final Minecraft client;
    private final PlayerSkinRenderCache cache;
    private final List<AtlasCatalog.Entry> entries;
    private final CompletableFuture<Void> loaded = new CompletableFuture<>();
    private final List<String> failures = new ArrayList<>();
    private IntConsumer progress;
    private AtlasCatalog.Entry firstFailure;
    private int nextIndex;
    private int active;
    private int completed;

    PlayerHeadSkins(Minecraft client, List<AtlasCatalog.Entry> entries) {
        this.client = client;
        this.cache = client.playerSkinRenderCache();
        this.entries = entries;
    }

    /**
     * Resolves every skin. Completes exceptionally with a {@link SkinLoadException} naming the
     * failed entries if any skin cannot be loaded. Must be called on the render thread.
     *
     * @param progress receives the number of completed loads, on the render thread
     */
    CompletableFuture<Void> preload(IntConsumer progress) {
        this.progress = progress;
        startMore();
        return loaded;
    }

    /**
     * Runs {@code render} once every head in {@code page} is resolved in the render cache,
     * reloading any that expired since {@link #preload}. Must be called on the render thread.
     */
    void whenReady(List<AtlasCatalog.Entry> page, Runnable render, Consumer<Throwable> failure) {
        List<CompletableFuture<Optional<PlayerSkinRenderCache.RenderInfo>>> pending = new ArrayList<>();
        for (AtlasCatalog.Entry entry : page) {
            CompletableFuture<Optional<PlayerSkinRenderCache.RenderInfo>> lookup = cache.lookup(profile(entry));
            if (!lookup.isDone()) {
                pending.add(lookup);
            }
        }
        if (pending.isEmpty()) {
            verifyAndRun(page, render, failure);
            return;
        }

        if (progress != null) {
            TextureAtlasGenerator.LOGGER.info(
                    "{} player-head skins expired from Minecraft's skin cache before rendering; reloading them",
                    pending.size());
        }
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, error) -> client.schedule(() -> {
                    if (error != null) {
                        failure.accept(error);
                    } else {
                        verifyAndRun(page, render, failure);
                    }
                }));
    }

    private void verifyAndRun(List<AtlasCatalog.Entry> page, Runnable render, Consumer<Throwable> failure) {
        for (AtlasCatalog.Entry entry : page) {
            String problem = problem(entry, cache.lookup(profile(entry)).getNow(Optional.empty()), null);
            if (problem != null) {
                failure.accept(new SkinLoadException(entry.name() + ": " + problem));
                return;
            }
        }
        render.run();
    }

    private void startMore() {
        while (active < MAX_CONCURRENT_LOADS && nextIndex < entries.size()) {
            AtlasCatalog.Entry entry = entries.get(nextIndex++);
            active++;
            cache.lookup(profile(entry)).whenComplete((info, error) -> client.schedule(() -> complete(entry, info, error)));
        }
    }

    private void complete(AtlasCatalog.Entry entry, Optional<PlayerSkinRenderCache.RenderInfo> info, Throwable error) {
        active--;
        completed++;
        String problem = problem(entry, info, error);
        if (problem != null) {
            if (firstFailure == null) {
                firstFailure = entry;
            }
            failures.add(entry.name() + " (profile " + entry.profile().id() + "): " + problem);
        }
        progress.accept(completed);
        if (completed < entries.size()) {
            startMore();
        } else if (failures.isEmpty()) {
            loaded.complete(null);
        } else {
            reportFailures();
        }
    }

    private static String problem(
            AtlasCatalog.Entry entry, Optional<PlayerSkinRenderCache.RenderInfo> info, Throwable error) {
        if (error != null) {
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            return cause.toString();
        }
        if (info == null || info.isEmpty()) {
            return "Minecraft could not load the skin (see the 'Failed to load texture for profile "
                    + entry.profile().id() + "' warning in latest.log)";
        }
        if (!(info.get().playerSkin().body() instanceof ClientAsset.DownloadedTexture)) {
            return "resolved to a default skin instead of its textures property";
        }
        return null;
    }

    /** Fails the preload, first re-requesting one failed skin URL so the HTTP result is in the message. */
    private void reportFailures() {
        AtlasCatalog.Entry probe = firstFailure;
        CompletableFuture.supplyAsync(() -> probe(probe.profile(), client.getProxy()), Util.ioPool()).whenComplete((result, error) ->
                client.schedule(() -> {
                    StringBuilder message = new StringBuilder()
                            .append(failures.size()).append(" of ").append(entries.size())
                            .append(" player-head skins failed to load; no atlas was written. ");
                    for (int index = 0; index < Math.min(REPORTED_FAILURES, failures.size()); index++) {
                        message.append(index == 0 ? "" : "; ").append(failures.get(index));
                    }
                    if (failures.size() > REPORTED_FAILURES) {
                        message.append("; and ").append(failures.size() - REPORTED_FAILURES).append(" more");
                    }
                    message.append(". Re-requesting ").append(probe.name()).append("'s skin: ")
                            .append(error != null ? error.toString() : result).append('.');
                    SkinLoadException exception = new SkinLoadException(message.toString());
                    for (String failure : failures) {
                        TextureAtlasGenerator.LOGGER.error("Player-head skin failed: {}", failure);
                    }
                    loaded.completeExceptionally(exception);
                }));
    }

    /** Requests the skin URL through the same proxy as Minecraft's skin downloader. */
    private static String probe(GameProfile profile, Proxy proxy) {
        String url;
        try {
            Property textures = profile.properties().get("textures").iterator().next();
            JsonObject decoded = JsonParser.parseString(new String(
                    Base64.getDecoder().decode(textures.value()), StandardCharsets.UTF_8)).getAsJsonObject();
            url = decoded.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
        } catch (RuntimeException error) {
            return "the textures property could not be decoded (" + error + ")";
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection(proxy);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            int status = connection.getResponseCode();
            return "GET " + url + " returned HTTP " + status;
        } catch (IOException | RuntimeException error) {
            return "GET " + url + " failed: " + error;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static ResolvableProfile profile(AtlasCatalog.Entry entry) {
        return entry.stack().get(DataComponents.PROFILE);
    }

    /** A player-head skin could not be resolved; the export is abandoned rather than drawn with a default skin. */
    static final class SkinLoadException extends RuntimeException {
        SkinLoadException(String message) {
            super(message);
        }
    }
}
