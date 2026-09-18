package com.halokaryamedia.lazybuilder.builder.axiom;

public record EntityBatchDispatchProgress(
        EntityBatchDispatchState state,
        long processedExtensions,
        String detail
) {
    public EntityBatchDispatchProgress {
        if (state == null) throw new NullPointerException("state");
        if (processedExtensions < 0) {
            throw new IllegalArgumentException("processedExtensions must be >= 0");
        }
    }
}
