package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.history.CompressedMemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.HistorySizingPolicy;
import com.halokaryamedia.lazybuilder.builder.history.HistoryStorageRouter;
import com.halokaryamedia.lazybuilder.builder.history.MemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructureMutationPreparerTest {
    @TempDir Path tempDir;
    @Test
    void commitsBlocksAndExtensionsBeforeMutation() throws Exception {
        StructureSnapshot snapshot = new StructureSnapshot(
                List.of(new StructureBlock(0, 0, 0, "minecraft:chest")),
                List.of(new StructureBlockEntity(0, 0, 0, new byte[]{2}))
        );
        StructurePastePlan plan = StructurePastePlanner.planWithBlockEntities(
                snapshot,
                new StructurePlacement(0, 64, 0, 0, false, false),
                BlockStateTransform.identity(),
                (x, y, z) -> "minecraft:air",
                (x, y, z) -> new byte[]{1},
                BlockEntityPayloadTransform.identity()
        );

        HistoryStorageRouter history = new HistoryStorageRouter(
                new HistorySizingPolicy(1024, 2048),
                new MemoryChangeSetStorage(),
                new CompressedMemoryChangeSetStorage(),
                new com.halokaryamedia.lazybuilder.builder.history.DiskChangeSetStorage(tempDir)
        );

        var prepared = StructureMutationPreparer.prepare(
                "structure-test",
                plan,
                history,
                512,
                new CancellationSource().token()
        ).orElseThrow();

        assertEquals(1, prepared.plannedChanges());
        assertEquals(1, prepared.plannedExtensions());
        assertEquals(1, prepared.changeSet().extensionCount());
        prepared.close();
    }
}
