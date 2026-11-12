package com.billyke2000s.natives;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import javax.annotation.Nullable;

/** Land protection for colonies and villages, and the war-party timer. */
public final class VillageEvents {
    private static final int RAID_CHECK_TICKS = 1200;

    private VillageEvents() {
    }

    /** The village whose land contains {@code pos}: claimed colonies first, then unclaimed village cores. */
    @Nullable
    private static NativeVillages.Village villageAt(ServerLevel level, BlockPos pos) {
        NativeVillages data = NativeVillages.get(level);
        int claim = NativesConfig.CLAIM_RADIUS.get();
        int core = NativesConfig.CORE_RADIUS.get();
        NativeVillages.Village best = null;
        double bestDist = Double.MAX_VALUE;
        for (NativeVillages.Village village : data.all()) {
            int radius = village.claimed() ? claim : core;
            double dist = village.heart().distSqr(pos);
            if (dist <= (double) radius * radius && dist < bestDist) {
                best = village;
                bestDist = dist;
            }
        }
        return best;
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (!(event.getLevel() instanceof ServerLevel level) || player.isCreative()) {
            return;
        }
        NativeVillages.Village village = villageAt(level, event.getPos());
        if (village == null || village.isOwnedBy(player.getUUID())) {
            return;
        }
        if (village.claimed()) {
            event.setCanceled(true);
            player.displayClientMessage(Component.literal("This land belongs to " + village.ownerName() + "'s colony.")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (event.getPos().equals(village.heart())) {
            event.setCanceled(true);
            player.displayClientMessage(Component.literal("The " + village.tribe().displayName()
                    + " guard their Village Heart. Win their trust or defeat their chief.").withStyle(ChatFormatting.RED), true);
            return;
        }
        // Wrecking an independent village angers it.
        int rep = village.addReputation(player.getUUID(), -3);
        if (rep < -20) {
            VillageLogic.provoke(level, village, player, level.getGameTime(), "damage");
        } else {
            player.displayClientMessage(Component.literal("The " + village.tribe().displayName()
                    + " don't like you damaging their village.").withStyle(ChatFormatting.YELLOW), true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getLevel() instanceof ServerLevel level) || player.isCreative()) {
            return;
        }
        NativeVillages.Village village = villageAt(level, event.getPos());
        if (village != null && village.claimed() && !village.isOwnedBy(player.getUUID())) {
            event.setCanceled(true);
            player.displayClientMessage(Component.literal("This land belongs to " + village.ownerName() + "'s colony.")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % RAID_CHECK_TICKS != 0) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            VillageLogic.tickRaids(level);
        }
    }
}
