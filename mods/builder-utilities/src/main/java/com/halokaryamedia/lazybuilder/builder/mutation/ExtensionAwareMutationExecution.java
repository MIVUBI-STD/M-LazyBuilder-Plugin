package com.halokaryamedia.lazybuilder.builder.mutation;

public record ExtensionAwareMutationExecution(
        MutationExecutionState state,
        long processedBlocks,
        long processedExtensions
) {
    public ExtensionAwareMutationExecution {
        if (state == null) throw new NullPointerException("state");
        if (processedBlocks < 0 || processedExtensions < 0) {
            throw new IllegalArgumentException("processed counts must be >= 0");
        }
    }
}
