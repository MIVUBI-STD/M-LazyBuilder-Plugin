package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldCreationServiceTest {
    @Test
    void flatAndVoidUseOneCreationPathAndPublishRegistryState() {
        WorldRegistry registry = new WorldRegistry();
        WorldRuntimeStateRegistry states = new WorldRuntimeStateRegistry();
        MemoryPersistence persistence = new MemoryPersistence();
        FakeRuntime runtime = new FakeRuntime();
        BuildReadyPolicy policy = BuildReadyPolicy.defaults();
        WorldCreationService service = new WorldCreationService(registry, persistence, runtime, states, policy);

        WorldRecord flat = service.create("FlatBuild", "Flat Build", WorldKind.FLAT);
        WorldRecord empty = service.create("VoidBuild", "Void Build", WorldKind.VOID);

        assertEquals(List.of(flat, empty), registry.all());
        assertEquals(List.of(flat, empty), persistence.saved);
        assertEquals(List.of(WorldKind.FLAT, WorldKind.VOID), runtime.createdKinds);
        assertEquals(WorldRuntimeState.LOADED, states.get(flat.id()));
        assertEquals(WorldRuntimeState.LOADED, states.get(empty.id()));
        assertTrue(flat.autoLoad());
        assertTrue(empty.autoLoad());
        assertEquals(2, runtime.policies.size());
        assertEquals(policy, runtime.policies.getFirst());
        assertEquals(policy, runtime.policies.getLast());
    }

    @Test
    void createRejectsImportedKind() {
        WorldCreationService service = new WorldCreationService(
                new WorldRegistry(),
                new MemoryPersistence(),
                new FakeRuntime(),
                new WorldRuntimeStateRegistry(),
                BuildReadyPolicy.defaults()
        );

        assertThrows(IllegalArgumentException.class,
                () -> service.create("Imported", "Imported", WorldKind.IMPORTED));
    }

    @Test
    void persistenceFailureRollsBackRuntimeRegistryAndState() {
        WorldRegistry registry = new WorldRegistry();
        WorldRuntimeStateRegistry states = new WorldRuntimeStateRegistry();
        MemoryPersistence persistence = new MemoryPersistence();
        persistence.failSave = true;
        FakeRuntime runtime = new FakeRuntime();
        WorldCreationService service = new WorldCreationService(
                registry,
                persistence,
                runtime,
                states,
                BuildReadyPolicy.defaults()
        );

        assertThrows(IllegalStateException.class,
                () -> service.create("Build", "Build", WorldKind.FLAT));

        assertTrue(registry.all().isEmpty());
        assertEquals(1, runtime.rollbackCount);
        assertFalse(runtime.createdKinds.isEmpty());
    }

    private static final class FakeRuntime implements WorldRuntimeGateway {
        private final List<WorldKind> createdKinds = new ArrayList<>();
        private final List<BuildReadyPolicy> policies = new ArrayList<>();
        private int rollbackCount;

        @Override
        public void createNewWorld(WorldRecord world, BuildReadyPolicy policy) {
            createdKinds.add(world.kind());
            policies.add(policy);
        }

        @Override
        public void rollbackCreatedWorld(WorldRecord world) {
            rollbackCount++;
        }

        @Override
        public boolean isLoaded(WorldRecord world) {
            return createdKinds.contains(world.kind());
        }

        @Override
        public void loadWorld(WorldRecord world) {
        }

        @Override
        public void unloadWorld(WorldRecord world) {
        }

        @Override
        public void teleportPlayerToSpawn(UUID playerId, WorldRecord world) {
        }
    }

    private static final class MemoryPersistence implements WorldRegistryPersistence {
        private List<WorldRecord> saved = List.of();
        private boolean failSave;

        @Override
        public List<WorldRecord> load() {
            return saved;
        }

        @Override
        public void save(List<WorldRecord> worlds) throws IOException {
            if (failSave) {
                throw new IOException("test failure");
            }
            saved = List.copyOf(worlds);
        }
    }
}
