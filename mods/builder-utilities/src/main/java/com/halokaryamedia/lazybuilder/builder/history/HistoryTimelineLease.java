package com.halokaryamedia.lazybuilder.builder.history;

import java.util.Objects;

/**
 * Exclusive asynchronous timeline action. Stack movement occurs only on complete().
 * Closing an unfinished lease aborts it without moving history pointers.
 */
public final class HistoryTimelineLease implements AutoCloseable {
    private final HistoryTimeline owner;
    private final StoredChangeSet changeSet;
    private final ReplayDirection direction;
    private boolean finished;
    private boolean valid = true;

    HistoryTimelineLease(
            HistoryTimeline owner,
            StoredChangeSet changeSet,
            ReplayDirection direction
    ) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.changeSet = Objects.requireNonNull(changeSet, "changeSet");
        this.direction = Objects.requireNonNull(direction, "direction");
    }

    public StoredChangeSet changeSet() { return changeSet; }
    public ReplayDirection direction() { return direction; }

    public synchronized void complete() {
        ensureOpen();
        owner.completeLease(this);
        finished = true;
    }

    public synchronized void abort() {
        if (finished || !valid) return;
        owner.abortLease(this);
        finished = true;
    }

    @Override
    public void close() {
        abort();
    }

    synchronized void invalidateFromOwner() {
        valid = false;
        finished = true;
    }

    private void ensureOpen() {
        if (finished || !valid) {
            throw new IllegalStateException("History replay lease is already finished");
        }
    }
}
