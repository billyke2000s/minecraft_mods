package com.billyke2000s.whip.client;

import com.billyke2000s.whip.WhipLashEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Draws the lash as a tapering, braided black leather thong from the owner's hand to its tip. */
public class WhipLashRenderer extends EntityRenderer<WhipLashEntity> {
    private static final int SEGMENTS = 32;
    private static final float HANDLE_WIDTH = 0.045F;
    private static final float TIP_WIDTH = 0.012F;

    public WhipLashRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(WhipLashEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(WhipLashEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    public void render(WhipLashEntity lash, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        Entity owner = lash.owner();
        if (owner == null) {
            return;
        }
        float unroll = lash.unroll(partialTick);
        float slack = lash.slack(partialTick);
        if (slack >= 0.999F) {
            return;
        }
        Vec3 hand = handPosition(owner, partialTick);
        Vec3 origin = new Vec3(Mth.lerp(partialTick, lash.xo, lash.getX()), Mth.lerp(partialTick, lash.yo, lash.getY()),
                Mth.lerp(partialTick, lash.zo, lash.getZ()));

        Vec3 dir = lash.target().subtract(hand);
        double length = dir.length();
        if (length < 1.0E-3) {
            return;
        }
        Vec3 forward = dir.scale(1.0 / length);
        Vec3 side = forward.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(forward).normalize();

        // A real lash unrolls: the part already thrown lies out in front, the rest is folded back above it
        // in a loop that rolls forward until the tip snaps straight. After the crack it sags and drops.
        double fold = length * unroll;
        double loopRadius = 0.18 + 0.25 * (1.0 - unroll);
        Vec3[] points = new Vec3[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            double s = length * i / SEGMENTS;
            Vec3 p;
            if (s <= fold) {
                double t = fold <= 0 ? 0 : s / length;
                p = hand.add(forward.scale(s)).add(up.scale(Math.sin(Math.PI * t) * 0.15 * (1.0 - unroll)));
            } else {
                double back = s - fold;
                double arc = Math.PI * loopRadius;
                Vec3 turn = hand.add(forward.scale(fold));
                if (back < arc) {
                    double a = back / loopRadius;
                    p = turn.add(forward.scale(loopRadius * Math.sin(a))).add(up.scale(loopRadius * (1.0 - Math.cos(a))));
                } else {
                    p = turn.subtract(forward.scale(back - arc)).add(up.scale(2.0 * loopRadius));
                }
            }
            double t = (double) i / SEGMENTS;
            double drop = slack * slack * Math.sin(Math.PI * 0.5 * t) * Math.min(2.5, length * 0.45);
            double sag = Math.sin(Math.PI * t) * 0.08;
            points[i] = p.add(0, -(drop + sag), 0).subtract(origin);
        }

        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = buffers.getBuffer(RenderType.leash());
        // Flat strip one way, then back along the other axis, so it reads as a round cord from any angle.
        for (int i = 0; i <= SEGMENTS; i++) {
            addPair(consumer, matrix, points[i], side, width(i), i, light);
        }
        for (int i = SEGMENTS; i >= 0; i--) {
            addPair(consumer, matrix, points[i], up, width(i), i, light);
        }
        poseStack.popPose();
        super.render(lash, yaw, partialTick, poseStack, buffers, light);
    }

    private static float width(int i) {
        float t = (float) i / SEGMENTS;
        return Mth.lerp(t, HANDLE_WIDTH, TIP_WIDTH);
    }

    private static void addPair(VertexConsumer consumer, Matrix4f matrix, Vec3 p, Vec3 offset, float width, int i, int light) {
        // Alternate shades every segment for a plaited-leather look.
        float shade = (i % 2 == 0) ? 0.07F : 0.16F;
        float ox = (float) (offset.x * width);
        float oy = (float) (offset.y * width);
        float oz = (float) (offset.z * width);
        consumer.addVertex(matrix, (float) p.x + ox, (float) p.y + oy, (float) p.z + oz).setColor(shade, shade, shade + 0.01F, 1.0F).setLight(light);
        consumer.addVertex(matrix, (float) p.x - ox, (float) p.y - oy, (float) p.z - oz).setColor(shade, shade, shade + 0.01F, 1.0F).setLight(light);
    }

    /** Roughly where the owner's whip hand is, in world space. */
    private static Vec3 handPosition(Entity owner, float partialTick) {
        Vec3 eye = owner.getEyePosition(partialTick);
        Vec3 look = owner.getViewVector(partialTick);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        int side = owner instanceof LivingEntity living && living.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
        boolean firstPerson = owner == Minecraft.getInstance().getCameraEntity()
                && Minecraft.getInstance().options.getCameraType().isFirstPerson();
        double out = firstPerson ? 0.55 : 0.45;
        double across = firstPerson ? 0.3 : 0.4;
        double down = firstPerson ? 0.3 : 0.55;
        return eye.add(look.scale(out)).add(right.scale(across * side)).add(0, -down, 0);
    }
}
