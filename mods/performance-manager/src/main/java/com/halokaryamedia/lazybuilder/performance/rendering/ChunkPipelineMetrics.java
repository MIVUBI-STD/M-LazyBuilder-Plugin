package com.halokaryamedia.lazybuilder.performance.rendering;

import java.util.concurrent.atomic.LongAdder;

/**
 * Low-overhead counters for first-party chunk-pipeline decisions and pressure signals.
 *
 * Safety/pressure counters remain always-on. High-frequency observational counters are disabled
 * until diagnostics or proof mode explicitly requests them, avoiding permanent observer cost on
 * chunk-meshing hot paths.
 */
public final class ChunkPipelineMetrics {
    private static final LongAdder COALESCED_REBUILD_REQUESTS = new LongAdder();
    private static final LongAdder BUFFER_ACQUIRE_MISSES = new LongAdder();
    private static final LongAdder AVOIDED_UPLOAD_BUFFER_BINDS = new LongAdder();
    private static final LongAdder STORAGE_SECTIONS_REMAPPED = new LongAdder();
    private static final LongAdder SECTION_VISIBILITY_CACHE_HITS = new LongAdder();
    private static final LongAdder AVOIDED_TRANSLUCENT_SORT_TASKS = new LongAdder();
    private static final LongAdder AVOIDED_TERRAIN_SECTION_VISITS = new LongAdder();
    private static final LongAdder SECTION_BUILDER_BUFFER_LOOKUP_HITS = new LongAdder();
    private static final LongAdder TERRAIN_BUFFER_LOOKUP_HITS = new LongAdder();
    private static final LongAdder UPLOAD_BUDGET_STOPS = new LongAdder();
    private static final LongAdder REBUILD_BACKPRESSURE_DEFERRALS = new LongAdder();
    private static final LongAdder REBUILD_BACKPRESSURE_RELEASES = new LongAdder();
    private static final LongAdder TERRAIN_GPU_RECLAIMED_BYTES = new LongAdder();
    private static final LongAdder TERRAIN_GPU_RECLAIMED_BUFFERS = new LongAdder();

    private static volatile boolean detailedMetricsEnabled =
            Boolean.getBoolean("lazybuilder.performance.metrics")
                    || Boolean.getBoolean("lazybuilder.performance.proof");

    private ChunkPipelineMetrics() {
    }

    /** Enables high-frequency diagnostics for the remainder of the session. */
    public static void enableDetailedMetrics() {
        detailedMetricsEnabled = true;
    }

    public static boolean detailedMetricsEnabled() {
        return detailedMetricsEnabled;
    }

    static void setDetailedMetricsEnabledForTest(boolean enabled) {
        detailedMetricsEnabled = enabled;
    }

