package com.halokaryamedia.lazybuilder.builder.axiom;

public record BiomeBatchDispatchProgress(
        BiomeBatchDispatchState state,
        long processedExtensions,
        String detail
) {
    public BiomeBatchDispatchProgress {
        if (state == null) throw new NullPointerException("state");
        if (processedExtensions < 0) {
            throw new IllegalArgumentException("processedExtensions must be >= 0");
        }
    }
}
