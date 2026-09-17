package com.halokaryamedia.lazybuilder.builder.material;

import com.halokaryamedia.lazybuilder.builder.history.ChangeSetWriter;
import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.HistoryRequirement;
import com.halokaryamedia.lazybuilder.builder.history.HistoryStorageRouter;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.operation.OperationPlan;
import com.halokaryamedia.lazybuilder.builder.region.ChunkWorkUnit;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/** Resolves a MaterialOperation into committed History v2 before any world mutation occurs. */
public final class MaterialOperationPreparer {
    private MaterialOperationPreparer() { }

    public static Optional<PreparedMaterialMutation> prepare(OperationPlan plan, BlockStateSource source,
            HistoryStorageRouter history, long estimatedHistoryBytes) throws IOException {
        Objects.requireNonNull(plan, "plan"); Objects.requireNonNull(source, "source"); Objects.requireNonNull(history, "history");
        if (estimatedHistoryBytes < 0) throw new IllegalArgumentException("estimatedHistoryBytes must be >= 0");
        if (!(plan.operation() instanceof MaterialOperation operation)) {
            throw new IllegalArgumentException("OperationPlan does not contain a MaterialOperation");
        }
        if (operation.historyRequirement() != HistoryRequirement.REQUIRED) {
            throw new IllegalArgumentException("Production material mutation requires durable history");
        }

        ChangeSetWriter writer = history.begin(HistoryRequirement.REQUIRED, estimatedHistoryBytes, operation.id().toString())
                .orElseThrow(() -> new IllegalStateException("Required history storage was not selected"));
        try (writer) {
            long plannedChanges = 0L;
            for (ChunkWorkUnit unit : plan.workUnits()) {
                if (operation.cancellationToken().isCancellationRequested()) {
                    writer.abort(); return Optional.empty();
                }
                ChunkChangeSet chunk = MaterialMutationPlanner.plan(unit, operation.region(), operation.material(),
                        operation.materialMask(), operation.seed(), source);
                if (chunk.size() == 0) continue;
                writer.append(chunk);
                plannedChanges = Math.addExact(plannedChanges, chunk.size());
            }
            StoredChangeSet stored = writer.commit();
            return Optional.of(new PreparedMaterialMutation(stored, plannedChanges));
        } catch (ArithmeticException e) {
            throw new IOException("Material mutation change count overflow", e);
        }
    }
}
