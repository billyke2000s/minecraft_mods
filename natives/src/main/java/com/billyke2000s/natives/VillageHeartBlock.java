package com.billyke2000s.natives;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The centre of a native village. Right-click to see the village's status; right-click with a Colony
 * Flag on a village you own to claim the land around it.
 */
public class VillageHeartBlock extends Block {
    public static final MapCodec<VillageHeartBlock> CODEC = simpleCodec(VillageHeartBlock::new);

    public VillageHeartBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    static NativeVillages.Village village(ServerLevel level, BlockPos pos) {
        NativeVillages data = NativeVillages.get(level);
        NativeVillages.Village village = data.at(pos);
        if (village == null) {
            Tribe tribe = Tribe.forBiome(level.getBiome(pos));
            village = data.getOrCreate(pos, tribe == null ? Tribe.PINEMARK : tribe);
        }
        return village;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(ModRegistry.COLONY_FLAG.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel serverLevel) {
            NativeVillages.Village village = village(serverLevel, pos);
            if (!village.isOwnedBy(player.getUUID())) {
                player.displayClientMessage(Component.literal("You must ally with or conquer this village before claiming its land.")
                        .withStyle(ChatFormatting.RED), true);
            } else if (village.claimed()) {
                player.displayClientMessage(Component.literal("This land is already claimed for your colony."), true);
            } else {
                village.setClaimed(true);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                int radius = NativesConfig.CLAIM_RADIUS.get();
                VillageLogic.announce(level, pos, Component.literal(player.getGameProfile().getName() + " has claimed the land around the "
                        + village.tribe().displayName() + " village (" + radius + " blocks) for their colony.")
                        .withStyle(ChatFormatting.GOLD));
                level.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel) {
            NativeVillages.Village village = village(serverLevel, pos);
            int rep = village.reputation(player.getUUID());
            player.sendSystemMessage(Component.literal("— " + village.tribe().displayName() + " village —").withStyle(ChatFormatting.GOLD));
            if (village.isOwned()) {
                player.sendSystemMessage(Component.literal("Colony of " + village.ownerName()
                        + (village.claimed() ? ", land claimed (" + NativesConfig.CLAIM_RADIUS.get() + " blocks)" : ", land not yet claimed")));
            } else {
                player.sendSystemMessage(Component.literal("Independent. Your trust: " + rep + " / "
                        + NativesConfig.ALLIANCE_REPUTATION.get()));
                player.sendSystemMessage(Component.literal("Trade emeralds or " + village.tribe().favourite().getDescription().getString()
                        + " to win them over, or defeat their chief.").withStyle(ChatFormatting.GRAY));
            }
            if (village.isHostileTo(player.getUUID(), level.getGameTime())) {
                player.sendSystemMessage(Component.literal("They are at war with you.").withStyle(ChatFormatting.RED));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            NativeVillages.get(serverLevel).remove(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
