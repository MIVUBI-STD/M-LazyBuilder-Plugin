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

class WholeSnapshotCancellationTest {
    @TempDir Path tempDir;

    @Test
    void cancellationDuringWholeSnapshotStopsTraversalAndRemovesPartialWorkspace() throws Exception {
        Path worldRoot = tempDir.resolve("worlds");
        Path workRoot = tempDir.resolve("work");
        Path source = worldRoot.resolve("Build");
        Files.createDirectories(source);
        Files.writeString(source.resolve("first.dat"), "first");
        Files.writeString(source.resolve("second.dat"), "second");

        UUID operationId = UUID.randomUUID();
        Path partialWorkspace = workRoot.resolve(operationId + ".copy");
        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);
        WorldRecord world = new WorldRecord(
                WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE);

        assertThrows(IOException.class, () -> repository.stageCopy(
                world,
                operationId,
                WorldCopyProfile.SNAPSHOT,
                () -> Files.exists(partialWorkspace.resolve("first.dat"))
                        || Files.exists(partialWorkspace.resolve("second.dat"))));

        assertFalse(Files.exists(partialWorkspace));
    }
}
