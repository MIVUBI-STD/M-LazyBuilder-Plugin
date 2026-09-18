package com.halokaryamedia.lazybuilder.builder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuilderRuntimeProofEvidenceTest {
    @TempDir Path tempDir;

    @Test
    void aggregatesMaximaWithoutDoubleCountingRepeatedSnapshots() throws Exception {
        Files.writeString(tempDir.resolve("one.json"), """
                {
                  "schema": 6,
                  "operationsCompleted": 2,
                  "operationsCancelled": 1,
                  "operationsFailed": 0,
                  "maxCompletedPlannedBlocks": 1000000,
                  "maxCompletedPlannedExtensions": 10,
                  "rollbackBlocksDispatched": 200,
                  "rollbackBiomeExtensions": 2,
                  "rollbackEntityExtensions": 3,
                  "forwardBiomeExtensions": 12,
                  "forwardEntityExtensions": 9,
                  "budgetExceeded": 0,
                  "extensionFailures": 0,
                  "historyReplayFailures": 0
                }
                """);
        Files.writeString(tempDir.resolve("two.json"), """
                {
                  "schema": 6,
                  "operationsCompleted": 2,
                  "operationsCancelled": 1,
                  "operationsFailed": 0,
                  "maxCompletedPlannedBlocks": 1000000,
                  "rollbackBlocksDispatched": 200,
                  "forwardBiomeExtensions": 12,
                  "forwardEntityExtensions": 9
                }
                """);

        BuilderRuntimeProofStore store = new BuilderRuntimeProofStore(tempDir);
        BuilderRuntimeProofEvidence evidence = store.aggregateEvidence();

        assertEquals(2, evidence.snapshotCount());
        assertEquals(2, evidence.maxCompletedOperations());
        assertEquals(1_000_000, evidence.maxCompletedPlannedBlocks());
        assertEquals(200, evidence.maxRollbackBlocks());
        assertEquals(12, evidence.maxForwardBiomeExtensions());
        assertEquals(9, evidence.maxForwardEntityExtensions());
    }
}
