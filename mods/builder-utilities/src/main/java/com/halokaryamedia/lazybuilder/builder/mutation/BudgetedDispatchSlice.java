package com.halokaryamedia.lazybuilder.builder.mutation;

public record BudgetedDispatchSlice(
        BudgetedDispatchState state,
        long sliceVisitedChunks,
        long sliceDispatchedBlocks,
        long totalVisitedChunks,
        long totalDispatchedBlocks,
        Integer conflictX,
        Integer conflictY,
        Integer conflictZ,
        String detail
) {
    public BudgetedDispatchSlice {
        if (state == null) throw new NullPointerException("state");
        if (sliceVisitedChunks < 0 || sliceDispatchedBlocks < 0
                || totalVisitedChunks < 0 || totalDispatchedBlocks < 0) {
            throw new IllegalArgumentException("dispatch counts must be >= 0");
        }
        if (state == BudgetedDispatchState.CONFLICT) {
            if (conflictX == null || conflictY == null || conflictZ == null) {
                throw new IllegalArgumentException("CONFLICT requires coordinates");
            }
        } else if (conflictX != null || conflictY != null || conflictZ != null) {
            throw new IllegalArgumentException("Conflict coordinates are valid only for CONFLICT");
        }
        if (state == BudgetedDispatchState.BUDGET_EXCEEDED) {
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException("BUDGET_EXCEEDED requires detail");
            }
        } else if (detail != null) {
            throw new IllegalArgumentException("detail is valid only for BUDGET_EXCEEDED");
        }
    }
}
