package com.halokaryamedia.lazybuilder.performance.rendering;

/** Pure sizing and compaction policy for future shared terrain GPU region arenas. */
public final class TerrainRegionArenaPolicy {
    public static final long SUBALLOCATION_ALIGNMENT = 256L;
    public static final long CAPACITY_QUANTUM = 1L << 20; // 1 MiB
    public static final long MIN_CAPACITY = 1L << 20;
    public static final long MIN_COMPACTION_RECLAIM = 2L << 20;

    private TerrainRegionArenaPolicy() {
    }

    public static long alignedSize(long bytes) {
        if (bytes <= 0L) return 0L;
        return roundUp(bytes, SUBALLOCATION_ALIGNMENT);
    }

    public static long plannedCapacity(long payloadBytes) {
        if (payloadBytes <= 0L) return 0L;
        long alignedPayload = alignedSize(payloadBytes);
        long withHeadroom = alignedPayload + Math.max(CAPACITY_QUANTUM / 4L, alignedPayload / 4L);
        return Math.max(MIN_CAPACITY, roundUp(withHeadroom, CAPACITY_QUANTUM));
    }

    public static long potentialReclaim(long currentCapacityBytes, long payloadBytes) {
        long target = plannedCapacity(payloadBytes);
        return Math.max(0L, currentCapacityBytes - target);
    }

    public static boolean shouldCompact(long currentCapacityBytes, long payloadBytes) {
        return potentialReclaim(currentCapacityBytes, payloadBytes) >= MIN_COMPACTION_RECLAIM;
    }

    private static long roundUp(long value, long quantum) {
        long remainder = value % quantum;
        if (remainder == 0L) return value;
        long delta = quantum - remainder;
        if (value > Long.MAX_VALUE - delta) return Long.MAX_VALUE;
        return value + delta;
    }
}
