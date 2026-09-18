package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChangeSetWriter;
import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.HistoryStorageRouter;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Builds a durable block-only changeset containing only mutations whose AFTER state
 * is currently present in the world. BEFORE matches are omitted; any third state is
 * an explicit conflict. This is the core primitive for KEEP_CHANGES cancellation.
 */
public final class AppliedMutationCompactor {
    private AppliedMutationCompactor() {
    }

    public static AppliedMutationCompaction compact(
            StoredChangeSet prepared,
            WorldBlockStateSource world,
            HistoryStorageRouter history,
            long estimatedHistoryBytes,
            String operationId
    ) throws IOException {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(history, "history");
        if (prepared.extensionCount() != 0) {
            throw new IllegalArgumentException("Applied mutation compaction supports block-only History v2 plans");
        }
        if (estimatedHistoryBytes < 0) throw new IllegalArgumentException("estimatedHistoryBytes must be >= 0");
        if (operationId == null || operationId.isBlank()) throw new IllegalArgumentException("operationId must be non-blank");

        ChangeSetWriter writer = history.beginDurable(operationId);

        MutableState state = new MutableState();
        try (writer) {
            prepared.visitChunks(chunk -> {
                ChunkChangeSet subset = appliedSubset(chunk, world, state);
                if (state.conflict) return false;
                if (subset.size() > 0) {
                    writer.append(subset);
                    state.applied = Math.addExact(state.applied, subset.size());
                }
                return true;
            });

            if (state.conflict) {
                writer.abort();
                return AppliedMutationCompaction.conflict(state.conflictX, state.conflictY, state.conflictZ);
            }
            if (state.applied == 0) {
                writer.abort();
                return AppliedMutationCompaction.empty();
            }
            StoredChangeSet compacted = writer.commit();
            if (compacted.changeCount() != state.applied) {
                compacted.close();
                throw new IOException("Compacted History count mismatch");
            }
            return AppliedMutationCompaction.compacted(compacted);
        } catch (ArithmeticException e) {
            throw new IOException("Applied mutation compaction count overflow", e);
        }
    }

    private static ChunkChangeSet appliedSubset(
            ChunkChangeSet chunk,
            WorldBlockStateSource world,
            MutableState state
    ) throws IOException {
        long[] positions = chunk.positions();
        int[] beforeIndexes = chunk.beforeStates();
        int[] afterIndexes = chunk.afterStates();
        long[] keptPositions = new long[positions.length];
        int[] keptBefore = new int[positions.length];
        int[] keptAfter = new int[positions.length];
        int kept = 0;
        final int chunkBaseX;
        final int chunkBaseZ;
        try {
            chunkBaseX = Math.multiplyExact(chunk.chunkX(), 16);
            chunkBaseZ = Math.multiplyExact(chunk.chunkZ(), 16);
        } catch (ArithmeticException e) {
            throw new IOException("History chunk coordinate overflow during compaction", e);
        }

        for (int i = 0; i < positions.length; i++) {
            long packed = positions[i];
            int worldX;
            int worldZ;
            try {
                worldX = Math.addExact(chunkBaseX, LocalBlockPosition.localX(packed));
                worldZ = Math.addExact(chunkBaseZ, LocalBlockPosition.localZ(packed));
            } catch (ArithmeticException e) {
                throw new IOException("History block coordinate overflow during compaction", e);
            }
            int y = LocalBlockPosition.y(packed);
            String actual = requireState(world.readBlockState(worldX, y, worldZ));
            String before = chunk.palette().get(beforeIndexes[i]);
            String after = chunk.palette().get(afterIndexes[i]);

            if (actual.equals(after)) {
                keptPositions[kept] = packed;
                keptBefore[kept] = beforeIndexes[i];
                keptAfter[kept] = afterIndexes[i];
                kept++;
            } else if (!actual.equals(before)) {
                state.conflict = true;
                state.conflictX = worldX;
                state.conflictY = y;
                state.conflictZ = worldZ;
                break;
            }
        }

        return new ChunkChangeSet(
                chunk.chunkX(),
                chunk.chunkZ(),
                chunk.palette(),
                Arrays.copyOf(keptPositions, kept),
                Arrays.copyOf(keptBefore, kept),
                Arrays.copyOf(keptAfter, kept)
        );
    }

    private static String requireState(String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("world state must be non-blank");
        }
        return state;
    }

    private static final class MutableState {
        long applied;
        boolean conflict;
        int conflictX;
        int conflictY;
        int conflictZ;
    }
}
