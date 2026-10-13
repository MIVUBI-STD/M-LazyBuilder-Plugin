package com.halokaryamedia.lazybuilder.world.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldHeavyOperationFeedbackTest {
    @Test
    void normalImportAndRestoreKeepFamiliarSuccessMessage() {
        assertEquals("Import finalized.",
                WorldHeavyOperationOrchestrator.importCompletionMessage(false, false, false));
        assertEquals("Backup restored as a separate world.",
                WorldHeavyOperationOrchestrator.importCompletionMessage(true, false, false));
    }

    @Test
    void completedImportWithLeftoverArchiveReportsWarningInsteadOfGenericSuccess() {
        String result = WorldHeavyOperationOrchestrator.importCompletionMessage(false, false, true);
        assertTrue(result.startsWith("Warning: "));
        assertTrue(result.contains("archive cleanup is pending"));
    }

    @Test
    void concurrentPublicationAndArchiveCleanupFailuresAreBothReported() {
        String importMessage = WorldHeavyOperationOrchestrator.importCompletionMessage(false, true, true);
        String restoreMessage = WorldHeavyOperationOrchestrator.importCompletionMessage(true, true, false);
        assertTrue(importMessage.startsWith("Warning: "));
        assertTrue(restoreMessage.startsWith("Warning: "));
        assertTrue(importMessage.contains("publication and import archive cleanup are pending"));
        assertTrue(restoreMessage.contains("publication cleanup is pending"));
    }
}
