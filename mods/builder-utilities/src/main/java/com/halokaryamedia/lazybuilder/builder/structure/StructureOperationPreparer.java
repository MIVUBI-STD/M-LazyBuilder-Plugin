package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.ChangeSetWriter;
import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.HistoryStorageRouter;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.material.BlockStateSource;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPlanEntry;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Compiles and durably prepares structure placement before any world mutation occurs. */
public final class StructureOperationPreparer {
    private StructureOperationPreparer() {}

    public static PreparedStructureMutation compileAndPrepare(
            UUID operationId,
            List<PlacementPlanEntry> placements,
            StructureTemplateSource templates,
            BlockStateSource existing,
            StructureOverlapPolicy overlapPolicy,
            HistoryStorageRouter history,
            long estimatedHistoryBytes
    ) throws IOException {
        Objects.requireNonNull(operationId, "operationId");
        List<ChunkChangeSet> chunks = StructurePlacementCompiler.compile(
                placements, templates, existing, overlapPolicy);
        return prepare(operationId.toString(), chunks, history, estimatedHistoryBytes);
    }

    public static PreparedStructureMutation prepare(
            String operationId,
            List<ChunkChangeSet> chunks,
            HistoryStorageRouter history,
            long estimatedHistoryBytes
    ) throws IOException {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId must be non-blank");
        }
        Objects.requireNonNull(chunks, "chunks");
        Objects.requireNonNull(history, "history");
        if (estimatedHistoryBytes < 0) {
            throw new IllegalArgumentException("estimatedHistoryBytes must be >= 0");
        }

        ChangeSetWriter writer = history.beginDurable(operationId);

        long planned = 0L;
        try (writer) {
            for (ChunkChangeSet chunk : chunks) {
                Objects.requireNonNull(chunk, "chunk");
                if (chunk.size() == 0) continue;
                writer.append(chunk);
                planned = Math.addExact(planned, chunk.size());
            }
            StoredChangeSet stored = writer.commit();
            return new PreparedStructureMutation(stored, planned);
        } catch (ArithmeticException e) {
            throw new IOException("Structure mutation change count overflow", e);
        }
    }
}
