package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChangeSetWriter;
import com.halokaryamedia.lazybuilder.builder.history.HistoryStorageRouter;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;

import java.io.IOException;
import java.util.Objects;

/** Builds a new durable block-only plan that applies the inverse of a committed changeset. */
public final class StoredChangeSetReverser {
    private StoredChangeSetReverser() {
    }

    public static StoredChangeSet reverse(
            StoredChangeSet source,
            HistoryStorageRouter history,
            long estimatedHistoryBytes,
            String operationId
    ) throws IOException {
        if (source.extensionCount() != 0) {
            throw new IllegalArgumentException(
                    "reverse() is block-only; use reverseBlocks() for mixed history");
        }
        return reverseBlocks(source, history, estimatedHistoryBytes, operationId);
    }

    public static StoredChangeSet reverseBlocks(
            StoredChangeSet source,
            HistoryStorageRouter history,
            long estimatedHistoryBytes,
            String operationId
    ) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(history, "history");
        if (estimatedHistoryBytes < 0) throw new IllegalArgumentException("estimatedHistoryBytes must be >= 0");
        if (operationId == null || operationId.isBlank()) throw new IllegalArgumentException("operationId must be non-blank");

        ChangeSetWriter writer = history.beginDurable(operationId);

        try (writer) {
            source.visitChunks(chunk -> {
                writer.append(ChunkChangeSetTransforms.reverse(chunk));
                return true;
            });
            StoredChangeSet reversed = writer.commit();
            if (reversed.changeCount() != source.changeCount()) {
                reversed.close();
                throw new IOException("Reversed History count mismatch");
            }
            return reversed;
        }
    }
}
