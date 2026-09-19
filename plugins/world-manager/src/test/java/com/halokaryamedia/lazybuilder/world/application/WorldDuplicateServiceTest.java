package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.files.WorldCopyProfile;
import com.halokaryamedia.lazybuilder.world.files.WorldFileRepository;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldDuplicateServiceTest {
    @Test
    void persistenceFailureRollsBackPublishedDuplicateAndRegistryOwnership() {
        Fixture fixture = fixture(false);
        fixture.persistence.failNextSave = true;

        WorldDuplicateService.DuplicateTask task = fixture.service.prepare(
                fixture.source.id(), "BuildCopy", "Build Copy");

        assertThrows(IllegalStateException.class, () -> fixture.service.executeFilePhase(task));
        fixture.service.finish(task);

        assertFalse(task.committed());
        assertFalse(fixture.files.published, "published duplicate must be retired after failed registry commit");
        assertEquals(1, fixture.registry.all().size(), "source registry ownership must remain unchanged");
        assertFalse(fixture.registry.findByFolderName("BuildCopy").isPresent());
        assertFalse(fixture.operations.isBusy(fixture.source.id()));
    }

    @Test
    void duplicateGetsFreshIdentityAndRestoresLoadedSource() {
        Fixture fixture = fixture(true);

        WorldDuplicateService.DuplicateTask task = fixture.service.prepare(
                fixture.source.id(), "BuildCopy", "Build Copy");
        assertFalse(fixture.runtime.loaded);

        WorldRecord duplicate = fixture.service.executeFilePhase(task);
        fixture.service.finish(task);

        assertNotEquals(fixture.source.id(), duplicate.id());
        assertEquals(WorldLifecycle.ACTIVE, duplicate.lifecycle());
        assertEquals(fixture.source.kind(), duplicate.kind());
        assertEquals(fixture.source.defaultGameMode(), duplicate.defaultGameMode());
        assertTrue(fixture.runtime.loaded);
        assertEquals(WorldCopyProfile.DUPLICATE, fixture.files.lastProfile);
        assertTrue(fixture.files.published);
        assertFalse(fixture.operations.isBusy(fixture.source.id()));
    }

    private static Fixture fixture(boolean loaded) {
        WorldRegistry registry = new WorldRegistry();
        WorldRecord source = new WorldRecord(
                WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE, "ADVENTURE");
        registry.register(source);
        FakeRuntime runtime = new FakeRuntime(loaded);
        WorldOperationCoordinator operations = new WorldOperationCoordinator();
        WorldRuntimeService runtimeService = new WorldRuntimeService(registry, runtime, operations);
        MemoryPersistence persistence = new MemoryPersistence();
        persistence.saved = registry.all();
        FakeFiles files = new FakeFiles();
        WorldDuplicateService service = new WorldDuplicateService(
                registry, persistence, runtimeService, operations, files);
        return new Fixture(registry, persistence, runtime, operations, files, service, source);
    }

    private record Fixture(
            WorldRegistry registry,
            MemoryPersistence persistence,
            FakeRuntime runtime,
            WorldOperationCoordinator operations,
            FakeFiles files,
            WorldDuplicateService service,
            WorldRecord source
    ) {}

    private static final class MemoryPersistence implements WorldRegistryPersistence {
        private List<WorldRecord> saved = List.of();
        private boolean failNextSave;
        @Override public List<WorldRecord> load() { return saved; }
        @Override public void save(List<WorldRecord> worlds) {
            if (failNextSave) {
                failNextSave = false;
                throw new IllegalStateException("test persistence failure");
            }
            saved = List.copyOf(worlds);
        }
    }

    private static final class FakeRuntime implements WorldRuntimeGateway {
        private boolean loaded;
        private FakeRuntime(boolean loaded) { this.loaded = loaded; }
        @Override public void createNewWorld(WorldRecord world, BuildReadyPolicy policy) { }
        @Override public void rollbackCreatedWorld(WorldRecord world) { }
        @Override public boolean isLoaded(WorldRecord world) { return loaded; }
        @Override public void loadWorld(WorldRecord world) { loaded = true; }
        @Override public void unloadWorld(WorldRecord world) { loaded = false; }
        @Override public void teleportPlayerToSpawn(UUID playerId, WorldRecord world) { }
    }

    private static final class FakeFiles implements WorldFileRepository {
        private WorldCopyProfile lastProfile;
        private boolean published;
        @Override public Path stageCopy(WorldRecord source, UUID operationId, WorldCopyProfile profile) {
            lastProfile = profile;
            return Path.of("work", operationId.toString()).toAbsolutePath();
        }
        @Override public Path stageDelete(WorldRecord world, UUID operationId) { throw new UnsupportedOperationException(); }
        @Override public void publishStagedWorld(Path stagedWorld, String destinationFolder) { published = true; }
        @Override public void deleteWorld(WorldRecord world) { published = false; }
        @Override public void deleteWorkspace(Path workspace) { }
    }
}
