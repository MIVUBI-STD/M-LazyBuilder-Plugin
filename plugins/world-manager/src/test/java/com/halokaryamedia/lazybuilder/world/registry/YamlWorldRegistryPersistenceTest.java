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
    void emptyCurrentRegistryCarriesSchemaAndRoundTripsAsEmpty() throws Exception {
        Path path = tempDir.resolve("registry-empty-current.yml");
        YamlWorldRegistryPersistence persistence = new YamlWorldRegistryPersistence(path);

        persistence.save(List.of());

        String raw = Files.readString(path);
        assertTrue(raw.contains("schema-version: 1"));
        assertTrue(persistence.load().isEmpty());
    }

    @Test
    void nonEmptyRegistryWithoutWorldsSectionFailsClosed() throws Exception {
        Path path = tempDir.resolve("registry-missing-worlds.yml");
        Files.writeString(path, "other: value\n");

        YamlWorldRegistryPersistence persistence = new YamlWorldRegistryPersistence(path);
        IOException error = assertThrows(IOException.class, persistence::load);
        assertTrue(error.getMessage().contains("missing the required worlds section"));
    }

    @Test
    void malformedRegistrySchemaTypeDoesNotFallBackToLegacy() throws Exception {
        Path path = tempDir.resolve("registry-schema-string.yml");
        Files.writeString(path, "schema-version: one\nworlds: {}\n");

        YamlWorldRegistryPersistence persistence = new YamlWorldRegistryPersistence(path);
        IOException error = assertThrows(IOException.class, persistence::load);
        assertTrue(error.getMessage().contains("must be an integer"));
    }

    @Test
    void explicitLegacySchemaNumberIsNotAcceptedAsMissingSchema() throws Exception {
        Path path = tempDir.resolve("registry-schema-zero.yml");
        Files.writeString(path, "schema-version: 0\nworlds: {}\n");

        YamlWorldRegistryPersistence persistence = new YamlWorldRegistryPersistence(path);
        IOException error = assertThrows(IOException.class, persistence::load);
        assertTrue(error.getMessage().contains("unsupported"));
    }

    @Test
    void unsupportedRegistrySchemaFailsClosed() throws Exception {
        Path path = tempDir.resolve("registry-newer-schema.yml");
        Files.writeString(path, "schema-version: 2\nworlds: {}\n");

        YamlWorldRegistryPersistence persistence = new YamlWorldRegistryPersistence(path);
        IOException error = assertThrows(IOException.class, persistence::load);
        assertTrue(error.getMessage().contains("unsupported"));
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
    void committedRegistryRemainsAuthoritativeWhenPreviousEvidenceExists() throws Exception {
        Path path = tempDir.resolve("registry-committed.yml");
        YamlWorldRegistryPersistence persistence = new YamlWorldRegistryPersistence(path);
        WorldRecord first = new WorldRecord(
                WorldId.create(), "First", "First", WorldKind.FLAT, WorldLifecycle.ACTIVE);
        WorldRecord second = new WorldRecord(
                WorldId.create(), "Second", "Second", WorldKind.FLAT, WorldLifecycle.ACTIVE);

        persistence.save(List.of(first));
        persistence.save(List.of(first, second));

        Path previous = path.resolveSibling(path.getFileName() + ".previous");
        Files.writeString(previous, "worlds: {}\n");

        List<WorldRecord> loaded = persistence.load();
        assertEquals(List.of(first, second), loaded);
        assertFalse(Files.exists(previous),
                "valid committed main registry should win before stale cleanup evidence");
    }

    @Test
    void stalePreviousCleanupDebtDoesNotBlockNextValidSave() throws Exception {
        Path path = tempDir.resolve("registry-next-save.yml");
        YamlWorldRegistryPersistence persistence = new YamlWorldRegistryPersistence(path);
        WorldRecord first = new WorldRecord(
                WorldId.create(), "First", "First", WorldKind.FLAT, WorldLifecycle.ACTIVE);
        WorldRecord second = new WorldRecord(
                WorldId.create(), "Second", "Second", WorldKind.FLAT, WorldLifecycle.ACTIVE);

        persistence.save(List.of(first));
        Path previous = path.resolveSibling(path.getFileName() + ".previous");
        Files.writeString(previous, "worlds: {}\n");

        persistence.save(List.of(first, second));

        assertEquals(List.of(first, second), persistence.load());
        assertFalse(Files.exists(previous));
    }

    @Test
    void invalidRegistryFailsClosed() throws Exception {
        Path path = tempDir.resolve("registry.yml");
        Files.writeString(path, "worlds:\n  broken:\n    folder: ../unsafe\n");
        YamlWorldRegistryPersistence persistence = new YamlWorldRegistryPersistence(path);

        assertThrows(Exception.class, persistence::load);
    }
}
