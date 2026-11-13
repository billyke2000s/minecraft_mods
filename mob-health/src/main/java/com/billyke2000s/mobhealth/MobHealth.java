package com.billyke2000s.mobhealth;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Shows the same label Damage Health puts over injured players (a red heart and the hearts left, in white)
 * over every other injured mob. Like Damage Health, each label is an invisible marker armour stand that
 * follows the mob, so it is entirely server-side and players need nothing installed.
 */
@Mod(MobHealth.MODID)
public final class MobHealth {
    public static final String MODID = "mobhealth";
    private static final String LABEL_TAG = "mobhealth_label";
    /** Above the head, at the same spacing Damage Health uses for players (2.55 over a 1.8 tall player). */
    private static final double HEAD_GAP = 0.75;
    private static final float EPSILON = 0.001F;

    /** Injured mobs and their label; the label is created on the next tick if it is still null. */
    private final Map<LivingEntity, ArmorStand> labels = new IdentityHashMap<>();
    /** Labels this mod has spawned and is about to add, so the join check does not cancel them. */
    private ArmorStand spawning;

    public MobHealth() {
        NeoForge.EVENT_BUS.register(this);
    }

    private static boolean eligible(Entity entity) {
        return entity instanceof LivingEntity && !(entity instanceof Player) && !(entity instanceof ArmorStand);
    }

    @SubscribeEvent
    public void onDamaged(LivingDamageEvent.Post event) {
        LivingEntity mob = event.getEntity();
        if (eligible(mob) && mob.level() instanceof ServerLevel) {
            labels.putIfAbsent(mob, null);
        }
    }

    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof ArmorStand && entity.getTags().contains(LABEL_TAG) && entity != spawning) {
            // A label saved with the chunk from an earlier session: drop it, a fresh one follows its mob.
            event.setCanceled(true);
            return;
        }
        if (eligible(entity)) {
            LivingEntity mob = (LivingEntity) entity;
            if (mob.getHealth() < mob.getMaxHealth() - EPSILON) {
                labels.putIfAbsent(mob, null);
            }
        }
    }

    @SubscribeEvent
    public void onTick(ServerTickEvent.Post event) {
        if (labels.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<LivingEntity, ArmorStand>> it = labels.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<LivingEntity, ArmorStand> entry = it.next();
            LivingEntity mob = entry.getKey();
            ArmorStand label = entry.getValue();
            boolean show = !mob.isRemoved() && mob.isAlive() && !mob.isInvisible()
                    && mob.getHealth() < mob.getMaxHealth() - EPSILON
                    && mob.level() instanceof ServerLevel;
            if (!show) {
                if (label != null) {
                    label.discard();
                }
                it.remove();
                continue;
            }
            ServerLevel level = (ServerLevel) mob.level();
            double x = mob.getX();
            double y = mob.getY() + mob.getBbHeight() + HEAD_GAP;
            double z = mob.getZ();
            Component text = healthText(mob);
            if (label == null || label.isRemoved() || label.level() != level) {
                if (label != null) {
                    label.discard();
                }
                label = createLabel(level, x, y, z, text);
                entry.setValue(label);
                continue;
            }
            if (label.getX() != x || label.getY() != y || label.getZ() != z) {
                label.moveTo(x, y, z, 0.0F, 0.0F);
            }
            if (!text.equals(label.getCustomName())) {
                label.setCustomName(text);
            }
        }
    }

    @SubscribeEvent
    public void onStopping(ServerStoppingEvent event) {
        for (ArmorStand label : new ArrayList<>(labels.values())) {
            if (label != null) {
                label.discard();
            }
        }
        labels.clear();
    }

    private ArmorStand createLabel(ServerLevel level, double x, double y, double z, Component text) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:armor_stand");
        tag.putBoolean("Marker", true);
        tag.putBoolean("Invisible", true);
        tag.putBoolean("NoGravity", true);
        tag.putBoolean("Invulnerable", true);
        tag.putBoolean("Silent", true);
        tag.putBoolean("CustomNameVisible", true);
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(LABEL_TAG));
        tag.put("Tags", tags);
        Entity created = EntityType.create(tag, level).orElse(null);
        if (!(created instanceof ArmorStand label)) {
            return null;
        }
        label.moveTo(x, y, z, 0.0F, 0.0F);
        label.setCustomName(text);
        spawning = label;
        try {
            level.addFreshEntity(label);
        } finally {
            spawning = null;
        }
        return label;
    }

    /** Same look as Damage Health: a red heart, then hearts left in white (one decimal, none if whole). */
    private static Component healthText(LivingEntity mob) {
        double hearts = Math.max(0.0, Math.round(mob.getHealth() / 2.0 * 10.0) / 10.0);
        String value = hearts == Math.rint(hearts)
                ? Integer.toString((int) hearts)
                : String.format(Locale.ROOT, "%.1f", hearts);
        return Component.literal("❤ ").withStyle(ChatFormatting.RED)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }
}
