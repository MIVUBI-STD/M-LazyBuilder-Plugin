package com.halokaryamedia.lazybuilder.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldManagerDiscoveryContractTest {
    @TempDir Path tempDir;

    @Test
    void directContainedDirectoryIsDiscoverable() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("worlds"));
        Path world = Files.createDirectory(root.resolve("Build"));

        assertTrue(WorldManager.isDirectContainedWorldDirectory(root.toRealPath(), world));
    }

    @Test
    void ordinaryFileIsNotDiscoverableAsWorldDirectory() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("worlds-file"));
        Path file = root.resolve("Build");
        Files.writeString(file, "not-a-world");

        assertFalse(WorldManager.isDirectContainedWorldDirectory(root.toRealPath(), file));
    }
}
