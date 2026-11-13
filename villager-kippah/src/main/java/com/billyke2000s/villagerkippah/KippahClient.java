package com.billyke2000s.villagerkippah;

import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = VillagerKippah.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KippahClient {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(VillagerKippah.MODID, "villager_kippah"), "main");

    private KippahClient() {
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LAYER, KippahLayer::createLayer);
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        LivingEntityRenderer<Villager, VillagerModel<Villager>> renderer = event.getRenderer(EntityType.VILLAGER);
        if (renderer != null) {
            renderer.addLayer(new KippahLayer(renderer, event.getEntityModels().bakeLayer(LAYER)));
        }
    }
}
