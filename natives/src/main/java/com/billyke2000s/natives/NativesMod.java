package com.billyke2000s.natives;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Natives: biome-specific tribes living in camps. A camp can be colonised by trading until the tribe
 * allies with you, or by defeating its chief. Owners claim the land with a Colony Flag. Attacking a
 * tribe may (or may not) make it fight back and send war parties.
 */
@Mod(NativesMod.MODID)
public final class NativesMod {
    public static final String MODID = "natives";

    public NativesMod(IEventBus modBus, ModContainer container) {
        ModRegistry.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, NativesConfig.SPEC);
        NeoForge.EVENT_BUS.register(VillageEvents.class);
        NeoForge.EVENT_BUS.addListener(NativesCommands::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
