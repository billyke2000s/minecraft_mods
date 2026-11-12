package com.billyke2000s.natives.world;

import com.billyke2000s.natives.ModRegistry;
import com.billyke2000s.natives.Tribe;
import com.billyke2000s.natives.entity.NativeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Builds a native camp: a Village Heart and campfire in a cleared plaza, ringed by huts, plus its people. */
public final class CampBuilder {
    private static final int PLAZA_RADIUS = 9;
    private static final int HUT_OFFSET = 6;
    private static final int MAX_SLOPE = 4;
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private CampBuilder() {
    }

    /**
     * @param heightmap {@code WORLD_SURFACE_WG} during world generation, {@code WORLD_SURFACE} afterwards
     * @return the Village Heart position, or null if the ground was unsuitable
     */
    public static BlockPos build(WorldGenLevel level, BlockPos origin, Tribe tribe, RandomSource random, Heightmap.Types heightmap) {
        int cx = origin.getX();
        int cz = origin.getZ();
        int centre = level.getHeight(heightmap, cx, cz) - 1;
        if (!level.getFluidState(new BlockPos(cx, centre, cz)).isEmpty()
                || !level.getFluidState(new BlockPos(cx, centre + 1, cz)).isEmpty()) {
            return null;
        }
        // Reject steep or watery ground.
        int[][] probes = {{PLAZA_RADIUS, 0}, {-PLAZA_RADIUS, 0}, {0, PLAZA_RADIUS}, {0, -PLAZA_RADIUS},
                {HUT_OFFSET, HUT_OFFSET}, {-HUT_OFFSET, -HUT_OFFSET}, {HUT_OFFSET, -HUT_OFFSET}, {-HUT_OFFSET, HUT_OFFSET}};
        for (int[] probe : probes) {
            int y = level.getHeight(heightmap, cx + probe[0], cz + probe[1]) - 1;
            if (Math.abs(y - centre) > MAX_SLOPE
                    || !level.getFluidState(new BlockPos(cx + probe[0], y, cz + probe[1])).isEmpty()) {
                return null;
            }
        }

        BlockState ground = groundFor(tribe);
        // Level the plaza: solid floor at `centre`, open air above.
        for (int dx = -PLAZA_RADIUS; dx <= PLAZA_RADIUS; dx++) {
            for (int dz = -PLAZA_RADIUS; dz <= PLAZA_RADIUS; dz++) {
                if (dx * dx + dz * dz > PLAZA_RADIUS * PLAZA_RADIUS) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                for (int y = centre - 3; y < centre; y++) {
                    BlockPos fill = new BlockPos(x, y, z);
                    if (level.getBlockState(fill).canBeReplaced()) {
                        level.setBlock(fill, Blocks.DIRT.defaultBlockState(), FLAGS);
                    }
                }
                BlockPos floor = new BlockPos(x, centre, z);
                BlockState current = level.getBlockState(floor);
                if (current.canBeReplaced() || current.is(Blocks.GRASS_BLOCK) || current.is(Blocks.DIRT)
                        || current.is(Blocks.SNOW_BLOCK) || current.is(Blocks.SAND) || current.is(Blocks.PODZOL)
                        || current.is(Blocks.COARSE_DIRT) || current.is(Blocks.MUD)) {
                    if (random.nextInt(4) != 0 || current.canBeReplaced()) {
                        level.setBlock(floor, ground, FLAGS);
                    }
                }
                for (int y = centre + 1; y <= centre + 7; y++) {
                    BlockPos air = new BlockPos(x, y, z);
                    if (!level.getBlockState(air).isAir()) {
                        level.setBlock(air, Blocks.AIR.defaultBlockState(), FLAGS);
                    }
                }
            }
        }

        BlockPos heart = new BlockPos(cx, centre + 1, cz);
        level.setBlock(heart, ModRegistry.VILLAGE_HEART.get().defaultBlockState(), FLAGS);
        level.setBlock(heart.offset(3, 0, 3), Blocks.CAMPFIRE.defaultBlockState(), FLAGS);

        BlockPos[] huts = {
                new BlockPos(cx + HUT_OFFSET, centre, cz),
                new BlockPos(cx - HUT_OFFSET, centre, cz),
                new BlockPos(cx, centre, cz + HUT_OFFSET),
                new BlockPos(cx, centre, cz - HUT_OFFSET)};
        Direction[] doors = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
        int hutCount = 3 + random.nextInt(2);
        for (int i = 0; i < hutCount; i++) {
            if (tribe.hutStyle() == Tribe.HutStyle.DOME) {
                buildDome(level, huts[i], doors[i], tribe);
            } else {
                buildHut(level, huts[i], doors[i], tribe);
            }
        }

        spawn(level, heart, tribe, NativeEntity.Role.CHIEF, random);
        int warriors = 2 + random.nextInt(2);
        for (int i = 0; i < warriors; i++) {
            spawn(level, heart, tribe, NativeEntity.Role.WARRIOR, random);
        }
        int villagers = 3 + random.nextInt(2);
        for (int i = 0; i < villagers; i++) {
            spawn(level, heart, tribe, NativeEntity.Role.VILLAGER, random);
        }
        return heart;
    }

