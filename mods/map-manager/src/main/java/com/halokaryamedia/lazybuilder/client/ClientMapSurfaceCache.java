package com.halokaryamedia.lazybuilder.client;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 * <p>Resident region data uses lazily allocated primitive arrays plus a BitSet,
 * pending sample coordinates stay in a primitive insertion-ordered set, region
 * loads are explicitly bounded, and persistence only marks a revision durable
 * after an asynchronous write succeeds.</p>
 */
public final class ClientMapSurfaceCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientMapSurfaceCache.class);
    private static final int FORMAT_VERSION = 5;
    private static final int LEGACY_FORMAT_VERSION = 1;
    private static final int REGION_SIZE = 128;
    private static final int REGION_CAPACITY = REGION_SIZE * REGION_SIZE;
    private static final int MAX_LOADED_REGIONS = 96;
    private static final int MAX_FAILED_REGION_LOADS = 4096;
    private static final int MAX_PENDING = 262_144;
    private static final int MAX_REGION_LOADS_IN_FLIGHT = 32;
    private static final int MAX_LIVE_SAMPLES_PER_TICK = 1024;
    private static final int MAX_COMPLETED_ENTRIES_PER_TICK = 4096;
    private static final int LIVE_SAMPLE_WORK_COST = 4;
    private static final int MAX_SURFACE_SCAN_DEPTH = 8;
    private static final int MAX_WATER_DEPTH = 16;
    private static final int NETHER_LAYER_HEIGHT = 16;
    private static final long SHUTDOWN_WAIT_SECONDS = 3L;

    public static final int UNEXPLORED_COLOR = 0xFF101419;

    /** Access-order LRU; all mutation happens on the Minecraft client thread. */
    private final LinkedHashMap<Long, RegionData> regions = new LinkedHashMap<>(32, 0.75f, true);
    private final LongLinkedOpenHashSet pending = new LongLinkedOpenHashSet();
    private final LongLinkedOpenHashSet failedRegionLoads = new LongLinkedOpenHashSet();
    private final ConcurrentLinkedQueue<LoadedRegion> completedLoads = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<RegionWriteCompletion> completedWrites = new ConcurrentLinkedQueue<>();
    private final AtomicInteger regionLoadsInFlight = new AtomicInteger();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "LazyBuilder-Map-IO");
        thread.setDaemon(true);
        return thread;
    });

    private String scope = "";
    private Path baseScopeDirectory;
    private Path scopeDirectory;
    private Integer activeNetherLayerCenter;
    private volatile long scopeGeneration;
    private RegionSnapshot activeCompletedSnapshot;
    private RegionData activeCompletedRegion;
    private int activeCompletedIndex;
    private int residentSampleCount;
    private long lastProcessedWorldTime = Long.MIN_VALUE;

    public ClientMapSurfaceCache() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdownIo());
    }

    public void useScope(String scope, Path storageRoot) {
        String normalized = Objects.requireNonNullElse(scope, "");
        if (this.scope.equals(normalized)) return;

        flushAsync();
        this.scope = normalized;
        this.scopeGeneration++;
        this.activeNetherLayerCenter = null;

        boolean identifiedManagedWorld = !normalized.startsWith("unmanaged|");
        this.baseScopeDirectory = !identifiedManagedWorld || normalized.isBlank() || storageRoot == null
                ? null
                : storageRoot.resolve(safeName(normalized));
        this.scopeDirectory = baseScopeDirectory;

        clearResidentState();

        if (scopeDirectory != null) {
            migrateLegacySnapshot(storageRoot, normalized, scopeDirectory, scopeGeneration);
        }
    }

    private void clearResidentState() {
        regions.clear();
        pending.clear();
        failedRegionLoads.clear();
        completedLoads.clear();
        activeCompletedSnapshot = null;
        activeCompletedRegion = null;
        activeCompletedIndex = 0;
        residentSampleCount = 0;
        lastProcessedWorldTime = Long.MIN_VALUE;
    }

    /** Returns remembered terrain immediately and queues missing loaded terrain. */
    public SurfaceSample sample(ClientWorld world, int blockX, int blockZ) {
        RegionData region = regionFor(blockX, blockZ, true);
        int index = localIndex(blockX, blockZ);
        if (region.contains(index)) {
            return new SurfaceSample(region.color(index), region.height(index), true);
        }

        queueIfLoaded(world, blockX, blockZ, pack(blockX, blockZ));
        return SurfaceSample.UNEXPLORED;
    }

    /**
     * Returns one stable representative surface sample for a raster footprint.
     * Far zoom uses a fixed five-point footprint only after all points are known,
     * avoiding progressive colour blending while preserving more roads, shorelines,
     * and small structures than a single center sample.
     */
    public SurfaceSample sampleArea(ClientWorld world, int blockX, int blockZ, int span) {
        int stableSpan = Math.max(1, span);
        int anchorX = stableSampleCoordinate(blockX, stableSpan);
        int anchorZ = stableSampleCoordinate(blockZ, stableSpan);
        SurfaceSample center = sample(world, anchorX, anchorZ);
        if (stableSpan < 4) return center;

        int offset = Math.max(1, stableSpan / 3);
        SurfaceSample nw = sample(world, anchorX - offset, anchorZ - offset);
        SurfaceSample ne = sample(world, anchorX + offset, anchorZ - offset);
        SurfaceSample sw = sample(world, anchorX - offset, anchorZ + offset);
        SurfaceSample se = sample(world, anchorX + offset, anchorZ + offset);
        if (!center.explored() || !nw.explored() || !ne.explored() || !sw.explored() || !se.explored()) {
            return center;
        }
        return blendStableFive(center, nw, ne, sw, se);
    }

    static int stableSampleCoordinate(int coordinate, int span) {
        int stableSpan = Math.max(1, span);
        int cell = Math.floorDiv(coordinate, stableSpan);
        return cell * stableSpan + stableSpan / 2;
    }

    static int netherLayerCenter(int blockY) {
        return Math.floorDiv(blockY, NETHER_LAYER_HEIGHT) * NETHER_LAYER_HEIGHT + NETHER_LAYER_HEIGHT / 2;
    }

    private static SurfaceSample blendStableFive(
            SurfaceSample center,
            SurfaceSample nw,
            SurfaceSample ne,
            SurfaceSample sw,
            SurfaceSample se
    ) {
        if (center.color() == UNEXPLORED_COLOR) return center;

        long red = 0;
        long green = 0;
        long blue = 0;
        long height = 0;
        int count = 0;
        SurfaceSample[] samples = {center, nw, ne, sw, se};
        for (SurfaceSample sample : samples) {
            int color = sample.color();
            if (color == UNEXPLORED_COLOR) continue;
            red += (color >>> 16) & 0xFF;
            green += (color >>> 8) & 0xFF;
            blue += color & 0xFF;
            height += sample.height();
            count++;
        }
        if (count == 0) return center;

        int color = 0xFF000000
                | ((int) (red / count) << 16)
                | ((int) (green / count) << 8)
                | (int) (blue / count);
        return new SurfaceSample(color, (int) (height / count), true);
    }

    /**
     * Uses one shared per-client-tick work budget for completed-cache merging and
     * live terrain sampling. Calls from multiple render frames in the same world
     * tick do not multiply terrain work on high-refresh-rate clients.
     *
     * @return number of surface entries that were merged or sampled this tick;
     *         callers can use this as a content-change signal instead of polling
     *         or periodically rebuilding the map viewport.
     */
    public int processPending(ClientWorld world, int budget) {
        drainCompletedWrites();
        boolean layerChanged = updateVerticalLayer(world);

        long worldTime = world.getTime();
        if (worldTime == lastProcessedWorldTime) {
            pruneRegions();
            return layerChanged ? 1 : 0;
        }
        lastProcessedWorldTime = worldTime;

        int remainingWork = Math.max(0, budget);
        int merged = drainCompletedLoads(Math.min(remainingWork, MAX_COMPLETED_ENTRIES_PER_TICK));
        remainingWork -= merged;

        int sampleBudget = Math.min(remainingWork / LIVE_SAMPLE_WORK_COST, MAX_LIVE_SAMPLES_PER_TICK);
        if (sampleBudget <= 0 || pending.isEmpty()) {
            pruneRegions();
            return merged + (layerChanged ? 1 : 0);
        }

        int processed = 0;
        int changed = merged + (layerChanged ? 1 : 0);
        while (!pending.isEmpty() && processed < sampleBudget) {
            long key = pending.removeFirstLong();
            int x = unpackX(key);
            int z = unpackZ(key);
            if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) continue;

            RegionData region = regionFor(x, z, true);
            long revisionBefore = region.revision();
            long packedSample = readSurfacePacked(world, x, z);
            if (region.put(localIndex(x, z), unpackSampleColor(packedSample), unpackSampleHeight(packedSample))) {
                residentSampleCount++;
            }
            if (region.revision() != revisionBefore) changed++;
            processed++;
        }
        pruneRegions();
        return changed;
    }

    private boolean updateVerticalLayer(ClientWorld world) {
        if (!World.NETHER.equals(world.getRegistryKey())) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;

        int nextCenter = netherLayerCenter(client.player.getBlockY());
        if (activeNetherLayerCenter != null && activeNetherLayerCenter == nextCenter) return false;

        flushAsync();
        scopeGeneration++;
        activeNetherLayerCenter = nextCenter;
        scopeDirectory = baseScopeDirectory == null ? null : baseScopeDirectory.resolve("y." + nextCenter);
        clearResidentState();
        return true;
    }

    public int pendingCount() {
        return pending.size();
    }

    /** Number of currently resident sparse base-column samples, maintained incrementally. */
    public int size() {
        return residentSampleCount;
    }

    /** Queues immutable snapshots of dirty revisions without falsely marking failed writes clean. */
    public void flushAsync() {
        Path directory = scopeDirectory;
        if (directory == null || ioExecutor.isShutdown()) return;
        long generation = scopeGeneration;
        for (Map.Entry<Long, RegionData> entry : regions.entrySet()) {
            RegionData region = entry.getValue();
            if (region.isEmpty() || !region.shouldQueueWrite()) continue;
            RegionSnapshot snapshot = region.snapshot();
            enqueueRegionWrite(directory, entry.getKey(), region, snapshot, generation);
        }
    }

    /**
     * Queues final dirty snapshots and waits a bounded amount of time for the
     * dedicated I/O lane to drain. Shutdown never waits indefinitely.
     */
    public void shutdownIo() {
        if (ioExecutor.isShutdown()) return;
        flushAsync();
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                LOGGER.warn("LazyBuilder map cache I/O did not drain within {} seconds; forcing shutdown", SHUTDOWN_WAIT_SECONDS);
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            ioExecutor.shutdownNow();
        }
        drainCompletedWrites();
    }

    private RegionData regionFor(int blockX, int blockZ, boolean scheduleLoad) {
        int regionX = Math.floorDiv(blockX, REGION_SIZE);
        int regionZ = Math.floorDiv(blockZ, REGION_SIZE);
        long regionKey = pack(regionX, regionZ);
        RegionData existing = regions.get(regionKey);
        Path directory = scopeDirectory;
        if (existing != null) {
            if (scheduleLoad && directory != null && !failedRegionLoads.contains(regionKey)
                    && !existing.loaded && !existing.loadScheduled) {
                scheduleRegionLoad(directory, regionKey, existing, scopeGeneration);
            }
            return existing;
        }

        RegionData created = new RegionData();
        regions.put(regionKey, created);
        if (scheduleLoad && directory != null && !failedRegionLoads.contains(regionKey)) {
            scheduleRegionLoad(directory, regionKey, created, scopeGeneration);
        }
        return created;
    }

    private void scheduleRegionLoad(Path directory, long regionKey, RegionData region, long generation) {
        if (region.loadScheduled || region.loaded || ioExecutor.isShutdown()) return;
        if (regionLoadsInFlight.incrementAndGet() > MAX_REGION_LOADS_IN_FLIGHT) {
            regionLoadsInFlight.decrementAndGet();
            return;
        }

        region.loadScheduled = true;
        Path file = regionFile(directory, regionKey);
        try {
            CompletableFuture.supplyAsync(() -> readRegion(file), ioExecutor)
                    .whenComplete((snapshot, failure) -> {
                        regionLoadsInFlight.decrementAndGet();
                        if (generation != scopeGeneration) return;
                        if (failure != null) {
                            LOGGER.warn("Suppressing unreadable LazyBuilder map cache region reload: {}", file, failure);
                        }
                        completedLoads.add(new LoadedRegion(
                                generation,
                                regionKey,
                                failure == null ? snapshot : RegionSnapshot.EMPTY,
                                failure != null
                        ));
                    });
        } catch (RuntimeException rejected) {
            region.loadScheduled = false;
            regionLoadsInFlight.decrementAndGet();
        }
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
                if (loaded.failed) {
                    rememberFailedRegionLoad(loaded.regionKey);
                    if (region != null) {
                        region.loaded = true;
                        region.loadScheduled = false;
                    }
                    continue;
                }

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
                int color = activeCompletedSnapshot.colors[activeCompletedIndex];
                int height = activeCompletedSnapshot.heights[activeCompletedIndex];
                if (activeCompletedRegion.putIfAbsent(index, color, height)) residentSampleCount++;
                activeCompletedIndex++;
                processed++;
            }

            if (activeCompletedIndex >= activeCompletedSnapshot.size()) finishActiveCompletedLoad();
        }
        return processed;
    }

    private void rememberFailedRegionLoad(long regionKey) {
        failedRegionLoads.remove(regionKey);
        failedRegionLoads.add(regionKey);
        while (failedRegionLoads.size() > MAX_FAILED_REGION_LOADS) {
            failedRegionLoads.removeFirstLong();
        }
    }

    private void finishActiveCompletedLoad() {
        if (activeCompletedRegion != null) {
            activeCompletedRegion.loaded = true;
            activeCompletedRegion.loadScheduled = false;
        }
        activeCompletedSnapshot = null;
        activeCompletedRegion = null;
        activeCompletedIndex = 0;
    }

    private void drainCompletedWrites() {
        for (RegionWriteCompletion completion; (completion = completedWrites.poll()) != null;) {
            if (completion.generation != scopeGeneration) continue;
            RegionData current = regions.get(completion.regionKey);
            if (current == completion.region) {
                current.completeWrite(completion.revision, completion.success);
            }
        }
    }

    private void pruneRegions() {
        if (regions.size() <= MAX_LOADED_REGIONS) return;
        Path directory = scopeDirectory;
        long generation = scopeGeneration;
        Iterator<Map.Entry<Long, RegionData>> iterator = regions.entrySet().iterator();
        while (regions.size() > MAX_LOADED_REGIONS && iterator.hasNext()) {
            Map.Entry<Long, RegionData> eldest = iterator.next();
            RegionData region = eldest.getValue();
            if (region == activeCompletedRegion) continue;

            if (region.isDirty() && directory != null && region.shouldQueueWrite()) {
                enqueueRegionWrite(directory, eldest.getKey(), region, region.snapshot(), generation);
            }
            if (region.isDirty() || region.hasWritesInFlight()) continue;

            residentSampleCount -= region.size();
            iterator.remove();
        }
    }

    private void queueIfLoaded(ClientWorld world, int blockX, int blockZ, long key) {
        if (pending.size() >= MAX_PENDING) return;
        if (!world.getChunkManager().isChunkLoaded(blockX >> 4, blockZ >> 4)) return;
        pending.add(key);
    }

    private void enqueueRegionWrite(
            Path directory,
            long regionKey,
            RegionData region,
            RegionSnapshot snapshot,
            long generation
    ) {
        if (ioExecutor.isShutdown() || snapshot.size() == 0) return;
        if (!region.markWriteQueued(snapshot.revision)) return;

        Path destination = regionFile(directory, regionKey);
        try {
            ioExecutor.execute(() -> {
                boolean success = writeRegionWithRetry(destination, snapshot);
                completedWrites.add(new RegionWriteCompletion(
                        generation, regionKey, region, snapshot.revision, success));
            });
        } catch (RuntimeException rejected) {
            region.completeWrite(snapshot.revision, false);
        }
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
            boolean complete = true;
            for (Map.Entry<Long, RegionData> entry : partitioned.entrySet()) {
                complete &= writeRegionWithRetry(regionFile(targetDirectory, entry.getKey()), entry.getValue().snapshot());
            }
            if (!complete || generation != scopeGeneration) return;
            try {
                Files.createDirectories(targetDirectory);
                Files.writeString(migratedMarker, "v1\n");
            } catch (IOException error) {
                LOGGER.warn("Could not mark LazyBuilder legacy map migration complete: {}", migratedMarker, error);
            }
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
                int color = in.readInt();
                int height = in.readInt();
                long regionKey = pack(Math.floorDiv(x, REGION_SIZE), Math.floorDiv(z, REGION_SIZE));
                result.computeIfAbsent(regionKey, ignored -> new RegionData())
                        .put(localIndex(x, z), color, height);
            }
        } catch (IOException error) {
            LOGGER.warn("Could not read LazyBuilder legacy map cache: {}", source, error);
            result.clear();
        }
        return result;
    }

    static RegionSnapshot readRegion(Path source) {
        if (source == null || !Files.isRegularFile(source)) return RegionSnapshot.EMPTY;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(source))))) {
            if (in.readInt() != FORMAT_VERSION) return RegionSnapshot.EMPTY;
            int count = in.readInt();
            if (count < 0 || count > REGION_CAPACITY) {
                throw new IOException("Invalid LazyBuilder map region entry count: " + count);
            }
            int[] indices = new int[count];
            int[] colors = new int[count];
            int[] heights = new int[count];
            for (int i = 0; i < count; i++) {
                int index = in.readUnsignedShort();
                if (index >= REGION_CAPACITY) {
                    throw new IOException("Invalid LazyBuilder map region index: " + index);
                }
                indices[i] = index;
                colors[i] = in.readInt();
                heights[i] = in.readInt();
            }
            return new RegionSnapshot(indices, colors, heights, 0L);
        } catch (IOException error) {
            throw new IllegalStateException("Could not read LazyBuilder map region " + source, error);
        }
    }

    private static boolean writeRegionWithRetry(Path destination, RegionSnapshot snapshot) {
        if (writeRegion(destination, snapshot)) return true;
        return writeRegion(destination, snapshot);
    }

    private static boolean writeRegion(Path destination, RegionSnapshot snapshot) {
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
            return true;
        } catch (IOException error) {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            LOGGER.warn("Could not persist LazyBuilder map region: {}", destination, error);
            return false;
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

    private long readSurfacePacked(ClientWorld world, int x, int z) {
        int surfaceY = surfaceY(world, x, z);
        BlockPos pos = new BlockPos(x, surfaceY, z);
        BlockState state = world.getBlockState(pos);
        boolean water = state.getFluidState().isIn(FluidTags.WATER);
        boolean lava = state.getFluidState().isIn(FluidTags.LAVA);

        int color;
        if (water) {
            int waterRgb = BiomeColors.getWaterColor(world, pos);
            color = 0xFF000000 | (waterRgb & 0x00FFFFFF);
            color = shade(color, waterDepthFactor(world, x, surfaceY, z) * shorelineFactor(world, x, z));
        } else if (lava) {
            color = resolveBlockColor(world, pos, state);
            if (color != UNEXPLORED_COLOR) color = shade(color, 1.10);
        } else {
            color = resolveBlockColor(world, pos, state);
        }

        int westX = x - 1;
        int northZ = z - 1;
        boolean westLoaded = world.getChunkManager().isChunkLoaded(westX >> 4, z >> 4);
        boolean northLoaded = world.getChunkManager().isChunkLoaded(x >> 4, northZ >> 4);
        if (!water && !lava && color != UNEXPLORED_COLOR && westLoaded && northLoaded) {
            int west = surfaceY(world, westX, z);
            int north = surfaceY(world, x, northZ);
            int relief = (surfaceY - west) + (surfaceY - north);
            if (relief >= 3) color = shade(color, 1.14);
            else if (relief <= -3) color = shade(color, 0.80);
            else if (relief >= 1) color = shade(color, 1.06);
            else if (relief <= -1) color = shade(color, 0.91);
        }

        return packSample(color, surfaceY + 1);
    }

    private int surfaceY(ClientWorld world, int x, int z) {
        if (World.NETHER.equals(world.getRegistryKey()) && activeNetherLayerCenter != null) {
            return findNetherLayerSurfaceY(world, x, z, activeNetherLayerCenter);
        }
        int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        return findVisibleSurfaceY(world, x, z, topY);
    }

    private static int findNetherLayerSurfaceY(ClientWorld world, int x, int z, int layerCenter) {
        int bottom = world.getBottomY();
        int top = bottom + world.getHeight() - 1;
        int bandBottom = Math.max(bottom, layerCenter - NETHER_LAYER_HEIGHT / 2);
        int bandTop = Math.min(top - 1, layerCenter + NETHER_LAYER_HEIGHT / 2 - 1);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos.Mutable abovePos = new BlockPos.Mutable();

        int bestY = Integer.MIN_VALUE;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = bandBottom; y <= bandTop; y++) {
            pos.set(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (!isRenderableSurface(world, pos, state)) continue;

            abovePos.set(x, y + 1, z);
            BlockState above = world.getBlockState(abovePos);
            if (!above.isAir()) continue;

            int distance = Math.abs((y + 1) - layerCenter);
            if (distance < bestDistance || distance == bestDistance && y > bestY) {
                bestDistance = distance;
                bestY = y;
            }
        }
        if (bestY != Integer.MIN_VALUE) return bestY;

        int clampedCenter = Math.max(bandBottom, Math.min(bandTop, layerCenter));
        for (int distance = 0; distance <= NETHER_LAYER_HEIGHT; distance++) {
            int low = clampedCenter - distance;
            if (low >= bandBottom) {
                pos.set(x, low, z);
                BlockState state = world.getBlockState(pos);
                if (isRenderableSurface(world, pos, state)) return low;
            }
            int high = clampedCenter + distance;
            if (high <= bandTop && high != low) {
                pos.set(x, high, z);
                BlockState state = world.getBlockState(pos);
                if (isRenderableSurface(world, pos, state)) return high;
            }
        }
        return clampedCenter;
    }

    private static int findVisibleSurfaceY(ClientWorld world, int x, int z, int topY) {
        int bottom = world.getBottomY();
        int start = Math.max(bottom, topY - 1);
        int limit = Math.max(bottom, start - MAX_SURFACE_SCAN_DEPTH);
        BlockPos.Mutable pos = new BlockPos.Mutable(x, start, z);
        for (int y = start; y >= limit; y--) {
            pos.set(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) return y;
            if (isRenderableSurface(world, pos, state)) return y;
        }
        return start;
    }

    private static boolean isRenderableSurface(ClientWorld world, BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        if (!state.getFluidState().isEmpty()) return true;
        if (state.getMapColor(world, pos) != MapColor.CLEAR) return true;
        return MinecraftClient.getInstance().getBlockColors().getParticleColor(state, world, pos) != -1;
    }

    private static int resolveBlockColor(ClientWorld world, BlockPos pos, BlockState state) {
        int blockRgb = MinecraftClient.getInstance().getBlockColors().getParticleColor(state, world, pos);
        if (blockRgb != -1) return 0xFF000000 | (blockRgb & 0x00FFFFFF);
        MapColor mapColor = state.getMapColor(world, pos);
        return mapColor == MapColor.CLEAR
                ? UNEXPLORED_COLOR
                : mapColor.getRenderColor(MapColor.Brightness.NORMAL);
    }

    private static double waterDepthFactor(ClientWorld world, int x, int surfaceY, int z) {
        int bottom = world.getBottomY();
        int limit = Math.max(bottom, surfaceY - MAX_WATER_DEPTH + 1);
        int depth = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable(x, surfaceY, z);
        for (int y = surfaceY; y >= limit; y--) {
            pos.set(x, y, z);
            if (!world.getBlockState(pos).getFluidState().isIn(FluidTags.WATER)) break;
            depth++;
        }
        return Math.max(0.64, 0.97 - Math.max(0, depth - 1) * 0.023);
    }

    private double shorelineFactor(ClientWorld world, int x, int z) {
        int waterNeighbors = 0;
        if (isWaterSurface(world, x - 1, z)) waterNeighbors++;
        if (isWaterSurface(world, x + 1, z)) waterNeighbors++;
        if (isWaterSurface(world, x, z - 1)) waterNeighbors++;
        if (isWaterSurface(world, x, z + 1)) waterNeighbors++;
        return switch (waterNeighbors) {
            case 0, 1 -> 1.12;
            case 2 -> 1.08;
            case 3 -> 1.04;
            default -> 1.0;
        };
    }

    private boolean isWaterSurface(ClientWorld world, int x, int z) {
        if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return true;
        int surfaceY = surfaceY(world, x, z);
        return world.getBlockState(new BlockPos(x, surfaceY, z)).getFluidState().isIn(FluidTags.WATER);
    }

    private static long packSample(int color, int height) {
        return ((long) color << 32) | (height & 0xFFFFFFFFL);
    }

    private static int unpackSampleColor(long packedSample) {
        return (int) (packedSample >> 32);
    }

    private static int unpackSampleHeight(long packedSample) {
        return (int) packedSample;
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

    static final class RegionData {
        private int[] colors;
        private int[] heights;
        private BitSet present;
        private int size;
        private boolean loadScheduled;
        private boolean loaded;
        private long revision;
        private long persistedRevision;
        private long lastQueuedRevision;
        private int writesInFlight;

        boolean contains(int index) {
            return present != null && present.get(index);
        }

        int color(int index) {
            return colors[index];
        }

        int height(int index) {
            return heights[index];
        }

        /** Returns true when this call inserted a previously absent slot. */
        boolean put(int index, int color, int height) {
            ensureStorage();
            boolean inserted = !present.get(index);
            boolean changed = inserted || colors[index] != color || heights[index] != height;
            if (!changed) return false;

            colors[index] = color;
            heights[index] = height;
            if (inserted) {
                present.set(index);
                size++;
            }
            revision++;
            return inserted;
        }

        /** Merges disk-loaded data without turning that data into a dirty local revision. */
        boolean putIfAbsent(int index, int color, int height) {
            ensureStorage();
            if (present.get(index)) return false;
            colors[index] = color;
            heights[index] = height;
            present.set(index);
            size++;
            return true;
        }

        boolean isEmpty() {
            return size == 0;
        }

        int size() {
            return size;
        }

        boolean isDirty() {
            return revision > persistedRevision;
        }

        boolean shouldQueueWrite() {
            return revision > lastQueuedRevision;
        }

        boolean hasWritesInFlight() {
            return writesInFlight > 0;
        }

        boolean markWriteQueued(long queuedRevision) {
            if (queuedRevision <= lastQueuedRevision) return false;
            lastQueuedRevision = queuedRevision;
            writesInFlight++;
            return true;
        }

        void completeWrite(long writtenRevision, boolean success) {
            writesInFlight = Math.max(0, writesInFlight - 1);
            if (success) {
                persistedRevision = Math.max(persistedRevision, writtenRevision);
            } else if (lastQueuedRevision == writtenRevision) {
                lastQueuedRevision = persistedRevision;
            }
        }

        long revision() {
            return revision;
        }

        long persistedRevision() {
            return persistedRevision;
        }

        RegionSnapshot snapshot() {
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
            return new RegionSnapshot(indices, snapshotColors, snapshotHeights, revision);
        }

        private void ensureStorage() {
            if (present != null) return;
            colors = new int[REGION_CAPACITY];
            heights = new int[REGION_CAPACITY];
            present = new BitSet(REGION_CAPACITY);
        }
    }

    record RegionSnapshot(int[] indices, int[] colors, int[] heights, long revision) {
        private static final RegionSnapshot EMPTY = new RegionSnapshot(new int[0], new int[0], new int[0], 0L);

        RegionSnapshot {
            if (indices.length != colors.length || colors.length != heights.length) {
                throw new IllegalArgumentException("Region snapshot arrays must have equal length");
            }
        }

        int size() {
            return indices.length;
        }
    }

    private record LoadedRegion(long generation, long regionKey, RegionSnapshot snapshot, boolean failed) { }

    private record RegionWriteCompletion(
            long generation,
            long regionKey,
            RegionData region,
            long revision,
            boolean success
    ) { }

    public record SurfaceSample(int color, int height, boolean explored) {
        private static final SurfaceSample UNEXPLORED =
                new SurfaceSample(UNEXPLORED_COLOR, Integer.MIN_VALUE, false);
    }
}
