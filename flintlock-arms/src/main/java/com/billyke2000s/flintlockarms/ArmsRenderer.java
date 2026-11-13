package com.billyke2000s.flintlockarms;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the player's own arms in first person while the Server Flintlock is held in the main hand.
 *
 * The gun is a carrot on a stick whose custom model data is switched by the server every tick to play
 * its animations (7301/7311 idle, 7400-7424 shot, 7500-7689 inspect, 7700-7734 reload). Each frame's
 * model carries its own first-person transform, so the gun hand is placed by running the exact same
 * transform chain the game uses for the held item and putting the hand on the pistol grip. During the
 * reload the other hand comes up, rides the end of the ramrod while it is rammed, then drops away.
 *
 * Positions are worked out in the event's pose space: we find where vanilla's own first-person arm
 * pose would put the hand, and shift the arm by the difference so the hand lands on the target.
 */
@EventBusSubscriber(modid = FlintlockArms.MODID, value = Dist.CLIENT)
public final class ArmsRenderer {
    /** The pistol grip in the gun model (model pixels). */
    private static final Vector3f GRIP = new Vector3f(8.13F, 6.54F, 12.97F);
    /** Height and side of the ramrod in the reload frames (model pixels); its end slides along z. */
    private static final float ROD_X = 8.0F;
    private static final float ROD_Y = 9.24F;
    /** Outer (held) end of the ramrod in reload frames 7708..7722. */
    private static final float[] ROD_END_Z = {-5.9533F, -4.8367F, -3.72F, -2.6033F, -1.4867F, -2.0F, -3.6F, -5.2F,
            -3.8133F, -2.4267F, -1.04F, -2.38F, -3.72F, -5.06F, -6.4F};
    private static final int RELOAD_START = 7700;
    private static final int ROD_START = 7708;
    private static final int ROD_END = 7722;
    private static final int RELOAD_END = 7734;
    /** Where the hand is measured on the arm model (wide arms: bottom centre of the arm box). */
    private static final float HAND_X = 6.0F / 16.0F;
    private static final float HAND_Y = 12.0F / 16.0F;

    private ArmsRenderer() {
    }

    private static int frameOf(ItemStack stack) {
        if (!stack.is(Items.CARROT_ON_A_STICK)) {
            return -1;
        }
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (data == null) {
            return -1;
        }
        int v = data.value();
        boolean gun = v == 7301 || v == 7311 || (v >= 7400 && v <= 7799);
        return gun ? v : -1;
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isInvisible()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        int frame = frameOf(stack);
        if (frame < 0) {
            return;
        }
        HumanoidArm gunArm = player.getMainArm();
        HumanoidArm otherArm = gunArm.getOpposite();
        float equip = event.getEquipProgress();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();

        Matrix4f item = itemMatrix(mc, player, stack, gunArm, equip);

        // Gun hand on the grip.
        Vector3f grip = item.transformPosition(px(GRIP), new Vector3f());
        renderArm(mc, player, poseStack, buffers, light, gunArm, grip);

        // Support hand during the reload.
        if (frame >= RELOAD_START && frame <= RELOAD_END) {
            Vector3f rodStart = item.transformPosition(px(new Vector3f(ROD_X, ROD_Y, ROD_END_Z[0])), new Vector3f());
            Vector3f rest = handPoint(otherArm).add(0.0F, -0.8F, 0.0F);
            Vector3f target;
            if (frame < ROD_START) {
                float t = ease((frame - RELOAD_START + 1) / (float) (ROD_START - RELOAD_START + 1));
                target = lerp(rest, rodStart, t);
            } else if (frame <= ROD_END) {
                target = item.transformPosition(px(new Vector3f(ROD_X, ROD_Y, ROD_END_Z[frame - ROD_START])), new Vector3f());
            } else {
                Vector3f rodLast = item.transformPosition(px(new Vector3f(ROD_X, ROD_Y, ROD_END_Z[ROD_END_Z.length - 1])), new Vector3f());
                float t = ease((frame - ROD_END) / (float) (RELOAD_END - ROD_END));
                target = lerp(rodLast, rest, t);
            }
            renderArm(mc, player, poseStack, buffers, light, otherArm, target);
        }
    }

    private static Vector3f px(Vector3f modelPixels) {
        return new Vector3f(modelPixels).div(16.0F);
    }

    private static float ease(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return t * t * (3.0F - 2.0F * t);
    }

    private static Vector3f lerp(Vector3f a, Vector3f b, float t) {
        return new Vector3f(a).lerp(b, t);
    }

    /**
     * The transform the game applies to the held item in first person (no swing), ending in the
     * gun model's own 0..1 block space.
     */
    private static Matrix4f itemMatrix(Minecraft mc, AbstractClientPlayer player, ItemStack stack, HumanoidArm arm, float equip) {
        boolean right = arm == HumanoidArm.RIGHT;
        float side = right ? 1.0F : -1.0F;
        PoseStack ps = new PoseStack();
        ps.translate(side * 0.56F, -0.52F + equip * -0.6F, -0.72F);
        BakedModel model = mc.getItemRenderer().getModel(stack, player.level(), player, player.getId());
        ItemDisplayContext context = right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        model.applyTransform(context, ps, !right);
        ps.translate(-0.5F, -0.5F, -0.5F);
        return new Matrix4f(ps.last().pose());
    }

    /** Vanilla's empty-hand first-person arm pose (no swing, fully equipped), as a fresh pose stack. */
    private static void armPose(PoseStack ps, HumanoidArm arm) {
        float f = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        ps.translate(f * 0.64000005F, -0.6F, -0.71999997F);
        ps.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
        ps.translate(f * -1.0F, 3.6F, 3.5F);
        ps.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        ps.mulPose(Axis.XP.rotationDegrees(200.0F));
        ps.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        ps.translate(f * 5.6F, 0.0F, 0.0F);
    }

    /** Where vanilla's arm pose puts the hand (bottom of the arm box, below the shoulder pivot). */
    private static Vector3f handPoint(HumanoidArm arm) {
        float f = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        PoseStack ps = new PoseStack();
        armPose(ps, arm);
        // Arm pivot is at (-5 or +5, 2, 0) px; the hand is 10 px further down the arm, centred 1 px outward.
        Vector3f local = new Vector3f(-f * HAND_X, HAND_Y, 0.0F);
        return ps.last().pose().transformPosition(local, new Vector3f());
    }

    /** Draw the player's arm in vanilla's pose, shifted so its hand lands on {@code target}. */
    private static void renderArm(Minecraft mc, AbstractClientPlayer player, PoseStack poseStack, MultiBufferSource buffers,
                                  int light, HumanoidArm arm, Vector3f target) {
        Vector3f hand = handPoint(arm);
        poseStack.pushPose();
        poseStack.translate(target.x - hand.x, target.y - hand.y, target.z - hand.z);
        armPose(poseStack, arm);
        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        if (arm == HumanoidArm.RIGHT) {
            renderer.renderRightHand(poseStack, buffers, light, player);
        } else {
            renderer.renderLeftHand(poseStack, buffers, light, player);
        }
        poseStack.popPose();
    }
}
