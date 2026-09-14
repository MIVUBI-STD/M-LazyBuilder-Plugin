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

class LocalWorldFileRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void duplicateProfileRemovesIdentityAndPlayerLocalData() throws Exception {
        Path worldRoot = tempDir.resolve("worlds");
        Path workRoot = tempDir.resolve("work");
        Path source = worldRoot.resolve("Build");
        Files.createDirectories(source.resolve("region"));
        Files.createDirectories(source.resolve("playerdata"));
        Files.writeString(source.resolve("level.dat"), "level");
        Files.writeString(source.resolve("uid.dat"), "uid");
        Files.writeString(source.resolve("session.lock"), "lock");
        Files.writeString(source.resolve("region/r.0.0.mca"), "region");
        Files.writeString(source.resolve("playerdata/player.dat"), "player");

        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);
        Path staged = repository.stageCopy(world(), UUID.randomUUID(), WorldCopyProfile.DUPLICATE);

        assertTrue(Files.exists(staged.resolve("level.dat")));
        assertTrue(Files.exists(staged.resolve("region/r.0.0.mca")));
        assertFalse(Files.exists(staged.resolve("uid.dat")));
        assertFalse(Files.exists(staged.resolve("session.lock")));
        assertFalse(Files.exists(staged.resolve("playerdata")));
    }

    @Test
    void snapshotKeepsWorldIdentityButNeverSessionLock() throws Exception {
        Path worldRoot = tempDir.resolve("worlds");
        Path workRoot = tempDir.resolve("work");
        Path source = worldRoot.resolve("Build");
        Files.createDirectories(source);
        Files.writeString(source.resolve("uid.dat"), "uid");
        Files.writeString(source.resolve("session.lock"), "lock");

        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);
        Path staged = repository.stageCopy(world(), UUID.randomUUID(), WorldCopyProfile.SNAPSHOT);

        assertTrue(Files.exists(staged.resolve("uid.dat")));
        assertFalse(Files.exists(staged.resolve("session.lock")));
    }

    @Test
    void publishAndDeleteOperateOnlyOnOwnedDirectChildren() throws Exception {
        Path worldRoot = tempDir.resolve("worlds");
        Path workRoot = tempDir.resolve("work");
        Path source = worldRoot.resolve("Build");
        Files.createDirectories(source);
        Files.writeString(source.resolve("level.dat"), "level");

        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);
        Path staged = repository.stageCopy(world(), UUID.randomUUID(), WorldCopyProfile.DUPLICATE);
        repository.publishStagedWorld(staged, "BuildCopy");
        assertTrue(Files.exists(worldRoot.resolve("BuildCopy/level.dat")));

        WorldRecord copy = new WorldRecord(WorldId.create(), "BuildCopy", "Build Copy",
                WorldKind.FLAT, WorldLifecycle.ACTIVE);
        repository.deleteWorld(copy);
        assertFalse(Files.exists(worldRoot.resolve("BuildCopy")));

        assertThrows(IllegalArgumentException.class,
                () -> repository.publishStagedWorld(tempDir.resolve("outside"), "Unsafe"));
    }

    private static WorldRecord world() {
        return new WorldRecord(WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE);
    }
}
