package com.billyke2000s.natives.world;

import com.billyke2000s.natives.ModRegistry;
import com.billyke2000s.natives.Tribe;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/**
 * Adds the camp feature to every biome a tribe lives in. Done in code rather than with a biome tag so
 * modded biomes (e.g. Moderner Beta's) are picked up by name.
 */
public record NativeCampBiomeModifier(Holder<PlacedFeature> feature) implements BiomeModifier {
    public static final MapCodec<NativeCampBiomeModifier> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            PlacedFeature.CODEC.fieldOf("feature").forGetter(NativeCampBiomeModifier::feature)
    ).apply(instance, NativeCampBiomeModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase == Phase.ADD && Tribe.forBiome(biome) != null) {
            builder.getGenerationSettings().addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, feature);
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return ModRegistry.NATIVE_CAMPS_MODIFIER.get();
    }
}
