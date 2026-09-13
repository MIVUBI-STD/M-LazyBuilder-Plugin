package com.halokaryamedia.lazybuilder.client;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Persistent client-side world-map memory split into bounded regional files.
 *
 * <p>The cache is presentation-only: it never force-loads chunks and never
 * becomes authoritative for server world state. Visible columns are sampled
 * only when their chunks are already present on the client. Persistent data is
 * partitioned into sparse 128x128-block regions so large explored worlds do not
 * disappear when a single whole-map LRU reaches its memory limit.</p>
 *
 * <p>Only a bounded set of regions remains resident. Region files load off the
 * render thread and dirty regions are written through one ordered async write
 * lane, preventing an older snapshot from racing a newer snapshot for the same
 * file.</p>
 */
public final class ClientMapSurfaceCache {
    private static final int FORMAT_VERSION = 2;
    private static final int LEGACY_FORMAT_VERSION = 1;
    private static final int REGION_SIZE = 128;
    private static final int REGION_CAPACITY = REGION_SIZE * REGION_SIZE;
    private static final int MAX_LOADED_REGIONS = 96;
    private static final int MAX_PENDING = 262_144;
    private static final int MAX_COMPLETED_LOADS_PER_TICK = 16;

    public static final int UNEXPLORED_COLOR = 0xFF101419;

    /** Access-order LRU; all mutation happens on the Minecraft client thread. */
    private final LinkedHashMap<Long, RegionData> regions = new LinkedHashMap<>(32, 0.75f, true);
    private final LinkedHashSet<Long> pending = new LinkedHashSet<>();
    private final ConcurrentLinkedQueue<LoadedRegion> completedLoads = new ConcurrentLinkedQueue<>();

    private String scope = "";
    private Path scopeDirectory;
    private long scopeGeneration;
    private CompletableFuture<Void> writeTail = CompletableFuture.completedFuture(null);

    public void useScope(String scope, Path storageRoot) {
        String normalized = Objects.requireNonNullElse(scope, "");
        if (this.scope.equals(normalized)) return;

        flushAsync();
        this.scope = normalized;
        this.scopeGeneration++;

        boolean identifiedManagedWorld = !normalized.startsWith("unmanaged|");
        this.scopeDirectory = !identifiedManagedWorld || normalized.isBlank() || storageRoot == null
                ? null
                : storageRoot.resolve(safeName(normalized));

        regions.clear();
        pending.clear();
        completedLoads.clear();

        // Preserve map memory produced by the previous whole-scope format. The
        // migration runs once and publishes regional files through the same
        // ordered writer used for normal persistence.
        if (scopeDirectory != null) migrateLegacySnapshot(storageRoot, normalized, scopeGeneration);
    }

    /** Returns remembered terrain immediately and queues missing loaded terrain. */
    public SurfaceSample sample(ClientWorld world, int blockX, int blockZ) {
        RegionData region = regionFor(blockX, blockZ, true);
        SurfaceSample cached = region.samples.get(localIndex(blockX, blockZ));
        if (cached != null) return cached;

        queueIfLoaded(world, blockX, blockZ, pack(blockX, blockZ));
        return SurfaceSample.UNEXPLORED;
    }

