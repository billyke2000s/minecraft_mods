package com.billyke2000s.flintlockballistics;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Real musket-ball flight. Each ball is simulated on the server: every tick it moves along its velocity,
 * gravity pulls it down and air drag slows it, and the path it sweeps that tick is checked for blocks
 * and creatures. The damage is dealt only when the ball actually reaches something.
 *
 * Numbers: a flintlock pistol round ball leaves the barrel at about 250 m/s. Minecraft runs 20 ticks a
 * second and a block is 1 m, so that is 12.5 blocks per tick. Gravity (9.81 m/s^2) adds 0.0245 blocks
 * per tick per tick of drop. A round ball is a poor aerodynamic shape and sheds speed fast; 7% per tick
 * loses about a quarter of its speed over the first 50 blocks, in line with period test firings.
 */
public final class Ballistics {
    private static final Logger LOG = LogUtils.getLogger();

    public static final double MUZZLE_SPEED = 12.5;     // blocks per tick (250 m/s)
    public static final double GRAVITY = 0.0245;        // blocks per tick^2 (9.81 m/s^2)
    public static final double DRAG = 0.93;             // speed kept each tick
    public static final double MAX_RANGE = 160.0;       // blocks; ball is spent well before this
    public static final double MIN_SPEED = 1.5;         // blocks per tick; slower than this it's spent
    public static final double BALL_RADIUS = 0.1;       // generous for a ~14 mm ball, makes hits reliable
    private static final int MAX_TICKS = 60;

    private static final String VISUAL_NBT = "{NoGravity:1b,Silent:1b,Invulnerable:1b,teleport_duration:1,"
            + "billboard:\"center\",view_range:2.0f,shadow_radius:0.0f,shadow_strength:0.0f,item_display:\"fixed\","
            + "transformation:{translation:[0.0f,0.0f,0.0f],left_rotation:[0.0f,0.0f,0.0f,1.0f],scale:[0.28f,0.28f,0.28f],"
            + "right_rotation:[0.0f,0.0f,0.0f,1.0f]},item:{id:\"minecraft:iron_nugget\",count:1,"
            + "components:{\"minecraft:custom_model_data\":7302}}}";

    private static final List<Ball> BALLS = new ArrayList<>();

    // Reflection into Server Flintlock and its Upgrades, so damage, headshots, knockback and stagger
    // follow the same rules as before. Each is optional: if it's missing we fall back to plain behaviour.
    private static Method spreadDirection;
    private static Method damageAt;
    private static Method notifyImpact;
    private static Method upgradesHit;
    private static boolean looked;

    private Ballistics() {
    }

    private static void lookUp() {
        if (looked) {
            return;
        }
        looked = true;
        try {
            Class<?> mod = Class.forName("dev.obskyra.flintlock.FlintlockMod");
            spreadDirection = find(mod, "spreadDirection", Vec3.class, ServerPlayer.class);
            damageAt = find(mod, "damageAt", double.class);
            notifyImpact = find(mod, "notifyServerTracesBulletImpact", ServerLevel.class, Vec3.class);
        } catch (Throwable t) {
            LOG.warn("[Flintlock Ballistics] Server Flintlock not found; using plain values", t);
        }
        try {
            Class<?> upgrades = Class.forName("dev.obskyra.flintlock.upgrade.Upgrades");
            upgradesHit = find(upgrades, "hit", Entity.class, DamageSource.class, float.class, ServerLevel.class, ServerPlayer.class);
        } catch (Throwable t) {
            LOG.info("[Flintlock Ballistics] Flintlock Upgrades not present; plain damage on hit");
        }
    }

    @Nullable
    private static Method find(Class<?> owner, String name, Class<?>... args) {
        try {
            Method m = owner.getDeclaredMethod(name, args);
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            LOG.warn("[Flintlock Ballistics] Could not find {}.{}", owner.getSimpleName(), name);
            return null;
        }
    }

    /** Called at the end of FlintlockMod.fire: launch a real ball from the shooter's eye. */
    public static void launch(ServerLevel level, ServerPlayer player) {
        lookUp();
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 dir = look;
        if (spreadDirection != null) {
            try {
                dir = (Vec3) spreadDirection.invoke(null, look, player);
            } catch (Throwable t) {
                dir = look;
            }
        }
        Ball ball = new Ball(level, player, eye, dir.normalize().scale(MUZZLE_SPEED));
        ball.visual = spawnVisual(level, eye);
        BALLS.add(ball);
    }

