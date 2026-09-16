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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSnapshotDimensionLayoutTest {
    @TempDir Path tempDir;

    @Test
    void snapshotNormalizesPaperSiblingDimensionsIntoJavaWorldLayout() throws Exception {
        Path worldRoot = tempDir.resolve("worlds");
        Path workRoot = tempDir.resolve("work");
        Path overworld = worldRoot.resolve("Build");
        Path nether = worldRoot.resolve("Build_nether/DIM-1/region");
        Path end = worldRoot.resolve("Build_the_end/DIM1/region");
        Files.createDirectories(overworld.resolve("region"));
        Files.createDirectories(nether);
        Files.createDirectories(end);
        Files.writeString(overworld.resolve("level.dat"), "level");
        Files.writeString(nether.resolve("r.0.0.mca"), "nether");
        Files.writeString(end.resolve("r.0.0.mca"), "end");

        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);
        Path staged = repository.stageCopy(world(), UUID.randomUUID(), WorldCopyProfile.SNAPSHOT);

        assertTrue(Files.exists(staged.resolve("level.dat")));
        assertEquals("nether", Files.readString(staged.resolve("DIM-1/region/r.0.0.mca")));
        assertEquals("end", Files.readString(staged.resolve("DIM1/region/r.0.0.mca")));
    }

    @Test
    void canonicalDimensionInsideManagedRootWinsOverPaperSibling() throws Exception {
        Path worldRoot = tempDir.resolve("worlds");
        Path workRoot = tempDir.resolve("work");
        Path canonicalNether = worldRoot.resolve("Build/DIM-1/region");
        Path paperNether = worldRoot.resolve("Build_nether/DIM-1/region");
        Files.createDirectories(canonicalNether);
        Files.createDirectories(paperNether);
        Files.writeString(worldRoot.resolve("Build/level.dat"), "level");
        Files.writeString(canonicalNether.resolve("r.0.0.mca"), "canonical");
        Files.writeString(paperNether.resolve("r.0.0.mca"), "sibling");

        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);
        Path staged = repository.stageCopy(world(), UUID.randomUUID(), WorldCopyProfile.SNAPSHOT);

        assertEquals("canonical", Files.readString(staged.resolve("DIM-1/region/r.0.0.mca")));
    }

    private static WorldRecord world() {
        return new WorldRecord(WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE);
    }
}
