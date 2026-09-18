package com.halokaryamedia.lazybuilder.performance.rendering;

/** Current draw-plan diagnostics for the vanilla terrain submission path. */
public final class TerrainArenaDrawDiagnostics {
    private static volatile TerrainArenaDrawPlanner.Plan current = TerrainArenaDrawPlanner.Plan.EMPTY;

    private TerrainArenaDrawDiagnostics() {
    }

    public static void publish(TerrainArenaDrawPlanner.Plan plan) {
        current = plan == null ? TerrainArenaDrawPlanner.Plan.EMPTY : plan;
    }

    public static TerrainArenaDrawPlanner.Plan snapshot() {
        return current;
    }

    public static void clear() {
        current = TerrainArenaDrawPlanner.Plan.EMPTY;
    }
}
