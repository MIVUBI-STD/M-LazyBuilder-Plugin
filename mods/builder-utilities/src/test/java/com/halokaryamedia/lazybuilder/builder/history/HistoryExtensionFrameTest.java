package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HistoryExtensionFrameTest {
    @Test
    void payloadsAreDefensivelyCopiedAndDirectionAware() {
        byte[] before = {1, 2};
        byte[] after = {3, 4};
        HistoryExtensionFrame frame = new HistoryExtensionFrame(
                "lazybuilder:block_entity", 1, -2, 7L, before, after);
        before[0] = 9;
        after[0] = 9;

        assertArrayEquals(new byte[]{1, 2}, frame.payload(ReplayDirection.UNDO));
        assertArrayEquals(new byte[]{3, 4}, frame.payload(ReplayDirection.REDO));
    }
}
