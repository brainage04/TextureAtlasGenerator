package io.github.brainage04.textureatlasgenerator.atlas;

import net.minecraft.network.chat.Component;

public enum AtlasKind {
    VANILLA("textureatlasgenerator.atlas.vanilla", "vanilla_items", "vanilla"),
    SKYBLOCK_ALL(
            "textureatlasgenerator.atlas.skyblock_all", "hypixel_skyblock_items", "skyblock-all"),
    SKYBLOCK_BAZAAR(
            "textureatlasgenerator.atlas.skyblock_bazaar",
            "hypixel_skyblock_bazaar_items",
            "skyblock-bazaar");

    private final String translationKey;
    private final String fileStem;
    private final String commandName;

    AtlasKind(String translationKey, String fileStem, String commandName) {
        this.translationKey = translationKey;
        this.fileStem = fileStem;
        this.commandName = commandName;
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    public String fileStem() {
        return fileStem;
    }

    public String commandName() {
        return commandName;
    }

    public boolean usesPlayerHeads() {
        return this == SKYBLOCK_ALL || this == SKYBLOCK_BAZAAR;
    }
}
