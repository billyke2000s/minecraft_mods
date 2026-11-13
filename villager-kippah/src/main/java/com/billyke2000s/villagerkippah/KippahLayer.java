package com.billyke2000s.villagerkippah;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

/**
 * A kippah on the crown of the villager's head and peyot hanging in front of the ears. Attached to the
 * head, so it follows every nod and turn (and scales down on baby villagers).
 *
 * The villager head is a box from (-4, -10, -4) to (4, 0, 4) in model pixels, face towards -z.
 */
public class KippahLayer extends RenderLayer<Villager, VillagerModel<Villager>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(VillagerKippah.MODID, "textures/entity/kippah.png");

    private final ModelPart root;

    public KippahLayer(RenderLayerParent<Villager, VillagerModel<Villager>> parent, ModelPart root) {
        super(parent);
        this.root = root;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();
        // Kippah: a small round-ish cap sitting on the back of the crown.
        parts.addOrReplaceChild("kippah", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.5F, -10.6F, -1.0F, 5.0F, 1.0F, 5.0F)
                        .texOffs(0, 6).addBox(-1.5F, -10.9F, 0.0F, 3.0F, 0.5F, 3.0F),
                PartPose.ZERO);
        // Peyot: a curl hanging in front of each ear, down past the jaw.
        parts.addOrReplaceChild("peyot_right", CubeListBuilder.create()
                        .texOffs(0, 10).addBox(-4.8F, -6.0F, -2.2F, 1.0F, 7.5F, 1.0F),
                PartPose.ZERO);
        parts.addOrReplaceChild("peyot_left", CubeListBuilder.create()
                        .texOffs(4, 10).addBox(3.8F, -6.0F, -2.2F, 1.0F, 7.5F, 1.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, Villager villager,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (villager.isInvisible()) {
            return;
        }
        poseStack.pushPose();
        getParentModel().getHead().translateAndRotate(poseStack);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        root.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
