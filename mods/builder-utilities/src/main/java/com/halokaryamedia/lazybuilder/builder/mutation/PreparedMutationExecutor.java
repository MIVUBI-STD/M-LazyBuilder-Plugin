package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationToken;

import java.io.IOException;
import java.util.Objects;

/**
 * Executes a committed block-only History v2 changeset directly from its storage tier.
 * The durable changeset is the recovery authority and can be streamed from disk without
 * retaining the whole operation in memory.
 */
public final class PreparedMutationExecutor {
    private PreparedMutationExecutor() {
    }

    public static PreparedMutationExecution execute(
            StoredChangeSet prepared,
            WorldBlockMutationTarget world,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (prepared.extensionCount() != 0) {
            throw new IllegalArgumentException(
                    "Block-only mutation executor cannot apply History extension frames");
        }

        MutableExecution aggregate = new MutableExecution(prepared.changeCount());
        prepared.visitChunks(chunk -> {
            if (aggregate.state != null || cancellationToken.isCancellationRequested()) {
                if (aggregate.state == null) {
                    aggregate.state = MutationExecutionState.CANCELLED;
                }
                return false;
            }

            ChunkMutationExecution result = ChunkMutationExecutor.apply(chunk, world, cancellationToken);
            aggregate.applied += result.appliedChanges();
            if (result.state() == MutationExecutionState.COMPLETED) {
                aggregate.completedChunks++;
                return true;
            }

            aggregate.state = result.state();
            aggregate.conflictX = result.conflictX();
            aggregate.conflictY = result.conflictY();
            aggregate.conflictZ = result.conflictZ();
            return false;
        });

        MutationExecutionState finalState = aggregate.state == null
                ? MutationExecutionState.COMPLETED
                : aggregate.state;
        return new PreparedMutationExecution(
                prepared.changeCount(),
                aggregate.applied,
                aggregate.completedChunks,
                finalState,
                aggregate.conflictX,
                aggregate.conflictY,
                aggregate.conflictZ
        );
    }

    private static final class MutableExecution {
        private final long planned;
        private long applied;
        private long completedChunks;
        private MutationExecutionState state;
        private Integer conflictX;
        private Integer conflictY;
        private Integer conflictZ;

        private MutableExecution(long planned) {
            this.planned = planned;
        }
    }
}
