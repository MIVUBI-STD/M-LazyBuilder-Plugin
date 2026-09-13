package com.halokaryamedia.lazybuilder.client;

import net.minecraft.block.MapColor;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Persistent, bounded client-side world-map memory for already-seen terrain.
 *
 * <p>This is presentation state only. It never loads chunks and never becomes
 * authoritative for server world state. New columns are queued while the map
 * renders and sampled with a per-frame budget. Samples survive screen closes
 * and reconnects per managed-world + dimension scope.</p>
 *
 * <p>Far zoom levels derive a small multi-sample LOD pixel from the same base
 * column cache instead of taking one isolated block for a very large map cell.
 * That keeps roads, shorelines and large structures readable while preserving
 * one canonical terrain-memory owner.</p>
 */
public final class ClientMapSurfaceCache {
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_COLUMNS = 262_144;
    private static final int MAX_PENDING = 262_144;
    public static final int UNEXPLORED_COLOR = 0xFF101419;

    private final Map<Long, SurfaceSample> samples = new LinkedHashMap<>(8192, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, SurfaceSample> eldest) {
            return size() > MAX_COLUMNS;
        }
    };
    private final LinkedHashSet<Long> pending = new LinkedHashSet<>();

    private String scope = "";
    private Path scopeFile;
    private boolean dirty;

    public void useScope(String scope, Path storageRoot) {
        String normalized = Objects.requireNonNullElse(scope, "");
        if (this.scope.equals(normalized)) return;

        flushAsync();
        this.scope = normalized;
        boolean identifiedManagedWorld = !normalized.startsWith("unmanaged|");
        this.scopeFile = !identifiedManagedWorld || normalized.isBlank() || storageRoot == null
                ? null
                : storageRoot.resolve(safeName(normalized) + ".surface.gz");
        samples.clear();
        pending.clear();
        dirty = false;
        load();
    }

    /** Returns a remembered sample immediately and queues missing loaded terrain. */
    public SurfaceSample sample(ClientWorld world, int blockX, int blockZ) {
        long key = pack(blockX, blockZ);
        SurfaceSample cached = samples.get(key);
        if (cached != null) return cached;

        queueIfLoaded(world, blockX, blockZ, key);
        return SurfaceSample.UNEXPLORED;
    }

    /**
     * Produces a stable map LOD pixel from the canonical base-column cache.
     *
     * <p>The renderer waits for a majority of the five-point footprint before
     * switching to the aggregate colour. Until then it keeps the centre sample
     * (when available). This prevents far-zoom cells from visibly changing
     * colour several times while their surrounding samples arrive.</p>
     */
    public SurfaceSample sampleArea(ClientWorld world, int blockX, int blockZ, int span) {
        if (span <= 2) return sample(world, blockX, blockZ);

        int offset = Math.max(1, span / 3);
        SurfaceSample center = sample(world, blockX, blockZ);
        SurfaceSample nw = sample(world, blockX - offset, blockZ - offset);
        SurfaceSample ne = sample(world, blockX + offset, blockZ - offset);
        SurfaceSample sw = sample(world, blockX - offset, blockZ + offset);
        SurfaceSample se = sample(world, blockX + offset, blockZ + offset);

        SurfaceSample[] footprint = {center, nw, ne, sw, se};
        int explored = exploredCount(footprint);
        if (explored < 3) return center.explored() ? center : SurfaceSample.UNEXPLORED;
        return blend(footprint);
    }

    /** Samples a bounded amount of queued terrain on the client thread. */
    public int processPending(ClientWorld world, int budget) {
        if (budget <= 0 || pending.isEmpty()) return 0;
        int processed = 0;
        var iterator = pending.iterator();
        while (iterator.hasNext() && processed < budget) {
            long key = iterator.next();
            iterator.remove();
            int x = unpackX(key);
            int z = unpackZ(key);
            if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) continue;

            samples.put(key, readSurface(world, x, z));
            dirty = true;
            processed++;
        }
        return processed;
    }

    public int pendingCount() {
        return pending.size();
    }

    public int size() {
        return samples.size();
    }

    public void flushAsync() {
        if (!dirty || scopeFile == null || samples.isEmpty()) return;
        Path destination = scopeFile;
        Map<Long, SurfaceSample> snapshot = new HashMap<>(samples);
        dirty = false;
        CompletableFuture.runAsync(() -> writeSnapshot(destination, snapshot));
    }

    private void queueIfLoaded(ClientWorld world, int blockX, int blockZ, long key) {
        if (pending.size() >= MAX_PENDING) return;
        if (!world.getChunkManager().isChunkLoaded(blockX >> 4, blockZ >> 4)) return;
        pending.add(key);
    }

    private static int exploredCount(SurfaceSample... values) {
        int count = 0;
        for (SurfaceSample value : values) {
            if (value != null && value.explored()) count++;
        }
        return count;
    }

    private static SurfaceSample blend(SurfaceSample... values) {
        long red = 0;
        long green = 0;
        long blue = 0;
        long height = 0;
        int count = 0;

        for (SurfaceSample value : values) {
            if (value == null || !value.explored()) continue;
            int color = value.color();
            red += (color >>> 16) & 0xFF;
            green += (color >>> 8) & 0xFF;
            blue += color & 0xFF;
            height += value.height();
            count++;
        }
        if (count == 0) return SurfaceSample.UNEXPLORED;

        int color = 0xFF000000
                | ((int) (red / count) << 16)
                | ((int) (green / count) << 8)
                | (int) (blue / count);
        return new SurfaceSample(color, (int) (height / count), true);
    }

    private static SurfaceSample readSurface(ClientWorld world, int x, int z) {
        int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        int blockY = Math.max(world.getBottomY(), topY - 1);
        BlockPos pos = new BlockPos(x, blockY, z);
        BlockState state = world.getBlockState(pos);
        MapColor mapColor = state.getMapColor(world, pos);
        int color = mapColor == MapColor.CLEAR
                ? UNEXPLORED_COLOR
                : mapColor.getRenderColor(MapColor.Brightness.NORMAL);

        // Do not query relief neighbours across unloaded chunk boundaries. Map
        // presentation must never cause terrain loads merely for shading.
        int westX = x - 1;
        int northZ = z - 1;
        boolean westLoaded = world.getChunkManager().isChunkLoaded(westX >> 4, z >> 4);
        boolean northLoaded = world.getChunkManager().isChunkLoaded(x >> 4, northZ >> 4);
        if (westLoaded && northLoaded) {
            int west = world.getTopY(Heightmap.Type.WORLD_SURFACE, westX, z);
            int north = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, northZ);
            int relief = (topY - west) + (topY - north);
            if (relief >= 2) color = shade(color, 1.12);
            else if (relief <= -2) color = shade(color, 0.82);
            else if (relief > 0) color = shade(color, 1.05);
            else if (relief < 0) color = shade(color, 0.92);
        }

        return new SurfaceSample(color, topY, true);
    }

    private static int shade(int argb, double factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, (int) Math.round(((argb >>> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) Math.round(((argb >>> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) Math.round((argb & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void load() {
        if (scopeFile == null || !Files.isRegularFile(scopeFile)) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(scopeFile))))) {
            if (in.readInt() != FORMAT_VERSION) return;
            int count = Math.min(MAX_COLUMNS, Math.max(0, in.readInt()));
            for (int i = 0; i < count; i++) {
                long key = in.readLong();
                int color = in.readInt();
                int height = in.readInt();
                samples.put(key, new SurfaceSample(color, height, true));
            }
        } catch (IOException ignored) {
            samples.clear();
        }
    }

    private static void writeSnapshot(Path destination, Map<Long, SurfaceSample> snapshot) {
        Path parent = destination.getParent();
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        try {
            if (parent != null) Files.createDirectories(parent);
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(temporary))))) {
                out.writeInt(FORMAT_VERSION);
                out.writeInt(snapshot.size());
                for (Map.Entry<Long, SurfaceSample> entry : snapshot.entrySet()) {
                    out.writeLong(entry.getKey());
                    out.writeInt(entry.getValue().color());
                    out.writeInt(entry.getValue().height());
                }
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            try { Files.deleteIfExists(temporary); } catch (IOException ignoredAgain) { }
        }
    }

    private static String safeName(String value) {
        String safe = value.replaceAll("[^A-Za-z0-9._-]+", "_");
        return safe.length() > 160 ? safe.substring(0, 160) : safe;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }

    public record SurfaceSample(int color, int height, boolean explored) {
        private static final SurfaceSample UNEXPLORED =
                new SurfaceSample(UNEXPLORED_COLOR, Integer.MIN_VALUE, false);
    }
}
