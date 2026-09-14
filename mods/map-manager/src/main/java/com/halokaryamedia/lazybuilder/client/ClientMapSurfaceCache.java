package com.halokaryamedia.lazybuilder.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
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
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 * <p>Resident region data uses lazily allocated primitive arrays plus a BitSet
 * instead of boxed map entries. Disk I/O stays sparse and compatible with the
 * existing region format. Region files load off the render thread and dirty
 * regions are written through one ordered LazyBuilder-owned I/O lane.</p>
 */
public final class ClientMapSurfaceCache {
    private static final int FORMAT_VERSION = 2;
    private static final int LEGACY_FORMAT_VERSION = 1;
    private static final int REGION_SIZE = 128;
    private static final int REGION_CAPACITY = REGION_SIZE * REGION_SIZE;
    private static final int MAX_LOADED_REGIONS = 96;
    private static final int MAX_PENDING = 262_144;
    private static final int MAX_LIVE_SAMPLES_PER_FRAME = 1024;
    private static final int MAX_COMPLETED_ENTRIES_PER_FRAME = 4096;
    private static final int LIVE_SAMPLE_WORK_COST = 4;

    public static final int UNEXPLORED_COLOR = 0xFF101419;

    /** Access-order LRU; all mutation happens on the Minecraft client thread. */
    private final LinkedHashMap<Long, RegionData> regions = new LinkedHashMap<>(32, 0.75f, true);
    private final LinkedHashSet<Long> pending = new LinkedHashSet<>();
    private final ConcurrentLinkedQueue<LoadedRegion> completedLoads = new ConcurrentLinkedQueue<>();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "LazyBuilder-Map-IO");
        thread.setDaemon(true);
        return thread;
    });

    private String scope = "";
    private Path scopeDirectory;
    private volatile long scopeGeneration;
    private RegionSnapshot activeCompletedSnapshot;
    private RegionData activeCompletedRegion;
    private int activeCompletedIndex;
    private int residentSampleCount;

    public ClientMapSurfaceCache() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdownIo());
    }

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
        activeCompletedSnapshot = null;
        activeCompletedRegion = null;
        activeCompletedIndex = 0;
        residentSampleCount = 0;

        if (scopeDirectory != null) {
            migrateLegacySnapshot(storageRoot, normalized, scopeDirectory, scopeGeneration);
        }
    }

    /** Returns remembered terrain immediately and queues missing loaded terrain. */
    public SurfaceSample sample(ClientWorld world, int blockX, int blockZ) {
        RegionData region = regionFor(blockX, blockZ, true);
        SurfaceSample cached = region.get(localIndex(blockX, blockZ));
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

        int explored = explored(center) + explored(nw) + explored(ne) + explored(sw) + explored(se);
        if (explored < 3) return center.explored() ? center : SurfaceSample.UNEXPLORED;
        return blendFive(center, nw, ne, sw, se);
    }

    /**
     * Uses one shared per-frame work budget for completed-cache merging and live terrain sampling.
     * A live world sample is weighted above a simple cache-entry merge because it reads heightmap,
     * block state and neighboring terrain.
     */
    public int processPending(ClientWorld world, int budget) {
        int remainingWork = Math.max(0, budget);
        int merged = drainCompletedLoads(Math.min(remainingWork, MAX_COMPLETED_ENTRIES_PER_FRAME));
        remainingWork -= merged;

        int sampleBudget = Math.min(remainingWork / LIVE_SAMPLE_WORK_COST, MAX_LIVE_SAMPLES_PER_FRAME);
        if (sampleBudget <= 0 || pending.isEmpty()) {
            pruneRegions();
            return 0;
        }

        int processed = 0;
        Iterator<Long> iterator = pending.iterator();
        while (iterator.hasNext() && processed < sampleBudget) {
            long key = iterator.next();
            iterator.remove();
            int x = unpackX(key);
            int z = unpackZ(key);
            if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) continue;

            RegionData region = regionFor(x, z, true);
            if (region.put(localIndex(x, z), readSurface(world, x, z))) residentSampleCount++;
            region.dirty = true;
            processed++;
        }
        pruneRegions();
        return processed;
    }

    public int pendingCount() {
        return pending.size();
    }

    /** Number of currently resident sparse base-column samples, maintained incrementally. */
    public int size() {
        return residentSampleCount;
    }

    /** Queues sparse primitive snapshots of all dirty resident regions without blocking rendering. */
    public void flushAsync() {
        Path directory = scopeDirectory;
        if (directory == null || ioExecutor.isShutdown()) return;
        for (Map.Entry<Long, RegionData> entry : regions.entrySet()) {
            RegionData region = entry.getValue();
            if (!region.dirty || region.isEmpty()) continue;
            region.dirty = false;
            enqueueRegionWrite(directory, entry.getKey(), region.snapshot());
        }
    }

    /** Queues final dirty snapshots and lets the dedicated I/O lane drain before JVM exit. */
    public void shutdownIo() {
        if (ioExecutor.isShutdown()) return;
        flushAsync();
        ioExecutor.shutdown();
    }

    private RegionData regionFor(int blockX, int blockZ, boolean scheduleLoad) {
        int regionX = Math.floorDiv(blockX, REGION_SIZE);
        int regionZ = Math.floorDiv(blockZ, REGION_SIZE);
        long regionKey = pack(regionX, regionZ);
        RegionData existing = regions.get(regionKey);
        if (existing != null) return existing;

        RegionData created = new RegionData();
        regions.put(regionKey, created);
        Path directory = scopeDirectory;
        if (scheduleLoad && directory != null) scheduleRegionLoad(directory, regionKey, created, scopeGeneration);
        return created;
    }

    private void scheduleRegionLoad(Path directory, long regionKey, RegionData region, long generation) {
        if (region.loadScheduled || ioExecutor.isShutdown()) return;
        region.loadScheduled = true;
        Path file = regionFile(directory, regionKey);
        CompletableFuture.supplyAsync(() -> readRegion(file), ioExecutor)
                .thenAccept(snapshot -> {
                    if (generation == scopeGeneration) {
                        completedLoads.add(new LoadedRegion(generation, regionKey, snapshot));
                    }
                });
    }

    private int drainCompletedLoads(int budget) {
        if (budget <= 0) return 0;

        int processed = 0;
        while (processed < budget) {
            if (activeCompletedSnapshot == null) {
                LoadedRegion loaded = completedLoads.poll();
                if (loaded == null) break;
                if (loaded.generation != scopeGeneration) continue;

                RegionData region = regions.get(loaded.regionKey);
                if (region == null) {
                    region = new RegionData();
                    region.loadScheduled = true;
                    regions.put(loaded.regionKey, region);
                }

                activeCompletedRegion = region;
                activeCompletedSnapshot = loaded.snapshot;
                activeCompletedIndex = 0;
                if (activeCompletedSnapshot.size() == 0) {
                    finishActiveCompletedLoad();
                    continue;
                }
            }

            while (processed < budget && activeCompletedIndex < activeCompletedSnapshot.size()) {
                int index = activeCompletedSnapshot.indices[activeCompletedIndex];
                SurfaceSample sample = new SurfaceSample(
                        activeCompletedSnapshot.colors[activeCompletedIndex],
                        activeCompletedSnapshot.heights[activeCompletedIndex],
                        true
                );
                if (activeCompletedRegion.putIfAbsent(index, sample)) residentSampleCount++;
                activeCompletedIndex++;
                processed++;
            }

            if (activeCompletedIndex >= activeCompletedSnapshot.size()) finishActiveCompletedLoad();
        }
        return processed;
    }

    private void finishActiveCompletedLoad() {
        if (activeCompletedRegion != null) activeCompletedRegion.loaded = true;
        activeCompletedSnapshot = null;
        activeCompletedRegion = null;
        activeCompletedIndex = 0;
    }

    private void pruneRegions() {
        if (regions.size() <= MAX_LOADED_REGIONS) return;
        Path directory = scopeDirectory;
        Iterator<Map.Entry<Long, RegionData>> iterator = regions.entrySet().iterator();
        while (regions.size() > MAX_LOADED_REGIONS && iterator.hasNext()) {
            Map.Entry<Long, RegionData> eldest = iterator.next();
            RegionData region = eldest.getValue();
            if (region == activeCompletedRegion) continue;
            if (region.dirty && directory != null && !region.isEmpty()) {
                region.dirty = false;
                enqueueRegionWrite(directory, eldest.getKey(), region.snapshot());
            }
            residentSampleCount -= region.size();
            iterator.remove();
        }
    }

    private void queueIfLoaded(ClientWorld world, int blockX, int blockZ, long key) {
        if (pending.size() >= MAX_PENDING) return;
        if (!world.getChunkManager().isChunkLoaded(blockX >> 4, blockZ >> 4)) return;
        pending.add(key);
    }

    private void enqueueRegionWrite(Path directory, long regionKey, RegionSnapshot snapshot) {
        if (ioExecutor.isShutdown()) return;
        Path destination = regionFile(directory, regionKey);
        ioExecutor.execute(() -> writeRegion(destination, snapshot));
    }

    private void migrateLegacySnapshot(
            Path storageRoot,
            String normalizedScope,
            Path targetDirectory,
            long generation
    ) {
        Path legacy = storageRoot.resolve(safeName(normalizedScope) + ".surface.gz");
        if (!Files.isRegularFile(legacy) || ioExecutor.isShutdown()) return;
        Path migratedMarker = targetDirectory.resolve(".legacy-v1-migrated");
        if (Files.exists(migratedMarker)) return;

        CompletableFuture.runAsync(() -> {
            Map<Long, RegionData> partitioned = readLegacySnapshot(legacy);
            if (partitioned.isEmpty() || generation != scopeGeneration) return;
            for (Map.Entry<Long, RegionData> entry : partitioned.entrySet()) {
                writeRegion(regionFile(targetDirectory, entry.getKey()), entry.getValue().snapshot());
            }
            try {
                Files.createDirectories(targetDirectory);
                Files.writeString(migratedMarker, "v1\n");
            } catch (IOException ignored) { }
        }, ioExecutor);
    }

    private static Map<Long, RegionData> readLegacySnapshot(Path source) {
        Map<Long, RegionData> result = new HashMap<>();
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
                result.computeIfAbsent(regionKey, ignored -> new RegionData())
                        .put(localIndex(x, z), sample);
            }
        } catch (IOException ignored) {
            result.clear();
        }
        return result;
    }

    private static RegionSnapshot readRegion(Path source) {
        if (source == null || !Files.isRegularFile(source)) return RegionSnapshot.EMPTY;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(source))))) {
            if (in.readInt() != FORMAT_VERSION) return RegionSnapshot.EMPTY;
            int count = Math.max(0, Math.min(REGION_CAPACITY, in.readInt()));
            int[] indices = new int[count];
            int[] colors = new int[count];
            int[] heights = new int[count];
            for (int i = 0; i < count; i++) {
                indices[i] = in.readUnsignedShort();
                colors[i] = in.readInt();
                heights[i] = in.readInt();
            }
            return new RegionSnapshot(indices, colors, heights);
        } catch (IOException ignored) {
            return RegionSnapshot.EMPTY;
        }
    }

    private static void writeRegion(Path destination, RegionSnapshot snapshot) {
        Path parent = destination.getParent();
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        try {
            if (parent != null) Files.createDirectories(parent);
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(temporary))))) {
                out.writeInt(FORMAT_VERSION);
                out.writeInt(snapshot.size());
                for (int i = 0; i < snapshot.size(); i++) {
                    out.writeShort(snapshot.indices[i]);
                    out.writeInt(snapshot.colors[i]);
                    out.writeInt(snapshot.heights[i]);
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

    private static int explored(SurfaceSample value) {
        return value != null && value.explored() ? 1 : 0;
    }

    private static SurfaceSample blendFive(
            SurfaceSample center,
            SurfaceSample nw,
            SurfaceSample ne,
            SurfaceSample sw,
            SurfaceSample se
    ) {
        long red = 0;
        long green = 0;
        long blue = 0;
        long height = 0;
        int count = 0;

        if (center != null && center.explored()) {
            int color = center.color();
            red += (color >>> 16) & 0xFF;
            green += (color >>> 8) & 0xFF;
            blue += color & 0xFF;
            height += center.height();
            count++;
        }
        if (nw != null && nw.explored()) {
            int color = nw.color();
            red += (color >>> 16) & 0xFF;
            green += (color >>> 8) & 0xFF;
            blue += color & 0xFF;
            height += nw.height();
            count++;
        }
        if (ne != null && ne.explored()) {
            int color = ne.color();
            red += (color >>> 16) & 0xFF;
            green += (color >>> 8) & 0xFF;
            blue += color & 0xFF;
            height += ne.height();
            count++;
        }
        if (sw != null && sw.explored()) {
            int color = sw.color();
            red += (color >>> 16) & 0xFF;
            green += (color >>> 8) & 0xFF;
            blue += color & 0xFF;
            height += sw.height();
            count++;
        }
        if (se != null && se.explored()) {
            int color = se.color();
            red += (color >>> 16) & 0xFF;
            green += (color >>> 8) & 0xFF;
            blue += color & 0xFF;
            height += se.height();
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
        private int[] colors;
        private int[] heights;
        private BitSet present;
        private int size;
        private boolean loadScheduled;
        private boolean loaded;
        private boolean dirty;

        private SurfaceSample get(int index) {
            if (present == null || !present.get(index)) return null;
            return new SurfaceSample(colors[index], heights[index], true);
        }

        /** Returns true when this call inserted a previously absent slot. */
        private boolean put(int index, SurfaceSample sample) {
            ensureStorage();
            boolean inserted = !present.get(index);
            colors[index] = sample.color();
            heights[index] = sample.height();
            if (inserted) {
                present.set(index);
                size++;
            }
            return inserted;
        }

        /** Returns true when the absent slot was inserted. */
        private boolean putIfAbsent(int index, SurfaceSample sample) {
            ensureStorage();
            if (present.get(index)) return false;
            colors[index] = sample.color();
            heights[index] = sample.height();
            present.set(index);
            size++;
            return true;
        }

        private boolean isEmpty() {
            return size == 0;
        }

        private int size() {
            return size;
        }

        private RegionSnapshot snapshot() {
            if (size == 0) return RegionSnapshot.EMPTY;
            int[] indices = new int[size];
            int[] snapshotColors = new int[size];
            int[] snapshotHeights = new int[size];
            int cursor = 0;
            for (int index = present.nextSetBit(0); index >= 0; index = present.nextSetBit(index + 1)) {
                indices[cursor] = index;
                snapshotColors[cursor] = colors[index];
                snapshotHeights[cursor] = heights[index];
                cursor++;
            }
            return new RegionSnapshot(indices, snapshotColors, snapshotHeights);
        }

        private void ensureStorage() {
            if (present != null) return;
            colors = new int[REGION_CAPACITY];
            heights = new int[REGION_CAPACITY];
            present = new BitSet(REGION_CAPACITY);
        }
    }

    private record RegionSnapshot(int[] indices, int[] colors, int[] heights) {
        private static final RegionSnapshot EMPTY = new RegionSnapshot(new int[0], new int[0], new int[0]);

        private RegionSnapshot {
            if (indices.length != colors.length || colors.length != heights.length) {
                throw new IllegalArgumentException("Region snapshot arrays must have equal length");
            }
        }

        private int size() {
            return indices.length;
        }
    }

    private record LoadedRegion(long generation, long regionKey, RegionSnapshot snapshot) { }

    public record SurfaceSample(int color, int height, boolean explored) {
        private static final SurfaceSample UNEXPLORED =
                new SurfaceSample(UNEXPLORED_COLOR, Integer.MIN_VALUE, false);
    }
}
