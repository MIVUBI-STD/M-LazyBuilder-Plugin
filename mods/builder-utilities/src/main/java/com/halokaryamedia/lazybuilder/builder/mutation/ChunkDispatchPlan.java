package com.halokaryamedia.lazybuilder.builder.mutation;

import java.util.List;

public record ChunkDispatchPlan(
        ChunkDispatchState state,
        List<ChunkDispatchEntry> entries,
        Integer conflictX,
        Integer conflictY,
        Integer conflictZ
) {
    public ChunkDispatchPlan {
        if (state == null) throw new NullPointerException("state");
        if (entries == null) throw new NullPointerException("entries");
        entries = List.copyOf(entries);
        if (state == ChunkDispatchState.CONFLICT) {
            if (conflictX == null || conflictY == null || conflictZ == null) {
                throw new IllegalArgumentException("CONFLICT requires coordinates");
            }
        } else if (conflictX != null || conflictY != null || conflictZ != null) {
            throw new IllegalArgumentException("Conflict coordinates are valid only for CONFLICT");
        }
        if (state == ChunkDispatchState.ALREADY_APPLIED && !entries.isEmpty()) {
            throw new IllegalArgumentException("ALREADY_APPLIED cannot contain dispatch entries");
        }
        if (state == ChunkDispatchState.READY && entries.isEmpty()) {
            throw new IllegalArgumentException("READY requires at least one dispatch entry");
        }
    }

    public static ChunkDispatchPlan ready(List<ChunkDispatchEntry> entries) {
        return new ChunkDispatchPlan(ChunkDispatchState.READY, entries, null, null, null);
    }

    public static ChunkDispatchPlan alreadyApplied() {
        return new ChunkDispatchPlan(ChunkDispatchState.ALREADY_APPLIED, List.of(), null, null, null);
    }

    public static ChunkDispatchPlan conflict(int x, int y, int z) {
        return new ChunkDispatchPlan(ChunkDispatchState.CONFLICT, List.of(), x, y, z);
    }
}
