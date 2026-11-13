package com.billyke2000s.flintlockarms;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.math.Axis;
import net.minecraft.world.item.component.CustomModelData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the player's own arms in first person while the Server Flintlock is held in the main hand,
 * posed like the arm in the original Blockbench model (flintlock-pistol.bbmodel).
 *
 * The gun is a carrot on a stick whose custom model data the server switches every tick to play its
 * animations (7301/7311 idle, 7400-7424 shot, 7500-7689 inspect, 7700-7734 reload). Each frame's model
 * carries its own first-person transform. The hand is pinned to the gun in that frame's transform, while
 * the shoulder stays where it is in the idle pose, so the arm pivots naturally from the shoulder.
 *
 * Coordinates are the resource pack's model pixels. The pack is the Blockbench model scaled by 1.6 and
 * moved by (8, 5, 11.2); the Blockbench arm's hand sits under the gun at the trigger, and its shoulder
 * is behind and a little below.
 */
@EventBusSubscriber(modid = FlintlockArms.MODID, value = Dist.CLIENT)
public final class ArmsRenderer {
    private static final float MODEL_SCALE = 1.6F;
    /** Gun hand and shoulder from the Blockbench idle pose, in pack pixels. */
    private static final Vector3f GUN_HAND = new Vector3f(7.84F, 4.95F, 9.10F);
    private static final Vector3f GUN_SHOULDER = new Vector3f(9.92F, 3.72F, 26.56F);
    /** The other shoulder: one shoulder-width (10 px at 1.6x) across. */
    private static final Vector3f OTHER_SHOULDER = new Vector3f(9.92F - 16.0F, 3.72F, 26.56F);
    /** Where the other hand waits out of sight before and after the reload. */
    private static final Vector3f OTHER_REST = new Vector3f(-6.08F, -12.0F, 18.0F);

    /** Ramrod in the reload frames (pack pixels); its outer end slides along z. */
    private static final float ROD_X = 8.0F;
    private static final float ROD_Y = 9.24F;
    private static final float[] ROD_END_Z = {-5.9533F, -4.8367F, -3.72F, -2.6033F, -1.4867F, -2.0F, -3.6F, -5.2F,
            -3.8133F, -2.4267F, -1.04F, -2.38F, -3.72F, -5.06F, -6.4F};
    /** The muzzle mouth (where the rod enters) and a point just above it, in pack pixels. */
    private static final Vector3f MUZZLE = new Vector3f(8.0F, 9.24F, 1.0F);
    private static final Vector3f MUZZLE_ABOVE = new Vector3f(8.0F, 13.5F, -1.5F);
    /** Reload frame (counted from the start) at which the ball is let go into the muzzle. */
    private static final int BALL_DROP_FRAME = 5;
    private static final int IDLE = 7301;
    private static final int BALL_MODEL_DATA = 7302;
    private static final int RELOAD_START = 7700;
    private static final int ROD_START = 7708;
    private static final int ROD_END = 7722;
    private static final int RELOAD_END = 7734;

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
        return (v == 7301 || v == 7311 || (v >= 7400 && v <= 7799)) ? v : -1;
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

