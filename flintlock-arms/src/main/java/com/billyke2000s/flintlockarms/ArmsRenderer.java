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

import java.util.HashMap;
import java.util.Map;

/**
 * Draws the player's own arms in first person while the Server Flintlock is held in the main hand,
 * posed like the arm in the original Blockbench model (flintlock-pistol.bbmodel).
 *
 * The gun is a carrot on a stick whose custom model data the server switches every tick to play its
 * animations (7301/7311 idle, 7400-7424 shot, 7500-7689 inspect, 7700-7734 reload). Each frame's model
 * carries its own first-person transform. The hand is pinned to the gun in that frame's transform, while
 * the shoulder stays where it is in the idle pose, so the arm pivots naturally from the shoulder.
 *
 * For the idle, idle_empty, shot and inspect frames the gun arm follows the designer's own arm keyframes
 * from the Blockbench model, sampled at the same 20 frames per second the server plays the gun at (see
 * {@link #BLOCKBENCH_POSES}). The reload frames use this mod's own two-handed reload instead.
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

    /**
     * The Blockbench "right" arm bone for each gun frame: {frame, x, y, z, qx, qy, qz, qw}. Applied after the
     * idle frame's item transform, it maps the vanilla arm part (model space) onto the Blockbench arm, then
     * scale by {@link #MODEL_SCALE}. Generated from flintlock-pistol.bbmodel: bone rotations Rz*Ry*Rx in
     * degrees as stored, catmull-rom/linear keyframes, expressed relative to the pistol bone's idle pose
     * (which is what the pack's idle display already contains).
     */
    private static final float[][] BLOCKBENCH_POSE_ROWS = {
            {7301, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7311, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7400, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7401, 0.52015F, 0.18262F, 1.56827F, -0.04712F, 0.75032F, -0.65912F, -0.01928F},
            {7402, 0.51674F, 0.15717F, 1.56859F, -0.05551F, 0.76628F, -0.64003F, -0.00983F},
            {7403, 0.51721F, 0.16064F, 1.56853F, -0.05434F, 0.76414F, -0.64266F, -0.01109F},
            {7404, 0.51813F, 0.16740F, 1.56843F, -0.05208F, 0.75994F, -0.64777F, -0.01358F},
            {7405, 0.51934F, 0.17646F, 1.56833F, -0.04911F, 0.75424F, -0.65454F, -0.01695F},
            {7406, 0.52069F, 0.18680F, 1.56825F, -0.04579F, 0.74764F, -0.66220F, -0.02087F},
            {7407, 0.52206F, 0.19744F, 1.56821F, -0.04247F, 0.74075F, -0.66998F, -0.02498F},
            {7408, 0.52331F, 0.20737F, 1.56821F, -0.03945F, 0.73422F, -0.67714F, -0.02888F},
            {7409, 0.52432F, 0.21560F, 1.56825F, -0.03701F, 0.72876F, -0.68302F, -0.03216F},
            {7410, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7411, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7412, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7413, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7414, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7415, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7416, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7417, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7418, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7419, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7420, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7421, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7422, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7423, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7424, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7500, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7501, 0.54625F, 0.21907F, 1.56834F, -0.02563F, 0.72531F, -0.68638F, -0.04633F},
            {7502, 0.57879F, 0.21566F, 1.56855F, -0.01216F, 0.72560F, -0.68503F, -0.06392F},
            {7503, 0.61586F, 0.21200F, 1.56871F, 0.00385F, 0.72554F, -0.68302F, -0.08405F},
            {7504, 0.65058F, 0.20911F, 1.56864F, 0.02120F, 0.72482F, -0.68077F, -0.10365F},
            {7505, 0.67592F, 0.20799F, 1.56812F, 0.03870F, 0.72322F, -0.67906F, -0.11972F},
            {7506, 0.68474F, 0.20962F, 1.56696F, 0.05519F, 0.72065F, -0.67889F, -0.12936F},
            {7507, 0.67389F, 0.21458F, 1.56486F, 0.07291F, 0.71688F, -0.68061F, -0.13240F},
            {7508, 0.64898F, 0.22192F, 1.56210F, 0.09218F, 0.71190F, -0.68375F, -0.13109F},
            {7509, 0.61672F, 0.23049F, 1.55915F, 0.11132F, 0.70593F, -0.68781F, -0.12725F},
            {7510, 0.58382F, 0.23916F, 1.55643F, 0.12867F, 0.69941F, -0.69226F, -0.12269F},
            {7511, 0.55693F, 0.24689F, 1.55430F, -0.14258F, -0.69295F, 0.69662F, 0.11917F},
            {7512, 0.54006F, 0.25406F, 1.55285F, -0.15269F, -0.68571F, 0.70178F, 0.11832F},
            {7513, 0.53584F, 0.26215F, 1.55252F, -0.15413F, -0.67513F, 0.71135F, 0.12013F},
            {7514, 0.54173F, 0.26884F, 1.55314F, -0.14854F, -0.66667F, 0.72005F, 0.12248F},
            {7515, 0.54667F, 0.27432F, 1.55358F, -0.14489F, -0.66422F, 0.72290F, 0.12338F},
            {7516, 0.54798F, 0.27814F, 1.55339F, -0.14961F, -0.67211F, 0.71435F, 0.12487F},
            {7517, 0.55022F, 0.28434F, 1.55322F, -0.15650F, -0.68626F, 0.69910F, 0.12572F},
            {7518, 0.55134F, 0.28821F, 1.55327F, 0.15523F, 0.69869F, -0.68800F, -0.11996F},
            {7519, 0.54911F, 0.28459F, 1.55346F, 0.13272F, 0.70298F, -0.69162F, -0.09928F},
            {7520, 0.54511F, 0.27321F, 1.55377F, -0.08895F, -0.69958F, 0.70637F, 0.06093F},
            {7521, 0.54326F, 0.26302F, 1.55410F, -0.05115F, -0.69351F, 0.71816F, 0.02597F},
            {7522, 0.54344F, 0.26427F, 1.55413F, -0.04515F, -0.69530F, 0.71702F, 0.01998F},
            {7523, 0.54574F, 0.27983F, 1.55386F, 0.07796F, 0.70886F, -0.69930F, -0.04912F},
            {7524, 0.55126F, 0.29939F, 1.55375F, 0.12662F, 0.72133F, -0.67513F, -0.08859F},
            {7525, 0.55647F, 0.31219F, 1.55370F, 0.16159F, 0.72355F, -0.66101F, -0.11585F},
            {7526, 0.55799F, 0.31634F, 1.55347F, 0.17378F, 0.71611F, -0.66373F, -0.12824F},
            {7527, 0.55855F, 0.31848F, 1.55316F, 0.18350F, 0.70252F, -0.67285F, -0.14166F},
            {7528, 0.55777F, 0.31837F, 1.55299F, 0.18599F, 0.68785F, -0.68535F, -0.15025F},
            {7529, 0.55559F, 0.31564F, 1.55304F, -0.17718F, -0.67730F, 0.69864F, 0.14755F},
            {7530, 0.55236F, 0.30971F, 1.55326F, -0.15312F, -0.67568F, 0.70987F, 0.12684F},
            {7531, 0.54970F, 0.30296F, 1.55348F, -0.12320F, -0.68083F, 0.71542F, 0.09724F},
            {7532, 0.54717F, 0.29266F, 1.55373F, -0.08041F, -0.68843F, 0.71880F, 0.05415F},
            {7533, 0.54586F, 0.27977F, 1.55399F, -0.02898F, -0.69526F, 0.71817F, 0.00258F},
            {7534, 0.54643F, 0.26553F, 1.55424F, 0.02652F, -0.69900F, 0.71272F, -0.05223F},
            {7535, 0.54887F, 0.25127F, 1.55445F, 0.08111F, -0.69871F, 0.70297F, -0.10512F},
            {7536, 0.55253F, 0.23823F, 1.55462F, -0.12971F, 0.69494F, -0.69089F, 0.15133F},
            {7537, 0.55628F, 0.22735F, 1.55474F, -0.16733F, 0.68952F, -0.67951F, 0.18661F},
            {7538, 0.55936F, 0.21833F, 1.55482F, -0.19392F, 0.68402F, -0.67067F, 0.21148F},
            {7539, 0.56044F, 0.21207F, 1.55486F, -0.20478F, 0.68141F, -0.66681F, 0.22162F},
            {7540, 0.55933F, 0.20811F, 1.55486F, -0.20116F, 0.68230F, -0.66811F, 0.21824F},
            {7541, 0.55719F, 0.20468F, 1.55484F, -0.19095F, 0.68470F, -0.67169F, 0.20870F},
            {7542, 0.55517F, 0.19999F, 1.55483F, -0.18202F, 0.68666F, -0.67471F, 0.20035F},
            {7543, 0.55339F, 0.18222F, 1.55494F, -0.18046F, 0.68376F, -0.67808F, 0.20028F},
            {7544, 0.55149F, 0.14138F, 1.55559F, 0.17998F, -0.66809F, 0.69176F, -0.20672F},
            {7545, 0.54664F, 0.09363F, 1.55629F, 0.17979F, -0.66305F, 0.69602F, -0.20878F},
            {7546, 0.53538F, 0.04889F, 1.55647F, -0.18063F, 0.69215F, -0.67050F, 0.19681F},
            {7547, 0.52139F, 0.00574F, 1.55762F, -0.18063F, 0.73640F, -0.62714F, 0.17826F},
            {7548, 0.50586F, -0.03247F, 1.56146F, -0.17797F, 0.79296F, -0.56202F, 0.15389F},
            {7549, 0.49154F, -0.06628F, 1.56888F, -0.17161F, 0.84885F, -0.48310F, 0.12889F},
            {7550, 0.48016F, -0.09630F, 1.57876F, -0.16251F, 0.89468F, -0.40197F, 0.10751F},
            {7551, 0.47227F, -0.12225F, 1.58816F, -0.15323F, 0.92576F, -0.33312F, 0.09232F},
            {7552, 0.46709F, -0.14698F, 1.59298F, -0.14688F, 0.94149F, -0.29142F, 0.08431F},
            {7553, 0.46366F, -0.16914F, 1.59065F, -0.14388F, 0.94691F, -0.27572F, 0.08148F},
            {7554, 0.46237F, -0.18002F, 1.58363F, -0.14330F, 0.94640F, -0.27768F, 0.08174F},
            {7555, 0.46248F, -0.18308F, 1.57449F, -0.14423F, 0.94238F, -0.29000F, 0.08380F},
            {7556, 0.46330F, -0.18181F, 1.56590F, -0.14571F, 0.93706F, -0.30531F, 0.08648F},
            {7557, 0.46410F, -0.17960F, 1.56042F, -0.14684F, 0.93301F, -0.31638F, 0.08848F},
            {7558, 0.46401F, -0.17988F, 1.56062F, -0.14781F, 0.93303F, -0.31573F, 0.08904F},
            {7559, 0.46273F, -0.18337F, 1.56736F, -0.15258F, 0.93694F, -0.30117F, 0.09038F},
            {7560, 0.46154F, -0.18459F, 1.57738F, -0.15826F, 0.94146F, -0.28324F, 0.09156F},
            {7561, 0.46196F, -0.17729F, 1.58697F, -0.16059F, 0.94386F, -0.27384F, 0.09143F},
            {7562, 0.46549F, -0.15508F, 1.59232F, -0.15528F, 0.94170F, -0.28492F, 0.08892F},
            {7563, 0.47161F, -0.12193F, 1.59124F, -0.14276F, 0.93392F, -0.31686F, 0.08378F},
            {7564, 0.47886F, -0.08357F, 1.58735F, -0.12718F, 0.92155F, -0.35881F, 0.07627F},
            {7565, 0.48757F, -0.03663F, 1.58210F, -0.10844F, 0.90244F, -0.41186F, 0.06494F},
            {7566, 0.49669F, 0.01488F, 1.57672F, -0.08908F, 0.87650F, -0.47046F, 0.04981F},
            {7567, 0.50526F, 0.06736F, 1.57221F, -0.07129F, 0.84481F, -0.52935F, 0.03163F},
            {7568, 0.51264F, 0.11759F, 1.56919F, -0.05660F, 0.80971F, -0.58397F, 0.01186F},
            {7569, 0.51847F, 0.16257F, 1.56781F, -0.04578F, 0.77470F, -0.63063F, -0.00751F},
            {7570, 0.52269F, 0.19923F, 1.56776F, -0.03876F, 0.74402F, -0.66660F, -0.02419F},
            {7571, 0.52486F, 0.22064F, 1.56842F, -0.03494F, 0.72216F, -0.68991F, -0.03593F},
            {7572, 0.52424F, 0.21848F, 1.56911F, -0.03312F, 0.70933F, -0.70280F, -0.04278F},
            {7573, 0.52393F, 0.21749F, 1.56947F, 0.03236F, -0.70334F, 0.70863F, 0.04595F},
            {7574, 0.52390F, 0.21740F, 1.56951F, 0.03229F, -0.70277F, 0.70918F, 0.04626F},
            {7575, 0.52407F, 0.21793F, 1.56931F, 0.03269F, -0.70602F, 0.70603F, 0.04453F},
            {7576, 0.52434F, 0.21884F, 1.56899F, -0.03340F, 0.71148F, -0.70068F, -0.04163F},
            {7577, 0.52464F, 0.21985F, 1.56866F, -0.03425F, 0.71750F, -0.69466F, -0.03842F},
            {7578, 0.52488F, 0.22070F, 1.56841F, -0.03500F, 0.72251F, -0.68954F, -0.03574F},
            {7579, 0.52499F, 0.22113F, 1.56829F, -0.03539F, 0.72499F, -0.68699F, -0.03442F},
            {7580, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7581, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7582, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7583, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7584, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7585, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7586, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7587, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7588, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7589, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7600, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7601, 0.54625F, 0.21907F, 1.56834F, -0.02563F, 0.72531F, -0.68638F, -0.04633F},
            {7602, 0.57879F, 0.21566F, 1.56855F, -0.01216F, 0.72560F, -0.68503F, -0.06392F},
            {7603, 0.61586F, 0.21200F, 1.56871F, 0.00385F, 0.72554F, -0.68302F, -0.08405F},
            {7604, 0.65058F, 0.20911F, 1.56864F, 0.02120F, 0.72482F, -0.68077F, -0.10365F},
            {7605, 0.67592F, 0.20799F, 1.56812F, 0.03870F, 0.72322F, -0.67906F, -0.11972F},
            {7606, 0.68474F, 0.20962F, 1.56696F, 0.05519F, 0.72065F, -0.67889F, -0.12936F},
            {7607, 0.67389F, 0.21458F, 1.56486F, 0.07291F, 0.71688F, -0.68061F, -0.13240F},
            {7608, 0.64898F, 0.22192F, 1.56210F, 0.09218F, 0.71190F, -0.68375F, -0.13109F},
            {7609, 0.61672F, 0.23049F, 1.55915F, 0.11132F, 0.70593F, -0.68781F, -0.12725F},
            {7610, 0.58382F, 0.23916F, 1.55643F, 0.12867F, 0.69941F, -0.69226F, -0.12269F},
            {7611, 0.55693F, 0.24689F, 1.55430F, -0.14258F, -0.69295F, 0.69662F, 0.11917F},
            {7612, 0.54006F, 0.25406F, 1.55285F, -0.15269F, -0.68571F, 0.70178F, 0.11832F},
            {7613, 0.53584F, 0.26215F, 1.55252F, -0.15413F, -0.67513F, 0.71135F, 0.12013F},
            {7614, 0.54173F, 0.26884F, 1.55314F, -0.14854F, -0.66667F, 0.72005F, 0.12248F},
            {7615, 0.54667F, 0.27432F, 1.55358F, -0.14489F, -0.66422F, 0.72290F, 0.12338F},
            {7616, 0.54798F, 0.27814F, 1.55339F, -0.14961F, -0.67211F, 0.71435F, 0.12487F},
            {7617, 0.55022F, 0.28434F, 1.55322F, -0.15650F, -0.68626F, 0.69910F, 0.12572F},
            {7618, 0.55134F, 0.28821F, 1.55327F, 0.15523F, 0.69869F, -0.68800F, -0.11996F},
            {7619, 0.54911F, 0.28459F, 1.55346F, 0.13272F, 0.70298F, -0.69162F, -0.09928F},
            {7620, 0.54511F, 0.27321F, 1.55377F, -0.08895F, -0.69958F, 0.70637F, 0.06093F},
            {7621, 0.54326F, 0.26302F, 1.55410F, -0.05115F, -0.69351F, 0.71816F, 0.02597F},
            {7622, 0.54344F, 0.26427F, 1.55413F, -0.04515F, -0.69530F, 0.71702F, 0.01998F},
            {7623, 0.54574F, 0.27983F, 1.55386F, 0.07796F, 0.70886F, -0.69930F, -0.04912F},
            {7624, 0.55126F, 0.29939F, 1.55375F, 0.12662F, 0.72133F, -0.67513F, -0.08859F},
            {7625, 0.55647F, 0.31219F, 1.55370F, 0.16159F, 0.72355F, -0.66101F, -0.11585F},
            {7626, 0.55799F, 0.31634F, 1.55347F, 0.17378F, 0.71611F, -0.66373F, -0.12824F},
            {7627, 0.55855F, 0.31848F, 1.55316F, 0.18350F, 0.70252F, -0.67285F, -0.14166F},
            {7628, 0.55777F, 0.31837F, 1.55299F, 0.18599F, 0.68785F, -0.68535F, -0.15025F},
            {7629, 0.55559F, 0.31564F, 1.55304F, -0.17718F, -0.67730F, 0.69864F, 0.14755F},
            {7630, 0.55236F, 0.30971F, 1.55326F, -0.15312F, -0.67568F, 0.70987F, 0.12684F},
            {7631, 0.54970F, 0.30296F, 1.55348F, -0.12320F, -0.68083F, 0.71542F, 0.09724F},
            {7632, 0.54717F, 0.29266F, 1.55373F, -0.08041F, -0.68843F, 0.71880F, 0.05415F},
            {7633, 0.54586F, 0.27977F, 1.55399F, -0.02898F, -0.69526F, 0.71817F, 0.00258F},
            {7634, 0.54643F, 0.26553F, 1.55424F, 0.02652F, -0.69900F, 0.71272F, -0.05223F},
            {7635, 0.54887F, 0.25127F, 1.55445F, 0.08111F, -0.69871F, 0.70297F, -0.10512F},
            {7636, 0.55253F, 0.23823F, 1.55462F, -0.12971F, 0.69494F, -0.69089F, 0.15133F},
            {7637, 0.55628F, 0.22735F, 1.55474F, -0.16733F, 0.68952F, -0.67951F, 0.18661F},
            {7638, 0.55936F, 0.21833F, 1.55482F, -0.19392F, 0.68402F, -0.67067F, 0.21148F},
            {7639, 0.56044F, 0.21207F, 1.55486F, -0.20478F, 0.68141F, -0.66681F, 0.22162F},
            {7640, 0.55933F, 0.20811F, 1.55486F, -0.20116F, 0.68230F, -0.66811F, 0.21824F},
            {7641, 0.55719F, 0.20468F, 1.55484F, -0.19095F, 0.68470F, -0.67169F, 0.20870F},
            {7642, 0.55517F, 0.19999F, 1.55483F, -0.18202F, 0.68666F, -0.67471F, 0.20035F},
            {7643, 0.55339F, 0.18222F, 1.55494F, -0.18046F, 0.68376F, -0.67808F, 0.20028F},
            {7644, 0.55149F, 0.14138F, 1.55559F, 0.17998F, -0.66809F, 0.69176F, -0.20672F},
            {7645, 0.54664F, 0.09363F, 1.55629F, 0.17979F, -0.66305F, 0.69602F, -0.20878F},
            {7646, 0.53538F, 0.04889F, 1.55647F, -0.18063F, 0.69215F, -0.67050F, 0.19681F},
            {7647, 0.52139F, 0.00574F, 1.55762F, -0.18063F, 0.73640F, -0.62714F, 0.17826F},
            {7648, 0.50586F, -0.03247F, 1.56146F, -0.17797F, 0.79296F, -0.56202F, 0.15389F},
            {7649, 0.49154F, -0.06628F, 1.56888F, -0.17161F, 0.84885F, -0.48310F, 0.12889F},
            {7650, 0.48016F, -0.09630F, 1.57876F, -0.16251F, 0.89468F, -0.40197F, 0.10751F},
            {7651, 0.47227F, -0.12225F, 1.58816F, -0.15323F, 0.92576F, -0.33312F, 0.09232F},
            {7652, 0.46709F, -0.14698F, 1.59298F, -0.14688F, 0.94149F, -0.29142F, 0.08431F},
            {7653, 0.46366F, -0.16914F, 1.59065F, -0.14388F, 0.94691F, -0.27572F, 0.08148F},
            {7654, 0.46237F, -0.18002F, 1.58363F, -0.14330F, 0.94640F, -0.27768F, 0.08174F},
            {7655, 0.46248F, -0.18308F, 1.57449F, -0.14423F, 0.94238F, -0.29000F, 0.08380F},
            {7656, 0.46330F, -0.18181F, 1.56590F, -0.14571F, 0.93706F, -0.30531F, 0.08648F},
            {7657, 0.46410F, -0.17960F, 1.56042F, -0.14684F, 0.93301F, -0.31638F, 0.08848F},
            {7658, 0.46401F, -0.17988F, 1.56062F, -0.14781F, 0.93303F, -0.31573F, 0.08904F},
            {7659, 0.46273F, -0.18337F, 1.56736F, -0.15258F, 0.93694F, -0.30117F, 0.09038F},
            {7660, 0.46154F, -0.18459F, 1.57738F, -0.15826F, 0.94146F, -0.28324F, 0.09156F},
            {7661, 0.46196F, -0.17729F, 1.58697F, -0.16059F, 0.94386F, -0.27384F, 0.09143F},
            {7662, 0.46549F, -0.15508F, 1.59232F, -0.15528F, 0.94170F, -0.28492F, 0.08892F},
            {7663, 0.47161F, -0.12193F, 1.59124F, -0.14276F, 0.93392F, -0.31686F, 0.08378F},
            {7664, 0.47886F, -0.08357F, 1.58735F, -0.12718F, 0.92155F, -0.35881F, 0.07627F},
            {7665, 0.48757F, -0.03663F, 1.58210F, -0.10844F, 0.90244F, -0.41186F, 0.06494F},
            {7666, 0.49669F, 0.01488F, 1.57672F, -0.08908F, 0.87650F, -0.47046F, 0.04981F},
            {7667, 0.50526F, 0.06736F, 1.57221F, -0.07129F, 0.84481F, -0.52935F, 0.03163F},
            {7668, 0.51264F, 0.11759F, 1.56919F, -0.05660F, 0.80971F, -0.58397F, 0.01186F},
            {7669, 0.51847F, 0.16257F, 1.56781F, -0.04578F, 0.77470F, -0.63063F, -0.00751F},
            {7670, 0.52269F, 0.19923F, 1.56776F, -0.03876F, 0.74402F, -0.66660F, -0.02419F},
            {7671, 0.52486F, 0.22064F, 1.56842F, -0.03494F, 0.72216F, -0.68991F, -0.03593F},
            {7672, 0.52424F, 0.21848F, 1.56911F, -0.03312F, 0.70933F, -0.70280F, -0.04278F},
            {7673, 0.52393F, 0.21749F, 1.56947F, 0.03236F, -0.70334F, 0.70863F, 0.04595F},
            {7674, 0.52390F, 0.21740F, 1.56951F, 0.03229F, -0.70277F, 0.70918F, 0.04626F},
            {7675, 0.52407F, 0.21793F, 1.56931F, 0.03269F, -0.70602F, 0.70603F, 0.04453F},
            {7676, 0.52434F, 0.21884F, 1.56899F, -0.03340F, 0.71148F, -0.70068F, -0.04163F},
            {7677, 0.52464F, 0.21985F, 1.56866F, -0.03425F, 0.71750F, -0.69466F, -0.03842F},
            {7678, 0.52488F, 0.22070F, 1.56841F, -0.03500F, 0.72251F, -0.68954F, -0.03574F},
            {7679, 0.52499F, 0.22113F, 1.56829F, -0.03539F, 0.72499F, -0.68699F, -0.03442F},
            {7680, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7681, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7682, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7683, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7684, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7685, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7686, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7687, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7688, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
            {7689, 0.52499F, 0.22114F, 1.56829F, -0.03540F, 0.72505F, -0.68693F, -0.03438F},
    };
    private static final Map<Integer, float[]> BLOCKBENCH_POSES = new HashMap<>();

    static {
        for (float[] row : BLOCKBENCH_POSE_ROWS) {
            BLOCKBENCH_POSES.put((int) row[0], row);
        }
    }

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

        // Gun arm: the designer's keyframed arm where there is one (right-handed only, since the Blockbench
        // arm is a right arm); otherwise hand on the gun in this frame, shoulder where it rests.
        float[] pose = gunArm == HumanoidArm.RIGHT ? BLOCKBENCH_POSES.get(frame) : null;
        if (pose != null) {
            renderArmPose(mc, player, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                    idle, pose);
        } else {
            renderArm(mc, player, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), gunArm,
                    at(now, GUN_HAND), at(idle, GUN_SHOULDER));
        }

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

    /** Draw the right arm exactly where the Blockbench arm bone puts it for this frame. */
    private static void renderArmPose(Minecraft mc, AbstractClientPlayer player, PoseStack poseStack,
                                      MultiBufferSource buffers, int light, Matrix4f idle, float[] row) {
        Vector3f origin = idle.transformPosition(new Vector3f(row[1], row[2], row[3]), new Vector3f());
        Quaternionf itemRotation = idle.getUnnormalizedRotation(new Quaternionf());
        float itemScale = idle.getScale(new Vector3f()).x;
        poseStack.pushPose();
        poseStack.translate(origin.x, origin.y, origin.z);
        poseStack.mulPose(itemRotation);
        poseStack.mulPose(new Quaternionf(row[4], row[5], row[6], row[7]));
        poseStack.scale(itemScale * MODEL_SCALE, itemScale * MODEL_SCALE, itemScale * MODEL_SCALE);
        drawArmPart(mc, player, poseStack, buffers, light, HumanoidArm.RIGHT);
        poseStack.popPose();
    }

    /** Draw the player's skinned arm with its hand at {@code hand}, pointing back towards {@code shoulder}. */
    private static void renderArm(Minecraft mc, AbstractClientPlayer player, PoseStack poseStack, MultiBufferSource buffers,
                                  int light, HumanoidArm side, Vector3f hand, Vector3f shoulder) {
        boolean right = side == HumanoidArm.RIGHT;
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

        drawArmPart(mc, player, poseStack, buffers, light, side);
        poseStack.popPose();
    }

    /** Render the player's arm and sleeve parts at the current pose, with the parts' own pose zeroed. */
    private static void drawArmPart(Minecraft mc, AbstractClientPlayer player, PoseStack poseStack,
                                    MultiBufferSource buffers, int light, HumanoidArm side) {
        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        boolean right = side == HumanoidArm.RIGHT;
        ModelPart arm = right ? model.rightArm : model.leftArm;
        ModelPart sleeve = right ? model.rightSleeve : model.leftSleeve;
        PlayerSkin skin = player.getSkin();
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
    }
}
