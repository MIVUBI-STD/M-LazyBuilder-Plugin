package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HistoryRecoveryUnreadableTest {
    @TempDir Path tempDir;

    @Test
    void unreadableIncompleteJournalIsQuarantinedAndExplicitlyDiscardable()
            throws Exception {
        Path broken = tempDir.resolve("broken.lbh2.incomplete");
        Files.write(broken, new byte[]{1, 2, 3});

        HistoryRecoveryManager manager =
                new HistoryRecoveryManager(new DiskChangeSetStorage(tempDir));
        assertEquals(1, manager.unreadableIncompleteFiles().size());
        assertEquals(1, manager.discardUnreadableIncompleteFiles());
        assertFalse(Files.exists(broken));
    }
}
