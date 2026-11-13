package com.example.sixshooter;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Drives the revolver's model frames: fire flash, cylinder open, loading and closing. */
@EventBusSubscriber(modid = SixShooter.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class RevolverClient {
    private RevolverClient() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(SixShooter.REVOLVER.get(),
                ResourceLocation.fromNamespaceAndPath(SixShooter.MODID, "frame"),
                (stack, level, entity, seed) -> {
                    CompoundTag data = RevolverItem.data(stack);
                    if (level == null) {
                        return 0.0F;
                    }
                    long now = level.getGameTime();
                    if (data.getBoolean("reloading")) {
                        long since = now - data.getLong("reloadStart");
                        if (since < 8L) {
                            return 0.4F;
                        }
                        if (since < 26L) {
                            return 0.6F;
                        }
                        return 0.8F;
                    }
                    long lastShot = data.getLong("lastShot");
                    if (lastShot > 0L && now >= lastShot && now - lastShot < 3L) {
                        return 0.2F;
                    }
                    return 0.0F;
                }));
    }
}
