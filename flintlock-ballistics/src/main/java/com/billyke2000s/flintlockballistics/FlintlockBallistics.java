package com.billyke2000s.flintlockballistics;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Ticks the musket balls launched by the Server Flintlock mixin. */
@Mod(FlintlockBallistics.MODID)
public final class FlintlockBallistics {
    public static final String MODID = "flintlockballistics";

    public FlintlockBallistics() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> Ballistics.tick());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> Ballistics.clear());
    }
}
