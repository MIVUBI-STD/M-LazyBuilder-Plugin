package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldOperationCoordinatorTest {
    @Test
    void oneWorldAllowsOnlyOneActiveOperation() {
        WorldOperationCoordinator coordinator = new WorldOperationCoordinator();
        WorldId worldId = WorldId.create();

        try (WorldOperationCoordinator.Lease ignored = coordinator.acquire(worldId, WorldOperationType.EXPORT)) {
            assertTrue(coordinator.isBusy(worldId));
            assertEquals(WorldOperationType.EXPORT, coordinator.activeOperation(worldId));
            assertThrows(IllegalStateException.class,
                    () -> coordinator.acquire(worldId, WorldOperationType.DELETE));
        }

        assertFalse(coordinator.isBusy(worldId));
    }

    @Test
    void differentWorldsCanOperateIndependently() {
        WorldOperationCoordinator coordinator = new WorldOperationCoordinator();
        WorldId first = WorldId.create();
        WorldId second = WorldId.create();

        try (WorldOperationCoordinator.Lease ignoredFirst = coordinator.acquire(first, WorldOperationType.DUPLICATE);
             WorldOperationCoordinator.Lease ignoredSecond = coordinator.acquire(second, WorldOperationType.EXPORT)) {
            assertTrue(coordinator.isBusy(first));
            assertTrue(coordinator.isBusy(second));
        }
    }

    @Test
    void leaseCloseIsIdempotent() {
        WorldOperationCoordinator coordinator = new WorldOperationCoordinator();
        WorldId worldId = WorldId.create();
        WorldOperationCoordinator.Lease lease = coordinator.acquire(worldId, WorldOperationType.ARCHIVE);

        lease.close();
        lease.close();

        assertFalse(coordinator.isBusy(worldId));
    }
}
