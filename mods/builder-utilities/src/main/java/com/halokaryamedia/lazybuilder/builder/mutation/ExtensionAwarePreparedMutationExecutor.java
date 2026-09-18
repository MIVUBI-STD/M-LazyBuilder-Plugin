package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.ReplayDirection;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationToken;

import java.io.IOException;
import java.util.Objects;

/** Applies mixed block + extension History using the correct dependency order. */
public final class ExtensionAwarePreparedMutationExecutor {
    private ExtensionAwarePreparedMutationExecutor() {}

    public static ExtensionAwareMutationExecution apply(
            StoredChangeSet stored,
            ReplayDirection direction,
            WorldBlockMutationTarget blocks,
            HistoryExtensionMutationTarget extensions,
            CancellationToken cancellation
    ) throws IOException {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(extensions, "extensions");
        Objects.requireNonNull(cancellation, "cancellation");

        long[] blockCount = {0L};
        long extensionCount = 0L;

        if (direction == ReplayDirection.UNDO) {
            ExtensionMutationExecution extensionResult = HistoryExtensionMutationExecutor.apply(
                    stored, direction, extensions, cancellation);
            extensionCount = extensionResult.processedExtensions();
            if (extensionResult.state() != MutationExecutionState.COMPLETED) {
                return new ExtensionAwareMutationExecution(
                        extensionResult.state(), blockCount[0], extensionCount);
            }
        }

        final ReplayDirection blockDirection = direction;
        final MutationExecutionState[] blockState = {MutationExecutionState.COMPLETED};
        stored.visitChunks(chunk -> {
            if (cancellation.isCancellationRequested()) {
                blockState[0] = MutationExecutionState.CANCELLED;
                return false;
            }
            ChunkChangeSet executable = blockDirection == ReplayDirection.REDO
                    ? chunk
                    : ChunkChangeSetTransforms.reverse(chunk);
            ChunkMutationExecution result = ChunkMutationExecutor.apply(
                    executable, blocks, cancellation);
            blockCount[0] = Math.addExact(blockCount[0], result.appliedChanges());
            blockState[0] = result.state();
            return result.state() == MutationExecutionState.COMPLETED;
        });
        if (blockState[0] != MutationExecutionState.COMPLETED) {
            return new ExtensionAwareMutationExecution(
                    blockState[0], blockCount[0], extensionCount);
        }

        if (direction == ReplayDirection.REDO) {
            ExtensionMutationExecution extensionResult = HistoryExtensionMutationExecutor.apply(
                    stored, direction, extensions, cancellation);
            extensionCount = extensionResult.processedExtensions();
            if (extensionResult.state() != MutationExecutionState.COMPLETED) {
                return new ExtensionAwareMutationExecution(
                        extensionResult.state(), blockCount[0], extensionCount);
            }
        }

        return new ExtensionAwareMutationExecution(
                MutationExecutionState.COMPLETED, blockCount[0], extensionCount);
    }
}
