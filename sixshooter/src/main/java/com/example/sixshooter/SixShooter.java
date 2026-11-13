package com.example.sixshooter;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(SixShooter.MODID)
public final class SixShooter {
    public static final String MODID = "sixshooter";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredItem<RevolverItem> REVOLVER = ITEMS.registerItem("revolver", RevolverItem::new,
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> BULLET = ITEMS.registerSimpleItem("revolver_round",
            new Item.Properties().stacksTo(64));

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE = sound("fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRY = sound("dry_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> OPEN = sound("cylinder_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOAD = sound("insert_rounds");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLOSE = sound("cylinder_close");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MODID, name)));
    }

    public SixShooter(IEventBus modBus) {
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(this::creativeTab);
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            // Compiled against NeoForge 21.1 this resolves to accept(ItemLike), which exists in 21.1.265.
            event.accept(REVOLVER.get());
            event.accept(BULLET.get());
        }
    }
}
