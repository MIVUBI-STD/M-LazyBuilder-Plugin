package com.halokaryamedia.lazybuilder.performance.rendering;

import java.util.concurrent.atomic.LongAdder;

/** Low-overhead counters for first-party chunk-pipeline decisions and pressure signals. */
public final class ChunkPipelineMetrics {
    private static final LongAdder COALESCED_REBUILD_REQUESTS = new LongAdder();
    private static final LongAdder BUFFER_ACQUIRE_MISSES = new LongAdder();
    private static final LongAdder AVOIDED_UPLOAD_BUFFER_BINDS = new LongAdder();
    private static final LongAdder STORAGE_SECTIONS_REMAPPED = new LongAdder();
    private static final LongAdder SECTION_VISIBILITY_CACHE_HITS = new LongAdder();
    private static final LongAdder AVOIDED_TRANSLUCENT_SORT_TASKS = new LongAdder();
    private static final LongAdder AVOIDED_TERRAIN_SECTION_VISITS = new LongAdder();
    private static final LongAdder SECTION_BUILDER_BUFFER_LOOKUP_HITS = new LongAdder();
    private static final LongAdder UPLOAD_BUDGET_STOPS = new LongAdder();
    private static final LongAdder REBUILD_BACKPRESSURE_DEFERRALS = new LongAdder();
    private static final LongAdder REBUILD_BACKPRESSURE_RELEASES = new LongAdder();
    private static final LongAdder PARTICLES_SUPPRESSED = new LongAdder();
    private static final LongAdder TERRAIN_GPU_RECLAIMED_BYTES = new LongAdder();
    private static final LongAdder TERRAIN_GPU_RECLAIMED_BUFFERS = new LongAdder();

    private ChunkPipelineMetrics() {
    }

    public static void recordCoalescedRebuild() { COALESCED_REBUILD_REQUESTS.increment(); }
    public static long coalescedRebuildRequests() { return COALESCED_REBUILD_REQUESTS.sum(); }
    public static void recordBufferAcquireMiss() { BUFFER_ACQUIRE_MISSES.increment(); }
    public static long bufferAcquireMisses() { return BUFFER_ACQUIRE_MISSES.sum(); }
    public static void recordUploadBatch(int taskCount) { if (taskCount > 1) AVOIDED_UPLOAD_BUFFER_BINDS.add(taskCount - 1L); }
    public static long avoidedUploadBufferBinds() { return AVOIDED_UPLOAD_BUFFER_BINDS.sum(); }
    public static void recordStorageSectionsRemapped(int count) { if (count > 0) STORAGE_SECTIONS_REMAPPED.add(count); }
    public static long storageSectionsRemapped() { return STORAGE_SECTIONS_REMAPPED.sum(); }
    public static void recordSectionVisibilityCacheHit() { SECTION_VISIBILITY_CACHE_HITS.increment(); }
    public static long sectionVisibilityCacheHits() { return SECTION_VISIBILITY_CACHE_HITS.sum(); }
    public static void recordAvoidedTranslucentSortTask() { AVOIDED_TRANSLUCENT_SORT_TASKS.increment(); }
    public static long avoidedTranslucentSortTasks() { return AVOIDED_TRANSLUCENT_SORT_TASKS.sum(); }
    public static void recordAvoidedTerrainSectionVisits(long count) { if (count > 0L) AVOIDED_TERRAIN_SECTION_VISITS.add(count); }
    public static long avoidedTerrainSectionVisits() { return AVOIDED_TERRAIN_SECTION_VISITS.sum(); }
    public static void recordSectionBuilderBufferLookupHit() { SECTION_BUILDER_BUFFER_LOOKUP_HITS.increment(); }
    public static long sectionBuilderBufferLookupHits() { return SECTION_BUILDER_BUFFER_LOOKUP_HITS.sum(); }
    public static void recordUploadBudgetStop() { UPLOAD_BUDGET_STOPS.increment(); }
    public static long uploadBudgetStops() { return UPLOAD_BUDGET_STOPS.sum(); }
    public static void recordRebuildBackpressureDeferral() { REBUILD_BACKPRESSURE_DEFERRALS.increment(); }
    public static long rebuildBackpressureDeferrals() { return REBUILD_BACKPRESSURE_DEFERRALS.sum(); }
    public static void recordRebuildBackpressureRelease() { REBUILD_BACKPRESSURE_RELEASES.increment(); }
    public static long rebuildBackpressureReleases() { return REBUILD_BACKPRESSURE_RELEASES.sum(); }
    public static void recordParticleSuppressed() { PARTICLES_SUPPRESSED.increment(); }
    public static long particlesSuppressed() { return PARTICLES_SUPPRESSED.sum(); }
    public static void recordTerrainGpuReclamation(long bytes) {
        if (bytes <= 0L) return;
        TERRAIN_GPU_RECLAIMED_BYTES.add(bytes);
        TERRAIN_GPU_RECLAIMED_BUFFERS.increment();
    }
    public static long terrainGpuReclaimedBytes() { return TERRAIN_GPU_RECLAIMED_BYTES.sum(); }
    public static long terrainGpuReclaimedBuffers() { return TERRAIN_GPU_RECLAIMED_BUFFERS.sum(); }

    static void resetForTest() {
        COALESCED_REBUILD_REQUESTS.reset();
        BUFFER_ACQUIRE_MISSES.reset();
        AVOIDED_UPLOAD_BUFFER_BINDS.reset();
        STORAGE_SECTIONS_REMAPPED.reset();
        SECTION_VISIBILITY_CACHE_HITS.reset();
        AVOIDED_TRANSLUCENT_SORT_TASKS.reset();
        AVOIDED_TERRAIN_SECTION_VISITS.reset();
        SECTION_BUILDER_BUFFER_LOOKUP_HITS.reset();
        UPLOAD_BUDGET_STOPS.reset();
        REBUILD_BACKPRESSURE_DEFERRALS.reset();
        REBUILD_BACKPRESSURE_RELEASES.reset();
        PARTICLES_SUPPRESSED.reset();
        TERRAIN_GPU_RECLAIMED_BYTES.reset();
        TERRAIN_GPU_RECLAIMED_BUFFERS.reset();
    }
}
