package com.halokaryamedia.lazybuilder.builder.history;

import com.halokaryamedia.lazybuilder.builder.mutation.ExtensionReconciliationReport;
import com.halokaryamedia.lazybuilder.builder.mutation.HistoryExtensionTargetRegistry;
import com.halokaryamedia.lazybuilder.builder.mutation.HistoryRecoveryScanner;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedExtensionMutationReconciler;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedMutationReconciler;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedReconciliationReport;
import com.halokaryamedia.lazybuilder.builder.mutation.ReconciliationState;
import com.halokaryamedia.lazybuilder.builder.mutation.WorldBlockStateSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** World-scoped durable History discovery and reconciliation. */
public final class HistoryRecoveryManager {
    private final DiskChangeSetStorage storage;

    public HistoryRecoveryManager(DiskChangeSetStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    public List<Path> incompleteFiles() throws IOException {
        return incompleteFiles(operationId -> true);
    }

    public List<Path> incompleteFiles(Predicate<String> operationFilter) throws IOException {
        Objects.requireNonNull(operationFilter, "operationFilter");
        List<Path> result = new ArrayList<>();
        for (Path path : storage.listRecoverableIncomplete()) {
            String operationId = readHeaderOperationId(path);
            if (operationId != null && operationFilter.test(operationId)) {
                result.add(path);
            }
        }
        return List.copyOf(result);
    }

    public int discardIncompleteFiles() throws IOException {
        return discardIncompleteFiles(operationId -> true);
    }

    public int discardIncompleteFiles(Predicate<String> operationFilter) throws IOException {
        Objects.requireNonNull(operationFilter, "operationFilter");
        storage.promoteRecoverableIncomplete();
        int deleted = 0;
        for (Path path : storage.listRecoverableIncomplete()) {
            String operationId = readHeaderOperationId(path);
            if (operationId != null
                    && operationFilter.test(operationId)
                    && Files.deleteIfExists(path)) {
                deleted++;
            }
        }
        return deleted;
    }

    public List<Path> unscopedIncompleteFiles() throws IOException {
        return incompleteFiles(operationId ->
                ScopedOperationIds.scopeOf(operationId).isEmpty());
    }

    public List<HistoryJournalSummary> unscopedCommittedSummaries() throws IOException {
        return committedSummaries(operationId ->
                ScopedOperationIds.scopeOf(operationId).isEmpty());
    }

    public List<HistoryJournalSummary> committedSummaries(
            Predicate<String> operationFilter
    ) throws IOException {
        return storage.inspectRecoverableCommitted(
                Objects.requireNonNull(operationFilter, "operationFilter"));
    }

    public List<RecoveredHistoryEntry> discover(WorldBlockStateSource world) throws IOException {
        return discover(
                world,
                HistoryExtensionTargetRegistry.empty(),
                operationId -> true);
    }

    public List<RecoveredHistoryEntry> discover(
            WorldBlockStateSource world,
            Predicate<String> operationFilter
    ) throws IOException {
        return discover(world, HistoryExtensionTargetRegistry.empty(), operationFilter);
    }

    public List<RecoveredHistoryEntry> discover(
            WorldBlockStateSource world,
            HistoryExtensionTargetRegistry extensions,
            Predicate<String> operationFilter
    ) throws IOException {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(extensions, "extensions");
        Objects.requireNonNull(operationFilter, "operationFilter");
        storage.promoteRecoverableIncomplete();

        List<RecoveredHistoryEntry> result = new ArrayList<>();
        java.util.Set<StoredChangeSet> wrapped =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<StoredChangeSet> recovered = storage.recoverCommitted(operationFilter);
        try {
            for (StoredChangeSet stored : recovered) {
                PreparedReconciliationReport blockReport =
                        PreparedMutationReconciler.reconcile(stored, world);
                if (stored.extensionCount() == 0) {
                    result.add(new RecoveredHistoryEntry(
                            stored,
                            blockReport,
                            new ExtensionReconciliationReport(
                                    0, 0, 0, 0, ReconciliationState.EMPTY),
                            blockReport.state(),
                            null));
                    wrapped.add(stored);
                    continue;
                }

                String unsupported = firstUnsupportedType(stored, extensions);
                if (unsupported != null) {
                    result.add(new RecoveredHistoryEntry(
                            stored,
                            blockReport,
                            null,
                            null,
                            "no recovery authority for extension type " + unsupported));
                    wrapped.add(stored);
                    continue;
                }

                ExtensionReconciliationReport extensionReport =
                        PreparedExtensionMutationReconciler.reconcile(stored, extensions);
                result.add(new RecoveredHistoryEntry(
                        stored,
                        blockReport,
                        extensionReport,
                        HistoryRecoveryScanner.combine(
                                blockReport.state(), extensionReport.state()),
                        null));
                wrapped.add(stored);
            }
            return List.copyOf(result);
        } catch (IOException | RuntimeException failure) {
            for (RecoveredHistoryEntry entry : result) {
                try {
                    entry.releaseForRetry();
                } catch (IOException suppressed) {
                    failure.addSuppressed(suppressed);
                }
            }
            for (StoredChangeSet stored : recovered) {
                if (!wrapped.contains(stored)) {
                    try {
                        if (!stored.preserveForRecovery()) {
                            failure.addSuppressed(new IOException(
                                    "Recovery journal could not be released after discovery failure: "
                                            + stored.operationId()));
                        }
                    } catch (RuntimeException suppressed) {
                        failure.addSuppressed(suppressed);
                    }
                }
            }
            throw failure;
        }
    }

    private static String firstUnsupportedType(
            StoredChangeSet stored,
            HistoryExtensionTargetRegistry registry
    ) throws IOException {
        String[] unsupported = {null};
        stored.visitExtensions(frame -> {
            if (!registry.supports(frame.typeId())) {
                unsupported[0] = frame.typeId();
                return false;
            }
            return true;
        });
        return unsupported[0];
    }

    private static String readHeaderOperationId(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            return ChangeSetCodec.readOperationId(input);
        } catch (IOException malformed) {
            return null;
        }
    }
}
