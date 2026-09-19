package com.halokaryamedia.lazybuilder.world.registry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldRegistryTest {
    @Test
    void registersAndFindsByStableIdentityAndFolder() {
        WorldRegistry registry = new WorldRegistry();
        WorldRecord world = world("Build One", "Build One");

        registry.register(world);

        assertEquals(world, registry.find(world.id()).orElseThrow());
        assertEquals(world, registry.findByFolderName("build one").orElseThrow());
        assertEquals(1, registry.size());
    }

    @Test
    void rejectsDuplicateFolderNamesCaseInsensitively() {
        WorldRegistry registry = new WorldRegistry();
        registry.register(world("Arena", "Arena"));

        assertThrows(IllegalArgumentException.class,
                () -> registry.register(world("arena", "Arena Copy")));
    }

    @Test
    void destinationReservationBlocksConcurrentPublicationAndReleasesCleanly() {
        WorldRegistry registry = new WorldRegistry();

        WorldRegistry.FolderReservation reservation = registry.reserveFolder("ImportedBuild");
        assertThrows(IllegalStateException.class,
                () -> registry.reserveFolder("importedbuild"));

        reservation.close();
        try (WorldRegistry.FolderReservation ignored = registry.reserveFolder("IMPORTEDBUILD")) {
            registry.register(world("ImportedBuild", "Imported Build"));
        }

        assertThrows(IllegalArgumentException.class,
                () -> registry.reserveFolder("ImportedBuild"));
    }

    @Test
    void rejectsUnsafeFolderNames() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldRecord(WorldId.create(), "../world", "World", WorldKind.IMPORTED,
                        WorldLifecycle.ACTIVE));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldRecord(WorldId.create(), "folder/world", "World", WorldKind.IMPORTED,
                        WorldLifecycle.ACTIVE));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldRecord(WorldId.create(), " world ", "World", WorldKind.IMPORTED,
                        WorldLifecycle.ACTIVE));
    }

    @Test
    void rejectsFolderNamesThatAreNotPortableToWindows() {
        for (String folder : List.of("CON", "nul.txt", "Build.", "Build:One", "Build?", "Build*")) {
            assertThrows(IllegalArgumentException.class,
                    () -> new WorldRecord(WorldId.create(), folder, "World", WorldKind.IMPORTED,
                            WorldLifecycle.ACTIVE), folder);
        }
        world("Build One", "Build One");
        world("Build.One", "Build One");
    }

    @Test
    void rejectsUnboundedOrControlCharacterNames() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldRecord(
                        WorldId.create(),
                        "x".repeat(256),
                        "World",
                        WorldKind.IMPORTED,
                        WorldLifecycle.ACTIVE));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldRecord(
                        WorldId.create(),
                        "Build",
                        "World\nName",
                        WorldKind.IMPORTED,
                        WorldLifecycle.ACTIVE));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldRecord(
                        WorldId.create(),
                        "Build",
                        "x".repeat(257),
                        WorldKind.IMPORTED,
                        WorldLifecycle.ACTIVE));
    }

    @Test
    void managedWorldCapacityMatchesClientControlSurface() {
        assertEquals(4_096, WorldRegistry.MAX_MANAGED_WORLDS);
        assertEquals(
                WorldRegistry.MAX_MANAGED_WORLDS,
                com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol.MAX_WORLDS
        );
    }

    @Test
    void metadataUpdateCannotRenameFilesystemIdentity() {
        WorldRegistry registry = new WorldRegistry();
        WorldRecord world = world("Build", "Build");
        registry.register(world);

        WorldRecord updated = world.withDisplayName("New Name")
                .withLifecycle(WorldLifecycle.ARCHIVED)
                .withDefaultGameMode("ADVENTURE");
        registry.updateMetadata(updated);

        WorldRecord stored = registry.find(world.id()).orElseThrow();
        assertEquals("New Name", stored.displayName());
        assertEquals(WorldLifecycle.ARCHIVED, stored.lifecycle());
        assertEquals("ADVENTURE", stored.defaultGameMode());

        WorldRecord renamedFolder = new WorldRecord(world.id(), "Build_Renamed", "New Name", world.kind(),
                world.lifecycle(), world.defaultGameMode());
        assertThrows(IllegalArgumentException.class, () -> registry.updateMetadata(renamedFolder));
    }

    @Test
    void snapshotsDoNotExposeMutableRegistryState() {
        WorldRegistry registry = new WorldRegistry();
        WorldRecord first = world("First", "First");
        registry.register(first);

        List<WorldRecord> snapshot = registry.all();
        registry.register(world("Second", "Second"));

        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(first));
    }

    @Test
    void removalReturnsPreviousRecord() {
        WorldRegistry registry = new WorldRegistry();
        WorldRecord world = world("Build", "Build");
        registry.register(world);

        assertEquals(world, registry.remove(world.id()).orElseThrow());
        assertFalse(registry.find(world.id()).isPresent());
        assertTrue(registry.all().isEmpty());
    }

    @Test
    void worldIdRoundTrips() {
        WorldId id = WorldId.create();
        assertEquals(id, WorldId.parse(id.toString()));
    }

    private static WorldRecord world(String folderName, String displayName) {
        return new WorldRecord(
                WorldId.create(),
                folderName,
                displayName,
                WorldKind.FLAT,
                WorldLifecycle.ACTIVE
        );
    }
}
