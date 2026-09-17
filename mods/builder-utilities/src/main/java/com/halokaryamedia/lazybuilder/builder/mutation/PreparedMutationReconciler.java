package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;

import java.io.IOException;
import java.util.Objects;

/** Reconciles a committed block-only History v2 plan against current world state. */
public final class PreparedMutationReconciler {
    private PreparedMutationReconciler() {
    }

    public static PreparedReconciliationReport reconcile(
            StoredChangeSet prepared,
            WorldBlockStateSource world
    ) throws IOException {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(world, "world");
        if (prepared.extensionCount() != 0) {
            throw new IllegalArgumentException("Block-only reconciler cannot inspect History extension frames");
        }

        Aggregate aggregate = new Aggregate();
        prepared.visitChunks(chunk -> {
            ChunkReconciliationReport report = ChunkMutationReconciler.reconcile(chunk, world);
            aggregate.visitedChunks = Math.addExact(aggregate.visitedChunks, 1L);
            aggregate.total = Math.addExact(aggregate.total, report.totalChanges());
            aggregate.before = Math.addExact(aggregate.before, report.beforeMatches());
            aggregate.after = Math.addExact(aggregate.after, report.afterMatches());
            aggregate.conflicts = Math.addExact(aggregate.conflicts, report.conflicts());
            return true;
        });

        return new PreparedReconciliationReport(
                aggregate.visitedChunks,
                aggregate.total,
                aggregate.before,
                aggregate.after,
                aggregate.conflicts,
                classify(aggregate)
        );
    }

    private static ReconciliationState classify(Aggregate aggregate) {
        if (aggregate.total == 0) return ReconciliationState.EMPTY;
        if (aggregate.conflicts > 0) return ReconciliationState.CONFLICT;
        if (aggregate.after == aggregate.total) return ReconciliationState.FULLY_APPLIED;
        if (aggregate.before == aggregate.total) return ReconciliationState.NOT_APPLIED;
        return ReconciliationState.PARTIALLY_APPLIED;
    }

    private static final class Aggregate {
        long visitedChunks;
        long total;
        long before;
        long after;
        long conflicts;
    }
}
