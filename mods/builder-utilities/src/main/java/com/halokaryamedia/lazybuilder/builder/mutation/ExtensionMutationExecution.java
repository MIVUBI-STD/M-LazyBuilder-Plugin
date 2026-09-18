package com.halokaryamedia.lazybuilder.builder.mutation;

public record ExtensionMutationExecution(
        MutationExecutionState state,
        long processedExtensions,
        String conflictTypeId,
        Integer conflictChunkX,
        Integer conflictChunkZ,
        Long conflictLocalKey
) {
    public ExtensionMutationExecution {
        if (state == null) throw new NullPointerException("state");
        if (processedExtensions < 0) throw new IllegalArgumentException("processedExtensions must be >= 0");
    }

    public static ExtensionMutationExecution completed(long count) {
        return new ExtensionMutationExecution(MutationExecutionState.COMPLETED, count, null, null, null, null);
    }

    public static ExtensionMutationExecution cancelled(long count) {
        return new ExtensionMutationExecution(MutationExecutionState.CANCELLED, count, null, null, null, null);
    }

    public static ExtensionMutationExecution conflict(long count, com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame frame) {
        return new ExtensionMutationExecution(
                MutationExecutionState.CONFLICT,
                count,
                frame.typeId(),
                frame.chunkX(),
                frame.chunkZ(),
                frame.localKey()
        );
    }
}
