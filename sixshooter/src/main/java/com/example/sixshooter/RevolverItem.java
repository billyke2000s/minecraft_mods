package com.example.sixshooter;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class RevolverItem extends Item {
    public static final int CAPACITY = 6;
    private static final int SHOT_COOLDOWN = 8;
    private static final int RELOAD_TICKS = 36;
    private static final double RANGE = 48.0;
    private static final float DAMAGE = 9.0F;

    public RevolverItem(Properties properties) {
        super(properties);
    }

    public static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static int rounds(ItemStack stack) {
        CompoundTag tag = data(stack);
        return Mth.clamp(tag.contains("rounds") ? tag.getInt("rounds") : CAPACITY, 0, CAPACITY);
    }

    public static boolean reloading(ItemStack stack) {
        return data(stack).getBoolean("reloading");
    }

    private static void update(ItemStack stack, Consumer<CompoundTag> change) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, change);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (player.isShiftKeyDown()) {
            reload(level, player, stack);
            return InteractionResultHolder.success(stack);
        }
        if (reloading(stack) || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.success(stack);
        }
        if (rounds(stack) <= 0) {
            sound(level, player, SixShooter.DRY, 0.75F, 0.96F);
            player.getCooldowns().addCooldown(this, SHOT_COOLDOWN);
            return InteractionResultHolder.success(stack);
        }
        int left = rounds(stack) - 1;
        update(stack, tag -> {
            tag.putInt("rounds", left);
            tag.putLong("lastShot", level.getGameTime());
        });
        player.getCooldowns().addCooldown(this, SHOT_COOLDOWN);
        sound(level, player, SixShooter.FIRE, 2.6F, 0.96F + level.random.nextFloat() * 0.09F);
        player.swing(hand, true);
        if (level instanceof ServerLevel serverLevel) {
            fire(serverLevel, player);
        }
        return InteractionResultHolder.success(stack);
    }

    private static void reload(Level level, Player player, ItemStack stack) {
        if (reloading(stack) || rounds(stack) == CAPACITY) {
            return;
        }
        if (!player.getAbilities().instabuild && available(player) == 0) {
            sound(level, player, SixShooter.DRY, 0.6F, 0.78F);
            return;
        }
        long now = level.getGameTime();
        update(stack, tag -> {
            tag.putBoolean("reloading", true);
            tag.putLong("reloadStart", now);
            tag.putInt("reloadStage", 0);
        });
        sound(level, player, SixShooter.OPEN, 1.15F, 1.0F);
        player.getCooldowns().addCooldown(SixShooter.REVOLVER.get(), 5);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof Player player) || !reloading(stack)) {
            return;
        }
        long since = level.getGameTime() - data(stack).getLong("reloadStart");
        int stage = data(stack).getInt("reloadStage");
        if (since >= 12L && stage < 1) {
            update(stack, tag -> tag.putInt("reloadStage", 1));
            sound(level, player, SixShooter.LOAD, 1.0F, 0.96F);
        }
        if (since >= 27L && stage < 2) {
            update(stack, tag -> tag.putInt("reloadStage", 2));
            sound(level, player, SixShooter.CLOSE, 1.0F, 1.03F);
        }
        if (since >= RELOAD_TICKS) {
            int needed = CAPACITY - rounds(stack);
            int got = player.getAbilities().instabuild ? needed : consume(player, needed);
            int total = Math.min(CAPACITY, rounds(stack) + got);
            update(stack, tag -> {
                tag.putInt("rounds", total);
                tag.putBoolean("reloading", false);
                tag.putInt("reloadStage", 0);
            });
        }
    }

    private static int available(Player player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(SixShooter.BULLET.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int consume(Player player, int wanted) {
        int taken = 0;
        for (int i = 0; i < player.getInventory().getContainerSize() && taken < wanted; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(SixShooter.BULLET.get())) {
                int take = Math.min(stack.getCount(), wanted - taken);
                stack.shrink(take);
                taken += take;
            }
        }
        return taken;
    }

    private static void fire(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle().add(level.random.nextGaussian() * 0.0025,
                level.random.nextGaussian() * 0.0025, level.random.nextGaussian() * 0.0025).normalize();
        Vec3 end = eye.add(dir.scale(RANGE));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }

        LivingEntity hit = null;
        double best = eye.distanceToSqr(end);
        AABB area = new AABB(eye, end).inflate(1.25);
        for (Entity entity : level.getEntities(player, area, e -> e instanceof LivingEntity && e.isPickable() && e.isAlive())) {
            Optional<Vec3> clip = entity.getBoundingBox().inflate(0.25).clip(eye, end);
            if (clip.isPresent()) {
                double dist = eye.distanceToSqr(clip.get());
                if (dist < best) {
                    best = dist;
                    hit = (LivingEntity) entity;
                }
            }
        }
        if (hit != null) {
            end = eye.add(dir.scale(Math.sqrt(best)));
            if (hit.hurt(level.damageSources().playerAttack(player), DAMAGE)) {
                hit.knockback(0.5, -dir.x, -dir.z);
            }
        }

        Vec3 muzzle = eye.add(dir.scale(0.8));
        level.sendParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y - 0.15, muzzle.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y - 0.15, muzzle.z, 7, 0.04, 0.04, 0.04, 0.035);
        double distance = muzzle.distanceTo(end);
        int steps = Math.max(2, (int) (distance * 1.25));
        for (int i = 1; i < Math.min(22, (int) (distance * 1.25)); i++) {
            Vec3 p = muzzle.lerp(end, i / (double) steps);
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y - 0.08, p.z, 1, 0, 0, 0, 0);
        }
        if (hit != null) {
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, end.x, end.y, end.z, 6, 0.17, 0.17, 0.17, 0.07);
        }
    }

    private static void sound(Level level, Player player, DeferredHolder<SoundEvent, SoundEvent> sound, float volume, float pitch) {
        level.playSound(null, player.blockPosition(), sound.get(), SoundSource.PLAYERS, volume, pitch);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Cylinder: " + rounds(stack) + "/6").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal(reloading(stack) ? "Reloading..." : "Right-click: fire  |  Sneak + right-click: reload")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Uses Revolver Rounds").withStyle(ChatFormatting.DARK_GRAY));
    }
}
