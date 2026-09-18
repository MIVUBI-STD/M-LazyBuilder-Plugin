package com.halokaryamedia.lazybuilder.builder;

import com.halokaryamedia.lazybuilder.builder.history.CompressedMemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.DiskChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.HistorySizingPolicy;
import com.halokaryamedia.lazybuilder.builder.history.HistoryStorageRouter;
import com.halokaryamedia.lazybuilder.builder.history.HistoryTimeline;
import com.halokaryamedia.lazybuilder.builder.history.MemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.operation.ExecutionBudget;
import com.halokaryamedia.lazybuilder.builder.operation.OperationState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class BuilderRetirementReadinessTest {
    @TempDir Path tempDir;

    @Test
    void blockEntityAuthorityAndRuntimeEvidenceRemainExplicitGates() throws Exception {
        BuilderRuntime runtime = runtime();
        try {
            var report = BuilderRetirementReadiness.evaluate(
                    runtime, true, false, true, 0, 0);
            assertEquals(BuilderRetirementReadiness.Status.BLOCKED, report.status());
            assertTrue(report.blockers().stream()
                    .anyMatch(value -> value.contains("BLOCK_ENTITY")));
            assertTrue(report.blockers().stream()
                    .anyMatch(value -> value.contains("runtime proof")));
        } finally {
            runtime.close();
        }
    }

    @Test
    void activeMutationBlocksRetirementReadiness() throws Exception {
        BuilderRuntime runtime = runtime();
        try {
            runtime.registerActiveOperation(() -> { });
            var report = BuilderRetirementReadiness.evaluate(
                    runtime, true, true, true, 0, 0);
            assertEquals(BuilderRetirementReadiness.Status.BLOCKED, report.status());
            assertTrue(report.blockers().stream()
                    .anyMatch(value -> value.contains("active Builder operations=1")));
        } finally {
            runtime.preserveActiveOperations();
            runtime.close();
        }
    }

    @Test
    void legacyUnscopedRecoveryResidueBlocksRetirement() throws Exception {
        BuilderRuntime runtime = runtime();
        try {
            var report = BuilderRetirementReadiness.evaluate(
                    runtime, true, true, true, 0, 0, 2);
            assertEquals(BuilderRetirementReadiness.Status.BLOCKED, report.status());
            assertTrue(report.blockers().stream()
                    .anyMatch(value -> value.contains(
                            "legacy unscoped recovery journals=2")));
        } finally {
            runtime.close();
        }
    }

    private BuilderRuntime runtime() throws Exception {
        Constructor<BuilderRuntime> constructor = BuilderRuntime.class.getDeclaredConstructor(
                HistoryTimeline.class,
                HistoryStorageRouter.class,
                ExecutionBudget.class,
                Path.class,
                DiskChangeSetStorage.class,
                BuilderRuntimeMetrics.class,
                BuilderRuntimeProofStore.class
        );
        constructor.setAccessible(true);
        DiskChangeSetStorage disk = new DiskChangeSetStorage(tempDir.resolve("history"));
        HistoryStorageRouter history = new HistoryStorageRouter(
                new HistorySizingPolicy(1024, 2048),
                new MemoryChangeSetStorage(),
                new CompressedMemoryChangeSetStorage(),
                disk
        );
        return constructor.newInstance(
                new HistoryTimeline(4),
                history,
                new ExecutionBudget(Duration.ofMillis(4), 1, 1024, 1024 * 1024),
                tempDir.resolve("schematics"),
                disk,
                new BuilderRuntimeMetrics(),
                new BuilderRuntimeProofStore(tempDir.resolve("proofs"))
        );
    }
}
