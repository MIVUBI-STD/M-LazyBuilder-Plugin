package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HistoryTimelineTest {
    @Test
    void successfulUndoAndRedoMoveTimelineCursor() throws IOException {
        HistoryTimeline timeline = new HistoryTimeline(4);
        timeline.record(stored("one"));
        timeline.record(stored("two"));

        List<String> applied = new ArrayList<>();
        assertTrue(timeline.undo((set, direction) -> applied.add(set.operationId() + ":" + direction)));
        assertEquals("one", timeline.nextUndoOperationId().orElseThrow());
        assertEquals("two", timeline.nextRedoOperationId().orElseThrow());

        assertTrue(timeline.redo((set, direction) -> applied.add(set.operationId() + ":" + direction)));
        assertEquals(List.of("two:UNDO", "two:REDO"), applied);
        timeline.close();
    }

    @Test
    void failedApplyLeavesTimelineUnchanged() throws IOException {
        HistoryTimeline timeline = new HistoryTimeline(4);
        timeline.record(stored("safe"));

        assertThrows(IOException.class, () -> timeline.undo((set, direction) -> {
            throw new IOException("world mutation rejected");
        }));
        assertEquals("safe", timeline.nextUndoOperationId().orElseThrow());
        assertTrue(timeline.nextRedoOperationId().isEmpty());
        timeline.close();
    }

    @Test
    void newCommitInvalidatesRedoAndBoundEvictsOldEntries() throws IOException {
        HistoryTimeline timeline = new HistoryTimeline(2);
        timeline.record(stored("one"));
        timeline.record(stored("two"));
        timeline.undo((set, direction) -> { });
        timeline.record(stored("three"));
        assertTrue(timeline.nextRedoOperationId().isEmpty());
        timeline.record(stored("four"));
        assertEquals(2, timeline.undoSize());
        assertEquals("four", timeline.nextUndoOperationId().orElseThrow());
        timeline.close();
    }

    private static StoredChangeSet stored(String operationId) throws IOException {
        ChangeSetWriter writer = new MemoryChangeSetStorage().begin(operationId);
        return writer.commit();
    }
}
