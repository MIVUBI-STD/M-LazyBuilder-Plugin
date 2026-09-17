package com.halokaryamedia.lazybuilder.builder.operation;

import com.halokaryamedia.lazybuilder.builder.history.HistoryRequirement;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import com.halokaryamedia.lazybuilder.builder.region.BoxRegion;
import com.halokaryamedia.lazybuilder.builder.region.BuilderRegion;
import com.halokaryamedia.lazybuilder.builder.region.DeterministicRegionPlanner;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.*;

class OperationPlanningTest {
    @Test
    void createsDeterministicPlanWithoutMutatingTheOperation() {
        CancellationSource cancellation = new CancellationSource();
        BuilderOperation operation = operation(
                new BoxRegion(new BlockBounds(-1, 0, -1, 15, 0, 15)),
                cancellation,
                2
        );
        OperationPlanner planner = new DefaultOperationPlanner(new DeterministicRegionPlanner());

        OperationPlan first = planner.plan(operation);
        OperationPlan second = planner.plan(operation);

        assertEquals(first.workUnits(), second.workUnits());
        assertEquals(4, first.workUnits().size());
        assertEquals(operation.region().bounds().blockCount(), first.candidateBlockCount());
    }

    @Test
    void refusesToPlanAlreadyCancelledOperation() {
        CancellationSource cancellation = new CancellationSource();
        cancellation.requestCancellation();
        BuilderOperation operation = operation(
                new BoxRegion(new BlockBounds(0, 0, 0, 0, 0, 0)),
                cancellation,
                1
        );

        assertThrows(CancellationException.class,
                () -> new DefaultOperationPlanner(new DeterministicRegionPlanner()).plan(operation));
    }

    @Test
    void workQueueUsesStableBatchesAndStopsDispatchAfterCancellation() {
        CancellationSource cancellation = new CancellationSource();
        BuilderOperation operation = operation(
                new BoxRegion(new BlockBounds(0, 0, 0, 47, 0, 15)),
                cancellation,
                2
        );
        OperationPlan plan = new DefaultOperationPlanner(new DeterministicRegionPlanner()).plan(operation);
        DeterministicWorkQueue queue = new DeterministicWorkQueue(plan);

        List<?> firstBatch = queue.pollBatch();
        assertEquals(2, firstBatch.size());
        assertEquals(1, queue.remainingWorkUnits());

        cancellation.requestCancellation();
        assertTrue(queue.pollBatch().isEmpty());
        assertFalse(queue.isExhausted());
        assertEquals(1, queue.remainingWorkUnits());
    }

    private static BuilderOperation operation(
            BuilderRegion region,
            CancellationSource cancellation,
            int maxChunksInFlight
    ) {
        return new BuilderOperation() {
            private final UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
            private final OperationSeed seed = new OperationSeed(123L);
            private final ExecutionBudget budget = new ExecutionBudget(
                    Duration.ofMillis(4), maxChunksInFlight, 1_000_000L, 64L * 1024L * 1024L);

            @Override
            public UUID id() {
                return id;
            }

            @Override
            public String type() {
                return "test";
            }

            @Override
            public BuilderRegion region() {
                return region;
            }

            @Override
            public OperationSeed seed() {
                return seed;
            }

            @Override
            public ExecutionBudget executionBudget() {
                return budget;
            }

            @Override
            public CancellationToken cancellationToken() {
                return cancellation.token();
            }

            @Override
            public MutationReadMode readMode() {
                return MutationReadMode.SNAPSHOT_READ;
            }

            @Override
            public HistoryRequirement historyRequirement() {
                return HistoryRequirement.REQUIRED;
            }

            @Override
            public CancellationDisposition cancellationDisposition() {
                return CancellationDisposition.ROLLBACK;
            }
        };
    }
}
