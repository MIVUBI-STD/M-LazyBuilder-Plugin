package com.halokaryamedia.lazybuilder.utility.debug;

/** Session-scoped, expiring server telemetry consumed by Compact Debug. */
public final class CompactDebugServerState {
    private static final long FRESHNESS_NANOS = 6_000_000_000L;
    private static volatile Snapshot snapshot = Snapshot.unavailable();

    private CompactDebugServerState() {
    }

    public static Snapshot snapshot() {
        Snapshot current = snapshot;
        if (!current.telemetryAvailable()) return current;
        if (System.nanoTime() - current.updatedAtNanos() > FRESHNESS_NANOS) {
            clear();
            return snapshot;
        }
        return current;
    }

    public static void update(String worldName, double cpuPercent, long usedMemoryBytes, long maxMemoryBytes) {
        snapshot = new Snapshot(
                sanitizeWorldName(worldName),
                clampPercent(cpuPercent),
                Math.max(0L, usedMemoryBytes),
                Math.max(0L, maxMemoryBytes),
                true,
                System.nanoTime()
        );
    }

    public static void clear() {
        snapshot = Snapshot.unavailable();
    }

    private static String sanitizeWorldName(String worldName) {
        if (worldName == null || worldName.isBlank()) return "Unknown";
        return worldName.strip();
    }

    private static double clampPercent(double value) {
        if (!Double.isFinite(value)) return Double.NaN;
        return Math.max(0.0D, Math.min(100.0D, value));
    }

    public record Snapshot(
            String worldName,
            double cpuPercent,
            long usedMemoryBytes,
            long maxMemoryBytes,
            boolean telemetryAvailable,
            long updatedAtNanos
    ) {
        private static Snapshot unavailable() {
            return new Snapshot("", Double.NaN, 0L, 0L, false, 0L);
        }
    }
}
