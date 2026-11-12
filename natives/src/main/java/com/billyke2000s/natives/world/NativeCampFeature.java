package com.billyke2000s.natives.world;

import com.billyke2000s.natives.Tribe;
import com.mojang.serialization.Codec;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** World-generation hook: builds a camp of whichever tribe lives in the biome at the origin. */
public class NativeCampFeature extends Feature<NoneFeatureConfiguration> {
    public NativeCampFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        Tribe tribe = Tribe.forBiome(context.level().getBiome(context.origin()));
        if (tribe == null) {
            return false;
        }
        return CampBuilder.build(context.level(), context.origin(), tribe, context.random(), Heightmap.Types.WORLD_SURFACE_WG) != null;
    }
}
