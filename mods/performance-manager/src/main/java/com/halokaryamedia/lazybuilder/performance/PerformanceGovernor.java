package com.halokaryamedia.lazybuilder.performance;

/**
 * Small adaptive governor for LazyBuilder-owned optional work.
 *
 * It does not change Minecraft correctness, networking, input, or authoritative state.
 * The governor only chooses budgets for first-party work that is already safe to defer.
 */
public final class PerformanceGovernor {
    private static final int STABLE_TICKS_TO_RELAX = 120;
    private static final int PRESSURED_TICKS_TO_PROTECT = 3;

    private Mode mode = Mode.BALANCED;
    private int stableTicks;
    private int pressuredTicks;
    private Profile profile = Profile.balanced();

    public Profile update(Input input) {
        Input safe = input == null ? Input.EMPTY : input;
        boolean protective = isProtective(safe);
        boolean relaxed = isRelaxed(safe);

        pressuredTicks = protective ? pressuredTicks + 1 : 0;
        stableTicks = relaxed ? stableTicks + 1 : 0;

        if (pressuredTicks >= PRESSURED_TICKS_TO_PROTECT) {
            mode = Mode.PROTECTIVE;
            stableTicks = 0;
        } else if (mode == Mode.PROTECTIVE && !protective && stableTicks >= 30) {
            mode = Mode.BALANCED;
            stableTicks = 0;
        } else if (mode == Mode.BALANCED && stableTicks >= STABLE_TICKS_TO_RELAX) {
            mode = Mode.THROUGHPUT;
            stableTicks = 0;
        } else if (mode == Mode.THROUGHPUT && (protective || safe.pressure() != FramePressure.NORMAL)) {
            mode = protective ? Mode.PROTECTIVE : Mode.BALANCED;
            stableTicks = 0;
        }

        int cullingBudgetPercent = cullingBudgetPercent(safe);
        profile = switch (mode) {
            case THROUGHPUT -> new Profile(mode, 64, 24, cullingBudgetPercent, 1);
            case BALANCED -> new Profile(mode, 48, 16, cullingBudgetPercent, 2);
            case PROTECTIVE -> new Profile(mode, 12, 2, Math.min(50, cullingBudgetPercent), 4);
        };
        return profile;
    }

    public Profile profile() {
        return profile;
    }

    public void reset() {
        mode = Mode.BALANCED;
        stableTicks = 0;
        pressuredTicks = 0;
        profile = Profile.balanced();
    }

    private static boolean isProtective(Input input) {
        return input.pressure() == FramePressure.HEAVY
                || input.frameControlMs() >= 33.33D
                || input.usedMemoryRatio() >= 0.90D
                || (input.chunkUploadBacklog() >= 64 && input.freeChunkBuffers() <= 1);
    }

    private static boolean isRelaxed(Input input) {
        return input.pressure() == FramePressure.NORMAL
                && input.frameControlMs() > 0.0D
                && input.frameControlMs() < 18.0D
                && input.usedMemoryRatio() < 0.75D
                && input.chunkUploadBacklog() < 16
                && input.chunkBuildBacklog() < 16;
    }

    private static int cullingBudgetPercent(Input input) {
        long hits = input.cullingCacheHits();
        long culled = input.cullingOccludedDecisions();
        double yield = hits <= 0L ? 0.0D : Math.min(1.0D, culled / (double) hits);

        if (input.cullingCpuAverageMs() >= 0.50D && yield < 0.05D) return 25;
        if (input.cullingCpuAverageMs() >= 0.25D && yield < 0.10D) return 50;
        if (input.cullingCpuAverageMs() >= 0.15D && yield < 0.15D) return 75;
        return 100;
    }

    public enum Mode {
        THROUGHPUT,
        BALANCED,
        PROTECTIVE
    }

    public record Profile(
            Mode mode,
            int chunkUploadBudget,
            int rebuildReleaseBudget,
            int cullingBudgetPercent,
            int shadowReuseMultiplier
    ) {
        public Profile {
            mode = mode == null ? Mode.BALANCED : mode;
            chunkUploadBudget = Math.max(1, chunkUploadBudget);
            rebuildReleaseBudget = Math.max(1, rebuildReleaseBudget);
            cullingBudgetPercent = Math.max(10, Math.min(100, cullingBudgetPercent));
            shadowReuseMultiplier = Math.max(1, Math.min(8, shadowReuseMultiplier));
        }

        public static Profile balanced() {
            return new Profile(Mode.BALANCED, 48, 16, 100, 2);
        }
    }

    public record Input(
            FramePressure pressure,
            double frameControlMs,
            int chunkUploadBacklog,
            int chunkBuildBacklog,
            int freeChunkBuffers,
            double usedMemoryRatio,
            long cullingCacheHits,
            long cullingOccludedDecisions,
            double cullingCpuAverageMs
    ) {
        private static final Input EMPTY =
                new Input(FramePressure.NORMAL, 0.0D, 0, 0, 0, 0.0D, 0L, 0L, 0.0D);

        public Input {
            pressure = pressure == null ? FramePressure.NORMAL : pressure;
            frameControlMs = Math.max(0.0D, frameControlMs);
            chunkUploadBacklog = Math.max(0, chunkUploadBacklog);
            chunkBuildBacklog = Math.max(0, chunkBuildBacklog);
            freeChunkBuffers = Math.max(0, freeChunkBuffers);
            usedMemoryRatio = Math.max(0.0D, Math.min(1.0D, usedMemoryRatio));
            cullingCacheHits = Math.max(0L, cullingCacheHits);
            cullingOccludedDecisions = Math.max(0L, cullingOccludedDecisions);
            cullingCpuAverageMs = Math.max(0.0D, cullingCpuAverageMs);
        }
    }
}