    private static BlockState groundFor(Tribe tribe) {
        return switch (tribe) {
            case DUNEKIN -> Blocks.SAND.defaultBlockState();
            case FROSTMARK -> Blocks.SNOW_BLOCK.defaultBlockState();
            case VERDANI, PINEMARK -> Blocks.COARSE_DIRT.defaultBlockState();
            case REDGRASS -> Blocks.DIRT_PATH.defaultBlockState();
        };
    }

    /** A 5x5 hut with corner posts, walls, a two-step roof and a doorway facing the plaza. */
    private static void buildHut(WorldGenLevel level, BlockPos floorCentre, Direction door, Tribe tribe) {
        BlockState wall = tribe.wall().defaultBlockState();
        BlockState roof = tribe.roof().defaultBlockState();
        BlockState frame = tribe.frame().defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                for (int dy = 1; dy <= 3; dy++) {
                    BlockPos pos = floorCentre.offset(dx, dy, dz);
                    if (corner) {
                        level.setBlock(pos, frame, FLAGS);
                    } else if (edge) {
                        level.setBlock(pos, isDoorway(dx, dz, dy, door) ? Blocks.AIR.defaultBlockState() : wall, FLAGS);
                    } else {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                    }
                }
                level.setBlock(floorCentre.offset(dx, 4, dz), roof, FLAGS);
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    level.setBlock(floorCentre.offset(dx, 5, dz), roof, FLAGS);
                }
            }
        }
        level.setBlock(floorCentre.offset(0, 6, 0), roof, FLAGS);
    }

    /** A snow dome about 5 blocks across with a doorway facing the plaza. */
    private static void buildDome(WorldGenLevel level, BlockPos floorCentre, Direction door, Tribe tribe) {
        BlockState wall = tribe.wall().defaultBlockState();
        double outer = 2.9;
        double inner = 1.9;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 1; dy <= 3; dy++) {
                    double d = Math.sqrt(dx * dx + (dy - 1) * (dy - 1) * 1.2 + dz * dz);
                    BlockPos pos = floorCentre.offset(dx, dy, dz);
                    if (d <= inner || isDoorway(dx, dz, dy, door, 2)) {
                        if (d <= outer) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                        }
                    } else if (d <= outer) {
                        level.setBlock(pos, wall, FLAGS);
                    }
                }
            }
        }
        level.setBlock(floorCentre.relative(door.getOpposite(), 1).above(), Blocks.WHITE_CARPET.defaultBlockState(), FLAGS);
    }

    private static boolean isDoorway(int dx, int dz, int dy, Direction door) {
        return isDoorway(dx, dz, dy, door, 2);
    }

    private static boolean isDoorway(int dx, int dz, int dy, Direction door, int reach) {
        if (dy > 2) {
            return false;
        }
        return switch (door) {
            case WEST -> dz == 0 && dx <= -reach + 1 && dx >= -reach;
            case EAST -> dz == 0 && dx >= reach - 1 && dx <= reach;
            case NORTH -> dx == 0 && dz <= -reach + 1 && dz >= -reach;
            case SOUTH -> dx == 0 && dz >= reach - 1 && dz <= reach;
            default -> false;
        };
    }

    private static void spawn(WorldGenLevel level, BlockPos heart, Tribe tribe, NativeEntity.Role role, RandomSource random) {
        NativeEntity native_ = ModRegistry.NATIVE.get().create(level.getLevel());
        if (native_ == null) {
            return;
        }
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = 2.0 + random.nextDouble() * 2.5;
        double x = heart.getX() + 0.5 + Math.cos(angle) * dist;
        double z = heart.getZ() + 0.5 + Math.sin(angle) * dist;
        native_.moveTo(x, heart.getY(), z, random.nextFloat() * 360.0F, 0.0F);
        native_.setup(tribe, role, heart);
        native_.finalizeSpawn(level, level.getCurrentDifficultyAt(heart), MobSpawnType.STRUCTURE, null);
        level.addFreshEntityWithPassengers(native_);
    }
}
