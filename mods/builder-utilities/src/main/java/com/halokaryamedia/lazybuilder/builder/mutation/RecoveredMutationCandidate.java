package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;

import java.io.IOException;
import java.util.Objects;

/** One durable orphan history file plus its world-aware startup classification. */
public final class RecoveredMutationCandidate implements AutoCloseable {
    private final StoredChangeSet changeSet;
    private final PreparedReconciliationReport blocks;
    private final ExtensionReconciliationReport extensions;
    private final ReconciliationState state;

    public RecoveredMutationCandidate(
            StoredChangeSet changeSet,
            PreparedReconciliationReport blocks,
            ExtensionReconciliationReport extensions,
            ReconciliationState state
    ) {
        this.changeSet = Objects.requireNonNull(changeSet, "changeSet");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.extensions = Objects.requireNonNull(extensions, "extensions");
        this.state = Objects.requireNonNull(state, "state");
    }

    public StoredChangeSet changeSet() { return changeSet; }
    public PreparedReconciliationReport blocks() { return blocks; }
    public ExtensionReconciliationReport extensions() { return extensions; }
    public ReconciliationState state() { return state; }

    public boolean requiresUserDecision() {
        return state == ReconciliationState.PARTIALLY_APPLIED
                || state == ReconciliationState.CONFLICT;
    }

    @Override
    public void close() throws IOException {
        changeSet.close();
    }
}
