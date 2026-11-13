package com.billyke2000s.whip;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * The visible lash. Lives for a few ticks: it shoots out from the owner's hand to the target point,
 * snaps, then pulls back. Purely visual; it never collides or saves.
 */
public class WhipLashEntity extends Entity {
    public static final int EXTEND_TICKS = 3;
    public static final int HOLD_TICKS = 2;
    public static final int RETRACT_TICKS = 3;
    public static final int LIFETIME = EXTEND_TICKS + HOLD_TICKS + RETRACT_TICKS;

    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_TARGET = SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.VECTOR3);

    public WhipLashEntity(EntityType<? extends WhipLashEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static WhipLashEntity create(Level level, Entity owner, Vec3 target) {
        WhipLashEntity lash = new WhipLashEntity(WhipMod.WHIP_LASH.get(), level);
        lash.setPos(owner.getX(), owner.getEyeY(), owner.getZ());
        lash.entityData.set(DATA_OWNER, owner.getId());
        lash.entityData.set(DATA_TARGET, new Vector3f((float) target.x, (float) target.y, (float) target.z));
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

    /** How far out the lash is, 0 (in hand) to 1 (fully extended). */
    public float reach(float partialTick) {
        float age = tickCount + partialTick;
        if (age < EXTEND_TICKS) {
            return age / EXTEND_TICKS;
        }
        if (age < EXTEND_TICKS + HOLD_TICKS) {
            return 1.0F;
        }
        return Math.max(0.0F, 1.0F - (age - EXTEND_TICKS - HOLD_TICKS) / RETRACT_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        Entity owner = owner();
        if (owner != null) {
            // Follow the owner so the lash stays loaded and in view with them.
            setPos(owner.getX(), owner.getEyeY(), owner.getZ());
        }
        if (!level().isClientSide && (tickCount > LIFETIME || owner == null)) {
            discard();
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
