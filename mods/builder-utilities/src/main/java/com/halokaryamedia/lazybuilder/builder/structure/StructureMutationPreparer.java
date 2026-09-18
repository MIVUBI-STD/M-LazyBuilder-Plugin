package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.ChangeSetWriter;
import com.halokaryamedia.lazybuilder.builder.history.HistoryStorageRouter;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationToken;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/** Commits a concrete structure paste plan to durable History before any mutation. */
public final class StructureMutationPreparer {
    private StructureMutationPreparer() {}

    public static Optional<PreparedStructureMutation> prepare(
            String operationId,
            StructurePastePlan plan,
            HistoryStorageRouter history,
            long estimatedHistoryBytes,
            CancellationToken cancellationToken
    ) throws IOException {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId must be non-blank");
        }
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (estimatedHistoryBytes < 0) {
            throw new IllegalArgumentException("estimatedHistoryBytes must be >= 0");
        }

        ChangeSetWriter writer = history.beginDurable(operationId);

        try (writer) {
            for (var chunk : plan.chunks()) {
                if (cancellationToken.isCancellationRequested()) {
                    writer.abort();
                    return Optional.empty();
                }
                writer.append(chunk);
            }
            for (var extension : plan.extensions()) {
                if (cancellationToken.isCancellationRequested()) {
                    writer.abort();
                    return Optional.empty();
                }
                writer.appendExtension(extension);
            }
            StoredChangeSet stored = writer.commit();
            return Optional.of(new PreparedStructureMutation(
                    stored,
                    plan.blockChanges(),
                    plan.extensionChanges()
            ));
        }
    }
}
