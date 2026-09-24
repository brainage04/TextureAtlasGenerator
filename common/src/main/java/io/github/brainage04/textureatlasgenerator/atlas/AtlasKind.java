package io.github.brainage04.textureatlasgenerator.atlas;

public enum AtlasKind {
    VANILLA("Vanilla items", "vanilla_items", "vanilla"),
    SKYBLOCK_ALL("Hypixel SkyBlock — all items", "hypixel_skyblock_items", "skyblock-all"),
    SKYBLOCK_BAZAAR(
            "Hypixel SkyBlock — Bazaar items", "hypixel_skyblock_bazaar_items", "skyblock-bazaar");

    private final String displayName;
    private final String fileStem;
    private final String commandName;

    AtlasKind(String displayName, String fileStem, String commandName) {
        this.displayName = displayName;
        this.fileStem = fileStem;
        this.commandName = commandName;
    }

    public String displayName() {
        return displayName;
    }

    public String fileStem() {
        return fileStem;
    }

    /** The literal used by {@code /atlas <type>}. */
    public String commandName() {
        return commandName;
    }

    /** Whether every entry is a player head whose skin must be downloaded before rendering. */
    public boolean usesPlayerHeads() {
        return this == SKYBLOCK_ALL || this == SKYBLOCK_BAZAAR;
    }
}