    /**
     * Produces a stable far-zoom pixel from the same canonical base-column data.
     * The aggregate is used only after most of its footprint is known so newly
     * discovered terrain does not flicker through several intermediate colours.
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

    /** Samples bounded live terrain and also merges bounded async region loads. */
    public int processPending(ClientWorld world, int budget) {
        drainCompletedLoads();
        if (budget <= 0 || pending.isEmpty()) {
            pruneRegions();
            return 0;
        }

        int processed = 0;
        Iterator<Long> iterator = pending.iterator();
        while (iterator.hasNext() && processed < budget) {
            long key = iterator.next();
            iterator.remove();
            int x = unpackX(key);
            int z = unpackZ(key);
            if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) continue;

            RegionData region = regionFor(x, z, true);
            region.samples.put(localIndex(x, z), readSurface(world, x, z));
            region.dirty = true;
            processed++;
        }
        pruneRegions();
        return processed;
    }

    public int pendingCount() {
        return pending.size();
    }

    /** Number of currently resident sparse base-column samples, not total disk history. */
    public int size() {
        int total = 0;
        for (RegionData region : regions.values()) total += region.samples.size();
        return total;
    }

    /** Queues snapshots of all dirty resident regions without blocking rendering. */
    public void flushAsync() {
        if (scopeDirectory == null) return;
        for (Map.Entry<Long, RegionData> entry : regions.entrySet()) {
            RegionData region = entry.getValue();
            if (!region.dirty || region.samples.isEmpty()) continue;
            region.dirty = false;
            enqueueRegionWrite(scopeDirectory, entry.getKey(), new HashMap<>(region.samples));
        }
    }

    private RegionData regionFor(int blockX, int blockZ, boolean scheduleLoad) {
        int regionX = Math.floorDiv(blockX, REGION_SIZE);
        int regionZ = Math.floorDiv(blockZ, REGION_SIZE);
        long regionKey = pack(regionX, regionZ);
        RegionData existing = regions.get(regionKey);
        if (existing != null) return existing;

        RegionData created = new RegionData();
        regions.put(regionKey, created);
        if (scheduleLoad && scopeDirectory != null) scheduleRegionLoad(regionKey, created, scopeGeneration);
        return created;
    }

    private void scheduleRegionLoad(long regionKey, RegionData region, long generation) {
        if (region.loadScheduled) return;
        region.loadScheduled = true;
        Path file = regionFile(scopeDirectory, regionKey);
        CompletableFuture.supplyAsync(() -> readRegion(file))
                .thenAccept(samples -> completedLoads.add(new LoadedRegion(generation, regionKey, samples)));
    }

    private void drainCompletedLoads() {
        int drained = 0;
        while (drained < MAX_COMPLETED_LOADS_PER_TICK) {
            LoadedRegion loaded = completedLoads.poll();
            if (loaded == null) break;
            drained++;
            if (loaded.generation != scopeGeneration) continue;

            RegionData region = regions.get(loaded.regionKey);
            if (region == null) {
                region = new RegionData();
                region.loadScheduled = true;
                regions.put(loaded.regionKey, region);
            }
            // Live samples always win over stale disk data that completed later.
            for (Map.Entry<Integer, SurfaceSample> entry : loaded.samples.entrySet()) {
                region.samples.putIfAbsent(entry.getKey(), entry.getValue());
            }
            region.loaded = true;
        }
    }

    private void pruneRegions() {
        if (regions.size() <= MAX_LOADED_REGIONS) return;
        Iterator<Map.Entry<Long, RegionData>> iterator = regions.entrySet().iterator();
        while (regions.size() > MAX_LOADED_REGIONS && iterator.hasNext()) {
            Map.Entry<Long, RegionData> eldest = iterator.next();
            RegionData region = eldest.getValue();
            if (region.dirty && scopeDirectory != null && !region.samples.isEmpty()) {
                region.dirty = false;
                enqueueRegionWrite(scopeDirectory, eldest.getKey(), new HashMap<>(region.samples));
            }
            iterator.remove();
        }
    }

    private void queueIfLoaded(ClientWorld world, int blockX, int blockZ, long key) {
        if (pending.size() >= MAX_PENDING) return;
        if (!world.getChunkManager().isChunkLoaded(blockX >> 4, blockZ >> 4)) return;
        pending.add(key);
    }

    private void enqueueRegionWrite(Path directory, long regionKey, Map<Integer, SurfaceSample> snapshot) {
        Path destination = regionFile(directory, regionKey);
        writeTail = writeTail.handle((ignored, failure) -> null)
                .thenRunAsync(() -> writeRegion(destination, snapshot));
    }

    private void migrateLegacySnapshot(Path storageRoot, String normalizedScope, long generation) {
        Path legacy = storageRoot.resolve(safeName(normalizedScope) + ".surface.gz");
        if (!Files.isRegularFile(legacy)) return;
        Path migratedMarker = scopeDirectory.resolve(".legacy-v1-migrated");
        if (Files.exists(migratedMarker)) return;

        CompletableFuture.runAsync(() -> {
            Map<Long, Map<Integer, SurfaceSample>> partitioned = readLegacySnapshot(legacy);
            if (partitioned.isEmpty() || generation != scopeGeneration) return;
            for (Map.Entry<Long, Map<Integer, SurfaceSample>> entry : partitioned.entrySet()) {
                enqueueRegionWrite(scopeDirectory, entry.getKey(), entry.getValue());
            }
            writeTail = writeTail.handle((ignored, failure) -> null).thenRunAsync(() -> {
                try {
                    Files.createDirectories(scopeDirectory);
                    Files.writeString(migratedMarker, "v1\n");
                } catch (IOException ignored) { }
            });
        });
    }

    private static Map<Long, Map<Integer, SurfaceSample>> readLegacySnapshot(Path source) {
        Map<Long, Map<Integer, SurfaceSample>> result = new HashMap<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(source))))) {
            if (in.readInt() != LEGACY_FORMAT_VERSION) return result;
            int count = Math.max(0, Math.min(262_144, in.readInt()));
            for (int i = 0; i < count; i++) {
                long packed = in.readLong();
                int x = unpackX(packed);
                int z = unpackZ(packed);
                SurfaceSample sample = new SurfaceSample(in.readInt(), in.readInt(), true);
                long regionKey = pack(Math.floorDiv(x, REGION_SIZE), Math.floorDiv(z, REGION_SIZE));
                result.computeIfAbsent(regionKey, ignored -> new HashMap<>())
                        .put(localIndex(x, z), sample);
            }
        } catch (IOException ignored) {
            result.clear();
        }
        return result;
    }

    private static Map<Integer, SurfaceSample> readRegion(Path source) {
        Map<Integer, SurfaceSample> result = new HashMap<>();
        if (source == null || !Files.isRegularFile(source)) return result;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(source))))) {
            if (in.readInt() != FORMAT_VERSION) return result;
            int count = Math.max(0, Math.min(REGION_CAPACITY, in.readInt()));
            for (int i = 0; i < count; i++) {
                int localIndex = in.readUnsignedShort();
                int color = in.readInt();
                int height = in.readInt();
                result.put(localIndex, new SurfaceSample(color, height, true));
            }
        } catch (IOException ignored) {
            result.clear();
        }
        return result;
    }

    private static void writeRegion(Path destination, Map<Integer, SurfaceSample> snapshot) {
        Path parent = destination.getParent();
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        try {
            if (parent != null) Files.createDirectories(parent);
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(temporary))))) {
                out.writeInt(FORMAT_VERSION);
                out.writeInt(snapshot.size());
                for (Map.Entry<Integer, SurfaceSample> entry : snapshot.entrySet()) {
                    out.writeShort(entry.getKey());
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

    private static Path regionFile(Path directory, long regionKey) {
        int regionX = unpackX(regionKey);
        int regionZ = unpackZ(regionKey);
        return directory.resolve("r." + regionX + "." + regionZ + ".surface.gz");
    }

    private static int localIndex(int x, int z) {
        int localX = Math.floorMod(x, REGION_SIZE);
        int localZ = Math.floorMod(z, REGION_SIZE);
        return localZ * REGION_SIZE + localX;
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

    private static final class RegionData {
        private final Map<Integer, SurfaceSample> samples = new HashMap<>();
        private boolean loadScheduled;
        private boolean loaded;
        private boolean dirty;
    }

    private record LoadedRegion(long generation, long regionKey, Map<Integer, SurfaceSample> samples) { }

    public record SurfaceSample(int color, int height, boolean explored) {
        private static final SurfaceSample UNEXPLORED =
                new SurfaceSample(UNEXPLORED_COLOR, Integer.MIN_VALUE, false);
    }
}
