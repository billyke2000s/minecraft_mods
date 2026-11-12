package com.billyke2000s.natives.client;

import com.billyke2000s.natives.NativesMod;
import com.billyke2000s.natives.Tribe;
import com.billyke2000s.natives.entity.NativeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Player-shaped model; the skin depends on tribe, role and variant. */
public class NativeRenderer extends HumanoidMobRenderer<NativeEntity, HumanoidModel<NativeEntity>> {
    private static final ResourceLocation[][][] TEXTURES = new ResourceLocation[Tribe.values().length][NativeEntity.Role.values().length][NativeEntity.VARIANTS];

    static {
        for (Tribe tribe : Tribe.values()) {
            for (NativeEntity.Role role : NativeEntity.Role.values()) {
                for (int v = 0; v < NativeEntity.VARIANTS; v++) {
                    TEXTURES[tribe.ordinal()][role.ordinal()][v] = NativesMod.id("textures/entity/native/"
                            + tribe.id() + "_" + role.name().toLowerCase(java.util.Locale.ROOT) + "_" + v + ".png");
                }
            }
        }
    }

    public NativeRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(NativeEntity entity) {
        return TEXTURES[entity.tribe().ordinal()][entity.role().ordinal()][entity.variant()];
    }

    @Override
    protected void scale(NativeEntity entity, PoseStack poseStack, float partialTick) {
        float scale = entity.role() == NativeEntity.Role.CHIEF ? 1.06F : 0.9375F;
        poseStack.scale(scale, scale, scale);
    }
}
