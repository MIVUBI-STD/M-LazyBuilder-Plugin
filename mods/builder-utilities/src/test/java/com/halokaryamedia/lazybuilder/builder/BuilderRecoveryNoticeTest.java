package com.halokaryamedia.lazybuilder.builder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuilderRecoveryNoticeTest {
    @Test
    void tracksPassiveRecoveryDiscoveryWithoutOwningActions() {
        BuilderRecoveryNotice notice = new BuilderRecoveryNotice();
        assertEquals(
                BuilderRecoveryNotice.Status.NOT_SCANNED,
                notice.snapshot().status());

        notice.update("scope", 2, 1);
        assertTrue(notice.snapshot().hasRecoveryWork());
        assertEquals(2, notice.snapshot().committedJournals());

        notice.clear();
        assertFalse(notice.snapshot().hasRecoveryWork());
    }
}
