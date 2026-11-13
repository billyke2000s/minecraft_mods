package com.billyke2000s.animatedmotd.mixin;

import com.billyke2000s.animatedmotd.AnimatedMotd;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every server-list ping asks for the status: hand back the current animation frame as the description. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerStatusMixin {
    @Inject(method = "getStatus", at = @At("RETURN"), cancellable = true)
    private void animatedMotd$frame(CallbackInfoReturnable<ServerStatus> cir) {
        ServerStatus status = cir.getReturnValue();
        if (status != null) {
            cir.setReturnValue(new ServerStatus(AnimatedMotd.currentFrame(), status.players(), status.version(),
                    status.favicon(), status.enforcesSecureChat()));
        }
    }
}
