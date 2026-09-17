package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationToken;

import java.util.Objects;

/**
 * Applies an already-planned chunk delta using optimistic before-state checks.
 *
 * <p>The caller must durably retain the corresponding mutation plan/history before
 * entering this method. A crash may still leave a partially applied chunk; the
 * plan is intentionally sufficient for {@link ChunkMutationReconciler} to classify
 * and recover that state later.</p>
 */
public final class ChunkMutationExecutor {
    private ChunkMutationExecutor() {
    }

    public static ChunkMutationExecution apply(
            ChunkChangeSet changes,
            WorldBlockMutationTarget world,
            CancellationToken cancellationToken
    ) {
        Objects.requireNonNull(changes, "changes");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(cancellationToken, "cancellationToken");

        long[] positions = changes.positions();
        int baseX = Math.multiplyExact(changes.chunkX(), 16);
        int baseZ = Math.multiplyExact(changes.chunkZ(), 16);
        long applied = 0;

        for (int i = 0; i < positions.length; i++) {
            if (cancellationToken.isCancellationRequested()) {
                return new ChunkMutationExecution(
                        changes.chunkX(), changes.chunkZ(), positions.length, applied,
                        MutationExecutionState.CANCELLED, null, null, null);
            }

            long packed = positions[i];
            int x = Math.addExact(baseX, LocalBlockPosition.localX(packed));
            int y = LocalBlockPosition.y(packed);
            int z = Math.addExact(baseZ, LocalBlockPosition.localZ(packed));
            String actual = requireState(world.readBlockState(x, y, z));
            String before = changes.beforeState(i);
            String after = changes.afterState(i);

            if (actual.equals(after)) {
                // Idempotent resume: this individual change is already applied.
                applied++;
                continue;
            }
            if (!actual.equals(before)) {
                return new ChunkMutationExecution(
                        changes.chunkX(), changes.chunkZ(), positions.length, applied,
                        MutationExecutionState.CONFLICT, x, y, z);
            }

            world.writeBlockState(x, y, z, after);
            String verified = requireState(world.readBlockState(x, y, z));
            if (!verified.equals(after)) {
                return new ChunkMutationExecution(
                        changes.chunkX(), changes.chunkZ(), positions.length, applied,
                        MutationExecutionState.CONFLICT, x, y, z);
            }
            applied++;
        }

        return new ChunkMutationExecution(
                changes.chunkX(), changes.chunkZ(), positions.length, applied,
                MutationExecutionState.COMPLETED, null, null, null);
    }

    private static String requireState(String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalStateException("world returned a blank block state");
        }
        return state;
    }
}
