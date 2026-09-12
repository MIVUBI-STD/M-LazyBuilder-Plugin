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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldCloneServiceTest {
    @Test
    void cloneGetsFreshIdentityAutoLoadOffAndRestoresLoadedSource() {
        Fixture fixture = fixture(true);

        WorldCloneService.CloneTask task = fixture.service.prepare(fixture.source.id(), "BuildCopy", "Build Copy");
        assertEquals(WorldRuntimeState.UNLOADED, fixture.states.get(fixture.source.id()));

        WorldRecord clone = fixture.service.executeFilePhase(task);
        fixture.service.finish(task);

        assertNotEquals(fixture.source.id(), clone.id());
        assertFalse(clone.autoLoad());
        assertEquals(fixture.source.kind(), clone.kind());
        assertEquals(fixture.source.defaultGameMode(), clone.defaultGameMode());
        assertEquals(WorldRuntimeState.UNLOADED, fixture.states.get(clone.id()));
        assertEquals(WorldRuntimeState.LOADED, fixture.states.get(fixture.source.id()));
        assertEquals(WorldCopyProfile.CLONE, fixture.files.lastProfile);
        assertTrue(fixture.files.published);
        assertFalse(fixture.operations.isBusy(fixture.source.id()));
    }

    private static Fixture fixture(boolean loaded) {
        WorldRegistry registry = new WorldRegistry();
        WorldRecord source = new WorldRecord(
                WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE, true, "ADVENTURE");
        registry.register(source);
        WorldRuntimeStateRegistry states = new WorldRuntimeStateRegistry();
        states.initialize(source.id(), loaded ? WorldRuntimeState.LOADED : WorldRuntimeState.UNLOADED);
        FakeRuntime runtime = new FakeRuntime(loaded);
        WorldRuntimeService runtimeService = new WorldRuntimeService(registry, states, runtime);
        MemoryPersistence persistence = new MemoryPersistence();
        persistence.saved = registry.all();
        WorldOperationCoordinator operations = new WorldOperationCoordinator();
        FakeFiles files = new FakeFiles();
        WorldCloneService service = new WorldCloneService(
                registry, persistence, runtimeService, states, operations, files);
        return new Fixture(registry, states, runtime, persistence, operations, files, service, source);
    }

    private record Fixture(
            WorldRegistry registry,
            WorldRuntimeStateRegistry states,
            FakeRuntime runtime,
            MemoryPersistence persistence,
            WorldOperationCoordinator operations,
            FakeFiles files,
            WorldCloneService service,
            WorldRecord source
    ) {
    }

    private static final class MemoryPersistence implements WorldRegistryPersistence {
        private List<WorldRecord> saved = List.of();

        @Override
        public List<WorldRecord> load() {
            return saved;
        }

        @Override
        public void save(List<WorldRecord> worlds) {
            saved = List.copyOf(worlds);
        }
    }

    private static final class FakeRuntime implements WorldRuntimeGateway {
        private boolean loaded;

        private FakeRuntime(boolean loaded) {
            this.loaded = loaded;
        }

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

        @Override
        public Path stageCopy(WorldRecord source, UUID operationId, WorldCopyProfile profile) {
            lastProfile = profile;
            return Path.of("work", operationId.toString()).toAbsolutePath();
        }

        @Override
        public Path stageDelete(WorldRecord world, UUID operationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void publishStagedWorld(Path stagedWorld, String destinationFolder) {
            published = true;
        }

        @Override public void deleteWorld(WorldRecord world) { }
        @Override public void deleteWorkspace(Path workspace) { }
    }
}
