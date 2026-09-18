package com.halokaryamedia.lazybuilder.builder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuilderRuntimeProofFingerprintTest {
    @TempDir Path tempDir;

    @Test
    void ignoresEvidenceFromAnotherBuilderArtifact() throws Exception {
        Files.writeString(tempDir.resolve("other-build.json"), """
                {
                  "schema": 7,
                  "buildFingerprint": "other-build",
                  "operationsCompleted": 10,
                  "operationsFailed": 0,
                  "maxCompletedPlannedBlocks": 5000000,
                  "budgetExceeded": 0,
                  "extensionFailures": 0,
                  "historyReplayFailures": 0
                }
                """);

        BuilderRuntimeProofEvidence evidence =
                new BuilderRuntimeProofStore(tempDir, "current-build")
                        .aggregateEvidence();

        assertEquals(0, evidence.snapshotCount());
        assertEquals(0, evidence.maxCompletedOperations());
        assertEquals(0, evidence.maxCompletedPlannedBlocks());
    }
}
