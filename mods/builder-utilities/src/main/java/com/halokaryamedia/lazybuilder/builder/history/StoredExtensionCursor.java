package com.halokaryamedia.lazybuilder.builder.history;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/** Back-pressured forward cursor over committed History extension frames. */
public final class StoredExtensionCursor implements HistoryExtensionCursor {
    private final StoredChangeSet stored;
    private final BlockingQueue<Event> events = new ArrayBlockingQueue<>(1);
    private final Thread producer;
    private volatile boolean closed;
    private boolean exhausted;
    private long observedExtensions;

    private StoredExtensionCursor(StoredChangeSet stored) {
        this.stored = Objects.requireNonNull(stored, "stored");
        this.producer = Thread.ofVirtual()
                .name("lazybuilder-history-extension-cursor-" + stored.operationId())
                .start(this::produce);
    }

    public static StoredExtensionCursor open(StoredChangeSet stored) {
        return new StoredExtensionCursor(stored);
    }

    @Override public String operationId() { return stored.operationId(); }
    @Override public long observedExtensions() { return observedExtensions; }
    @Override public boolean exhausted() { return exhausted; }

    @Override
    public HistoryExtensionFrame nextExtension() throws IOException {
        ensureOpen();
        if (exhausted) return null;
        Event event;
        try {
            event = events.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for History extension", e);
        }
        if (event instanceof FrameEvent frame) {
            observedExtensions = Math.addExact(observedExtensions, 1L);
            return frame.frame();
        }
        if (event instanceof EndEvent) {
            exhausted = true;
            if (observedExtensions != stored.extensionCount()) {
                throw new IOException("History extension cursor count mismatch: expected "
                        + stored.extensionCount() + ", observed " + observedExtensions);
            }
            return null;
        }
        FailureEvent failure = (FailureEvent) event;
        if (failure.cause() instanceof IOException io) throw io;
        throw new IOException("History extension cursor producer failed", failure.cause());
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
            stored.visitExtensions(frame -> {
                if (closed) throw new IOException("History extension cursor closed");
                put(new FrameEvent(frame));
                return true;
            });
            if (!closed) put(EndEvent.INSTANCE);
        } catch (Throwable failure) {
            if (!closed) {
                try { put(new FailureEvent(failure)); }
                catch (IOException ignored) { }
            }
        }
    }

    private void put(Event event) throws IOException {
        try {
            events.put(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("History extension cursor producer interrupted", e);
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("History extension cursor is closed");
    }

    private sealed interface Event permits FrameEvent, EndEvent, FailureEvent {}
    private record FrameEvent(HistoryExtensionFrame frame) implements Event {
        private FrameEvent { Objects.requireNonNull(frame, "frame"); }
    }
    private enum EndEvent implements Event { INSTANCE }
    private record FailureEvent(Throwable cause) implements Event {
        private FailureEvent { Objects.requireNonNull(cause, "cause"); }
    }
}
