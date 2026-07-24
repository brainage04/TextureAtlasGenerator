package io.github.brainage04.textureatlasgenerator.atlas;

public enum AtlasKind {
    VANILLA("Vanilla items", "vanilla_items"),
    SKYBLOCK_ALL("Hypixel SkyBlock — all items", "hypixel_skyblock_items"),
    SKYBLOCK_BAZAAR("Hypixel SkyBlock — Bazaar items", "hypixel_skyblock_bazaar_items");

    private final String displayName;
    private final String fileStem;

    AtlasKind(String displayName, String fileStem) {
        this.displayName = displayName;
        this.fileStem = fileStem;
    }

    public String displayName() {
        return displayName;
    }

    public String fileStem() {
        return fileStem;
    }

    public static AtlasKind fromCommand(String value) {
        return switch (value) {
            case "vanilla" -> VANILLA;
            case "skyblock-all" -> SKYBLOCK_ALL;
            case "skyblock-bazaar" -> SKYBLOCK_BAZAAR;
            default -> throw new IllegalArgumentException("Unknown atlas type: " + value);
        };
    }
}
