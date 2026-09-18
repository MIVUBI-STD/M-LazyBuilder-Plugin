package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HistoryTimelineFailureTest {
    @Test
    void redoCleanupFailureDoesNotLeaveEarlierClosedEntriesInStack() throws Exception {
        HistoryTimeline timeline = new HistoryTimeline(8);
        TrackingStored first = new TrackingStored("first", false);
        TrackingStored failing = new TrackingStored("failing", true);

        // Build redo stack through successful undo moves.
        timeline.record(first);
        timeline.record(failing);
        timeline.undo((set, direction) -> {});
        timeline.undo((set, direction) -> {});
        assertEquals(2, timeline.redoSize());

        TrackingStored incoming = new TrackingStored("incoming", false);
        assertThrows(IOException.class, () -> timeline.record(incoming));

        // first was successfully closed and removed; failing remains as the only redo owner.
        assertEquals(1, first.closeCalls.get());
        assertEquals(1, timeline.redoSize());
        assertEquals("failing", timeline.nextRedoOperationId().orElseThrow());

        failing.failClose = false;
        timeline.close();
    }

    private static final class TrackingStored implements StoredChangeSet {
        private final String id;
        private final AtomicInteger closeCalls = new AtomicInteger();
        private boolean failClose;

        private TrackingStored(String id, boolean failClose) {
            this.id = id;
            this.failClose = failClose;
        }

        @Override public String operationId() { return id; }
        @Override public HistoryStorageTier storageTier() { return HistoryStorageTier.MEMORY; }
        @Override public long changeCount() { return 0; }
        @Override public long extensionCount() { return 0; }
        @Override public void replayAll(ReplayDirection direction, HistoryReplayConsumer consumer) {}
        @Override public void visitChunks(ChunkChangeSetVisitor visitor) {}

        @Override
        public void close() throws IOException {
            closeCalls.incrementAndGet();
            if (failClose) throw new IOException("close failed");
        }
    }
}
