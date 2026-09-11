package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldRuntimeServiceTest {
    @Test
    void loadAndUnloadHaveExplicitTransientStatesAndIdempotentEndpoints() {
        WorldRegistry registry = new WorldRegistry();
        WorldRuntimeStateRegistry states = new WorldRuntimeStateRegistry();
        FakeRuntime runtime = new FakeRuntime();
        WorldRecord world = world(WorldLifecycle.ACTIVE);
        registry.register(world);
        states.initialize(world.id(), WorldRuntimeState.UNLOADED);
        WorldRuntimeService service = new WorldRuntimeService(registry, states, runtime);

        service.load(world.id());
        assertEquals(WorldRuntimeState.LOADED, service.state(world.id()));
        assertEquals(1, runtime.loadCount);

        service.load(world.id());
        assertEquals(1, runtime.loadCount);

        service.unload(world.id());
        assertEquals(WorldRuntimeState.UNLOADED, service.state(world.id()));
        assertEquals(1, runtime.unloadCount);

        service.unload(world.id());
        assertEquals(1, runtime.unloadCount);
    }

    @Test
    void runtimeFailuresReturnStateToPreviousStableValue() {
        WorldRegistry registry = new WorldRegistry();
        WorldRuntimeStateRegistry states = new WorldRuntimeStateRegistry();
        FakeRuntime runtime = new FakeRuntime();
        WorldRecord world = world(WorldLifecycle.ACTIVE);
        registry.register(world);
        states.initialize(world.id(), WorldRuntimeState.UNLOADED);
        WorldRuntimeService service = new WorldRuntimeService(registry, states, runtime);

        runtime.failLoad = true;
        assertThrows(IllegalStateException.class, () -> service.load(world.id()));
        assertEquals(WorldRuntimeState.UNLOADED, service.state(world.id()));

        runtime.failLoad = false;
        service.load(world.id());
        runtime.failUnload = true;
        assertThrows(IllegalStateException.class, () -> service.unload(world.id()));
        assertEquals(WorldRuntimeState.LOADED, service.state(world.id()));
    }

    @Test
    void archivedWorldCannotLoad() {
        WorldRegistry registry = new WorldRegistry();
        WorldRuntimeStateRegistry states = new WorldRuntimeStateRegistry();
        FakeRuntime runtime = new FakeRuntime();
        WorldRecord world = world(WorldLifecycle.ARCHIVED);
        registry.register(world);
        states.initialize(world.id(), WorldRuntimeState.UNLOADED);
        WorldRuntimeService service = new WorldRuntimeService(registry, states, runtime);

        assertThrows(IllegalStateException.class, () -> service.load(world.id()));
        assertEquals(0, runtime.loadCount);
    }

    private static WorldRecord world(WorldLifecycle lifecycle) {
        return new WorldRecord(
                WorldId.create(),
                "Build",
                "Build",
                WorldKind.FLAT,
                lifecycle,
                true
        );
    }

    private static final class FakeRuntime implements WorldRuntimeGateway {
        private int loadCount;
        private int unloadCount;
        private boolean failLoad;
        private boolean failUnload;

        @Override
        public void createNewWorld(WorldRecord world, BuildReadyPolicy policy) {
        }

        @Override
        public void rollbackCreatedWorld(WorldRecord world) {
        }

        @Override
        public boolean isLoaded(WorldRecord world) {
            return false;
        }

        @Override
        public void loadWorld(WorldRecord world) {
            loadCount++;
            if (failLoad) {
                throw new IllegalStateException("load failed");
            }
        }

        @Override
        public void unloadWorld(WorldRecord world) {
            unloadCount++;
            if (failUnload) {
                throw new IllegalStateException("unload failed");
            }
        }
    }
}
