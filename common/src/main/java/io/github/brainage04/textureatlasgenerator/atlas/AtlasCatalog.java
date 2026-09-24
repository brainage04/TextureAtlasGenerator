package io.github.brainage04.textureatlasgenerator.atlas;

import com.google.common.collect.ArrayListMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AtlasCatalog {
    private AtlasCatalog() {}

    /**
     * One atlas cell.
     *
     * @param name unique key: a namespaced item ID (with a component suffix for variants), or a
     *     SkyBlock item ID
     * @param displayName in-game item name, or {@code null} where none is known (SkyBlock)
     * @param cssClass CSS-safe, catalog-unique form of {@code name}
     */
    public record Entry(
            ItemStack stack,
            String name,
            @Nullable String displayName,
            String cssClass,
            String searchText,
            @Nullable GameProfile profile) {
        public Entry {
            stack = stack.copy();
            searchText = searchText.toLowerCase(Locale.ROOT);
        }

        /** The name shown in previews: the display name when known, otherwise the key. */
        public String label() {
            return displayName == null ? name : displayName + " (" + name + ")";
        }
    }

    public static List<Entry> create(AtlasKind kind) {
        List<Entry> entries = switch (kind) {
            case VANILLA -> vanilla();
            case SKYBLOCK_ALL -> skyBlock(SkyBlockData.all());
            case SKYBLOCK_BAZAAR -> skyBlock(SkyBlockData.bazaar());
        };
        requireUniqueKeys(kind, entries);
        return entries;
    }

    /**
     * Every registered item except air, in registry order. Items carrying potion contents (potions,
     * splash and lingering potions, tipped arrows) expand in place into their default "uncraftable"
     * stack followed by one stack per registered potion.
     */
    private static List<Entry> vanilla() {
        List<Entry> entries = new ArrayList<>(BuiltInRegistries.ITEM.size() + 256);
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }

            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            ItemStack stack = new ItemStack(item);
            entries.add(entry(stack, id));
            if (!stack.has(DataComponents.POTION_CONTENTS)) {
                continue;
            }
            for (Potion potion : BuiltInRegistries.POTION) {
                Holder<Potion> holder = BuiltInRegistries.POTION.wrapAsHolder(potion);
                ItemStack variant = new ItemStack(item);
                variant.set(DataComponents.POTION_CONTENTS, new PotionContents(holder));
                entries.add(entry(variant, potionVariantName(id, holder)));
            }
        }
        return List.copyOf(entries);
    }

    private static List<Entry> skyBlock(List<SkyBlockData.Entry> source) {
        List<Entry> entries = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            SkyBlockData.Entry sourceEntry = source.get(index);
            UUID uuid = UUID.nameUUIDFromBytes(
                    ("textureatlasgenerator:" + sourceEntry.name()).getBytes(StandardCharsets.UTF_8)
            );
            ArrayListMultimap<String, Property> properties = ArrayListMultimap.create();
            properties.put("textures", new Property("textures", sourceEntry.skinValue()));
            GameProfile profile = new GameProfile(uuid, "Atlas" + index, new PropertyMap(properties));

            ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
            entries.add(new Entry(
                    stack,
                    sourceEntry.name(),
                    null,
                    cssClass(sourceEntry.name()),
                    sourceEntry.name().replace('_', ' '),
                    profile));
        }
        return List.copyOf(entries);
    }

    private static Entry entry(ItemStack stack, String name) {
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        String displayName = stack.getHoverName().getString();
        return new Entry(stack, name, displayName, cssClass(name), name + ' ' + displayName, null);
    }

    /** {@code /give}-compatible item syntax for a potion variant, e.g. {@code minecraft:potion[potion_contents="minecraft:swiftness"]}. */
    private static String potionVariantName(String itemId, Holder<Potion> potion) {
        String potionId = potion.unwrapKey()
                .orElseThrow(() -> new IllegalStateException("Unregistered potion " + potion))
                .identifier()
                .toString();
        return itemId + "[potion_contents=\"" + potionId + "\"]";
    }

    /** Replaces every run of characters outside {@code [A-Za-z0-9_-]} with a single hyphen. */
    static String cssClass(String name) {
        String slug = name.replaceAll("[^A-Za-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty() || Character.isDigit(slug.charAt(0))) {
            slug = "_" + slug;
        }
        return slug;
    }

    private static void requireUniqueKeys(AtlasKind kind, List<Entry> entries) {
        Map<String, String> names = new HashMap<>();
        Map<String, String> classes = new HashMap<>();
        for (Entry entry : entries) {
            String previous = names.put(entry.name(), entry.name());
            if (previous != null) {
                throw new IllegalStateException(kind + " atlas has duplicate entry " + entry.name());
            }
            previous = classes.put(entry.cssClass(), entry.name());
            if (previous != null) {
                throw new IllegalStateException(kind + " atlas entries " + previous + " and "
                        + entry.name() + " share CSS class " + entry.cssClass());
            }
        }
    }
}
