package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.export.ExportSettingsWire;
import com.halokaryamedia.lazybuilder.world.map.MapActionWireProtocol;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientMapControllerTest {
    @Test
    void failedCurrentWorldRefreshRollsBackPendingState() {
        List<String> notifications = new ArrayList<>();
        ClientMapController controller = new ClientMapController(
                ignored -> { },
                ignored -> { throw new IllegalStateException("network unavailable"); },
                notifications::add
        );
        long revisionBefore = controller.revision();

        assertThrows(IllegalStateException.class, controller::refreshCurrentWorld);

        assertEquals(0L, controller.activeCurrentWorldRequestId());
        assertEquals("Could not refresh current world", controller.lastError());
        assertTrue(controller.revision() > revisionBefore);
        assertTrue(notifications.isEmpty());
    }

    @Test
    void teleportErrorDoesNotResolveActiveExport() throws Exception {
        Harness harness = new Harness();
        WorldId worldId = WorldId.create();
        harness.controller.accept(new MapActionWireProtocol.CurrentWorldResult(
                0L, worldId, "Build", "Build"));

        harness.controller.teleportCurrent(8, 12);
        long teleportRequestId = harness.controller.activeTeleportRequestId();
        assertTrue(teleportRequestId > 0L);

        harness.controller.exportArea(
                worldId, "minecraft:overworld", 0, 0, 15, 15,
                "JAVA_1_21_4", "area", ExportSettingsWire.Settings.inherit());
        long exportRequestId = harness.controller.activeExportRequestId();
        assertTrue(exportRequestId > 0L);
        assertTrue(harness.controller.exportBusy());

        harness.controller.accept(new MapActionWireProtocol.ErrorResponse(
                teleportRequestId, "teleport denied"));

        assertFalse(harness.controller.teleportPending());
        assertTrue(harness.controller.exportBusy());
        assertEquals(exportRequestId, harness.controller.activeExportRequestId());
        assertEquals("teleport denied", harness.controller.lastError());
    }

    @Test
    void negativeTeleportPermissionFailureRoundTripsThroughWire() throws Exception {
        Harness harness = new Harness();
        WorldId worldId = WorldId.create();
        harness.controller.accept(new MapActionWireProtocol.CurrentWorldResult(
                0L, worldId, "Build", "Build"));

        harness.controller.teleportCurrent(-128, -64);

        MapActionWireProtocol.TeleportLocation request =
                (MapActionWireProtocol.TeleportLocation) MapActionWireProtocol.decodeRequest(
                        harness.sent.get(harness.sent.size() - 1));
        assertEquals(-128, request.blockX());
        assertEquals(-64, request.blockZ());

        byte[] deniedPayload = MapActionWireProtocol.error(
                request.requestId(), "Missing permission: lazybuilder.world.teleport");
        harness.controller.accept(MapActionWireProtocol.decodeResponse(deniedPayload));

        assertFalse(harness.controller.teleportPending());
        assertEquals("Missing permission: lazybuilder.world.teleport", harness.controller.lastError());
        assertEquals(
                List.of("LazyBuilder: Missing permission: lazybuilder.world.teleport"),
                harness.notifications);
    }

    @Test
    void negativeAreaExportPermissionFailureRoundTripsThroughWire() throws Exception {
        Harness harness = new Harness();
        WorldId worldId = WorldId.create();

        harness.controller.exportArea(
                worldId,
                "minecraft:overworld",
                -96, -80, -1, -17,
                "JAVA_1_21_4",
                "negative-area",
                ExportSettingsWire.Settings.inherit());

        MapActionWireProtocol.ExportArea request =
                (MapActionWireProtocol.ExportArea) MapActionWireProtocol.decodeRequest(
                        harness.sent.get(harness.sent.size() - 1));
        assertEquals(-96, request.x1());
        assertEquals(-80, request.z1());
        assertEquals(-1, request.x2());
        assertEquals(-17, request.z2());

        byte[] deniedPayload = MapActionWireProtocol.error(
                request.requestId(), "Missing permission: lazybuilder.world.manage");
        harness.controller.accept(MapActionWireProtocol.decodeResponse(deniedPayload));

        assertFalse(harness.controller.exportBusy());
        assertEquals("Missing permission: lazybuilder.world.manage", harness.controller.lastError());
        assertEquals(
                List.of("LazyBuilder: Missing permission: lazybuilder.world.manage"),
                harness.notifications);
    }

    @Test
    void staleErrorDoesNotNotifyOrMutateActiveRequest() {
        Harness harness = new Harness();
        WorldId worldId = WorldId.create();
        harness.controller.exportArea(
                worldId, "minecraft:overworld", 0, 0, 15, 15,
                "JAVA_1_21_4", "current", ExportSettingsWire.Settings.inherit());
        long activeRequestId = harness.controller.activeExportRequestId();

        harness.controller.accept(new MapActionWireProtocol.ErrorResponse(
                activeRequestId + 1L, "stale export failed"));

        assertTrue(harness.controller.exportBusy());
        assertEquals(activeRequestId, harness.controller.activeExportRequestId());
        assertNull(harness.controller.lastError());
        assertTrue(harness.notifications.isEmpty());
    }

    @Test
    void unboundServerErrorStillNotifies() {
        Harness harness = new Harness();

        harness.controller.accept(new MapActionWireProtocol.ErrorResponse(0L, "server warning"));

        assertEquals(List.of("LazyBuilder: server warning"), harness.notifications);
        assertNull(harness.controller.lastError());
    }

    @Test
    void currentWorldRequestErrorUpdatesObservableState() {
        Harness harness = new Harness();
        harness.controller.refreshCurrentWorld();
        long requestId = harness.controller.activeCurrentWorldRequestId();
        long revisionBefore = harness.controller.revision();

        harness.controller.accept(new MapActionWireProtocol.ErrorResponse(requestId, "current world unavailable"));

        assertEquals(0L, harness.controller.activeCurrentWorldRequestId());
        assertEquals("current world unavailable", harness.controller.lastError());
        assertTrue(harness.controller.revision() > revisionBefore);
        assertEquals(List.of("LazyBuilder: current world unavailable"), harness.notifications);
    }

    @Test
    void mismatchedCompletionCannotCompleteNewerActiveExport() {
        Harness harness = new Harness();
        WorldId worldId = WorldId.create();
        harness.controller.exportArea(
                worldId, "minecraft:overworld", 0, 0, 15, 15,
                "JAVA_1_21_4", "current", ExportSettingsWire.Settings.inherit());
        long activeRequestId = harness.controller.activeExportRequestId();

        harness.controller.accept(new MapActionWireProtocol.ExportComplete(
                activeRequestId + 1L, worldId, "stale.zip", "JAVA_1_21_4"));

        assertTrue(harness.controller.exportBusy());
        assertEquals(activeRequestId, harness.controller.activeExportRequestId());
        assertTrue(harness.completed.isEmpty());

        harness.controller.accept(new MapActionWireProtocol.ExportComplete(
                activeRequestId, worldId, "current.zip", "JAVA_1_21_4"));

        assertFalse(harness.controller.exportBusy());
        assertEquals(0L, harness.controller.activeExportRequestId());
        assertEquals(List.of("current.zip"), harness.completed);
        assertNull(harness.controller.lastError());
    }

    @Test
    void disconnectResetStillAllowsPendingExportCompletionAfterReconnect() {
        Harness harness = new Harness();
        WorldId worldId = WorldId.create();

        harness.controller.exportArea(
                worldId,
                "minecraft:overworld",
                0, 0, 15, 15,
                "JAVA_1_21_4",
                "pending",
                ExportSettingsWire.Settings.inherit());
        long originalRequestId = harness.controller.activeExportRequestId();
        assertTrue(originalRequestId > 0L);

        harness.controller.reset();

        assertFalse(harness.controller.exportBusy());
        harness.controller.accept(new MapActionWireProtocol.ExportComplete(
                originalRequestId, worldId, "pending.zip", "JAVA_1_21_4"));

        assertEquals(List.of("pending.zip"), harness.completed);
        assertEquals(List.of("Export ready: pending.zip"), harness.notifications);
    }

    @Test
    void disconnectResetRejectsOldRequestError() {
        Harness harness = new Harness();
        WorldId worldId = WorldId.create();

        harness.controller.exportArea(
                worldId,
                "minecraft:overworld",
                0, 0, 15, 15,
                "JAVA_1_21_4",
                "pending",
                ExportSettingsWire.Settings.inherit());
        long oldRequestId = harness.controller.activeExportRequestId();

        harness.controller.reset();
        harness.controller.accept(new MapActionWireProtocol.ErrorResponse(
                oldRequestId, "old connection failed"));

        assertNull(harness.controller.lastError());
        assertTrue(harness.notifications.isEmpty());
        assertTrue(harness.completed.isEmpty());
    }

    @Test
    void reconnectCompletionStillDeliversWhenNoExportIsActive() {
        Harness harness = new Harness();
        WorldId worldId = WorldId.create();

        harness.controller.accept(new MapActionWireProtocol.ExportComplete(
                42L, worldId, "reconnected.zip", "JAVA_1_21_4"));

        assertFalse(harness.controller.exportBusy());
        assertEquals(List.of("reconnected.zip"), harness.completed);
    }

    @Test
    void staleCurrentWorldResponseCannotOverwriteUnsolicitedPush() throws Exception {
        Harness harness = new Harness();
        WorldId oldWorld = WorldId.create();
        WorldId pushedWorld = WorldId.create();

        harness.controller.refreshCurrentWorld();
        long requestId = harness.controller.activeCurrentWorldRequestId();
        assertTrue(requestId > 0L);

        harness.controller.accept(new MapActionWireProtocol.CurrentWorldResult(
                0L, pushedWorld, "Pushed", "Pushed"));
        harness.controller.accept(new MapActionWireProtocol.CurrentWorldResult(
                requestId, oldWorld, "Old", "Old"));

        assertEquals(pushedWorld, harness.controller.currentWorld().worldId());
        assertEquals(0L, harness.controller.activeCurrentWorldRequestId());
    }

    @Test
    void dimensionTransitionInvalidatesIdentityWithoutCancellingExport() throws Exception {
        Harness harness = new Harness();
        WorldId worldId = WorldId.create();
        harness.controller.accept(new MapActionWireProtocol.CurrentWorldResult(
                0L, worldId, "Build", "Build"));
        harness.controller.refreshCurrentWorld();
        long staleCurrentWorldRequest = harness.controller.activeCurrentWorldRequestId();

        harness.controller.exportArea(
                worldId, "minecraft:overworld", 0, 0, 15, 15,
                "JAVA_1_21_4", "area", ExportSettingsWire.Settings.inherit());
        long exportRequestId = harness.controller.activeExportRequestId();

        harness.controller.clearCurrentWorldForTransition();

        assertNull(harness.controller.currentWorld());
        assertEquals(0L, harness.controller.activeCurrentWorldRequestId());
        assertTrue(harness.controller.exportBusy());
        assertEquals(exportRequestId, harness.controller.activeExportRequestId());

        harness.controller.accept(new MapActionWireProtocol.CurrentWorldResult(
                staleCurrentWorldRequest, worldId, "Stale", "Stale"));
        assertNull(harness.controller.currentWorld());

        harness.controller.accept(new MapActionWireProtocol.ExportComplete(
                exportRequestId, worldId, "area.zip", "JAVA_1_21_4"));
        assertFalse(harness.controller.exportBusy());
        assertEquals(List.of("area.zip"), harness.completed);
    }

    private static final class Harness {
        final List<byte[]> sent = new ArrayList<>();
        final List<String> notifications = new ArrayList<>();
        final List<String> completed = new ArrayList<>();
        final ClientMapController controller = new ClientMapController(
                completed::add, sent::add, notifications::add);
    }
}
