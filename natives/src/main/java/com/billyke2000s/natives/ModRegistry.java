package com.billyke2000s.natives;

import com.billyke2000s.natives.entity.NativeEntity;
import com.billyke2000s.natives.world.NativeCampBiomeModifier;
import com.billyke2000s.natives.world.NativeCampFeature;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModRegistry {
    private static final String MODID = NativesMod.MODID;

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MODID);
    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<NativeEntity>> NATIVE = ENTITIES.register("native",
            () -> EntityType.Builder.<NativeEntity>of(NativeEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("native"));

    public static final DeferredBlock<VillageHeartBlock> VILLAGE_HEART = BLOCKS.register("village_heart",
            () -> new VillageHeartBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(3.0F, 1200.0F)
                    .sound(SoundType.WOOD)));
    public static final DeferredItem<BlockItem> VILLAGE_HEART_ITEM = ITEMS.registerSimpleBlockItem("village_heart", VILLAGE_HEART);

    public static final DeferredItem<Item> COLONY_FLAG = ITEMS.register("colony_flag",
            () -> new Item(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<DeferredSpawnEggItem> NATIVE_SPAWN_EGG = ITEMS.register("native_spawn_egg",
            () -> new DeferredSpawnEggItem(NATIVE, 0x8B5A2B, 0xD9A441, new Item.Properties()));

    public static final DeferredHolder<Feature<?>, NativeCampFeature> NATIVE_CAMP = FEATURES.register("native_camp",
            () -> new NativeCampFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<NativeCampBiomeModifier>> NATIVE_CAMPS_MODIFIER =
            BIOME_MODIFIERS.register("native_camps", () -> NativeCampBiomeModifier.CODEC);

    private ModRegistry() {
    }

    static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        FEATURES.register(modBus);
        BIOME_MODIFIERS.register(modBus);
        modBus.addListener(ModRegistry::onAttributes);
        modBus.addListener(ModRegistry::onCreativeTabs);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(NATIVE.get(), NativeEntity.createAttributes().build());
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(NATIVE_SPAWN_EGG);
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(COLONY_FLAG);
        } else if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(VILLAGE_HEART_ITEM);
        }
    }
}
