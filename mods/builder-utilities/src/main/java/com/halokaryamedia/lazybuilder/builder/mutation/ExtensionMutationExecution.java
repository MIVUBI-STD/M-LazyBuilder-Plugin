package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;

public record ExtensionMutationExecution(
        long totalExtensions,
        long processedExtensions,
        MutationExecutionState state,
        String conflictTypeId,
        Integer conflictChunkX,
        Integer conflictChunkZ,
        Long conflictLocalKey
) {
    public ExtensionMutationExecution {
        if (state == null) throw new NullPointerException("state");
        if (totalExtensions < 0 || processedExtensions < 0 || processedExtensions > totalExtensions) {
            throw new IllegalArgumentException("invalid extension execution counts");
        }
    }

    public static ExtensionMutationExecution completed(long total) {
        return new ExtensionMutationExecution(total, total, MutationExecutionState.COMPLETED,
                null, null, null, null);
    }

    public static ExtensionMutationExecution cancelled(long total, long processed) {
        return new ExtensionMutationExecution(total, processed, MutationExecutionState.CANCELLED,
                null, null, null, null);
    }

    public static ExtensionMutationExecution conflict(
            long total,
            long processed,
            HistoryExtensionFrame frame
    ) {
        return new ExtensionMutationExecution(
                total, processed, MutationExecutionState.CONFLICT,
                frame.typeId(), frame.chunkX(), frame.chunkZ(), frame.localKey());
    }
}
