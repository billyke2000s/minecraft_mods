package com.billyke2000s.natives;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-tunable numbers. Lives in config/natives-common.toml. */
public final class NativesConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue FIGHT_BACK_CHANCE = BUILDER
            .comment("Chance (0-1) that a village takes up arms when a player attacks one of its people.",
                    "If the roll fails, the villagers flee instead.")
            .defineInRange("fightBackChance", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue RAID_CHANCE = BUILDER
            .comment("Chance (0-1), checked about once a minute, that a village which hates a nearby player sends a war party.")
            .defineInRange("raidChance", 0.25, 0.0, 1.0);

    public static final ModConfigSpec.IntValue ALLIANCE_REPUTATION = BUILDER
            .comment("Reputation needed with an unowned village before it allies with you and joins your colony.")
            .defineInRange("allianceReputation", 100, 1, 10000);

    public static final ModConfigSpec.IntValue CLAIM_RADIUS = BUILDER
            .comment("Radius in blocks of the land protected after a Colony Flag is planted on the Village Heart.")
            .defineInRange("claimRadius", 48, 8, 256);

    public static final ModConfigSpec.IntValue CORE_RADIUS = BUILDER
            .comment("Radius around an unclaimed village's heart where breaking blocks angers the tribe.")
            .defineInRange("coreRadius", 12, 4, 64);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private NativesConfig() {
    }
}
