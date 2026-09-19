package com.halokaryamedia.lazybuilder.world.registry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                WorldLifecycle.ACTIVE
        );

        persistence.save(List.of(world));

        assertTrue(Files.isRegularFile(path));
        assertFalse(Files.readString(path).contains("auto-load"));
        assertEquals(List.of(world), persistence.load());
    }

    @Test
    void legacyAutoLoadKeyIsIgnoredDuringMigration() throws Exception {
        Path path = tempDir.resolve("registry.yml");
        WorldId id = WorldId.create();
        Files.writeString(path, """
                worlds:
                  %s:
                    folder: BuildWorld
                    display-name: Build World
                    kind: FLAT
                    lifecycle: ACTIVE
                    auto-load: true
                    default-game-mode: CREATIVE
                """.formatted(id));

        YamlWorldRegistryPersistence persistence = new YamlWorldRegistryPersistence(path);
        WorldRecord loaded = persistence.load().getFirst();
        assertEquals(id, loaded.id());
        assertEquals(WorldLifecycle.ACTIVE, loaded.lifecycle());
    }

    @Test
    void missingMainRegistryRecoversPreviousCommittedCopyFirst() throws Exception {
        Path path = tempDir.resolve("registry-previous.yml");
        Path previous = path.resolveSibling(path.getFileName() + ".previous");
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        WorldId id = WorldId.create();
        Files.writeString(previous, """
                worlds:
                  %s:
                    folder: BuildWorld
                    display-name: Build World
                    kind: FLAT
                    lifecycle: ACTIVE
                    default-game-mode: CREATIVE
                """.formatted(id));
        Files.writeString(temporary, "worlds: {}\n");

        YamlWorldRegistryPersistence persistence = new YamlWorldRegistryPersistence(path);
        List<WorldRecord> loaded = persistence.load();

        assertEquals(1, loaded.size());
        assertEquals(id, loaded.getFirst().id());
        assertFalse(Files.exists(previous));
        assertFalse(Files.exists(temporary));
    }

    @Test
    void firstPublishStagingRecoversWhenMainRegistryIsMissing() throws Exception {
        Path path = tempDir.resolve("registry-staging.yml");
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        WorldId id = WorldId.create();
        Files.writeString(temporary, """
                worlds:
                  %s:
                    folder: BuildWorld
                    display-name: Build World
                    kind: FLAT
                    lifecycle: ACTIVE
                    default-game-mode: SURVIVAL
                """.formatted(id));

        YamlWorldRegistryPersistence persistence = new YamlWorldRegistryPersistence(path);
        assertEquals(id, persistence.load().getFirst().id());
        assertTrue(Files.isRegularFile(path));
        assertFalse(Files.exists(temporary));
    }

    @Test
    void malformedMainRegistryPreservesRecoveryEvidence() throws Exception {
        Path path = tempDir.resolve("registry-malformed.yml");
        Path previous = path.resolveSibling(path.getFileName() + ".previous");
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(path, "worlds: [broken");
        Files.writeString(previous, "worlds: {}\n");
        Files.writeString(temporary, "worlds: {}\n");

        YamlWorldRegistryPersistence persistence = new YamlWorldRegistryPersistence(path);
        assertThrows(Exception.class, persistence::load);

        assertTrue(Files.exists(previous));
        assertTrue(Files.exists(temporary));
    }

    @Test
    void invalidRegistryFailsClosed() throws Exception {
        Path path = tempDir.resolve("registry.yml");
        Files.writeString(path, "worlds:\n  broken:\n    folder: ../unsafe\n");
        YamlWorldRegistryPersistence persistence = new YamlWorldRegistryPersistence(path);

        assertThrows(Exception.class, persistence::load);
    }
}
