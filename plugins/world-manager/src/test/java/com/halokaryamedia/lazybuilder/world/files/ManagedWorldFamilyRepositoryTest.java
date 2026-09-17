package com.halokaryamedia.lazybuilder.world.files;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedWorldFamilyRepositoryTest {
    @TempDir Path tempDir;

    @Test
    void transactionalPublishSplitsCanonicalDimensionsAndCommitClearsFamilyMarkers() throws Exception {
        Path worlds = Files.createDirectories(tempDir.resolve("worlds"));
        Path work = tempDir.resolve("work");
        LocalWorldFileRepository repository = new LocalWorldFileRepository(worlds, work);
        Path staged = repository.reserveWorkspace(UUID.randomUUID());
        Files.createDirectories(staged.resolve("DIM-1/region"));
        Files.createDirectories(staged.resolve("DIM1/region"));
        Files.write(staged.resolve("level.dat"), new byte[]{1, 2, 3});
        Files.write(staged.resolve("DIM-1/region/r.0.0.mca"), new byte[]{4});
        Files.write(staged.resolve("DIM1/region/r.0.0.mca"), new byte[]{5});

        repository.publishStagedWorld(staged, "Imported");

        assertTrue(Files.isRegularFile(worlds.resolve("Imported/level.dat")));
        assertFalse(Files.exists(worlds.resolve("Imported/DIM-1")));
        assertFalse(Files.exists(worlds.resolve("Imported/DIM1")));
        assertTrue(Files.isRegularFile(worlds.resolve("Imported_nether/DIM-1/region/r.0.0.mca")));
        assertTrue(Files.isRegularFile(worlds.resolve("Imported_the_end/DIM1/region/r.0.0.mca")));
        assertTrue(Files.isRegularFile(worlds.resolve("Imported/.lazybuilder-publish-pending")));
        assertTrue(Files.isRegularFile(worlds.resolve("Imported_nether/.lazybuilder-family-publish-pending")));
        assertTrue(Files.isRegularFile(worlds.resolve("Imported_the_end/.lazybuilder-family-publish-pending")));

        repository.markPublishedWorldCommitted("Imported");
        assertFalse(Files.exists(worlds.resolve("Imported/.lazybuilder-publish-pending")));
        assertFalse(Files.exists(worlds.resolve("Imported_nether/.lazybuilder-family-publish-pending")));
        assertFalse(Files.exists(worlds.resolve("Imported_the_end/.lazybuilder-family-publish-pending")));
    }

    @Test
    void familyCollisionFailsBeforeMovingStagedSource() throws Exception {
        Path worlds = tempDir.resolve("collision-worlds");
        Path work = tempDir.resolve("collision-work");
        LocalWorldFileRepository repository = new LocalWorldFileRepository(worlds, work);
        Path staged = repository.reserveWorkspace(UUID.randomUUID());
        Files.createDirectories(staged.resolve("DIM-1/region"));
        Files.write(staged.resolve("level.dat"), new byte[]{1});
        Files.createDirectories(worlds.resolve("Imported_nether/DIM-1"));
        Files.write(worlds.resolve("Imported_nether/level.dat"), new byte[]{9});

        assertThrows(java.io.IOException.class, () -> repository.publishStagedWorld(staged, "Imported"));

        assertTrue(Files.isDirectory(staged));
        assertTrue(Files.isRegularFile(staged.resolve("level.dat")));
        assertFalse(Files.exists(worlds.resolve("Imported")));
        assertTrue(Files.isDirectory(worlds.resolve("Imported_nether")));
    }

    @Test
    void duplicateCopyIncludesPaperSiblingDimensionDataInCanonicalStage() throws Exception {
        Path worlds = tempDir.resolve("worlds");
        Path work = tempDir.resolve("work");
        Files.createDirectories(worlds.resolve("Build"));
        Files.createDirectories(worlds.resolve("Build_nether/DIM-1/region"));
        Files.createDirectories(worlds.resolve("Build_the_end/DIM1/region"));
        Files.write(worlds.resolve("Build/level.dat"), new byte[]{1});
        Files.write(worlds.resolve("Build_nether/DIM-1/region/r.0.0.mca"), new byte[]{2});
        Files.write(worlds.resolve("Build_the_end/DIM1/region/r.0.0.mca"), new byte[]{3});

        LocalWorldFileRepository repository = new LocalWorldFileRepository(worlds, work);
        Path staged = repository.stageCopy(world(), UUID.randomUUID(), WorldCopyProfile.DUPLICATE);

        assertTrue(Files.isRegularFile(staged.resolve("DIM-1/region/r.0.0.mca")));
        assertTrue(Files.isRegularFile(staged.resolve("DIM1/region/r.0.0.mca")));
    }

    @Test
    void deleteStagingConsolidatesFamilyAndRollbackPublishRestoresPaperLayout() throws Exception {
        Path worlds = tempDir.resolve("worlds");
        Path work = tempDir.resolve("work");
        Files.createDirectories(worlds.resolve("Build"));
        Files.createDirectories(worlds.resolve("Build_nether/DIM-1/region"));
        Files.createDirectories(worlds.resolve("Build_the_end/DIM1/region"));
        Files.write(worlds.resolve("Build/level.dat"), new byte[]{1});
        Files.write(worlds.resolve("Build_nether/level.dat"), new byte[]{1});
        Files.write(worlds.resolve("Build_the_end/level.dat"), new byte[]{1});
        Files.write(worlds.resolve("Build_nether/DIM-1/region/r.0.0.mca"), new byte[]{2});
        Files.write(worlds.resolve("Build_the_end/DIM1/region/r.0.0.mca"), new byte[]{3});

        LocalWorldFileRepository repository = new LocalWorldFileRepository(worlds, work);
        Path staged = repository.stageDelete(world(), UUID.randomUUID());

        assertFalse(Files.exists(worlds.resolve("Build")));
        assertFalse(Files.exists(worlds.resolve("Build_nether")));
        assertFalse(Files.exists(worlds.resolve("Build_the_end")));
        assertTrue(Files.isRegularFile(staged.resolve("DIM-1/region/r.0.0.mca")));
        assertTrue(Files.isRegularFile(staged.resolve("DIM1/region/r.0.0.mca")));

        repository.publishStagedWorld(staged, "Build");
        assertTrue(Files.isRegularFile(worlds.resolve("Build/level.dat")));
        assertTrue(Files.isRegularFile(worlds.resolve("Build_nether/DIM-1/region/r.0.0.mca")));
        assertTrue(Files.isRegularFile(worlds.resolve("Build_the_end/DIM1/region/r.0.0.mca")));
    }

    private static WorldRecord world() {
        return new WorldRecord(WorldId.create(), "Build", "Build", WorldKind.IMPORTED, WorldLifecycle.ACTIVE);
    }
}
