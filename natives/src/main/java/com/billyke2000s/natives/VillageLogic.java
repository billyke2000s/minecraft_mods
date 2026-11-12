package com.billyke2000s.natives;

import com.billyke2000s.natives.entity.NativeEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;

/** The rules for trading, alliances, conquest, retaliation and war parties. Server side only. */
public final class VillageLogic {
    /** How long a village stays hostile after deciding to fight (5 minutes). */
    private static final long HOSTILE_TICKS = 6000L;
    /** Minimum time between war parties from the same village (10 minutes). */
    private static final long RAID_COOLDOWN = 12000L;
    /** War parties only come for players this disliked. */
    private static final int RAID_REPUTATION = -30;
    private static final int RAID_RANGE = 160;

    private VillageLogic() {
    }

    @Nullable
    private static NativeVillages.Village villageOf(NativeEntity native_) {
        BlockPos heart = native_.heart();
        if (heart == null || !(native_.level() instanceof ServerLevel level)) {
            return null;
        }
        if (!level.getBlockState(heart).is(ModRegistry.VILLAGE_HEART.get())) {
            return null;
        }
        return NativeVillages.get(level).getOrCreate(heart, native_.tribe());
    }

    // ---- trade & alliance --------------------------------------------------------------------

    /** Trades one emerald or favourite item for tribe goods. Returns true if a trade happened. */
    public static boolean trade(NativeEntity native_, Player player, ItemStack payment) {
        NativeVillages.Village village = villageOf(native_);
        Tribe tribe = native_.tribe();
        long now = native_.level().getGameTime();
        if (village != null ? village.isHostileTo(player.getUUID(), now) : native_.isHostileTo(player)) {
            player.displayClientMessage(Component.literal("The " + tribe.displayName() + " refuse to trade with you.")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        boolean favourite = !payment.is(Items.EMERALD);
        if (!player.getAbilities().instabuild) {
            payment.shrink(1);
        }
        RandomSource random = native_.getRandom();
        ItemStack goods = tribe.randomGood(random);
        if (village != null && village.isOwnedBy(player.getUUID())) {
            goods.setCount(Math.min(goods.getMaxStackSize(), goods.getCount() * 2));
        }
        if (!player.getInventory().add(goods)) {
            player.drop(goods, false);
        }
        native_.level().playSound(null, native_.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);

        if (village == null) {
            player.displayClientMessage(Component.literal("Traded with a " + tribe.displayName() + " wanderer."), true);
            return true;
        }
        int gain = favourite ? 8 : 5;
        int rep = village.addReputation(player.getUUID(), gain);
        village.clearHostile(player.getUUID());
        int needed = NativesConfig.ALLIANCE_REPUTATION.get();
        if (village.isOwnedBy(player.getUUID())) {
            player.displayClientMessage(Component.literal("Your " + tribe.displayName() + " colony trades at a discount."), true);
        } else if (!village.isOwned() && rep >= needed) {
            village.setOwner(player.getUUID(), player.getGameProfile().getName());
            announce(native_.level(), village.heart(), Component.literal("The " + tribe.displayName()
                    + " village has allied with " + player.getGameProfile().getName() + " and joined their colony!")
                    .withStyle(ChatFormatting.GOLD));
            native_.level().playSound(null, village.heart(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        } else if (village.isOwned()) {
            player.displayClientMessage(Component.literal("This village belongs to " + village.ownerName()
                    + "'s colony. Standing: " + rep), true);
        } else {
            player.displayClientMessage(Component.literal(tribe.displayName() + " trust: " + rep + " / " + needed
                    + (favourite ? " (they loved that!)" : "")), true);
        }
        return true;
    }

    // ---- violence ----------------------------------------------------------------------------

    public static void onNativeAttacked(NativeEntity native_, Player player) {
        if (player.isCreative() && player.isShiftKeyDown()) {
            return; // admins can test without starting a war
        }
        NativeVillages.Village village = villageOf(native_);
        long now = native_.level().getGameTime();
        if (village == null) {
            if (!native_.isHostileTo(player) && native_.getRandom().nextDouble() < NativesConfig.FIGHT_BACK_CHANCE.get()) {
                native_.holdGrudge(player, now + HOSTILE_TICKS);
                if (native_.isFighter()) {
                    native_.setTarget(player);
                }
            }
            return;
        }
        if (village.isOwnedBy(player.getUUID())) {
            return; // your own colonists put up with you
        }
        village.addReputation(player.getUUID(), -10);
        provoke(native_.level(), village, player, now, "attack");
    }

    /** The village decides whether to fight. Already-hostile villages just keep fighting. */
    static void provoke(net.minecraft.world.level.Level level, NativeVillages.Village village, Player player, long now, String reason) {
        Tribe tribe = village.tribe();
        if (village.isHostileTo(player.getUUID(), now)) {
            village.setHostile(player.getUUID(), now + HOSTILE_TICKS);
            rally(level, village, player);
            return;
        }
        if (level.getRandom().nextDouble() < NativesConfig.FIGHT_BACK_CHANCE.get()) {
            village.setHostile(player.getUUID(), now + HOSTILE_TICKS);
            player.displayClientMessage(Component.literal("The " + tribe.displayName() + " take up arms against you!")
                    .withStyle(ChatFormatting.RED), false);
            rally(level, village, player);
        } else if ("attack".equals(reason)) {
            player.displayClientMessage(Component.literal("The " + tribe.displayName() + " flee from you.")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
    }

    private static void rally(net.minecraft.world.level.Level level, NativeVillages.Village village, Player player) {
        AABB area = new AABB(village.heart()).inflate(40);
        for (NativeEntity other : level.getEntitiesOfClass(NativeEntity.class, area,
                n -> village.heart().equals(n.heart()) && n.isFighter())) {
            other.setTarget(player);
        }
    }

    public static void onNativeKilled(NativeEntity native_, Player player) {
        NativeVillages.Village village = villageOf(native_);
        if (village == null) {
            return;
        }
        if (!village.isOwnedBy(player.getUUID())) {
            village.addReputation(player.getUUID(), -25);
        }
        if (native_.role() == NativeEntity.Role.CHIEF && !village.isOwnedBy(player.getUUID())) {
            conquer(native_.level(), village, player);
        }
    }

    /** Defeating the chief: the village surrenders to the player. */
    private static void conquer(net.minecraft.world.level.Level level, NativeVillages.Village village, Player player) {
        String previous = village.isOwned() ? village.ownerName() : null;
        village.setOwner(player.getUUID(), player.getGameProfile().getName());
        String name = village.tribe().displayName();
        announce(level, village.heart(), Component.literal(player.getGameProfile().getName() + " has conquered the " + name
                + " village" + (previous != null ? " from " + previous : "") + "! It is now part of their colony.")
                .withStyle(ChatFormatting.GOLD));
        AABB area = new AABB(village.heart()).inflate(48);
        for (NativeEntity other : level.getEntitiesOfClass(NativeEntity.class, area, n -> village.heart().equals(n.heart()))) {
            if (other.getTarget() == player) {
                other.setTarget(null);
            }
        }
        level.playSound(null, village.heart(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0F, 1.0F);
    }

    // ---- war parties -------------------------------------------------------------------------

    /** Called about once a minute per dimension. */
    static void tickRaids(ServerLevel level) {
        NativeVillages data = NativeVillages.get(level);
        long now = level.getGameTime();
        RandomSource random = level.getRandom();
        for (NativeVillages.Village village : List.copyOf(data.all())) {
            if (village.isOwned() || now - village.lastRaid() < RAID_COOLDOWN) {
                continue;
            }
            for (ServerPlayer player : level.players()) {
                if (player.isCreative() || player.isSpectator()
                        || village.reputation(player.getUUID()) > RAID_REPUTATION
                        || player.blockPosition().distSqr(village.heart()) > (double) RAID_RANGE * RAID_RANGE) {
                    continue;
                }
                if (random.nextDouble() < NativesConfig.RAID_CHANCE.get()) {
                    sendWarParty(level, village, player, random);
                    village.setLastRaid(now);
                    break;
                }
            }
        }
    }

    private static void sendWarParty(ServerLevel level, NativeVillages.Village village, ServerPlayer player, RandomSource random) {
        int count = 2 + random.nextInt(3);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = 16 + random.nextInt(9);
            int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            if (!level.isLoaded(new BlockPos(x, 0, z))) {
                continue;
            }
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (!level.getFluidState(pos.below()).isEmpty()) {
                continue;
            }
            NativeEntity warrior = ModRegistry.NATIVE.get().create(level);
            if (warrior == null) {
                continue;
            }
            warrior.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
            warrior.setup(village.tribe(), NativeEntity.Role.WARRIOR, village.heart());
            warrior.makeRaider();
            warrior.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            warrior.setTarget(player);
            level.addFreshEntity(warrior);
            spawned++;
        }
        if (spawned > 0) {
            village.setHostile(player.getUUID(), level.getGameTime() + HOSTILE_TICKS);
            player.displayClientMessage(Component.literal("A " + village.tribe().displayName()
                    + " war party is coming for you!").withStyle(ChatFormatting.DARK_RED), false);
            level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.5F, 1.2F);
        }
    }

    static void announce(net.minecraft.world.level.Level level, BlockPos center, Component message) {
        for (Player player : level.players()) {
            if (player.blockPosition().distSqr(center) <= 96.0 * 96.0) {
                player.displayClientMessage(message, false);
            }
        }
    }
}
