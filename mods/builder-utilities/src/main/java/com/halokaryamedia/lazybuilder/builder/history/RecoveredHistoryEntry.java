package com.halokaryamedia.lazybuilder.builder.history;

import com.halokaryamedia.lazybuilder.builder.mutation.ExtensionReconciliationReport;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedReconciliationReport;
import com.halokaryamedia.lazybuilder.builder.mutation.ReconciliationState;
import com.halokaryamedia.lazybuilder.builder.mutation.RecoveredPreparedMutation;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;

/** Ownership wrapper for one durable recovery journal and its world-aware classification. */
public final class RecoveredHistoryEntry implements AutoCloseable {
    private final StoredChangeSet stored;
    private final PreparedReconciliationReport blocks;
    private final ExtensionReconciliationReport extensions;
    private final ReconciliationState overallState;
    private final String unsupportedReason;
    private boolean transferred;
    private boolean closed;

    RecoveredHistoryEntry(
            StoredChangeSet stored,
            PreparedReconciliationReport blocks,
            ExtensionReconciliationReport extensions,
            ReconciliationState overallState,
            String unsupportedReason
    ) {
        this.stored = Objects.requireNonNull(stored, "stored");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.extensions = extensions;
        this.overallState = overallState;
        this.unsupportedReason = unsupportedReason;
        if ((overallState == null) == (unsupportedReason == null)) {
            throw new IllegalArgumentException(
                    "entry must be either classified or explicitly unsupported");
        }
    }

    public String operationId() { return stored.operationId(); }
    public long changeCount() { return stored.changeCount(); }
    public long extensionCount() { return stored.extensionCount(); }
    public boolean blockOnly() { return stored.extensionCount() == 0; }
    public PreparedReconciliationReport blockReconciliation() { return blocks; }
    public Optional<ExtensionReconciliationReport> extensionReconciliation() {
        return Optional.ofNullable(extensions);
    }
    public Optional<ReconciliationState> state() {
        return Optional.ofNullable(overallState);
    }
    public Optional<String> unsupportedReason() {
        return Optional.ofNullable(unsupportedReason);
    }
    public RecoveredPreparedMutation transferForResume() throws IOException {
        ensureOwned();
        if (overallState == null) {
            throw new IllegalStateException(
                    "Recovery entry cannot resume: " + unsupportedReason);
        }
        if (overallState == ReconciliationState.CONFLICT) {
            throw new IllegalStateException("Conflicted recovery entry cannot resume automatically");
        }
        long plannedChanges = actualBlockMutationCount();
        RecoveredPreparedMutation prepared =
                new RecoveredPreparedMutation(stored, plannedChanges);
        transferred = true;
        return prepared;
    }

    /**
     * Transfers an unclassified mixed journal to a caller that owns authoritative
     * compare-and-set execution for every extension type in the journal.
     *
     * <p>Block reconciliation must already be non-conflicting. This is intended for
     * async authorities such as ENTITY where a synchronous read target is unavailable
     * but replay itself is idempotent and conflict-detecting.</p>
     */
    public RecoveredPreparedMutation transferForAuthoritativeResume(
            Set<String> authoritativeExtensionTypes
    ) throws IOException {
        ensureOwned();
        Objects.requireNonNull(authoritativeExtensionTypes, "authoritativeExtensionTypes");
        if (blocks.state() == ReconciliationState.CONFLICT) {
            throw new IllegalStateException(
                    "Conflicted block state cannot resume automatically");
        }
        Set<String> required = extensionTypeIds();
        if (!authoritativeExtensionTypes.containsAll(required)) {
            Set<String> missing = new LinkedHashSet<>(required);
            missing.removeAll(authoritativeExtensionTypes);
            throw new IllegalStateException(
                    "Missing authoritative recovery support for extension types " + missing);
        }
        long plannedChanges = actualBlockMutationCount();
        RecoveredPreparedMutation prepared =
                new RecoveredPreparedMutation(stored, plannedChanges);
        transferred = true;
        return prepared;
    }

    /**
     * Reclaims wrapper ownership when a recovery mutation could not start and the
     * prepared journal was deliberately left untouched.
     */
    public void reclaimAfterFailedStart() {
        if (closed || !transferred) {
            throw new IllegalStateException(
                    "Recovery entry has no transferred ownership to reclaim");
        }
        transferred = false;
    }

    private long actualBlockMutationCount() throws IOException {
        long[] total = {0L};
        stored.visitChunks(chunk -> {
            for (int i = 0; i < chunk.size(); i++) {
                if (!chunk.beforeState(i).equals(chunk.afterState(i))) {
                    total[0] = Math.addExact(total[0], 1L);
                }
            }
            return true;
        });
        return total[0];
    }

    public long estimatedHistoryBytes() throws IOException {
        long[] total = {Math.multiplyExact(stored.changeCount(), 96L)};
        stored.visitExtensions(frame -> {
            long payloadBytes = Math.addExact(
                    frame.beforePayload().length,
                    frame.afterPayload().length);
            total[0] = Math.addExact(
                    total[0],
                    Math.addExact(96L, payloadBytes));
            return true;
        });
        return Math.max(1L, total[0]);
    }

    public Set<String> extensionTypeIds() throws IOException {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        stored.visitExtensions(frame -> {
            ids.add(frame.typeId());
            return true;
        });
        return Set.copyOf(ids);
    }

    public void transferFullyAppliedTo(HistoryTimeline timeline) throws IOException {
        Objects.requireNonNull(timeline, "timeline");
        ensureOwned();
        if (overallState != ReconciliationState.FULLY_APPLIED) {
            throw new IllegalStateException(
                    "Only FULLY_APPLIED recovered history can be published to undo");
        }
        timeline.record(stored);
        transferred = true;
    }

    /**
     * Releases runtime ownership while leaving the durable journal on disk.
     * Used when discovery/inspection fails before the user has chosen an action.
     */
    public void releaseForRetry() throws IOException {
        ensureOwned();
        if (!stored.preserveForRecovery()) {
            throw new IOException(
                    "Recovery journal could not be released for a later retry: "
                            + stored.operationId());
        }
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