        ItemStack idleStack = stack.copy();
        idleStack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(IDLE));
        Matrix4f idle = itemMatrix(mc, player, idleStack, gunArm, equip);
        Matrix4f now = itemMatrix(mc, player, stack, gunArm, equip);

        // Gun arm: hand on the gun in this frame, shoulder where it rests.
        renderArm(mc, player, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), gunArm,
                at(now, GUN_HAND), at(idle, GUN_SHOULDER));

        // Other arm does the reload: brings a musket ball to the muzzle and drops it in, then grips the
        // ramrod and rams it home, then lets go and drops away.
        if (frame >= RELOAD_START && frame <= RELOAD_END) {
            Vector3f rest = at(idle, OTHER_REST);
            Vector3f hand;
            if (frame < ROD_START) {
                // Reach up to just above the muzzle with the ball, then dip as it is dropped in.
                Vector3f aboveMuzzle = at(now, MUZZLE_ABOVE);
                Vector3f atMuzzle = at(now, MUZZLE);
                int step = frame - RELOAD_START;
                if (step < BALL_DROP_FRAME) {
                    hand = new Vector3f(rest).lerp(aboveMuzzle, ease((step + 1) / (float) BALL_DROP_FRAME));
                } else {
                    hand = new Vector3f(aboveMuzzle).lerp(atMuzzle, ease((step - BALL_DROP_FRAME + 1)
                            / (float) (ROD_START - RELOAD_START - BALL_DROP_FRAME)));
                }
                if (step <= BALL_DROP_FRAME) {
                    renderBall(mc, player, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), hand);
                }
            } else if (frame <= ROD_END) {
                hand = at(now, rod(frame - ROD_START));
            } else {
                float t = ease((frame - ROD_END) / (float) (RELOAD_END - ROD_END));
                hand = at(now, rod(ROD_END_Z.length - 1)).lerp(rest, t);
            }
            renderArm(mc, player, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), otherArm,
                    hand, at(idle, OTHER_SHOULDER));
        }
    }

    /** The musket ball held between the fingers, drawn at the hand. */
    private static void renderBall(Minecraft mc, AbstractClientPlayer player, PoseStack poseStack, MultiBufferSource buffers,
                                   int light, Vector3f hand) {
        ItemStack ball = new ItemStack(Items.IRON_NUGGET);
        ball.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(BALL_MODEL_DATA));
        poseStack.pushPose();
        poseStack.translate(hand.x, hand.y + 0.03F, hand.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(45.0F));
        poseStack.scale(0.3F, 0.3F, 0.3F);
        mc.getItemRenderer().renderStatic(ball, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, poseStack,
                buffers, player.level(), 0);
        poseStack.popPose();
    }

    private static Vector3f rod(int index) {
        return new Vector3f(ROD_X, ROD_Y, ROD_END_Z[index]);
    }

    private static Vector3f at(Matrix4f item, Vector3f packPixels) {
        return item.transformPosition(new Vector3f(packPixels).div(16.0F), new Vector3f());
    }

    private static float ease(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return t * t * (3.0F - 2.0F * t);
    }

    /** The transform the game applies to a held item in first person (no swing), into model 0..1 space. */
    private static Matrix4f itemMatrix(Minecraft mc, AbstractClientPlayer player, ItemStack stack, HumanoidArm arm, float equip) {
        boolean right = arm == HumanoidArm.RIGHT;
        PoseStack ps = new PoseStack();
        ps.translate((right ? 1.0F : -1.0F) * 0.56F, -0.52F + equip * -0.6F, -0.72F);
        BakedModel model = mc.getItemRenderer().getModel(stack, player.level(), player, player.getId());
        model.applyTransform(right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                ps, !right);
        ps.translate(-0.5F, -0.5F, -0.5F);
        return new Matrix4f(ps.last().pose());
    }

    /** Draw the player's skinned arm with its hand at {@code hand}, pointing back towards {@code shoulder}. */
    private static void renderArm(Minecraft mc, AbstractClientPlayer player, PoseStack poseStack, MultiBufferSource buffers,
                                  int light, HumanoidArm side, Vector3f hand, Vector3f shoulder) {
        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        boolean right = side == HumanoidArm.RIGHT;
        ModelPart arm = right ? model.rightArm : model.leftArm;
        ModelPart sleeve = right ? model.rightSleeve : model.leftSleeve;
        PlayerSkin skin = player.getSkin();
        boolean slim = skin.model() == PlayerSkin.Model.SLIM;
        // The arm box hangs from its pivot along +y; the hand is 10 px down, centred across the arm.
        float centre = slim ? 0.5F : 1.0F;
        Vector3f handLocal = new Vector3f(right ? -centre : centre, 10.0F, 0.0F);

        Vector3f along = new Vector3f(hand).sub(shoulder);
        if (along.lengthSquared() < 1.0E-8F) {
            return;
        }
        along.normalize();

        poseStack.pushPose();
        poseStack.translate(hand.x, hand.y, hand.z);
        poseStack.mulPose(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), along));
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        poseStack.translate(-handLocal.x / 16.0F, -handLocal.y / 16.0F, -handLocal.z / 16.0F);

        PartPose armPose = arm.storePose();
        PartPose sleevePose = sleeve.storePose();
        boolean sleeveVisible = sleeve.visible;
        arm.setPos(0.0F, 0.0F, 0.0F);
        arm.setRotation(0.0F, 0.0F, 0.0F);
        sleeve.setPos(0.0F, 0.0F, 0.0F);
        sleeve.setRotation(0.0F, 0.0F, 0.0F);
        sleeve.visible = player.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);

        ResourceLocation texture = skin.texture();
        arm.render(poseStack, buffers.getBuffer(RenderType.entitySolid(texture)), light, OverlayTexture.NO_OVERLAY);
        sleeve.render(poseStack, buffers.getBuffer(RenderType.entityTranslucent(texture)), light, OverlayTexture.NO_OVERLAY);

        arm.loadPose(armPose);
        sleeve.loadPose(sleevePose);
        sleeve.visible = sleeveVisible;
        poseStack.popPose();
    }
}
