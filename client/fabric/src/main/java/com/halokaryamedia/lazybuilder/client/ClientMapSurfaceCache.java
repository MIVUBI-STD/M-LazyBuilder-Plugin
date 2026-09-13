package com.halokaryamedia.lazybuilder.client;

import net.minecraft.block.MapColor;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded presentation cache for already-loaded client terrain.
 *
 * <p>The cache never owns world truth and never loads chunks. It only avoids
 * re-sampling the same visible surface columns every frame. Missing/unloaded
 * chunks are rendered as unexplored. Scope changes clear cached columns so two
 * managed worlds or dimensions can never share terrain accidentally.</p>
 */
public final class ClientMapSurfaceCache {
    private static final int MAX_COLUMNS = 65_536;
    public static final int UNEXPLORED_COLOR = 0xFF12161B;

    private final Map<Long, SurfaceSample> samples = new LinkedHashMap<>(4096, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, SurfaceSample> eldest) {
            return size() > MAX_COLUMNS;
        }
    };
    private String scope = "";

    public void useScope(String scope) {
        String normalized = Objects.requireNonNullElse(scope, "");
        if (this.scope.equals(normalized)) return;
        this.scope = normalized;
        samples.clear();
    }

    public SurfaceSample sample(ClientWorld world, int blockX, int blockZ) {
        if (!world.getChunkManager().isChunkLoaded(blockX >> 4, blockZ >> 4)) {
            return SurfaceSample.UNEXPLORED;
        }
        long key = pack(blockX, blockZ);
        SurfaceSample cached = samples.get(key);
        if (cached != null) return cached;

        int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, blockX, blockZ);
        BlockPos pos = new BlockPos(blockX, Math.max(world.getBottomY(), topY - 1), blockZ);
        BlockState state = world.getBlockState(pos);
        MapColor mapColor = state.getMapColor(world, pos);
        int color = mapColor == MapColor.CLEAR
                ? UNEXPLORED_COLOR
                : mapColor.getRenderColor(MapColor.Brightness.NORMAL);

        SurfaceSample created = new SurfaceSample(color, topY, true);
        samples.put(key, created);
        return created;
    }

    public void clear() {
        samples.clear();
    }

    public int size() {
        return samples.size();
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    public record SurfaceSample(int color, int height, boolean explored) {
        private static final SurfaceSample UNEXPLORED =
                new SurfaceSample(UNEXPLORED_COLOR, Integer.MIN_VALUE, false);
    }
}
