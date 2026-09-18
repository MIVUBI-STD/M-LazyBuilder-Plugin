package com.halokaryamedia.lazybuilder.world.files;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaSnapshotCopyTest {
    @TempDir Path tempDir;

    @Test
    void overworldAreaCopiesOnlyIntersectingMcaRegionsAndWorldMetadata() throws Exception {
        Path worldRoot = tempDir.resolve("worlds");
        Path workRoot = tempDir.resolve("work");
        Path world = worldRoot.resolve("Build");
        Files.createDirectories(world.resolve("region"));
        Files.createDirectories(world.resolve("entities"));
        Files.createDirectories(world.resolve("poi"));
        Files.createDirectories(world.resolve("DIM-1/region"));
        Files.createDirectories(world.resolve("DIM1/region"));
        Files.createDirectories(world.resolve("dimensions/example/moon/region"));
        Files.writeString(world.resolve("level.dat"), "level");
        Files.writeString(world.resolve("region/r.0.0.mca"), "selected");
        Files.writeString(world.resolve("region/r.1.0.mca"), "outside");
        Files.writeString(world.resolve("region/r.-1.-1.mca"), "outside-negative");
        Files.writeString(world.resolve("entities/r.0.0.mca"), "entities");
        Files.writeString(world.resolve("poi/r.0.0.mca"), "poi");
        Files.writeString(world.resolve("DIM-1/region/r.0.0.mca"), "nether");
        Files.writeString(world.resolve("DIM1/region/r.0.0.mca"), "end");
        Files.writeString(world.resolve("dimensions/example/moon/region/r.0.0.mca"), "custom");

        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);
        Path staged = repository.stageAreaCopy(
                world(), UUID.randomUUID(), new AreaCopySelection("minecraft:overworld", 0, 0, 31, 31));

        assertTrue(Files.exists(staged.resolve("level.dat")));
        assertTrue(Files.exists(staged.resolve("region/r.0.0.mca")));
        assertTrue(Files.exists(staged.resolve("entities/r.0.0.mca")));
        assertTrue(Files.exists(staged.resolve("poi/r.0.0.mca")));
        assertFalse(Files.exists(staged.resolve("region/r.1.0.mca")));
        assertFalse(Files.exists(staged.resolve("region/r.-1.-1.mca")));
        assertFalse(Files.exists(staged.resolve("DIM-1")));
        assertFalse(Files.exists(staged.resolve("DIM1")));
        assertFalse(Files.exists(staged.resolve("dimensions")));
    }

    @Test
    void paperSiblingNetherAreaNormalizesOnlySelectedNegativeRegion() throws Exception {
        Path worldRoot = tempDir.resolve("worlds");
        Path workRoot = tempDir.resolve("work");
        Path overworld = worldRoot.resolve("Build");
        Path nether = worldRoot.resolve("Build_nether/DIM-1");
        Path end = worldRoot.resolve("Build_the_end/DIM1/region");
        Files.createDirectories(overworld.resolve("region"));
        Files.createDirectories(nether.resolve("region"));
        Files.createDirectories(nether.resolve("entities"));
        Files.createDirectories(nether.resolve("poi"));
        Files.createDirectories(end);
        Files.writeString(overworld.resolve("level.dat"), "level");
        Files.writeString(overworld.resolve("region/r.-1.-1.mca"), "overworld");
        Files.writeString(nether.resolve("region/r.-1.-1.mca"), "selected");
        Files.writeString(nether.resolve("region/r.0.0.mca"), "outside");
        Files.writeString(nether.resolve("entities/r.-1.-1.mca"), "entities");
        Files.writeString(nether.resolve("poi/r.-1.-1.mca"), "poi");
        Files.writeString(end.resolve("r.-1.-1.mca"), "end");

        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);
        Path staged = repository.stageAreaCopy(
                world(), UUID.randomUUID(), new AreaCopySelection("minecraft:the_nether", -32, -32, -1, -1));

        assertTrue(Files.exists(staged.resolve("level.dat")));
        assertTrue(Files.exists(staged.resolve("DIM-1/region/r.-1.-1.mca")));
        assertTrue(Files.exists(staged.resolve("DIM-1/entities/r.-1.-1.mca")));
        assertTrue(Files.exists(staged.resolve("DIM-1/poi/r.-1.-1.mca")));
        assertFalse(Files.exists(staged.resolve("DIM-1/region/r.0.0.mca")));
        assertFalse(Files.exists(staged.resolve("region")));
        assertFalse(Files.exists(staged.resolve("DIM1")));
    }

    @Test
    void cancellationDuringCopyStopsTraversalAndRemovesPartialWorkspace() throws Exception {
        Path worldRoot = tempDir.resolve("worlds");
        Path workRoot = tempDir.resolve("work");
        Path world = worldRoot.resolve("Build");
        Files.createDirectories(world.resolve("region"));
        Files.writeString(world.resolve("level.dat"), "level");
        Files.writeString(world.resolve("region/r.0.0.mca"), "first");
        Files.writeString(world.resolve("region/r.1.0.mca"), "second");

        UUID operationId = UUID.randomUUID();
        Path partialWorkspace = workRoot.resolve(operationId + ".copy");
        AreaCopySelection selection = new AreaCopySelection(
                "minecraft:overworld", 0, 0, 63, 31,
                () -> Files.exists(partialWorkspace.resolve("level.dat")));
        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);

        assertThrows(IOException.class, () -> repository.stageAreaCopy(world(), operationId, selection));
        assertFalse(Files.exists(partialWorkspace), "cancelled area snapshot must remove partial workspace");
    }

    private static WorldRecord world() {
        return new WorldRecord(WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE);
    }
}
