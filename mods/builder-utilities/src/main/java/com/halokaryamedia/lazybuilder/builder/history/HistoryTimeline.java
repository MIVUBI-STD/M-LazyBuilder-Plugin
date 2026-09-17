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

    public HistoryTimeline(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be > 0");
        }
        this.maxEntries = maxEntries;
    }

    public synchronized void record(StoredChangeSet changeSet) throws IOException {
        Objects.requireNonNull(changeSet, "changeSet");
        // Clear redo first. If cleanup fails, the new entry has not been published.
        closeAll(redo);
        redo.clear();

        // Evict before adding the new entry so an eviction failure cannot leave a
        // newly-published entry behind while record() reports failure.
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

    public synchronized boolean undo(HistoryApplier applier) throws IOException {
        Objects.requireNonNull(applier, "applier");
        StoredChangeSet next = undo.peekLast();
        if (next == null) {
            return false;
        }
        applier.apply(next, ReplayDirection.UNDO);
        undo.removeLast();
        redo.addLast(next);
        return true;
    }

    public synchronized boolean redo(HistoryApplier applier) throws IOException {
        Objects.requireNonNull(applier, "applier");
        StoredChangeSet next = redo.peekLast();
        if (next == null) {
            return false;
        }
        applier.apply(next, ReplayDirection.REDO);
        redo.removeLast();
        undo.addLast(next);
        return true;
    }

    public synchronized int undoSize() {
        return undo.size();
    }

    public synchronized int redoSize() {
        return redo.size();
    }

    @Override
    public synchronized void close() throws IOException {
        IOException failure = null;
        failure = closeAllCollecting(undo, failure);
        failure = closeAllCollecting(redo, failure);
        undo.clear();
        redo.clear();
        if (failure != null) {
            throw failure;
        }
    }

    private static void closeAll(Deque<StoredChangeSet> sets) throws IOException {
        IOException failure = closeAllCollecting(sets, null);
        if (failure != null) {
            throw failure;
        }
    }

    private static IOException closeAllCollecting(Deque<StoredChangeSet> sets, IOException failure) {
        for (StoredChangeSet set : sets) {
            try {
                set.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        return failure;
    }
}
