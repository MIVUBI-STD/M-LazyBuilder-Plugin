package com.halokaryamedia.lazybuilder.utility.debug;

/**
 * Session-scoped server telemetry consumed by Compact Debug.
 *
 * The state intentionally has no transport logic. A later protocol adapter may
 * update it, while disconnect/server-switch lifecycle always clears it.
 */
public final class CompactDebugServerState {
    private static Snapshot snapshot = Snapshot.unavailable();

    private CompactDebugServerState() {
    }

    public static Snapshot snapshot() {
        return snapshot;
    }

    public static void update(String worldName, double cpuPercent, long usedMemoryBytes, long maxMemoryBytes) {
        snapshot = new Snapshot(
                sanitizeWorldName(worldName),
                clampPercent(cpuPercent),
                Math.max(0L, usedMemoryBytes),
                Math.max(0L, maxMemoryBytes),
                true
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
            boolean telemetryAvailable
    ) {
        private static Snapshot unavailable() {
            return new Snapshot("", Double.NaN, 0L, 0L, false);
        }
    }
}
