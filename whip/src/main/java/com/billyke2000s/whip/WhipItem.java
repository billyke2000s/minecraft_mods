package com.billyke2000s.whip;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Left-click: a close lash (with extra reach). Right-click: crack the whip out to {@link #RANGE} blocks,
 * lashing the first creature in the way. Either way the whip visibly lashes out and back, it cracks,
 * the target loses half a heart and is jolted forward with a little hop.
 */
public class WhipItem extends Item {
    public static final double RANGE = 7.0;
    private static final float DAMAGE = 1.0F; // half a heart
    private static final double FORWARD = 0.7;
    private static final double HOP = 0.4;
    private static final int COOLDOWN = 12;

    public WhipItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        jolt(target, attacker);
        if (attacker.level() instanceof ServerLevel level) {
            drawLash(level, attacker, target.position().add(0, target.getBbHeight() * 0.6, 0));
            crack(level, attacker.position());
        }
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.swing(hand);
        player.getCooldowns().addCooldown(this, COOLDOWN);
        if (level instanceof ServerLevel serverLevel) {
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            Vec3 far = eye.add(look.scale(RANGE));
            BlockHitResult block = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
            AABB search = player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, search,
                    entity -> entity instanceof LivingEntity && entity != player && entity.isPickable() && !entity.isSpectator(),
                    eye.distanceToSqr(end));
            if (hit != null && hit.getEntity() instanceof LivingEntity target) {
                end = hit.getLocation();
                if (target.hurt(level.damageSources().playerAttack(player), DAMAGE)) {
                    jolt(target, player);
                    stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                }
            }
            drawLash(serverLevel, player, end);
            crack(serverLevel, player.position());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Push the target the way the attacker is facing, with a small hop. */
    private static void jolt(LivingEntity target, Entity attacker) {
        Vec3 look = attacker.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() > 1.0E-4) {
            flat = flat.normalize().scale(FORWARD);
        }
        Vec3 motion = target.getDeltaMovement();
        target.setDeltaMovement(motion.x + flat.x, Math.max(motion.y, HOP), motion.z + flat.z);
        target.hasImpulse = true;
        target.hurtMarked = true;
        if (target instanceof ServerPlayer player) {
            // Players control their own movement, so tell their client about the jolt directly.
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }

    /** Sends out the visible lash from the attacker's hand to the end point. */
    private static void drawLash(ServerLevel level, Entity from, Vec3 end) {
        level.addFreshEntity(WhipLashEntity.create(level, from, end));
    }

    private static void crack(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, WhipMod.CRACK.get(), SoundSource.PLAYERS, 2.0F,
                0.9F + level.getRandom().nextFloat() * 0.2F);
    }
}
