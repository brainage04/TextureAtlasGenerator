package io.github.brainage04.textureatlasgenerator.atlas;

import com.google.common.collect.ArrayListMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AtlasCatalog {
    private AtlasCatalog() {}

    public record Entry(ItemStack stack, String name, String searchText, GameProfile profile) {
        public Entry {
            stack = stack.copy();
            searchText = searchText.toLowerCase(Locale.ROOT);
        }
    }

    public static List<Entry> create(AtlasKind kind) {
        return switch (kind) {
            case VANILLA -> vanilla();
            case SKYBLOCK_ALL -> skyBlock(SkyBlockData.all());
            case SKYBLOCK_BAZAAR -> skyBlock(SkyBlockData.bazaar());
        };
    }

    private static List<Entry> vanilla() {
        List<Entry> entries = new ArrayList<>(BuiltInRegistries.ITEM.size() - 1);
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }

            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            ItemStack stack = new ItemStack(item);
            String name = id.toString();
            entries.add(new Entry(stack, name, name + ' ' + stack.getHoverName().getString(), null));
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
            entries.add(new Entry(stack, sourceEntry.name(), sourceEntry.name().replace('_', ' '), profile));
        }
        return List.copyOf(entries);
    }
}
