package com.halokaryamedia.lazybuilder.builder.history;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Back-pressured cursor adapter over {@link StoredChangeSet#visitChunks}.
 * A single virtual producer keeps the underlying committed stream open while the
 * consumer yields between slices; the queue capacity of one bounds read-ahead.
 */
public final class StoredChunkCursor implements ChunkChangeSetCursor {
    private final StoredChangeSet stored;
    private final BlockingQueue<Event> events = new ArrayBlockingQueue<>(1);
    private final Thread producer;
    private volatile boolean closed;
    private boolean exhausted;
    private long observedChanges;

    private StoredChunkCursor(StoredChangeSet stored) {
        this.stored = Objects.requireNonNull(stored, "stored");
        this.producer = Thread.ofVirtual()
                .name("lazybuilder-history-cursor-" + stored.operationId())
                .start(this::produce);
    }

    public static StoredChunkCursor open(StoredChangeSet stored) {
        return new StoredChunkCursor(stored);
    }

    @Override
    public String operationId() {
        return stored.operationId();
    }

    @Override
    public long observedChanges() {
        return observedChanges;
    }

    @Override
    public long observedExtensions() {
        return exhausted ? stored.extensionCount() : 0L;
    }

    @Override
    public boolean exhausted() {
        return exhausted;
    }

    @Override
    public ChunkChangeSet nextChunk() throws IOException {
        ensureOpen();
        if (exhausted) return null;
        Event event;
        try {
            event = events.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for History chunk", e);
        }
        if (event instanceof ChunkEvent chunkEvent) {
            try {
                observedChanges = Math.addExact(observedChanges, chunkEvent.chunk().size());
            } catch (ArithmeticException e) {
                throw new IOException("History cursor change count overflow", e);
            }
            return chunkEvent.chunk();
        }
        if (event instanceof EndEvent) {
            exhausted = true;
            if (observedChanges != stored.changeCount()) {
                throw new IOException("History cursor count mismatch: expected "
                        + stored.changeCount() + ", observed " + observedChanges);
            }
            return null;
        }
        FailureEvent failure = (FailureEvent) event;
        if (failure.cause() instanceof IOException io) throw io;
        throw new IOException("History cursor producer failed", failure.cause());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        producer.interrupt();
        events.clear();
    }

    private void produce() {
        try {
            stored.visitChunks(chunk -> {
                if (closed) throw new IOException("History cursor closed");
                put(new ChunkEvent(chunk));
                return true;
            });
            if (!closed) put(EndEvent.INSTANCE);
        } catch (Throwable failure) {
            if (!closed) {
                try {
                    put(new FailureEvent(failure));
                } catch (IOException ignored) {
                    // Consumer closed/interrupted while failure was being published.
                }
            }
        }
    }

    private void put(Event event) throws IOException {
        try {
            events.put(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("History cursor producer interrupted", e);
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("History chunk cursor is closed");
    }

    private sealed interface Event permits ChunkEvent, EndEvent, FailureEvent {
    }

    private record ChunkEvent(ChunkChangeSet chunk) implements Event {
        private ChunkEvent {
            Objects.requireNonNull(chunk, "chunk");
        }
    }

    private enum EndEvent implements Event { INSTANCE }

    private record FailureEvent(Throwable cause) implements Event {
        private FailureEvent {
            Objects.requireNonNull(cause, "cause");
        }
    }
}
