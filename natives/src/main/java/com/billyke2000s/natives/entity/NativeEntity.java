package com.billyke2000s.natives.entity;

import com.billyke2000s.natives.ModRegistry;
import com.billyke2000s.natives.NativeVillages;
import com.billyke2000s.natives.Tribe;
import com.billyke2000s.natives.VillageLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;
import java.util.UUID;

public class NativeEntity extends PathfinderMob {
    public enum Role { VILLAGER, WARRIOR, CHIEF }

    public static final int VARIANTS = 2;
    /** Entity event id that shows happy particles on clients after a trade. */
    private static final byte EVENT_TRADED = 14;
    private static final int HOME_RADIUS = 20;

    private static final EntityDataAccessor<Integer> DATA_TRIBE = SynchedEntityData.defineId(NativeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ROLE = SynchedEntityData.defineId(NativeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(NativeEntity.class, EntityDataSerializers.INT);

    @Nullable
    private BlockPos heart;
    private boolean assigned;
    private boolean raider;
    private int tradeCooldown;
    /** Grudge held by a native without a village (e.g. from a spawn egg). */
    @Nullable
    private UUID grudge;
    private long grudgeUntil;

    public NativeEntity(EntityType<? extends NativeEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TRIBE, 0);
        builder.define(DATA_ROLE, 0);
        builder.define(DATA_VARIANT, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15, true) {
            @Override
            public boolean canUse() {
                return isFighter() && super.canUse();
            }
        });
        goalSelector.addGoal(2, new PanicGoal(this, 1.3) {
            @Override
            public boolean canUse() {
                return !isFighter() && super.canUse();
            }
        });
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                target -> isFighter() && target instanceof Player player && isHostileTo(player)));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                target -> isFighter()));
    }

    // ---- identity ----------------------------------------------------------------------------

    public Tribe tribe() {
        return Tribe.byIndex(entityData.get(DATA_TRIBE));
    }

    public Role role() {
        Role[] roles = Role.values();
        return roles[Math.floorMod(entityData.get(DATA_ROLE), roles.length)];
    }

    public int variant() {
        return Math.floorMod(entityData.get(DATA_VARIANT), VARIANTS);
    }

    public boolean isFighter() {
        return role() != Role.VILLAGER;
    }

    public boolean isRaider() {
        return raider;
    }

    @Nullable
    public BlockPos heart() {
        return heart;
    }

    /** Gives this native its tribe, role and home. Call before adding it to the world. */
    public void setup(Tribe tribe, Role role, @Nullable BlockPos heart) {
        entityData.set(DATA_TRIBE, tribe.ordinal());
        entityData.set(DATA_ROLE, role.ordinal());
        entityData.set(DATA_VARIANT, getRandom().nextInt(VARIANTS));
        this.heart = heart == null ? null : heart.immutable();
        this.assigned = true;
        if (heart != null) {
            restrictTo(heart, HOME_RADIUS);
        }
        applyRoleStats();
    }

    public void makeRaider() {
        raider = true;
        clearRestriction();
    }

    private void applyRoleStats() {
        double health = switch (role()) {
            case VILLAGER -> 20.0;
            case WARRIOR -> 26.0;
            case CHIEF -> 40.0;
        };
        double damage = switch (role()) {
            case VILLAGER -> 2.0;
            case WARRIOR -> 5.0;
            case CHIEF -> 7.0;
        };
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(damage);
        setHealth(getMaxHealth());
        if (isFighter()) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(tribe().weapon()));
            setDropChance(EquipmentSlot.MAINHAND, 0.05F);
        } else {
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    @Override
    protected Component getTypeName() {
        String role = switch (role()) {
            case VILLAGER -> "Villager";
            case WARRIOR -> "Warrior";
            case CHIEF -> "Chief";
        };
        return Component.literal(tribe().displayName() + " " + role);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData spawnGroupData) {
        if (!assigned) {
            // Spawn egg or summon: take the tribe of the land, or a random one.
            Tribe tribe = Tribe.forBiome(level.getBiome(blockPosition()));
            if (tribe == null) {
                tribe = Tribe.byIndex(getRandom().nextInt(Tribe.values().length));
            }
            float roll = getRandom().nextFloat();
            setup(tribe, roll < 0.6F ? Role.VILLAGER : Role.WARRIOR, null);
        }
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
    }

    // ---- behaviour ---------------------------------------------------------------------------

    @Nullable
    private NativeVillages.Village village() {
        if (heart == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return NativeVillages.get(serverLevel).at(heart);
    }

    public boolean isHostileTo(Player player) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        long now = level().getGameTime();
        NativeVillages.Village village = village();
        if (village != null) {
            return village.isHostileTo(player.getUUID(), now);
        }
        return player.getUUID().equals(grudge) && grudgeUntil > now;
    }

    /** For natives without a village: remember who attacked them. */
    public void holdGrudge(Player player, long untilGameTime) {
        grudge = player.getUUID();
        grudgeUntil = untilGameTime;
    }

    @Override
    public void tick() {
        super.tick();
        if (tradeCooldown > 0) {
            tradeCooldown--;
        }
        if (level() instanceof ServerLevel serverLevel && heart != null && tickCount % 100 == 0) {
            if (serverLevel.isLoaded(heart)) {
                if (serverLevel.getBlockState(heart).is(ModRegistry.VILLAGE_HEART.get())) {
                    NativeVillages.get(serverLevel).getOrCreate(heart, tribe());
                } else {
                    // The Village Heart is gone: the village no longer exists.
                    heart = null;
                    clearRestriction();
                }
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && source.getEntity() instanceof Player player) {
            VillageLogic.onNativeAttacked(this, player);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide && source.getEntity() instanceof Player player) {
            VillageLogic.onNativeKilled(this, player);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean tradeItem = stack.is(Items.EMERALD) || stack.is(tribe().favourite());
        if (!tradeItem || !isAlive()) {
            return super.mobInteract(player, hand);
        }
        if (!level().isClientSide && tradeCooldown <= 0) {
            tradeCooldown = 10;
            if (VillageLogic.trade(this, player, stack)) {
                level().broadcastEntityEvent(this, EVENT_TRADED);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_TRADED) {
            for (int i = 0; i < 5; i++) {
                level().addParticle(ParticleTypes.HAPPY_VILLAGER, getRandomX(1.0), getRandomY() + 0.5, getRandomZ(1.0),
                        getRandom().nextGaussian() * 0.02, getRandom().nextGaussian() * 0.02, getRandom().nextGaussian() * 0.02);
            }
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return raider;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof NativeEntity) && super.canAttack(target);
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    // ---- saving ------------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Tribe", tribe().id());
        tag.putInt("Role", role().ordinal());
        tag.putInt("Variant", variant());
        tag.putBoolean("Assigned", assigned);
        tag.putBoolean("Raider", raider);
        if (heart != null) {
            tag.putLong("Heart", heart.asLong());
        }
        if (grudge != null) {
            tag.putUUID("Grudge", grudge);
            tag.putLong("GrudgeUntil", grudgeUntil);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        Tribe tribe = Tribe.byId(tag.getString("Tribe"));
        entityData.set(DATA_TRIBE, (tribe == null ? Tribe.PINEMARK : tribe).ordinal());
        entityData.set(DATA_ROLE, tag.getInt("Role"));
        entityData.set(DATA_VARIANT, tag.getInt("Variant"));
        assigned = tag.getBoolean("Assigned");
        raider = tag.getBoolean("Raider");
        heart = tag.contains("Heart") ? BlockPos.of(tag.getLong("Heart")) : null;
        if (heart != null && !raider) {
            restrictTo(heart, HOME_RADIUS);
        }
        if (tag.hasUUID("Grudge")) {
            grudge = tag.getUUID("Grudge");
            grudgeUntil = tag.getLong("GrudgeUntil");
        }
    }
}
