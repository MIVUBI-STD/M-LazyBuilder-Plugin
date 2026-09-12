package com.halokaryamedia.lazybuilder.world.conversion;

import java.util.UUID;

/** Global V1 single-job owner for internal conversion work. */
public final class ConversionJobCoordinator {
    private UUID activeJob;

    public synchronized Lease acquire() {
        if (activeJob != null) {
            throw new IllegalStateException("A conversion job is already active: " + activeJob);
        }
        activeJob = UUID.randomUUID();
        return new Lease(activeJob);
    }

    public synchronized boolean isBusy() {
        return activeJob != null;
    }

    public synchronized UUID activeJobId() {
        return activeJob;
    }

    public final class Lease implements AutoCloseable {
        private final UUID jobId;
        private boolean closed;

        private Lease(UUID jobId) {
            this.jobId = jobId;
        }

        public UUID jobId() {
            return jobId;
        }

        @Override
        public void close() {
            synchronized (ConversionJobCoordinator.this) {
                if (closed) return;
                if (!jobId.equals(activeJob)) {
                    throw new IllegalStateException("Conversion job ownership changed unexpectedly: " + jobId);
                }
                activeJob = null;
                closed = true;
            }
        }
    }
}
