package com.billyke2000s.natives;

import com.billyke2000s.natives.world.CampBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;

/**
 * /natives camp [tribe]  - build a camp at your feet (ops)
 * /natives villages      - list known villages in this dimension (ops)
 * /natives info          - the nearest village and your standing with it (everyone)
 */
public final class NativesCommands {
    private NativesCommands() {
    }

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("natives")
                .then(Commands.literal("camp")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> camp(ctx, null))
                        .then(Commands.argument("tribe", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Tribe.values()).map(Tribe::id), builder))
                                .executes(ctx -> camp(ctx, StringArgumentType.getString(ctx, "tribe")))))
                .then(Commands.literal("villages")
                        .requires(source -> source.hasPermission(2))
                        .executes(NativesCommands::villages))
                .then(Commands.literal("info")
                        .executes(NativesCommands::info)));
    }

    private static int camp(CommandContext<CommandSourceStack> ctx, String tribeName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        Tribe tribe = tribeName == null ? Tribe.forBiome(level.getBiome(player.blockPosition())) : Tribe.byId(tribeName);
        if (tribe == null) {
            ctx.getSource().sendFailure(Component.literal(tribeName == null
                    ? "No tribe lives in this biome. Name one: " + String.join(", ", Arrays.stream(Tribe.values()).map(Tribe::id).toList())
                    : "Unknown tribe '" + tribeName + "'."));
            return 0;
        }
        BlockPos heart = CampBuilder.build(level, player.blockPosition(), tribe, level.getRandom(), Heightmap.Types.WORLD_SURFACE);
        if (heart == null) {
            ctx.getSource().sendFailure(Component.literal("The ground here is too steep or wet for a camp."));
            return 0;
        }
        NativeVillages.get(level).getOrCreate(heart, tribe);
        player.teleportTo(heart.getX() + 0.5, heart.getY(), heart.getZ() - 1.5);
        Tribe built = tribe;
        ctx.getSource().sendSuccess(() -> Component.literal("Built a " + built.displayName() + " camp at "
                + heart.getX() + " " + heart.getY() + " " + heart.getZ() + "."), true);
        return 1;
    }

    private static int villages(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        NativeVillages data = NativeVillages.get(level);
        if (data.all().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No native villages have been found in this dimension yet."), false);
            return 0;
        }
        for (NativeVillages.Village village : data.all()) {
            BlockPos h = village.heart();
            String status = village.isOwned() ? "colony of " + village.ownerName() + (village.claimed() ? " (claimed)" : "") : "independent";
            ctx.getSource().sendSuccess(() -> Component.literal(village.tribe().displayName() + " at " + h.getX() + " " + h.getY() + " "
                    + h.getZ() + ": " + status), false);
        }
        return data.all().size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        NativeVillages.Village village = NativeVillages.get(player.serverLevel()).nearest(player.blockPosition(), 256);
        if (village == null) {
            ctx.getSource().sendSuccess(() -> Component.literal("No known native village within 256 blocks."), false);
            return 0;
        }
        BlockPos h = village.heart();
        int rep = village.reputation(player.getUUID());
        String status = village.isOwned() ? "colony of " + village.ownerName() : "independent, your trust " + rep + " / "
                + NativesConfig.ALLIANCE_REPUTATION.get();
        ctx.getSource().sendSuccess(() -> Component.literal("Nearest: " + village.tribe().displayName() + " village at "
                + h.getX() + " " + h.getY() + " " + h.getZ() + " (" + status + ")"), false);
        return 1;
    }
}