    public static void recordCoalescedRebuild() { if (detailedMetricsEnabled) COALESCED_REBUILD_REQUESTS.increment(); }
    public static long coalescedRebuildRequests() { return COALESCED_REBUILD_REQUESTS.sum(); }
    public static void recordBufferAcquireMiss() { if (detailedMetricsEnabled) BUFFER_ACQUIRE_MISSES.increment(); }
    public static long bufferAcquireMisses() { return BUFFER_ACQUIRE_MISSES.sum(); }
    public static void recordUploadBatch(int taskCount) {
        if (detailedMetricsEnabled && taskCount > 1) AVOIDED_UPLOAD_BUFFER_BINDS.add(taskCount - 1L);
    }
    public static long avoidedUploadBufferBinds() { return AVOIDED_UPLOAD_BUFFER_BINDS.sum(); }
    public static void recordStorageSectionsRemapped(int count) {
        if (detailedMetricsEnabled && count > 0) STORAGE_SECTIONS_REMAPPED.add(count);
    }
    public static long storageSectionsRemapped() { return STORAGE_SECTIONS_REMAPPED.sum(); }
    public static void recordSectionVisibilityCacheHit() {
        if (detailedMetricsEnabled) SECTION_VISIBILITY_CACHE_HITS.increment();
    }
    public static long sectionVisibilityCacheHits() { return SECTION_VISIBILITY_CACHE_HITS.sum(); }
    public static void recordAvoidedTranslucentSortTask() {
        if (detailedMetricsEnabled) AVOIDED_TRANSLUCENT_SORT_TASKS.increment();
    }
    public static long avoidedTranslucentSortTasks() { return AVOIDED_TRANSLUCENT_SORT_TASKS.sum(); }
    public static void recordAvoidedTerrainSectionVisits(long count) {
        if (detailedMetricsEnabled && count > 0L) AVOIDED_TERRAIN_SECTION_VISITS.add(count);
    }
    public static long avoidedTerrainSectionVisits() { return AVOIDED_TERRAIN_SECTION_VISITS.sum(); }
    public static void recordSectionBuilderBufferLookupHit() {
        if (detailedMetricsEnabled) SECTION_BUILDER_BUFFER_LOOKUP_HITS.increment();
    }
    public static long sectionBuilderBufferLookupHits() { return SECTION_BUILDER_BUFFER_LOOKUP_HITS.sum(); }
    public static void recordTerrainBufferLookupHit() {
        if (detailedMetricsEnabled) TERRAIN_BUFFER_LOOKUP_HITS.increment();
    }
    public static long terrainBufferLookupHits() { return TERRAIN_BUFFER_LOOKUP_HITS.sum(); }

    public static void recordUploadBudgetStop() { if (detailedMetricsEnabled) UPLOAD_BUDGET_STOPS.increment(); }
    public static long uploadBudgetStops() { return UPLOAD_BUDGET_STOPS.sum(); }
    public static void recordRebuildBackpressureDeferral() { if (detailedMetricsEnabled) REBUILD_BACKPRESSURE_DEFERRALS.increment(); }
    public static long rebuildBackpressureDeferrals() { return REBUILD_BACKPRESSURE_DEFERRALS.sum(); }
    public static void recordRebuildBackpressureRelease() { if (detailedMetricsEnabled) REBUILD_BACKPRESSURE_RELEASES.increment(); }
    public static long rebuildBackpressureReleases() { return REBUILD_BACKPRESSURE_RELEASES.sum(); }
    public static void recordTerrainGpuReclamation(long bytes) {
        if (bytes <= 0L) return;
        if (!detailedMetricsEnabled) return;
        TERRAIN_GPU_RECLAIMED_BYTES.add(bytes);
        TERRAIN_GPU_RECLAIMED_BUFFERS.increment();
    }
    public static long terrainGpuReclaimedBytes() { return TERRAIN_GPU_RECLAIMED_BYTES.sum(); }
    public static long terrainGpuReclaimedBuffers() { return TERRAIN_GPU_RECLAIMED_BUFFERS.sum(); }

    static void resetForTest() {
        detailedMetricsEnabled = true;
        COALESCED_REBUILD_REQUESTS.reset();
        BUFFER_ACQUIRE_MISSES.reset();
        AVOIDED_UPLOAD_BUFFER_BINDS.reset();
        STORAGE_SECTIONS_REMAPPED.reset();
        SECTION_VISIBILITY_CACHE_HITS.reset();
        AVOIDED_TRANSLUCENT_SORT_TASKS.reset();
        AVOIDED_TERRAIN_SECTION_VISITS.reset();
        SECTION_BUILDER_BUFFER_LOOKUP_HITS.reset();
        TERRAIN_BUFFER_LOOKUP_HITS.reset();
        UPLOAD_BUDGET_STOPS.reset();
        REBUILD_BACKPRESSURE_DEFERRALS.reset();
        REBUILD_BACKPRESSURE_RELEASES.reset();
        TERRAIN_GPU_RECLAIMED_BYTES.reset();
        TERRAIN_GPU_RECLAIMED_BUFFERS.reset();
    }
}
