package com.billyke2000s.natives;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every native village in a dimension, keyed by the position of its Village Heart. Only touched from
 * the server thread (world generation never writes here; villages register when their people tick).
 */
public final class NativeVillages extends SavedData {
    private static final String NAME = "natives_villages";

    private final Map<Long, Village> villages = new HashMap<>();

    public static NativeVillages get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NativeVillages::new, NativeVillages::load, null), NAME);
    }

    @Nullable
    public Village at(BlockPos heart) {
        return villages.get(heart.asLong());
    }

    public Village getOrCreate(BlockPos heart, Tribe tribe) {
        Village village = villages.get(heart.asLong());
        if (village == null) {
            village = new Village(this, heart.immutable(), tribe);
            villages.put(heart.asLong(), village);
            setDirty();
        }
        return village;
    }

    public void remove(BlockPos heart) {
        if (villages.remove(heart.asLong()) != null) {
            setDirty();
        }
    }

    public Collection<Village> all() {
        return villages.values();
    }

    /** The nearest village whose heart is within {@code radius} blocks of {@code pos}. */
    @Nullable
    public Village nearest(BlockPos pos, int radius) {
        Village best = null;
        double bestDist = (double) radius * radius;
        for (Village village : villages.values()) {
            double dist = village.heart.distSqr(pos);
            if (dist <= bestDist) {
                bestDist = dist;
                best = village;
            }
        }
        return best;
    }

    private static NativeVillages load(CompoundTag tag, HolderLookup.Provider registries) {
        NativeVillages data = new NativeVillages();
        ListTag list = tag.getList("Villages", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Village village = Village.load(data, list.getCompound(i));
            data.villages.put(village.heart.asLong(), village);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Village village : villages.values()) {
            list.add(village.save());
        }
        tag.put("Villages", list);
        return tag;
    }

    public static final class Village {
        private final NativeVillages data;
        private final BlockPos heart;
        private final Tribe tribe;
        @Nullable
        private UUID owner;
        private String ownerName = "";
        private boolean claimed;
        private long lastRaid;
        private final Map<UUID, Integer> reputation = new HashMap<>();
        private final Map<UUID, Long> hostileUntil = new HashMap<>();

        private Village(NativeVillages data, BlockPos heart, Tribe tribe) {
            this.data = data;
            this.heart = heart;
            this.tribe = tribe;
        }

        public BlockPos heart() { return heart; }
        public Tribe tribe() { return tribe; }
        @Nullable public UUID owner() { return owner; }
        public String ownerName() { return ownerName; }
        public boolean isOwned() { return owner != null; }
        public boolean isOwnedBy(UUID player) { return player.equals(owner); }
        public boolean claimed() { return claimed; }
        public long lastRaid() { return lastRaid; }

        public void setOwner(UUID player, String name) {
            owner = player;
            ownerName = name;
            hostileUntil.remove(player);
            setDirty();
        }

        public void setClaimed(boolean value) {
            claimed = value;
            setDirty();
        }

        public void setLastRaid(long gameTime) {
            lastRaid = gameTime;
            setDirty();
        }

        public int reputation(UUID player) {
            return reputation.getOrDefault(player, 0);
        }

        public int addReputation(UUID player, int amount) {
            int value = Math.max(-1000, Math.min(1000, reputation(player) + amount));
            reputation.put(player, value);
            setDirty();
            return value;
        }

        public Map<UUID, Integer> reputations() {
            return reputation;
        }

        public boolean isHostileTo(UUID player, long gameTime) {
            if (player.equals(owner)) {
                return false;
            }
            Long until = hostileUntil.get(player);
            return until != null && until > gameTime;
        }

        public void setHostile(UUID player, long untilGameTime) {
            hostileUntil.put(player, untilGameTime);
            setDirty();
        }

        public void clearHostile(UUID player) {
            if (hostileUntil.remove(player) != null) {
                setDirty();
            }
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Heart", heart.asLong());
            tag.putString("Tribe", tribe.id());
            if (owner != null) {
                tag.putUUID("Owner", owner);
                tag.putString("OwnerName", ownerName);
            }
            tag.putBoolean("Claimed", claimed);
            tag.putLong("LastRaid", lastRaid);
            ListTag rep = new ListTag();
            reputation.forEach((uuid, value) -> {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("Player", uuid);
                entry.putInt("Value", value);
                rep.add(entry);
            });
            tag.put("Reputation", rep);
            ListTag hostile = new ListTag();
            hostileUntil.forEach((uuid, until) -> {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("Player", uuid);
                entry.putLong("Until", until);
                hostile.add(entry);
            });
            tag.put("Hostile", hostile);
            return tag;
        }

        private static Village load(NativeVillages data, CompoundTag tag) {
            Tribe tribe = Tribe.byId(tag.getString("Tribe"));
            Village village = new Village(data, BlockPos.of(tag.getLong("Heart")), tribe == null ? Tribe.PINEMARK : tribe);
            if (tag.hasUUID("Owner")) {
                village.owner = tag.getUUID("Owner");
                village.ownerName = tag.getString("OwnerName");
            }
            village.claimed = tag.getBoolean("Claimed");
            village.lastRaid = tag.getLong("LastRaid");
            ListTag rep = tag.getList("Reputation", Tag.TAG_COMPOUND);
            for (int i = 0; i < rep.size(); i++) {
                CompoundTag entry = rep.getCompound(i);
                village.reputation.put(entry.getUUID("Player"), entry.getInt("Value"));
            }
            ListTag hostile = tag.getList("Hostile", Tag.TAG_COMPOUND);
            for (int i = 0; i < hostile.size(); i++) {
                CompoundTag entry = hostile.getCompound(i);
                village.hostileUntil.put(entry.getUUID("Player"), entry.getLong("Until"));
            }
            return village;
        }

        private void setDirty() {
            data.setDirty();
        }
    }
}
