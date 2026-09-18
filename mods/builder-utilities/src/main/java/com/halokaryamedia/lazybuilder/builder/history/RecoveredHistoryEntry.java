package com.halokaryamedia.lazybuilder.builder.history;

import com.halokaryamedia.lazybuilder.builder.mutation.PreparedReconciliationReport;
import com.halokaryamedia.lazybuilder.builder.mutation.ReconciliationState;
import com.halokaryamedia.lazybuilder.builder.mutation.RecoveredBlockMutation;

import java.io.IOException;
import java.util.Objects;

/**
 * Ownership wrapper for one committed History file discovered after restart.
 * An entry must be transferred to a session/timeline or explicitly closed.
 */
public final class RecoveredHistoryEntry implements AutoCloseable {
    private final StoredChangeSet stored;
    private final PreparedReconciliationReport reconciliation;
    private boolean transferred;
    private boolean closed;

    RecoveredHistoryEntry(
            StoredChangeSet stored,
            PreparedReconciliationReport reconciliation
    ) {
        this.stored = Objects.requireNonNull(stored, "stored");
        this.reconciliation = reconciliation;
    }

    public String operationId() { return stored.operationId(); }
    public long changeCount() { return stored.changeCount(); }
    public long extensionCount() { return stored.extensionCount(); }
    public boolean blockOnly() { return stored.extensionCount() == 0; }
    public PreparedReconciliationReport reconciliation() { return reconciliation; }

    public RecoveredBlockMutation transferForResume() {
        ensureOwned();
        if (!blockOnly()) {
            throw new IllegalStateException("Extension-aware history cannot use block-only resume");
        }
        transferred = true;
        return new RecoveredBlockMutation(stored);
    }

    public void transferFullyAppliedTo(HistoryTimeline timeline) throws IOException {
        Objects.requireNonNull(timeline, "timeline");
        ensureOwned();
        if (reconciliation == null || reconciliation.state() != ReconciliationState.FULLY_APPLIED) {
            throw new IllegalStateException("Only FULLY_APPLIED recovered history can be published to undo");
        }
        timeline.record(stored);
        transferred = true;
    }

    @Override
    public void close() throws IOException {
        if (closed || transferred) return;
        stored.close();
        closed = true;
    }

    private void ensureOwned() {
        if (closed || transferred) {
            throw new IllegalStateException("Recovered history ownership was already released");
        }
    }
}
