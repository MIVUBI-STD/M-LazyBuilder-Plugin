package com.halokaryamedia.lazybuilder.builder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuilderRuntimeProofEvidenceCleanTest {
    @TempDir Path tempDir;

    @Test
    void failedHistoricalSnapshotDoesNotPoisonLaterCleanEvidence() throws Exception {
        Files.writeString(tempDir.resolve("failed.json"), """
                {
                  "schema": 7,
                  "buildFingerprint": "test-build",
                  "operationsCompleted": 5,
                  "operationsFailed": 2,
                  "maxCompletedPlannedBlocks": 5000000,
                  "budgetExceeded": 1
                }
                """);
        Files.writeString(tempDir.resolve("clean.json"), """
                {
                  "schema": 7,
                  "buildFingerprint": "test-build",
                  "operationsCompleted": 3,
                  "operationsCancelled": 1,
                  "operationsFailed": 0,
                  "maxCompletedPlannedBlocks": 1000000,
                  "rollbackBlocksDispatched": 1000,
                  "forwardBiomeExtensions": 10,
                  "forwardEntityExtensions": 5,
                  "budgetExceeded": 0,
                  "extensionFailures": 0,
                  "historyReplayFailures": 0
                }
                """);

        BuilderRuntimeProofEvidence e =
                new BuilderRuntimeProofStore(tempDir, "test-build").aggregateEvidence();

        assertEquals(2, e.snapshotCount());
        assertEquals(1, e.cleanSnapshotCount());
        assertEquals(1, e.rejectedSnapshotCount());
        assertEquals(1_000_000, e.maxCompletedPlannedBlocks());
        assertEquals(3, e.maxCompletedOperations());
    }
}
