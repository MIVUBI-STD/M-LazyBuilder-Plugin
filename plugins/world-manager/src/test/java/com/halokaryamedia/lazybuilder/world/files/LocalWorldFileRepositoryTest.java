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
    void createTransactionRecoveryUsesPersistedRegistryAsCommitAuthority() throws Exception {
        Path worldRoot = tempDir.resolve("worlds");
        Path workRoot = tempDir.resolve("work");
        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);

        UUID committedOperation = UUID.randomUUID();
        repository.markCreatePending(committedOperation, "CommittedCreate");
        Files.createDirectories(worldRoot.resolve("CommittedCreate"));
        Files.writeString(worldRoot.resolve("CommittedCreate/level.dat"), "level");
        WorldRecord committed = new WorldRecord(WorldId.create(), "CommittedCreate", "Committed Create",
                WorldKind.FLAT, WorldLifecycle.ACTIVE);

        WorldFileRepository.CreateRecovery finalized = repository.recoverCreateTransactions(List.of(committed));
        assertEquals(1, finalized.committed());
        assertEquals(0, finalized.rolledBack());
        assertEquals(0, finalized.preserved());
        assertTrue(Files.exists(worldRoot.resolve("CommittedCreate/level.dat")));

        UUID orphanOperation = UUID.randomUUID();
        repository.markCreatePending(orphanOperation, "OrphanCreate");
        repository.markCreateRuntimeCreated(orphanOperation, "OrphanCreate");
        Files.createDirectories(worldRoot.resolve("OrphanCreate"));
        Files.writeString(worldRoot.resolve("OrphanCreate/level.dat"), "level");

        WorldFileRepository.CreateRecovery pending = repository.recoverCreateTransactions(List.of(committed));
        assertEquals(0, pending.committed());
        assertEquals(0, pending.rolledBack());
        assertEquals(1, pending.preserved());
        assertEquals("level", Files.readString(worldRoot.resolve("OrphanCreate/level.dat")));
        try (var markers = Files.list(workRoot)) {
            assertTrue(markers.anyMatch(path -> path.getFileName().toString().endsWith(".create")),
                    "preserved create transaction must retain its durable recovery marker");
        }
    }

    @Test
    void createIntentWithExistingUnmanagedFolderIsPreservedInsteadOfDeleted() throws Exception {
        Path worldRoot = tempDir.resolve("worlds-create-intent");
        Path workRoot = tempDir.resolve("work-create-intent");
        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);

        UUID operation = UUID.randomUUID();
        repository.markCreatePending(operation, "ExistingWorld");
        Files.createDirectories(worldRoot.resolve("ExistingWorld"));
        Files.writeString(worldRoot.resolve("ExistingWorld/level.dat"), "pre-existing");

        WorldFileRepository.CreateRecovery result =
                repository.recoverCreateTransactions(List.of());

        assertEquals(0, result.committed());
        assertEquals(0, result.rolledBack());
        assertEquals(1, result.preserved());
        assertEquals("pre-existing", Files.readString(worldRoot.resolve("ExistingWorld/level.dat")));
    }

    @Test
    void createRecoveryClearsMarkerWhenRuntimeNeverCreatedFolder() throws Exception {
        Path worldRoot = tempDir.resolve("worlds");
        Path workRoot = tempDir.resolve("work");
        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);

        repository.markCreatePending(UUID.randomUUID(), "NeverCreated");
        WorldFileRepository.CreateRecovery result = repository.recoverCreateTransactions(List.of());

        assertEquals(0, result.committed());
        assertEquals(1, result.rolledBack());
        assertEquals(0, result.preserved());
        assertFalse(Files.exists(worldRoot.resolve("NeverCreated")));
    }

    @Test
    void transactionalPublishMarkerContainsAttributableRecoveryIdentity() throws Exception {
        Path worldRoot = tempDir.resolve("worlds-publish-marker");
        Path workRoot = tempDir.resolve("work-publish-marker");
        Files.createDirectories(worldRoot.resolve("Build"));
        Files.writeString(worldRoot.resolve("Build/level.dat"), "level");
        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);

        Path staged = repository.stageCopy(world(), UUID.randomUUID(), WorldCopyProfile.DUPLICATE);
        repository.publishStagedWorld(staged, "Published");

        Path marker = worldRoot.resolve("Published/.lazybuilder-publish-pending");
        String payload = Files.readString(marker);
        assertTrue(payload.contains("version=1"));
        assertTrue(payload.contains("operationId="));
        assertTrue(payload.contains("destination="));
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
        WorldFileRepository.PublishRecovery pending = repository.recoverPublishedWorlds(List.of(committed));
        assertEquals(0, pending.finalized());
        assertEquals(0, pending.discarded());
        assertEquals(1, pending.preserved());
        assertEquals("level", Files.readString(worldRoot.resolve("UncommittedCopy/level.dat")));
        assertTrue(Files.exists(worldRoot.resolve("UncommittedCopy/.lazybuilder-publish-pending")),
                "uncommitted world must retain evidence for operator reconciliation");
    }

    @Test
    void malformedMarkerOnRegisteredWorldIsPreservedUntilReconciliation() throws Exception {
        Path worldRoot = tempDir.resolve("worlds-invalid-publish-marker");
        Path workRoot = tempDir.resolve("work-invalid-publish-marker");
        Path folder = worldRoot.resolve("Registered");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("level.dat"), "important world");
        Files.writeString(folder.resolve(".lazybuilder-publish-pending"), "invalid marker");
        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);
        WorldRecord registered = new WorldRecord(WorldId.create(), "Registered", "Registered",
                WorldKind.FLAT, WorldLifecycle.ACTIVE);
        WorldFileRepository.PublishRecovery result =
                repository.recoverPublishedWorlds(List.of(registered));
        assertEquals(0, result.finalized());
        assertEquals(1, result.preserved());
        assertEquals("important world", Files.readString(folder.resolve("level.dat")));
        assertTrue(Files.exists(folder.resolve(".lazybuilder-publish-pending")));
    }

    @Test
    void preservedRootMarkerMustNotTriggerDimensionCleanupOnSecondPass() throws Exception {
        Path worldRoot = tempDir.resolve("worlds-pending-family");
        Path workRoot = tempDir.resolve("work-pending-family");
        Path root = worldRoot.resolve("Registered");
        Path nether = worldRoot.resolve("Registered_nether");
        Files.createDirectories(root);
        Files.createDirectories(nether.resolve("DIM-1"));
        Files.writeString(root.resolve("level.dat"), "important root");
        Files.writeString(root.resolve(".lazybuilder-publish-pending"), "invalid marker");
        Files.writeString(nether.resolve("level.dat"), "important dimension");
        Files.writeString(nether.resolve("DIM-1/region.dat"), "nether data");
        Files.createFile(nether.resolve(".lazybuilder-family-publish-pending"));
        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);
        WorldRecord registered = new WorldRecord(WorldId.create(), "Registered", "Registered",
                WorldKind.FLAT, WorldLifecycle.ACTIVE);

        WorldFileRepository.PublishRecovery outcome =
                repository.recoverPublishedWorlds(List.of(registered));

        assertEquals(0, outcome.finalized());
        assertEquals(1, outcome.preserved());
        assertEquals("nether data", Files.readString(nether.resolve("DIM-1/region.dat")));
        assertTrue(Files.exists(root.resolve(".lazybuilder-publish-pending")));
        assertTrue(Files.exists(nether.resolve(".lazybuilder-family-publish-pending")),
                "unresolved root must block dimension marker cleanup");
    }

    @Test
    void readOnlyRecoveryEvidenceListsOwnedMarkersWithoutChangingThem() throws Exception {
        Path worldRoot = tempDir.resolve("worlds-recovery-inspection");
        Path workRoot = tempDir.resolve("work-recovery-inspection");
        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);
        repository.markCreatePending(UUID.randomUUID(), "CreateInterrupted");
        Path root = worldRoot.resolve("InterruptedPublication");
        Files.createDirectories(root);
        Files.writeString(root.resolve("level.dat"), "preserve me");
        Files.writeString(root.resolve(".lazybuilder-publish-pending"), "unresolved");

        List<String> evidence = repository.pendingRecoveryEvidence();

        assertTrue(evidence.stream().anyMatch(item -> item.startsWith("CREATE marker: ")));
        assertTrue(evidence.stream().anyMatch(item -> item.startsWith("WORLD publication marker: ")));
        assertEquals("preserve me", Files.readString(root.resolve("level.dat")));
        assertTrue(Files.exists(root.resolve(".lazybuilder-publish-pending")));
        try (var markers = Files.list(workRoot)) {
            assertTrue(markers.anyMatch(p -> p.getFileName().toString().endsWith(".create")));
        }
    }

    @Test
    void legacyEmptyPublishMarkerIsPreservedInsteadOfDeletingUnregisteredWorld() throws Exception {
        Path worldRoot = tempDir.resolve("worlds-legacy-publish");
        Path workRoot = tempDir.resolve("work-legacy-publish");
        Path legacyWorld = worldRoot.resolve("LegacyUncommitted");
        Files.createDirectories(legacyWorld);
        Files.writeString(legacyWorld.resolve("level.dat"), "level");
        Files.createFile(legacyWorld.resolve(".lazybuilder-publish-pending"));

        LocalWorldFileRepository repository = new LocalWorldFileRepository(worldRoot, workRoot);
        WorldFileRepository.PublishRecovery result =
                repository.recoverPublishedWorlds(List.of());

        assertEquals(0, result.finalized());
        assertEquals(0, result.discarded());
        assertEquals(1, result.preserved());
        assertTrue(Files.exists(legacyWorld.resolve("level.dat")));
        assertTrue(Files.exists(legacyWorld.resolve(".lazybuilder-publish-pending")));
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
