package com.billyke2000s.natives;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * The five tribes. Each is loosely inspired by the clothing, dwellings and trade of real peoples from
 * that kind of landscape, but the tribes, names and details are invented.
 */
public enum Tribe {
    /** Deserts: indigo-robed nomads with sandstone shelters and canvas roofs. */
    DUNEKIN("Dunekin", HutStyle.HUT, Blocks.SMOOTH_SANDSTONE, Blocks.BROWN_WOOL, Blocks.SANDSTONE, Items.IRON_SWORD,
            Items.IRON_INGOT, List.of(new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.CACTUS, 4),
            new ItemStack(Items.RABBIT_HIDE, 3), new ItemStack(Items.BLUE_DYE, 3), new ItemStack(Items.GOLD_NUGGET, 6))),
    /** Jungles: forest gardeners with bamboo-roofed wooden huts. */
    VERDANI("Verdani", HutStyle.HUT, Blocks.JUNGLE_PLANKS, Blocks.BAMBOO_BLOCK, Blocks.JUNGLE_LOG, Items.STONE_SWORD,
            Items.COPPER_INGOT, List.of(new ItemStack(Items.COCOA_BEANS, 4), new ItemStack(Items.MELON_SLICE, 6),
            new ItemStack(Items.BAMBOO, 8), new ItemStack(Items.GREEN_DYE, 3), new ItemStack(Items.GLOW_BERRIES, 4))),
    /** Snow and ice: fur-clad hunters who build snow domes. */
    FROSTMARK("Frostmark", HutStyle.DOME, Blocks.SNOW_BLOCK, Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Items.TRIDENT,
            Items.COAL, List.of(new ItemStack(Items.COD, 4), new ItemStack(Items.SALMON, 3),
            new ItemStack(Items.RABBIT_HIDE, 3), new ItemStack(Items.LEATHER, 2), new ItemStack(Items.PRISMARINE_SHARD, 2))),
    /** Savannas: cattle herders in red cloth with mud-walled, thatched homes. */
    REDGRASS("Redgrass", HutStyle.HUT, Blocks.PACKED_MUD, Blocks.HAY_BLOCK, Blocks.MUD_BRICKS, Items.STONE_SWORD,
            Items.WHEAT, List.of(new ItemStack(Items.LEATHER, 3), new ItemStack(Items.BEEF, 3),
            new ItemStack(Items.RED_DYE, 3), new ItemStack(Items.GOLD_NUGGET, 6), new ItemStack(Items.ACACIA_LOG, 4))),
    /** Taiga: reindeer herders in blue tunics with hide-covered lodges. */
    PINEMARK("Pinemark", HutStyle.HUT, Blocks.BROWN_WOOL, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Items.IRON_AXE,
            Items.BREAD, List.of(new ItemStack(Items.SWEET_BERRIES, 8), new ItemStack(Items.LEATHER, 3),
            new ItemStack(Items.SPRUCE_LOG, 6), new ItemStack(Items.SALMON, 3), new ItemStack(Items.RED_DYE, 2)));

    public enum HutStyle { HUT, DOME }

    private final String displayName;
    private final HutStyle hutStyle;
    private final Block wall;
    private final Block roof;
    private final Block frame;
    private final Item weapon;
    private final Item favourite;
    private final List<ItemStack> goods;

    Tribe(String displayName, HutStyle hutStyle, Block wall, Block roof, Block frame, Item weapon, Item favourite, List<ItemStack> goods) {
        this.displayName = displayName;
        this.hutStyle = hutStyle;
        this.wall = wall;
        this.roof = roof;
        this.frame = frame;
        this.weapon = weapon;
        this.favourite = favourite;
        this.goods = goods;
    }

    public String displayName() { return displayName; }
    public HutStyle hutStyle() { return hutStyle; }
    public Block wall() { return wall; }
    public Block roof() { return roof; }
    public Block frame() { return frame; }
    public Item weapon() { return weapon; }
    /** Traded besides emeralds; counts extra towards the tribe's trust. */
    public Item favourite() { return favourite; }
    public String id() { return name().toLowerCase(Locale.ROOT); }

    public ItemStack randomGood(RandomSource random) {
        return goods.get(random.nextInt(goods.size())).copy();
    }

    public static Tribe byIndex(int index) {
        Tribe[] all = values();
        return all[Math.floorMod(index, all.length)];
    }

    @Nullable
    public static Tribe byId(String id) {
        for (Tribe tribe : values()) {
            if (tribe.id().equalsIgnoreCase(id) || tribe.displayName.equalsIgnoreCase(id)) {
                return tribe;
            }
        }
        return null;
    }

    /**
     * The tribe living in a biome, or null if none does. Uses tags first, then the biome's name so that
     * modded biomes (such as Moderner Beta's) are recognised too.
     */
    @Nullable
    public static Tribe forBiome(Holder<Biome> biome) {
        String path = biome.unwrapKey().map(key -> key.location().getPath()).orElse("");
        if (has(path, "ocean", "river", "beach", "swamp", "mushroom", "shrine", "cave", "deep_dark", "sky", "void")) {
            return null;
        }
        if (biome.is(Tags.Biomes.IS_SNOWY) || has(path, "snow", "ice", "tundra", "frozen", "glacier")) {
            return FROSTMARK;
        }
        if (biome.is(Tags.Biomes.IS_DESERT) || has(path, "desert")) {
            return DUNEKIN;
        }
        if (biome.is(BiomeTags.IS_JUNGLE) || has(path, "jungle", "rainforest")) {
            return VERDANI;
        }
        if (biome.is(BiomeTags.IS_SAVANNA) || has(path, "savanna", "shrubland")) {
            return REDGRASS;
        }
        if (biome.is(BiomeTags.IS_TAIGA) || has(path, "taiga")) {
            return PINEMARK;
        }
        return null;
    }

    private static boolean has(String path, String... words) {
        for (String word : words) {
            if (path.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
