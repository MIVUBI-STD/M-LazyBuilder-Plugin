package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.MemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.HistorySizingPolicy;
import com.halokaryamedia.lazybuilder.builder.history.HistoryStorageRouter;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPlanEntry;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementTransform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructureOperationPreparerTest {
    @TempDir Path tempDir;
    @Test
    void compilesIntoDurableGenericPreparedMutation() throws Exception {
        StructureTemplate template = StructureTemplate.of(
                "test:two",
                new StructureBlock(0, 0, 0, "minecraft:stone"),
                new StructureBlock(1, 0, 0, "minecraft:dirt")
        );
        HistoryStorageRouter history = new HistoryStorageRouter(
                new HistorySizingPolicy(1024 * 1024, 2 * 1024 * 1024),
                new MemoryChangeSetStorage(),
                new com.halokaryamedia.lazybuilder.builder.history.DiskChangeSetStorage(tempDir)
        );

        try (PreparedStructureMutation prepared = StructureOperationPreparer.compileAndPrepare(
                UUID.randomUUID(),
                List.of(new PlacementPlanEntry(
                        new PlacementPoint(0, 64, 0, 0),
                        "test:two",
                        new PlacementTransform(0, 1, false))),
                id -> template,
                (x, y, z) -> "minecraft:air",
                StructureOverlapPolicy.ERROR,
                history,
                1024
        )) {
            assertEquals(2, prepared.plannedChanges());
            assertEquals(2, prepared.changeSet().changeCount());
            assertEquals(0, prepared.extensionChanges());
            assertEquals(true, prepared.blockOnly());
        }
    }
}
