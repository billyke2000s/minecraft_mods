package com.billyke2000s.whip;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Right-click: throw the whip out to {@link #RANGE} blocks. The lash unrolls, cracks at the tip, and
 * whatever is still under the tip at that moment takes half a heart and is jolted forward with a hop.
 * Left-click: a close lash with extra reach (the hit lands straight away, the lash is shown too).
 */
public class WhipItem extends Item {
    public static final double RANGE = 7.0;
    private static final float DAMAGE = 1.0F; // half a heart
    private static final double FORWARD = 0.7;
    private static final double HOP = 0.4;
    private static final int COOLDOWN = 14;
    /** Chance the lash wraps a creature's held item and pulls it free. */
    private static final float DISARM_CHANCE = 0.15F;
    /** Loose items this close to the tip are snagged. */
    private static final double SNAG_RADIUS = 1.0;
    /** Animals this close to the crack bolt. */
    private static final double SCARE_RADIUS = 6.0;

    public WhipItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        jolt(target, attacker);
        if (attacker.level() instanceof ServerLevel level) {
            if (level.getRandom().nextFloat() < DISARM_CHANCE) {
                disarm(level, attacker, target);
            }
            // The hit already landed; just show the lash (it cracks at the tip, no second hit).
            level.addFreshEntity(WhipLashEntity.create(level, attacker,
                    target.position().add(0, target.getBbHeight() * 0.6, 0), null, false));
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
            // OUTLINE: like a real lash, the tip stops on anything in the way, including grass and flowers,
            // and can land on thin things like buttons, levers and pressure plates.
            BlockHitResult block = level.clip(new ClipContext(eye, far, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
            AABB search = player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, search,
                    entity -> entity != player && entity.isPickable() && !entity.isSpectator() && !(entity instanceof WhipLashEntity),
                    eye.distanceToSqr(end));
            Entity target = null;
            BlockPos blockTarget = null;
            if (hit != null) {
                target = hit.getEntity();
                end = hit.getLocation();
            } else if (block.getType() == HitResult.Type.BLOCK) {
                blockTarget = block.getBlockPos();
            }
            serverLevel.addFreshEntity(WhipLashEntity.create(serverLevel, player, end, target, blockTarget, true));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static boolean isWorkable(BlockState state) {
        Block block = state.getBlock();
        return block instanceof ButtonBlock || block instanceof LeverBlock || block instanceof BasePressurePlateBlock;
    }

    /** Grass, flowers, ferns and the like are cut by the tip. Crops and saplings are left alone. */
    private static boolean isCuttable(Block block) {
        return block instanceof BushBlock && !(block instanceof CropBlock) && !(block instanceof StemBlock)
                && !(block instanceof SaplingBlock) && !(block instanceof NetherWartBlock) && !(block instanceof SweetBerryBushBlock);
    }

    /**
     * The tip cracks on a block: buttons are pressed, levers flipped, pressure plates tripped briefly,
     * bells rung, and grass and flowers cut.
     */
    static void hitBlock(ServerLevel level, Entity attacker, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        Player player = attacker instanceof Player p ? p : null;
        if (block instanceof ButtonBlock button) {
            if (!state.getValue(ButtonBlock.POWERED)) {
                button.press(state, level, pos, player);
            }
        } else if (block instanceof LeverBlock lever) {
            lever.pull(state, level, pos, player);
        } else if (block instanceof BellBlock bell) {
            bell.attemptToRing(attacker, level, pos, null);
        } else if (isCuttable(block)) {
            level.destroyBlock(pos, true, attacker);
        } else if (block instanceof BasePressurePlateBlock) {
            BlockState pressed = state;
            if (state.hasProperty(BlockStateProperties.POWERED) && !state.getValue(BlockStateProperties.POWERED)) {
                pressed = state.setValue(BlockStateProperties.POWERED, true);
            } else if (state.hasProperty(BlockStateProperties.POWER) && state.getValue(BlockStateProperties.POWER) == 0) {
                pressed = state.setValue(BlockStateProperties.POWER, 1);
            }
            if (pressed != state) {
                level.setBlock(pos, pressed, Block.UPDATE_ALL);
                level.updateNeighborsAt(pos, block);
                level.updateNeighborsAt(pos.below(), block);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.STONE_PRESSURE_PLATE_CLICK_ON,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.3F, 0.6F);
                // The plate's own tick re-checks for anything standing on it and releases if empty.
                level.scheduleTick(pos, block, 20);
            }
        }
    }

    /**
     * The tip lands on something. Creatures take half a heart and are jolted forward, and sometimes the
     * lash wraps their weapon and yanks it out of their hand. Things like item frames, boats and minecarts
     * take the hit too (an item frame drops its item).
     */
    static void strike(ServerLevel level, Entity attacker, Entity target) {
        DamageSource source = attacker instanceof Player player
                ? level.damageSources().playerAttack(player)
                : attacker instanceof LivingEntity living ? level.damageSources().mobAttack(living) : level.damageSources().generic();
        if (!target.hurt(source, DAMAGE)) {
            return;
        }
        if (target instanceof LivingEntity living) {
            jolt(living, attacker);
            if (level.getRandom().nextFloat() < DISARM_CHANCE) {
                disarm(level, attacker, living);
            }
        }
        if (attacker instanceof LivingEntity living && living.getMainHandItem().getItem() instanceof WhipItem) {
            living.getMainHandItem().hurtAndBreak(1, living, EquipmentSlot.MAINHAND);
        }
    }

    /** The lash wraps the target's held item and pulls it out of their hand towards the attacker. */
    private static void disarm(ServerLevel level, Entity attacker, LivingEntity target) {
        if (target instanceof Player p && (p.isCreative() || p.isSpectator())) {
            return;
        }
        ItemStack held = target.getMainHandItem();
        if (held.isEmpty()) {
            return;
        }
        target.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ItemEntity flying = new ItemEntity(level, target.getX(), target.getEyeY() - 0.3, target.getZ(), held.copy());
        flying.setDeltaMovement(towards(target.position(), attacker.position(), 0.45, 0.3));
        flying.setPickUpDelay(20);
        level.addFreshEntity(flying);
        if (target instanceof Player p) {
            p.displayClientMessage(net.minecraft.network.chat.Component.literal("The whip yanked your item out of your hand!"), true);
        }
    }

    /**
     * Things that happen at the crack whatever it hits: loose items at the tip are snagged and flicked
     * back towards the attacker, and animals nearby bolt away from the bang.
     */
    static void crackEffects(ServerLevel level, Entity attacker, Vec3 tip) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(tip, tip).inflate(SNAG_RADIUS))) {
            item.setDeltaMovement(towards(item.position(), attacker.position(), 0.5, 0.35));
            item.hasImpulse = true;
        }
        for (Animal animal : level.getEntitiesOfClass(Animal.class, new AABB(tip, tip).inflate(SCARE_RADIUS))) {
            Vec3 away = animal.position().subtract(tip);
            if (away.lengthSqr() < 1.0E-3) {
                away = new Vec3(level.getRandom().nextDouble() - 0.5, 0, level.getRandom().nextDouble() - 0.5);
            }
            Vec3 flee = animal.position().add(new Vec3(away.x, 0, away.z).normalize().scale(8.0));
            animal.getNavigation().moveTo(flee.x, flee.y, flee.z, 1.6);
        }
    }

    private static Vec3 towards(Vec3 from, Vec3 to, double speed, double lift) {
        Vec3 flat = new Vec3(to.x - from.x, 0, to.z - from.z);
        if (flat.lengthSqr() > 1.0E-4) {
            flat = flat.normalize().scale(speed);
        }
        return new Vec3(flat.x, lift, flat.z);
    }

    /** Push the target the way the attacker is facing, with a small hop. */
    static void jolt(LivingEntity target, Entity attacker) {
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
}
