package com.billyke2000s.whip;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * The visible lash, behaving like a real bullwhip: it starts thrown back over the shoulder, a loop rolls
 * forward along it until the tip snaps straight, and only then does it crack and strike. After the
 * crack it goes slack and drops. Never collides or saves.
 */
public class WhipLashEntity extends Entity {
    /** Ticks from the throw until the loop reaches the tip and it cracks. */
    public static final int THROW_TICKS = 4;
    /** Ticks the whip hangs slack after the crack before it's gone. */
    public static final int FALL_TICKS = 5;
    public static final int LIFETIME = THROW_TICKS + FALL_TICKS;
    /** How close the creature must still be to where the tip lands to be struck. */
    private static final double STRIKE_TOLERANCE = 2.0;

    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_TARGET = SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.VECTOR3);

    /** Server only: the creature the tip is aimed at, struck when the whip cracks (-1 for none). */
    private int strikeTargetId = -1;
    /** Server only: whether this lash should deal the hit itself (right-click) or not (left-click, already hit). */
    private boolean strikes;
    /** Server only: a button, lever or pressure plate the tip lands on, worked when it cracks. */
    @Nullable
    private BlockPos strikeBlock;

    public WhipLashEntity(EntityType<? extends WhipLashEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static WhipLashEntity create(Level level, Entity owner, Vec3 target, @Nullable LivingEntity strikeTarget, boolean strikes) {
        return create(level, owner, target, strikeTarget, null, strikes);
    }

    public static WhipLashEntity create(Level level, Entity owner, Vec3 target, @Nullable LivingEntity strikeTarget,
                                        @Nullable BlockPos strikeBlock, boolean strikes) {
        WhipLashEntity lash = new WhipLashEntity(WhipMod.WHIP_LASH.get(), level);
        lash.setPos(owner.getX(), owner.getEyeY(), owner.getZ());
        lash.entityData.set(DATA_OWNER, owner.getId());
        lash.entityData.set(DATA_TARGET, new Vector3f((float) target.x, (float) target.y, (float) target.z));
        lash.strikeTargetId = strikeTarget == null ? -1 : strikeTarget.getId();
        lash.strikes = strikes;
        lash.strikeBlock = strikeBlock == null ? null : strikeBlock.immutable();
        return lash;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, -1);
        builder.define(DATA_TARGET, new Vector3f());
    }

    @Nullable
    public Entity owner() {
        return level().getEntity(entityData.get(DATA_OWNER));
    }

    public Vec3 target() {
        Vector3f t = entityData.get(DATA_TARGET);
        return new Vec3(t.x(), t.y(), t.z());
    }

    /**
     * How far the rolling loop has travelled along the whip: 0 = thrown back over the shoulder,
     * 1 = fully straight (the crack). Speeds up towards the end, as a real lash does.
     */
    public float unroll(float partialTick) {
        float age = tickCount + partialTick;
        if (age >= THROW_TICKS) {
            return 1.0F;
        }
        float t = Math.max(0.0F, age / THROW_TICKS);
        return t * t;
    }

    /** How slack the whip has gone after the crack: 0 = taut, 1 = fully dropped. */
    public float slack(float partialTick) {
        float age = tickCount + partialTick - THROW_TICKS;
        return age <= 0 ? 0.0F : Math.min(1.0F, age / FALL_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        Entity owner = owner();
        if (owner != null) {
            setPos(owner.getX(), owner.getEyeY(), owner.getZ());
        }
        if (level() instanceof ServerLevel serverLevel) {
            if (tickCount == THROW_TICKS && owner != null) {
                crack(serverLevel, owner);
            }
            if (tickCount > LIFETIME || owner == null) {
                discard();
            }
        }
    }

    /** The tip breaks the sound barrier: crack at the tip, and strike whatever is still there. */
    private void crack(ServerLevel level, Entity owner) {
        Vec3 tip = target();
        level.playSound(null, tip.x, tip.y, tip.z, WhipMod.CRACK.get(), net.minecraft.sounds.SoundSource.PLAYERS, 3.0F,
                0.9F + level.getRandom().nextFloat() * 0.2F);
        if (!strikes) {
            return;
        }
        if (strikeBlock != null) {
            WhipItem.hitBlock(level, owner, strikeBlock);
        }
        if (strikeTargetId < 0) {
            return;
        }
        Entity hit = level.getEntity(strikeTargetId);
        if (hit instanceof LivingEntity target && target.isAlive()
                && target.getBoundingBox().inflate(STRIKE_TOLERANCE).contains(tip)) {
            WhipItem.strike(level, owner, target);
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
