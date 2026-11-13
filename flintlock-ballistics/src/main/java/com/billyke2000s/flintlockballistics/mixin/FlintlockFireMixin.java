package com.billyke2000s.flintlockballistics.mixin;

import com.billyke2000s.flintlockballistics.Ballistics;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

/**
 * Turns Server Flintlock's instant hitscan into a real projectile. Inside {@code FlintlockMod.fire}:
 * the instant entity search finds nothing (so nothing is hit instantly), the instant impact sparks,
 * impact trace and slow decorative ball are skipped, and at the end a real ball is launched. Everything
 * else (the gunshot, muzzle smoke, the Upgrades muzzle effects) is untouched.
 */
@Mixin(targets = "dev.obskyra.flintlock.FlintlockMod", remap = false)
public abstract class FlintlockFireMixin {

    @Redirect(method = "fire", remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/server/level/ServerLevel;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private static List<Entity> flintlockBallistics$noInstantHit(ServerLevel level, Entity except, AABB area, Predicate<? super Entity> filter) {
        return List.of();
    }

    @Redirect(method = "fire", remap = false, at = @At(value = "INVOKE", ordinal = 1, remap = false,
            target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"))
    private static int flintlockBallistics$noInstantSparks(ServerLevel level, ParticleOptions type, double x, double y, double z,
                                                         int count, double dx, double dy, double dz, double speed) {
        return 0;
    }

    @Redirect(method = "fire", remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Ldev/obskyra/flintlock/FlintlockMod;notifyServerTracesBulletImpact(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;)V"))
    private static void flintlockBallistics$noInstantTrace(ServerLevel level, Vec3 at) {
    }

    @Redirect(method = "fire", remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Ldev/obskyra/flintlock/BallVisual;spawn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V"))
    private static void flintlockBallistics$noFakeBall(ServerLevel level, Vec3 from, Vec3 to) {
    }

    @Inject(method = "fire", remap = false, at = @At("TAIL"))
    private static void flintlockBallistics$launch(ServerLevel level, ServerPlayer player, CallbackInfo ci) {
        Ballistics.launch(level, player);
    }
}
