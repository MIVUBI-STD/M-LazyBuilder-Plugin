package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldLocationTeleportServiceTest {
    @Test
    void mapTeleportLoadsWorldAndDelegatesServerSideSafeResolution() {
        WorldRegistry registry = new WorldRegistry();
        WorldRuntimeStateRegistry states = new WorldRuntimeStateRegistry();
        FakeRuntime runtime = new FakeRuntime();
        WorldRecord world = new WorldRecord(
                WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE, false
        );
        registry.register(world);
        states.initialize(world.id(), WorldRuntimeState.UNLOADED);

        WorldRuntimeService runtimeService = new WorldRuntimeService(registry, states, runtime);
        FakeLocationGateway locations = new FakeLocationGateway();
        WorldLocationTeleportService service = new WorldLocationTeleportService(registry, runtimeService, locations);
        UUID player = UUID.randomUUID();

        WorldLocationTeleportService.TeleportResult result = service.teleport(player, world.id(), -17, 35);

        assertEquals(1, runtime.loadCount);
        assertEquals(WorldRuntimeState.LOADED, runtimeService.state(world.id()));
        assertEquals(player, locations.playerId);
        assertEquals(world, locations.world);
        assertEquals(-17, locations.x);
        assertEquals(35, locations.z);
        assertEquals(WorldGameMode.CREATIVE, locations.gameMode);
        assertEquals(80.0D, result.resolved().y());
    }

    private static final class FakeLocationGateway implements WorldLocationGateway {
        private UUID playerId;
        private WorldRecord world;
        private int x;
        private int z;
        private WorldGameMode gameMode;

        @Override
        public ResolvedLocation teleportToSafeSurface(UUID playerId, WorldRecord world, int blockX, int blockZ,
                                                      WorldGameMode gameMode) {
            this.playerId = playerId;
            this.world = world;
            this.x = blockX;
            this.z = blockZ;
            this.gameMode = gameMode;
            return new ResolvedLocation(blockX + 0.5D, 80.0D, blockZ + 0.5D);
        }
    }

    private static final class FakeRuntime implements WorldRuntimeGateway {
        private int loadCount;
        @Override public void createNewWorld(WorldRecord world, BuildReadyPolicy policy) {}
        @Override public void rollbackCreatedWorld(WorldRecord world) {}
        @Override public boolean isLoaded(WorldRecord world) { return false; }
        @Override public void loadWorld(WorldRecord world) { loadCount++; }
        @Override public void unloadWorld(WorldRecord world) {}
        @Override public void teleportPlayerToSpawn(UUID playerId, WorldRecord world) {}
    }
}
