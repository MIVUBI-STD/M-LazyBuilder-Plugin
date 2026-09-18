package com.halokaryamedia.lazybuilder.builder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BuilderRuntimeProofBackpressureTest {
    @TempDir Path tempDir;

    @Test
    void persistsDispatchYieldCountersInRuntimeProof() throws Exception {
        BuilderRuntimeMetrics metrics = new BuilderRuntimeMetrics();
        metrics.forwardDispatchYielded();
        metrics.forwardDispatchYielded();
        metrics.rollbackDispatchYielded();

        BuilderRuntimeProofStore store =
                new BuilderRuntimeProofStore(tempDir, "test-build");
        Path snapshot = store.writeSnapshot(metrics.snapshot(), "backpressure");

        String json = Files.readString(snapshot);
        assertTrue(json.contains("\"forwardDispatchYields\": 2"));
        assertTrue(json.contains("\"rollbackDispatchYields\": 1"));
    }
}
