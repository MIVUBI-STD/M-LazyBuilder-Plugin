package com.halokaryamedia.lazybuilder.world.registry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlWorldRegistryPersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void missingRegistryLoadsAsEmpty() throws Exception {
        YamlWorldRegistryPersistence persistence = new YamlWorldRegistryPersistence(tempDir.resolve("registry.yml"));
        assertTrue(persistence.load().isEmpty());
    }

    @Test
    void savesAndLoadsStableWorldMetadata() throws Exception {
        Path path = tempDir.resolve("nested/world/registry.yml");
        YamlWorldRegistryPersistence persistence = new YamlWorldRegistryPersistence(path);
        WorldRecord world = new WorldRecord(
                WorldId.create(),
                "BuildWorld",
                "Build World",
                WorldKind.FLAT,
                WorldLifecycle.ACTIVE,
                true
        );

        persistence.save(List.of(world));

        assertTrue(Files.isRegularFile(path));
        assertEquals(List.of(world), persistence.load());
    }

    @Test
    void invalidRegistryFailsClosed() throws Exception {
        Path path = tempDir.resolve("registry.yml");
        Files.writeString(path, "worlds:\n  broken:\n    folder: ../unsafe\n");
        YamlWorldRegistryPersistence persistence = new YamlWorldRegistryPersistence(path);

        assertThrows(Exception.class, persistence::load);
    }
}
