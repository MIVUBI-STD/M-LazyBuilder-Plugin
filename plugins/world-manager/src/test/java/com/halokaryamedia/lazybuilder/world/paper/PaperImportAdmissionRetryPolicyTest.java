package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.world.task.WorldTaskRegistry;
import com.halokaryamedia.lazybuilder.world.task.WorldTaskSnapshot;
import com.halokaryamedia.lazybuilder.world.task.WorldTaskType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperImportAdmissionRetryPolicyTest {
    @Test
    void onlyTerminalFailureAllowsRetry() {
        WorldTaskRegistry registry = new WorldTaskRegistry();
        WorldTaskSnapshot queued = registry.create(WorldTaskType.IMPORT, null, "Queued");
        assertFalse(PaperLocalControlServer.mayRetryFailedImport(queued));
        WorldTaskSnapshot running = registry.markRunning(queued.taskId(), "Running");
        assertFalse(PaperLocalControlServer.mayRetryFailedImport(running));
        WorldTaskSnapshot failed = registry.fail(queued.taskId(), "Failed", "Failed");
        assertTrue(PaperLocalControlServer.mayRetryFailedImport(failed));
    }

    @Test
    void succeededImportCannotBeRetried() {
        WorldTaskRegistry registry = new WorldTaskRegistry();
        WorldTaskSnapshot queued = registry.create(WorldTaskType.IMPORT, null, "Queued");
        registry.markRunning(queued.taskId(), "Running");
        WorldTaskSnapshot successful = registry.succeed(queued.taskId(), "world-id", "Imported");
        assertFalse(PaperLocalControlServer.mayRetryFailedImport(successful));
    }
}
