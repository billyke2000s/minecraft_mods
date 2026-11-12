package com.billyke2000s.natives.client;

import com.billyke2000s.natives.ModRegistry;
import com.billyke2000s.natives.NativesMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = NativesMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NativesClient {
    private NativesClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.NATIVE.get(), NativeRenderer::new);
    }
}
