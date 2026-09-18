package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.HistoryStorageRouter;
import com.halokaryamedia.lazybuilder.builder.material.BlockStateSource;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationToken;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPlanEntry;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One-shot bridge from Placement Engine output to durable structure mutation. */
public final class PlacementStructureMutationPreparer {
    private PlacementStructureMutationPreparer() {}

    public static Optional<PreparedStructureMutation> prepareBlocks(
            String operationId,
            List<PlacementPlanEntry> placements,
            StructureSourceResolver resolver,
            BlockStateTransform stateTransform,
            BlockStateSource existing,
            HistoryStorageRouter history,
            long estimatedHistoryBytes,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (cancellationToken.isCancellationRequested()) return Optional.empty();

        StructurePastePlan plan = StructurePlacementBatchPlanner.planBlocks(
                placements, resolver, stateTransform, existing);
        return StructureMutationPreparer.prepare(
                operationId,
                plan,
                history,
                estimatedHistoryBytes,
                cancellationToken
        );
    }

    public static Optional<PreparedStructureMutation> prepareAll(
            String operationId,
            List<PlacementPlanEntry> placements,
            StructureSourceResolver resolver,
            BlockStateTransform stateTransform,
            BlockStateSource existing,
            StructureAuxiliaryContext auxiliary,
            HistoryStorageRouter history,
            long estimatedHistoryBytes,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (cancellationToken.isCancellationRequested()) return Optional.empty();

        StructurePastePlan plan = StructurePlacementBatchPlanner.planAll(
                placements, resolver, stateTransform, existing, auxiliary);
        return StructureMutationPreparer.prepare(
                operationId,
                plan,
                history,
                estimatedHistoryBytes,
                cancellationToken
        );
    }
}
