package com.halokaryamedia.lazybuilder.world.files;

import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Path-safe filesystem boundary for World Manager file operations. */
public interface WorldFileRepository {
    Path stageCopy(WorldRecord source, UUID operationId, WorldCopyProfile profile) throws IOException;

    /**
     * Stages a copy that can observe cooperative cancellation. Implementations that do not
     * provide a cancellable traversal retain compatibility through the normal copy path.
     */
    default Path stageCopy(
            WorldRecord source,
            UUID operationId,
            WorldCopyProfile profile,
            BooleanSupplier cancellationRequested
    ) throws IOException {
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        if (cancellationRequested.getAsBoolean()) throw new IOException("Managed world copy was cancelled");
        return stageCopy(source, operationId, profile);
    }

    /**
     * Stages an area snapshot. Implementations may optimize filesystem work to the
     * selected region files; the default preserves compatibility by staging a normal snapshot.
     */
    default Path stageAreaCopy(WorldRecord source, UUID operationId, AreaCopySelection selection) throws IOException {
        return stageCopy(source, operationId, WorldCopyProfile.SNAPSHOT);
    }

    /** Move one managed world into an owned workspace before destructive deletion is committed. */
    Path stageDelete(WorldRecord world, UUID operationId) throws IOException;

    /** Returns a non-existing, owned direct workspace path reserved by caller identity. */
    default Path reserveWorkspace(UUID operationId) throws IOException {
        throw new UnsupportedOperationException("Workspace reservation is not supported by this repository");
    }

    /** Records a durable Create World transaction before runtime creation begins. */
    default void markCreatePending(UUID operationId, String folderName) throws IOException { }

    /** Clears a durable Create World transaction marker after commit or rollback. */
    default void clearCreatePending(UUID operationId, String folderName) throws IOException { }

    /**
     * Reconciles pending Create World transactions against persisted registry truth.
     * Registered worlds keep their folder and only clear the marker; unregistered worlds
     * created by an interrupted transaction are removed. Unsafe/unattributable state is preserved.
     */
    default CreateRecovery recoverCreateTransactions(Collection<WorldRecord> managedWorlds) throws IOException {
        return new CreateRecovery(0, 0, 0);
    }

    /**
     * Removes only transient workspaces that are safe to discard after a previous process ended.
     * Destructive delete staging is intentionally excluded because it may be the only surviving
     * copy of a managed world after an interrupted delete operation.
     */
    default int recoverTransientWorkspaces() throws IOException {
        return 0;
    }

    /**
     * Reconciles explicitly attributable delete staging after restart. A staged world whose
     * registry record still exists is restored; one whose registry record is already absent is
     * a committed delete and its staging is discarded. Unattributable legacy staging is preserved.
     */
    default DeleteRecovery recoverDeleteWorkspaces(Collection<WorldRecord> managedWorlds) throws IOException {
        return new DeleteRecovery(0, 0, 0);
    }

    /**
     * Reconciles worlds published by a transactional copy/import before the process ended.
     * Persisted registry membership is the commit authority: committed worlds have their
     * pending marker cleared, while explicitly marked uncommitted publications are discarded.
     */
    default PublishRecovery recoverPublishedWorlds(Collection<WorldRecord> managedWorlds) throws IOException {
        return new PublishRecovery(0, 0, 0);
    }

    /** Clears the pending-publication marker after registry persistence has committed. */
    default void markPublishedWorldCommitted(String destinationFolder) throws IOException { }

    /**
     * Audits persisted managed records against the filesystem. Missing or unsafe managed roots
     * are integrity faults and must not be silently removed from registry truth.
     */
    default ManagedWorldAudit auditManagedWorldFolders(Collection<WorldRecord> managedWorlds) throws IOException {
        return new ManagedWorldAudit(List.of(), List.of());
    }

    void publishStagedWorld(Path stagedWorld, String destinationFolder) throws IOException;

    void deleteWorld(WorldRecord world) throws IOException;

    void deleteWorkspace(Path workspace) throws IOException;

    record CreateRecovery(int committed, int rolledBack, int preserved) {
        public CreateRecovery {
            if (committed < 0 || rolledBack < 0 || preserved < 0) {
                throw new IllegalArgumentException("Create recovery counts must not be negative");
            }
        }
    }

    record DeleteRecovery(int restored, int discarded, int preserved) {
        public DeleteRecovery {
            if (restored < 0 || discarded < 0 || preserved < 0) {
                throw new IllegalArgumentException("Delete recovery counts must not be negative");
            }
        }
    }

    record PublishRecovery(int finalized, int discarded, int preserved) {
        public PublishRecovery {
            if (finalized < 0 || discarded < 0 || preserved < 0) {
                throw new IllegalArgumentException("Publish recovery counts must not be negative");
            }
        }
    }

    record ManagedWorldAudit(List<String> missingFolders, List<String> unsafeFolders) {
        public ManagedWorldAudit {
            missingFolders = List.copyOf(missingFolders);
            unsafeFolders = List.copyOf(unsafeFolders);
        }

        public boolean healthy() {
            return missingFolders.isEmpty() && unsafeFolders.isEmpty();
        }
    }
}
