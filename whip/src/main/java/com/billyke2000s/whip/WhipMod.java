package com.billyke2000s.whip;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Adds the Whip: half a heart per lash, and the target is jolted forward with a little hop. */
@Mod(WhipMod.MODID)
public final class WhipMod {
    public static final String MODID = "whip";

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredItem<WhipItem> WHIP = ITEMS.register("whip", () -> new WhipItem(new Item.Properties()
            .durability(250)
            .attributes(ItemAttributeModifiers.builder()
                    // No extra damage: a player's bare 1.0 attack is exactly half a heart.
                    .add(Attributes.ATTACK_SPEED,
                            new AttributeModifier(ResourceLocation.fromNamespaceAndPath(MODID, "whip_speed"), -2.0, AttributeModifier.Operation.ADD_VALUE),
                            EquipmentSlotGroup.MAINHAND)
                    // A whip reaches further than a fist.
                    .add(Attributes.ENTITY_INTERACTION_RANGE,
                            new AttributeModifier(ResourceLocation.fromNamespaceAndPath(MODID, "whip_reach"), 2.0, AttributeModifier.Operation.ADD_VALUE),
                            EquipmentSlotGroup.MAINHAND)
                    .build())));

    public WhipMod(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(WhipMod::onCreativeTabs);
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(WHIP);
        }
    }
}
