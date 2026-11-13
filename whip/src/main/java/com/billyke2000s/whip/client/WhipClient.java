package com.billyke2000s.whip.client;

import com.billyke2000s.whip.WhipMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = WhipMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WhipClient {
    private WhipClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WhipMod.WHIP_LASH.get(), WhipLashRenderer::new);
    }
}
