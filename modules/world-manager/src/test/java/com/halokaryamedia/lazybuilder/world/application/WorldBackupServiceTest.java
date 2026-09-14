package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.files.WorldBackupStore;
import com.halokaryamedia.lazybuilder.world.files.WorldCopyProfile;
import com.halokaryamedia.lazybuilder.world.files.WorldFileRepository;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldBackupServiceTest {
    @TempDir Path tempDir;

    @Test
    void snapshotIsCapturedOnlyWhileBackupOwnsAnUnloadedSource() throws Exception {
        Fixture fixture = fixture(true);

        WorldBackupService.BackupTask task = fixture.service.prepare(fixture.world.id());
        assertFalse(fixture.runtime.loaded);
        assertEquals(WorldOperationType.BACKUP, fixture.operations.activeOperation(fixture.world.id()));

        WorldBackupService.BackupResult result = fixture.service.executeFilePhase(task);

        assertTrue(task.committed());
        assertEquals(WorldCopyProfile.SNAPSHOT, fixture.files.lastProfile);
        assertEquals(1, fixture.files.stageCopyCount);
        assertTrue(result.artifactName().endsWith(".zip"));

        fixture.service.finish(task);
        assertTrue(fixture.runtime.loaded);
        assertEquals(1, fixture.runtime.loadCount);
        assertFalse(fixture.operations.isBusy(fixture.world.id()));
    }

    @Test
    void externallyReloadedWorldIsRejectedBeforeBackupSnapshotCopy() {
        Fixture fixture = fixture(true);
        WorldBackupService.BackupTask task = fixture.service.prepare(fixture.world.id());
        fixture.runtime.loaded = true;

        assertThrows(IllegalStateException.class, () -> fixture.service.executeFilePhase(task));
        assertEquals(0, fixture.files.stageCopyCount);

        fixture.runtime.loaded = false;
        fixture.service.finish(task);
        assertTrue(fixture.runtime.loaded);
        assertFalse(fixture.operations.isBusy(fixture.world.id()));
    }

    @Test
    void finishDoesNotReloadSourceAfterLifecycleStopsBeingActive() throws Exception {
        Fixture fixture = fixture(true);
        WorldBackupService.BackupTask task = fixture.service.prepare(fixture.world.id());
        fixture.service.executeFilePhase(task);

        fixture.registry.updateMetadata(fixture.world.withLifecycle(WorldLifecycle.ARCHIVED));
        fixture.service.finish(task);

        assertFalse(fixture.runtime.loaded);
        assertEquals(0, fixture.runtime.loadCount);
        assertFalse(fixture.operations.isBusy(fixture.world.id()));
    }

    private Fixture fixture(boolean loaded) {
        WorldRegistry registry = new WorldRegistry();
        WorldRecord world = new WorldRecord(
                WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE);
        registry.register(world);
        FakeRuntime runtime = new FakeRuntime(loaded);
        WorldOperationCoordinator operations = new WorldOperationCoordinator();
        WorldRuntimeService runtimeService = new WorldRuntimeService(registry, runtime, operations, ignored -> false);
        FakeFiles files = new FakeFiles(tempDir);
        WorldBackupStore backups = (stagedWorld, backupId) -> {
            Path artifact = tempDir.resolve(backupId + ".zip");
            Files.writeString(artifact, "backup");
            return artifact;
        };
        WorldBackupService service = new WorldBackupService(
                registry, runtimeService, operations, files, backups);
        return new Fixture(registry, world, runtime, operations, files, service);
    }

    private record Fixture(
            WorldRegistry registry,
            WorldRecord world,
            FakeRuntime runtime,
            WorldOperationCoordinator operations,
            FakeFiles files,
            WorldBackupService service
    ) { }

    private static final class FakeRuntime implements WorldRuntimeGateway {
        private boolean loaded;
        private int loadCount;
        private int unloadCount;
        FakeRuntime(boolean loaded) { this.loaded = loaded; }
        @Override public void createNewWorld(WorldRecord world, BuildReadyPolicy policy) { }
        @Override public void rollbackCreatedWorld(WorldRecord world) { }
        @Override public boolean isLoaded(WorldRecord world) { return loaded; }
        @Override public void loadWorld(WorldRecord world) { loaded = true; loadCount++; }
        @Override public void unloadWorld(WorldRecord world) { loaded = false; unloadCount++; }
        @Override public void teleportPlayerToSpawn(UUID playerId, WorldRecord world) { }
    }

    private static final class FakeFiles implements WorldFileRepository {
        private final Path root;
        private int stageCopyCount;
        private WorldCopyProfile lastProfile;
        FakeFiles(Path root) { this.root = root; }
        @Override public Path stageCopy(WorldRecord source, UUID operationId, WorldCopyProfile profile) throws IOException {
            stageCopyCount++;
            lastProfile = profile;
            Path staged = root.resolve(operationId + ".copy-test");
            Files.createDirectory(staged);
            Files.writeString(staged.resolve("level.dat"), "level");
            return staged;
        }
        @Override public Path stageDelete(WorldRecord world, UUID operationId) { throw new UnsupportedOperationException(); }
        @Override public void publishStagedWorld(Path stagedWorld, String destinationFolder) { throw new UnsupportedOperationException(); }
        @Override public void deleteWorld(WorldRecord world) { throw new UnsupportedOperationException(); }
        @Override public void deleteWorkspace(Path workspace) throws IOException {
            if (Files.notExists(workspace)) return;
            try (var walk = Files.walk(workspace)) {
                for (Path item : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(item);
            }
        }
    }
}
