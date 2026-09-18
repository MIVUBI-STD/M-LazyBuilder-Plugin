package com.halokaryamedia.lazybuilder.builder.history;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/**
 * Bounded undo/redo timeline. Stack movement occurs only after the supplied
 * applier reports successful completion.
 */
public final class HistoryTimeline implements AutoCloseable {
    private final int maxEntries;
    private final Deque<StoredChangeSet> undo = new ArrayDeque<>();
    private final Deque<StoredChangeSet> redo = new ArrayDeque<>();
    private HistoryTimelineLease activeLease;

    public HistoryTimeline(int maxEntries) {
        if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be > 0");
        this.maxEntries = maxEntries;
    }

    public synchronized void record(StoredChangeSet changeSet) throws IOException {
        Objects.requireNonNull(changeSet, "changeSet");
        ensureNoActiveLease();

        // Remove entries only after their close succeeds. If cleanup fails,
        // the remaining stack never contains entries already closed earlier
        // in this cleanup pass.
        closeAndRemoveAll(redo);

        while (undo.size() >= maxEntries) {
            StoredChangeSet oldest = undo.peekFirst();
            oldest.close();
            undo.removeFirst();
        }
        undo.addLast(changeSet);
    }

    public synchronized Optional<String> nextUndoOperationId() {
        StoredChangeSet next = undo.peekLast();
        return next == null ? Optional.empty() : Optional.of(next.operationId());
    }

    public synchronized Optional<String> nextRedoOperationId() {
        StoredChangeSet next = redo.peekLast();
        return next == null ? Optional.empty() : Optional.of(next.operationId());
    }

    public synchronized Optional<HistoryJournalSummary> nextUndoSummary() {
        StoredChangeSet next = undo.peekLast();
        return next == null ? Optional.empty() : Optional.of(summary(next));
    }

    public synchronized Optional<HistoryJournalSummary> nextRedoSummary() {
        StoredChangeSet next = redo.peekLast();
        return next == null ? Optional.empty() : Optional.of(summary(next));
    }

    public synchronized Optional<HistoryTimelineLease> beginUndo() {
        ensureNoActiveLease();
        StoredChangeSet next = undo.peekLast();
        if (next == null) return Optional.empty();
        activeLease = new HistoryTimelineLease(this, next, ReplayDirection.UNDO);
        return Optional.of(activeLease);
    }

    public synchronized Optional<HistoryTimelineLease> beginRedo() {
        ensureNoActiveLease();
        StoredChangeSet next = redo.peekLast();
        if (next == null) return Optional.empty();
        activeLease = new HistoryTimelineLease(this, next, ReplayDirection.REDO);
        return Optional.of(activeLease);
    }

    public synchronized boolean undo(HistoryApplier applier) throws IOException {
        Objects.requireNonNull(applier, "applier");
        Optional<HistoryTimelineLease> lease = beginUndo();
        if (lease.isEmpty()) return false;
        try (HistoryTimelineLease action = lease.get()) {
            applier.apply(action.changeSet(), action.direction());
            action.complete();
        }
        return true;
    }

    public synchronized boolean redo(HistoryApplier applier) throws IOException {
        Objects.requireNonNull(applier, "applier");
        Optional<HistoryTimelineLease> lease = beginRedo();
        if (lease.isEmpty()) return false;
        try (HistoryTimelineLease action = lease.get()) {
            applier.apply(action.changeSet(), action.direction());
            action.complete();
        }
        return true;
    }

    synchronized void completeLease(HistoryTimelineLease lease) {
        requireActiveLease(lease);
        if (lease.direction() == ReplayDirection.UNDO) {
            if (undo.peekLast() != lease.changeSet()) {
                throw new IllegalStateException("Undo stack changed during active lease");
            }
            undo.removeLast();
            redo.addLast(lease.changeSet());
        } else {
            if (redo.peekLast() != lease.changeSet()) {
                throw new IllegalStateException("Redo stack changed during active lease");
            }
            redo.removeLast();
            undo.addLast(lease.changeSet());
        }
        activeLease = null;
    }

    synchronized void abortLease(HistoryTimelineLease lease) {
        requireActiveLease(lease);
        activeLease = null;
    }

    synchronized boolean preserveLeaseForRecovery(
            HistoryTimelineLease lease
    ) throws IOException {
        requireActiveLease(lease);
        StoredChangeSet changeSet = lease.changeSet();
        Deque<StoredChangeSet> ownerStack =
                lease.direction() == ReplayDirection.UNDO ? undo : redo;
        if (ownerStack.peekLast() != changeSet) {
            throw new IllegalStateException(
                    "History stack changed during active replay lease");
        }
        if (!changeSet.preserveForRecovery()) {
            return false;
        }
        ownerStack.removeLast();
        activeLease = null;
        return true;
    }

    public synchronized int undoSize() { return undo.size(); }
    public synchronized int redoSize() { return redo.size(); }

    @Override
    public synchronized void close() throws IOException {
        if (activeLease != null) {
            activeLease.invalidateFromOwner();
            activeLease = null;
        }
        IOException failure = null;
        failure = closeAllCollectingAndRemoving(undo, failure);
        failure = closeAllCollectingAndRemoving(redo, failure);
        if (failure != null) throw failure;
    }

    private void ensureNoActiveLease() {
        if (activeLease != null) {
            throw new IllegalStateException("History timeline already has an active replay lease");
        }
    }

    private void requireActiveLease(HistoryTimelineLease lease) {
        if (activeLease != lease) {
            throw new IllegalStateException("History replay lease is not active");
        }
    }

    private static HistoryJournalSummary summary(StoredChangeSet set) {
        return new HistoryJournalSummary(
                set.operationId(), set.changeCount(), set.extensionCount());
    }

    private static void closeAndRemoveAll(Deque<StoredChangeSet> sets) throws IOException {
        while (!sets.isEmpty()) {
            StoredChangeSet next = sets.peekFirst();
            next.close();
            sets.removeFirst();
        }
    }

    private static IOException closeAllCollectingAndRemoving(
            Deque<StoredChangeSet> sets,
            IOException failure
    ) {
        while (!sets.isEmpty()) {
            StoredChangeSet next = sets.removeFirst();
            try {
                next.close();
            } catch (IOException e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        }
        return failure;
    }
}
