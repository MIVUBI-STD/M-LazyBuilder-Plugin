package com.halokaryamedia.lazybuilder.builder.history;

import com.halokaryamedia.lazybuilder.builder.mutation.PreparedMutationReconciler;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedReconciliationReport;
import com.halokaryamedia.lazybuilder.builder.mutation.WorldBlockStateSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Discovers durable History left by a previous process and classifies block-only
 * operations against the current world before any resume/discard decision.
 */
public final class HistoryRecoveryManager {
    private final DiskChangeSetStorage storage;

    public HistoryRecoveryManager(DiskChangeSetStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    public List<Path> incompleteFiles() throws IOException {
        return storage.listIncomplete();
    }

    /**
     * Promotes any staging journal that already has a valid committed footer/checksum,
     * then deletes only the remaining invalid/truncated staging files.
     */
    public int discardIncompleteFiles() throws IOException {
        storage.promoteRecoverableIncomplete();
        int deleted = 0;
        for (Path path : storage.listIncomplete()) {
            if (java.nio.file.Files.deleteIfExists(path)) deleted++;
        }
        return deleted;
    }

    public List<RecoveredHistoryEntry> discover(WorldBlockStateSource world) throws IOException {
        Objects.requireNonNull(world, "world");
        storage.promoteRecoverableIncomplete();
        List<RecoveredHistoryEntry> result = new ArrayList<>();
        try {
            for (StoredChangeSet stored : storage.recoverCommitted()) {
                PreparedReconciliationReport report = null;
                if (stored.extensionCount() == 0) {
                    report = PreparedMutationReconciler.reconcile(stored, world);
                }
                result.add(new RecoveredHistoryEntry(stored, report));
            }
            return List.copyOf(result);
        } catch (IOException | RuntimeException failure) {
            for (RecoveredHistoryEntry entry : result) {
                try {
                    entry.close();
                } catch (IOException suppressed) {
                    failure.addSuppressed(suppressed);
                }
            }
            throw failure;
        }
    }
}
