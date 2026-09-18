package com.halokaryamedia.lazybuilder.builder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuilderRuntimeProofSchemaTest {
    @TempDir Path tempDir;

    @Test
    void ignoresProofSnapshotsFromUnknownSchemas() throws Exception {
        Files.writeString(
                tempDir.resolve("old.json"),
                "{\"schema\":6,\"operationsCompleted\":99,"
                        + "\"maxCompletedPlannedBlocks\":9999999}");
        BuilderRuntimeProofStore store = new BuilderRuntimeProofStore(tempDir, "test-build");
        BuilderRuntimeProofEvidence evidence = store.aggregateEvidence();

        assertEquals(0, evidence.snapshotCount());
        assertEquals(0, evidence.maxCompletedOperations());
        assertEquals(0, evidence.maxCompletedPlannedBlocks());
    }
}
