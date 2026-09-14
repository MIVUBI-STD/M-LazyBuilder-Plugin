package com.halokaryamedia.lazybuilder.world.files;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalWorldFileRepositoryTest {
    @TempDir Path tempDir;

    @Test
    void duplicateProfileRemovesIdentityAndPlayerLocalData() throws Exception {
        Path worldRoot = tempDir.resolve("worlds");
        Path workRoot = tempDir.resolve("work");
        Path source = worldRoot.resolve("Build");
        Files.createDirectories(source.resolve("region"));
        Files.createDirectories(source.resolve("playerdata"));
        Files.writeString(source.resolve("level.dat"), "level");
        Files.writeString(source.resolve("uid.dat"), "uid");
        Files.writeString(source.resolve("session.lock"), "lock");
        Files.writeString(source.resolve("region/r.0.0.mca"), "region");
        Files.writeString(source.resolve("playerdata/player.dat"), "player");

        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);
        Path staged = repository.stageCopy(world(), UUID.randomUUID(), WorldCopyProfile.DUPLICATE);

        assertTrue(Files.exists(staged.resolve("level.dat")));
        assertTrue(Files.exists(staged.resolve("region/r.0.0.mca")));
        assertFalse(Files.exists(staged.resolve("uid.dat")));
        assertFalse(Files.exists(staged.resolve("session.lock")));
        assertFalse(Files.exists(staged.resolve("playerdata")));
    }

    @Test
    void snapshotKeepsWorldIdentityButNeverSessionLock() throws Exception {
        Path worldRoot = tempDir.resolve("worlds");
        Path workRoot = tempDir.resolve("work");
        Path source = worldRoot.resolve("Build");
        Files.createDirectories(source);
        Files.writeString(source.resolve("uid.dat"), "uid");
        Files.writeString(source.resolve("session.lock"), "lock");

        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);
        Path staged = repository.stageCopy(world(), UUID.randomUUID(), WorldCopyProfile.SNAPSHOT);

        assertTrue(Files.exists(staged.resolve("uid.dat")));
        assertFalse(Files.exists(staged.resolve("session.lock")));
    }

    @Test
    void publishAndDeleteOperateOnlyOnOwnedDirectChildren() throws Exception {
        Path worldRoot = tempDir.resolve("worlds");
        Path workRoot = tempDir.resolve("work");
        Path source = worldRoot.resolve("Build");
        Files.createDirectories(source);
        Files.writeString(source.resolve("level.dat"), "level");

        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);
        Path staged = repository.stageCopy(world(), UUID.randomUUID(), WorldCopyProfile.DUPLICATE);
        repository.publishStagedWorld(staged, "BuildCopy");
        assertTrue(Files.exists(worldRoot.resolve("BuildCopy/level.dat")));

        WorldRecord copy = new WorldRecord(WorldId.create(), "BuildCopy", "Build Copy",
                WorldKind.FLAT, WorldLifecycle.ACTIVE);
        repository.deleteWorld(copy);
        assertFalse(Files.exists(worldRoot.resolve("BuildCopy")));

        assertThrows(IllegalArgumentException.class,
                () -> repository.publishStagedWorld(tempDir.resolve("outside"), "Unsafe"));
    }

    @Test
    void transactionalPublishRecoveryUsesPersistedRegistryAsCommitAuthority() throws Exception {
        Path worldRoot = tempDir.resolve("worlds");
        Path workRoot = tempDir.resolve("work");
        Files.createDirectories(worldRoot.resolve("Build"));
        Files.writeString(worldRoot.resolve("Build/level.dat"), "level");
        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);

        Path committedStage = repository.stageCopy(world(), UUID.randomUUID(), WorldCopyProfile.DUPLICATE);
        repository.publishStagedWorld(committedStage, "CommittedCopy");
        WorldRecord committed = new WorldRecord(WorldId.create(), "CommittedCopy", "Committed Copy",
                WorldKind.FLAT, WorldLifecycle.ACTIVE);
        assertTrue(Files.exists(worldRoot.resolve("CommittedCopy/.lazybuilder-publish-pending")));

        WorldFileRepository.PublishRecovery finalized = repository.recoverPublishedWorlds(List.of(committed));
        assertEquals(1, finalized.finalized());
        assertEquals(0, finalized.discarded());
        assertTrue(Files.exists(worldRoot.resolve("CommittedCopy/level.dat")));
        assertFalse(Files.exists(worldRoot.resolve("CommittedCopy/.lazybuilder-publish-pending")));

        Path orphanStage = repository.stageCopy(world(), UUID.randomUUID(), WorldCopyProfile.DUPLICATE);
        repository.publishStagedWorld(orphanStage, "UncommittedCopy");
        WorldFileRepository.PublishRecovery discarded = repository.recoverPublishedWorlds(List.of(committed));
        assertEquals(0, discarded.finalized());
        assertEquals(1, discarded.discarded());
        assertFalse(Files.exists(worldRoot.resolve("UncommittedCopy")));
    }

    @Test
    void managedWorldAuditReportsMissingAndUnsafeRegistryRoots() throws Exception {
        Path worldRoot = tempDir.resolve("worlds");
        Path workRoot = tempDir.resolve("work");
        Files.createDirectories(worldRoot);
        Files.writeString(worldRoot.resolve("Unsafe"), "not-a-directory");
        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);

        WorldRecord missing = new WorldRecord(WorldId.create(), "Missing", "Missing",
                WorldKind.FLAT, WorldLifecycle.ACTIVE);
        WorldRecord unsafe = new WorldRecord(WorldId.create(), "Unsafe", "Unsafe",
                WorldKind.FLAT, WorldLifecycle.ACTIVE);
        WorldFileRepository.ManagedWorldAudit audit = repository.auditManagedWorldFolders(List.of(missing, unsafe));

        assertFalse(audit.healthy());
        assertEquals(List.of("Missing"), audit.missingFolders());
        assertEquals(List.of("Unsafe"), audit.unsafeFolders());
    }

    @Test
    void startupRecoveryDeletesOnlyTypedDisposableWorkspaces() throws Exception {
        Path worldRoot = tempDir.resolve("worlds");
        Path workRoot = tempDir.resolve("work");
        Files.createDirectories(worldRoot.resolve("Build"));
        Files.writeString(worldRoot.resolve("Build/level.dat"), "level");
        Files.createDirectories(worldRoot.resolve("DeleteMe"));
        Files.writeString(worldRoot.resolve("DeleteMe/level.dat"), "level");

        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);
        Path generic = repository.reserveWorkspace(UUID.randomUUID());
        Files.createDirectories(generic);
        Files.writeString(generic.resolve("partial.bin"), "partial");
        Path copied = repository.stageCopy(world(), UUID.randomUUID(), WorldCopyProfile.SNAPSHOT);
        WorldRecord deleteMe = new WorldRecord(WorldId.create(), "DeleteMe", "Delete Me",
                WorldKind.FLAT, WorldLifecycle.ACTIVE);
        Path deleteStage = repository.stageDelete(deleteMe, UUID.randomUUID());
        Path legacyUnknown = workRoot.resolve(UUID.randomUUID().toString());
        Files.createDirectories(legacyUnknown);
        Files.writeString(legacyUnknown.resolve("unknown.bin"), "unknown");

        assertEquals(2, repository.recoverTransientWorkspaces());
        assertFalse(Files.exists(generic));
        assertFalse(Files.exists(copied));
        assertTrue(Files.exists(deleteStage));
        assertTrue(Files.exists(legacyUnknown));
    }

    @Test
    void interruptedUncommittedDeleteRestoresWorldFromPersistedRegistryTruth() throws Exception {
        Path worldRoot = tempDir.resolve("worlds");
        Path workRoot = tempDir.resolve("work");
        WorldRecord managed = new WorldRecord(WorldId.create(), "Delete Me", "Delete Me",
                WorldKind.FLAT, WorldLifecycle.ACTIVE);
        Files.createDirectories(worldRoot.resolve(managed.folderName()));
        Files.writeString(worldRoot.resolve(managed.folderName()).resolve("level.dat"), "level");

        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);
        Path staged = repository.stageDelete(managed, UUID.randomUUID());
        WorldFileRepository.DeleteRecovery result = repository.recoverDeleteWorkspaces(List.of(managed));

        assertEquals(1, result.restored());
        assertEquals(0, result.discarded());
        assertEquals(0, result.preserved());
        assertTrue(Files.exists(worldRoot.resolve(managed.folderName()).resolve("level.dat")));
        assertFalse(Files.exists(staged));
    }

    @Test
    void interruptedCommittedDeleteDiscardsStagingWhenRegistryRecordIsGone() throws Exception {
        Path worldRoot = tempDir.resolve("worlds");
        Path workRoot = tempDir.resolve("work");
        WorldRecord managed = new WorldRecord(WorldId.create(), "DeleteMe", "Delete Me",
                WorldKind.FLAT, WorldLifecycle.ACTIVE);
        Files.createDirectories(worldRoot.resolve(managed.folderName()));
        Files.writeString(worldRoot.resolve(managed.folderName()).resolve("level.dat"), "level");

        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);
        Path staged = repository.stageDelete(managed, UUID.randomUUID());
        WorldFileRepository.DeleteRecovery result = repository.recoverDeleteWorkspaces(List.of());

        assertEquals(0, result.restored());
        assertEquals(1, result.discarded());
        assertEquals(0, result.preserved());
        assertFalse(Files.exists(staged));
        assertFalse(Files.exists(worldRoot.resolve(managed.folderName())));
    }

    @Test
    void unattributableOrConflictingDeleteStagingIsPreserved() throws Exception {
        Path worldRoot = tempDir.resolve("worlds");
        Path workRoot = tempDir.resolve("work");
        Files.createDirectories(workRoot);
        Path legacy = workRoot.resolve(UUID.randomUUID() + ".delete");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("level.dat"), "level");

        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);
        WorldFileRepository.DeleteRecovery result = repository.recoverDeleteWorkspaces(List.of());

        assertEquals(1, result.preserved());
        assertTrue(Files.exists(legacy));
    }

    private static WorldRecord world() {
        return new WorldRecord(WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE);
    }
}
