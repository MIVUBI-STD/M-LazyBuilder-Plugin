package com.halokaryamedia.lazybuilder.builder.axiom;

public record BlockEntityBatchDispatchProgress(
        BlockEntityBatchDispatchState state,
        long processedExtensions,
        String detail
) {
    public BlockEntityBatchDispatchProgress {
        if (state == null) throw new NullPointerException("state");
        if (processedExtensions < 0) {
            throw new IllegalArgumentException("processedExtensions must be >= 0");
        }
    }
}