    /** Called every server tick. */
    static void tick() {
        Iterator<Ball> it = BALLS.iterator();
        while (it.hasNext()) {
            Ball ball = it.next();
            boolean done;
            try {
                done = ball.step();
            } catch (Throwable t) {
                LOG.error("[Flintlock Ballistics] Ball failed; removing it", t);
                done = true;
            }
            if (done) {
                if (ball.visual != null) {
                    ball.visual.discard();
                }
                it.remove();
            }
        }
    }

    static void clear() {
        BALLS.clear();
    }

    @Nullable
    private static Display.ItemDisplay spawnVisual(ServerLevel level, Vec3 at) {
        try {
            Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
            CompoundTag tag = TagParser.parseTag(VISUAL_NBT);
            display.load(tag);
            display.setPos(at.x, at.y, at.z);
            level.addFreshEntity(display);
            return display;
        } catch (Throwable t) {
            return null;
        }
    }

    private static final class Ball {
        final ServerLevel level;
        final ServerPlayer shooter;
        final Vec3 origin;
        Vec3 pos;
        Vec3 velocity;
        double travelled;
        int age;
        @Nullable
        Display.ItemDisplay visual;

        Ball(ServerLevel level, ServerPlayer shooter, Vec3 origin, Vec3 velocity) {
            this.level = level;
            this.shooter = shooter;
            this.origin = origin;
            this.pos = origin;
            this.velocity = velocity;
        }

        /** Advance one tick. Returns true when the ball is finished. */
        boolean step() {
            age++;
            Vec3 next = pos.add(velocity);
            if (!level.isLoaded(BlockPos.containing(next)) || shooter.level() != level) {
                return true;
            }

            BlockHitResult block = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
            Vec3 end = block.getType() == HitResult.Type.MISS ? next : block.getLocation();

            Entity hitEntity = null;
            Vec3 hitPoint = null;
            double best = Double.MAX_VALUE;
            AABB sweep = new AABB(pos, end).inflate(BALL_RADIUS + 1.0);
            for (Entity entity : level.getEntities(shooter, sweep, e -> e.isPickable() && e.isAlive() && !e.isSpectator()
                    && !(e instanceof Display))) {
                Optional<Vec3> clip = entity.getBoundingBox().inflate(BALL_RADIUS).clip(pos, end);
                if (clip.isPresent()) {
                    double d = pos.distanceToSqr(clip.get());
                    if (d < best) {
                        best = d;
                        hitEntity = entity;
                        hitPoint = clip.get();
                    }
                }
            }

            if (hitEntity != null) {
                travelled += Math.sqrt(best);
                strike(hitEntity, hitPoint);
                return true;
            }
            if (block.getType() != HitResult.Type.MISS) {
                impactBlock(block.getLocation());
                return true;
            }

            travelled += velocity.length();
            pos = next;
            velocity = velocity.scale(DRAG).add(0, -GRAVITY, 0);
            if (visual != null) {
                visual.setPos(pos.x, pos.y, pos.z);
            }
            return travelled > MAX_RANGE || velocity.length() < MIN_SPEED || age > MAX_TICKS;
        }

        private void strike(Entity target, Vec3 at) {
            float damage = damageFor(origin.distanceTo(at));
            DamageSource source = shooter.damageSources().playerAttack(shooter);
            boolean hurt;
            if (upgradesHit != null) {
                try {
                    hurt = (boolean) upgradesHit.invoke(null, target, source, damage, level, shooter);
                } catch (Throwable t) {
                    hurt = target.hurt(source, damage);
                }
            } else {
                hurt = target.hurt(source, damage);
            }
            if (hurt && target instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false, true), shooter);
            }
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 5, 0.08, 0.08, 0.08, 0.08);
        }

        private void impactBlock(Vec3 at) {
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 5, 0.08, 0.08, 0.08, 0.08);
            if (notifyImpact != null) {
                try {
                    notifyImpact.invoke(null, level, at);
                } catch (Throwable ignored) {
                    // Server Traces is optional.
                }
            }
        }
    }

    /** Server Flintlock's own damage-by-distance curve (20 up close, falling to 12 far away). */
    private static float damageFor(double distance) {
        if (damageAt != null) {
            try {
                return (float) damageAt.invoke(null, distance);
            } catch (Throwable ignored) {
                // fall through
            }
        }
        if (distance < 8.0) {
            return 20.0F;
        }
        if (distance < 20.0) {
            return 17.0F;
        }
        if (distance < 32.0) {
            return 14.0F;
        }
        return 12.0F;
    }
}
