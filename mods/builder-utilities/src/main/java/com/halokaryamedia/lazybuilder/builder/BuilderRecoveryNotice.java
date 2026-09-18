package com.halokaryamedia.lazybuilder.builder;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Passive world-join recovery discovery state; never performs mutation automatically. */
public final class BuilderRecoveryNotice {
    private final AtomicReference<Snapshot> snapshot =
            new AtomicReference<>(Snapshot.none());

    public Snapshot snapshot() {
        return snapshot.get();
    }

    public void update(String scopeId, int committed, int incomplete) {
        if (scopeId == null || scopeId.isBlank()) {
            throw new IllegalArgumentException("scopeId must be non-blank");
        }
        if (committed < 0 || incomplete < 0) {
            throw new IllegalArgumentException("journal counts must be >= 0");
        }
        snapshot.set(new Snapshot(
                Status.SCANNED,
                scopeId,
                committed,
                incomplete,
                null
        ));
    }

    public void failure(String detail) {
        snapshot.set(new Snapshot(
                Status.FAILED,
                "",
                0,
                0,
                detail == null || detail.isBlank() ? "unknown recovery scan failure" : detail
        ));
    }

    public void clear() {
        snapshot.set(Snapshot.none());
    }

    public enum Status {
        NOT_SCANNED,
        SCANNED,
        FAILED
    }

    public record Snapshot(
            Status status,
            String scopeId,
            int committedJournals,
            int incompleteJournals,
            String failure
    ) {
        public Snapshot {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(scopeId, "scopeId");
            if (committedJournals < 0 || incompleteJournals < 0) {
                throw new IllegalArgumentException("journal counts must be >= 0");
            }
            if (status == Status.FAILED && (failure == null || failure.isBlank())) {
                throw new IllegalArgumentException("FAILED notice requires detail");
            }
        }

        static Snapshot none() {
            return new Snapshot(Status.NOT_SCANNED, "", 0, 0, null);
        }

        public boolean hasRecoveryWork() {
            return committedJournals > 0 || incompleteJournals > 0;
        }
    }
}
