package com.halokaryamedia.lazybuilder.utility.debug;

import com.sun.management.OperatingSystemMXBean;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.biome.Biome;

import java.lang.management.ManagementFactory;
import java.util.Locale;

/**
 * Small bounded metric reader for Compact Debug.
 *
 * Fast Minecraft values are read from current client state. Process CPU is
 * sampled at most once per second and cached. GPU utilization deliberately
 * reports unavailable until a supported, truthful source is added.
 */
public final class CompactDebugMetrics {
    private static final long CPU_SAMPLE_INTERVAL_NANOS = 1_000_000_000L;
    private static final double BYTES_PER_MIB = 1024.0D * 1024.0D;

    private static long nextCpuSampleNanos;
    private static double cachedProcessCpuPercent = Double.NaN;

    private CompactDebugMetrics() {
    }

    public static CompactDebugSnapshot capture(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return new CompactDebugSnapshot(
                    Math.max(0, client.getCurrentFps()),
                    formatCpuPercent(sampleProcessCpuPercent()),
                    "Unavailable",
                    formatClientMemory(),
                    0,
                    0,
                    0,
                    "Unavailable",
                    "Unavailable",
                    "Unavailable",
                    "Unavailable",
                    "Unavailable",
                    "Unavailable",
                    false
            );
        }

        BlockPos pos = client.player.getBlockPos();
        CompactDebugServerState.Snapshot server = CompactDebugServerState.snapshot();
        String fallbackWorld = readableIdentifier(client.world.getRegistryKey().getValue().getPath());

        return new CompactDebugSnapshot(
                Math.max(0, client.getCurrentFps()),
                formatCpuPercent(sampleProcessCpuPercent()),
                "Unavailable",
                formatClientMemory(),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                formatFacing(client.player.getHorizontalFacing()),
                formatBiome(client, pos),
                formatTime(client.world.getTimeOfDay()),
                server.worldName().isBlank() ? fallbackWorld : server.worldName(),
                server.telemetryAvailable() ? formatCpuPercent(server.cpuPercent()) : "Unavailable",
                server.telemetryAvailable()
                        ? formatMemory(server.usedMemoryBytes(), server.maxMemoryBytes())
                        : "Unavailable",
                server.telemetryAvailable()
        );
    }

    private static double sampleProcessCpuPercent() {
        long now = System.nanoTime();
        if (now < nextCpuSampleNanos) return cachedProcessCpuPercent;
        nextCpuSampleNanos = now + CPU_SAMPLE_INTERVAL_NANOS;

        java.lang.management.OperatingSystemMXBean baseBean = ManagementFactory.getOperatingSystemMXBean();
        if (baseBean instanceof OperatingSystemMXBean bean) {
            double load = bean.getProcessCpuLoad();
            cachedProcessCpuPercent = load < 0.0D ? Double.NaN : load * 100.0D;
        } else {
            cachedProcessCpuPercent = Double.NaN;
        }
        return cachedProcessCpuPercent;
    }

    static String formatCpuPercent(double value) {
        return Double.isFinite(value) ? formatPercent(value) : "Unavailable";
    }

    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.0f%%", Math.max(0.0D, Math.min(100.0D, value)));
    }

    private static String formatClientMemory() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        return formatMemory(used, runtime.maxMemory());
    }

    static String formatMemory(long usedBytes, long maxBytes) {
        if (usedBytes < 0L || maxBytes <= 0L || usedBytes > maxBytes) return "Unavailable";
        return String.format(
                Locale.ROOT,
                "%.0f / %.0f MiB",
                usedBytes / BYTES_PER_MIB,
                maxBytes / BYTES_PER_MIB
        );
    }

    private static String formatFacing(Direction direction) {
        return switch (direction) {
            case NORTH -> "North (-Z)";
            case SOUTH -> "South (+Z)";
            case WEST -> "West (-X)";
            case EAST -> "East (+X)";
            default -> readableIdentifier(direction.getName());
        };
    }

    private static String formatBiome(MinecraftClient client, BlockPos pos) {
        return client.world.getBiome(pos)
                .getKey()
                .map(RegistryKey<Biome>::getValue)
                .map(id -> readableIdentifier(id.getPath()))
                .orElse("Unknown");
    }

    private static String formatTime(long timeOfDay) {
        long dayTicks = Math.floorMod(timeOfDay, 24_000L);
        int totalMinutes = (int) (((dayTicks + 6_000L) % 24_000L) * 1_440L / 24_000L);
        int hour = totalMinutes / 60;
        int minute = totalMinutes % 60;
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    private static String readableIdentifier(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        String[] words = value.replace('-', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) result.append(word.substring(1));
        }
        return result.isEmpty() ? value : result.toString();
    }
}
