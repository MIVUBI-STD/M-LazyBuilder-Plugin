package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.world.application.WorldCloneService;
import com.halokaryamedia.lazybuilder.world.application.WorldDeleteService;
import com.halokaryamedia.lazybuilder.world.application.WorldExportService;
import com.halokaryamedia.lazybuilder.world.application.WorldImportService;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;

import java.util.Objects;

/**
 * Shared phased orchestration for heavy World-Manager operations used by both
 * desktop HTTP and Fabric plugin-message transports.
 *
 * <p>Domain/file logic remains in the canonical application services. This class
 * only centralizes Paper main-thread boundaries, phased finish handling, and
 * failure combination so transports do not implement the same workflow twice.</p>
 */
public final class WorldHeavyOperationOrchestrator {
    private final PaperMainThreadDispatcher mainThread;
    private final WorldCloneService cloneService;
    private final WorldDeleteService deleteService;
    private final WorldExportService exportService;
    private final WorldImportService importService;

    public WorldHeavyOperationOrchestrator(
            PaperMainThreadDispatcher mainThread,
            WorldCloneService cloneService,
            WorldDeleteService deleteService,
            WorldExportService exportService,
            WorldImportService importService
    ) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.cloneService = Objects.requireNonNull(cloneService, "cloneService");
        this.deleteService = Objects.requireNonNull(deleteService, "deleteService");
        this.exportService = Objects.requireNonNull(exportService, "exportService");
        this.importService = Objects.requireNonNull(importService, "importService");
    }

    public WorldRecord cloneWorld(
            WorldId sourceId,
            String destinationFolder,
            String displayName,
            Progress progress
    ) throws Exception {
        Progress reporter = progressOrNone(progress);
        reporter.update(10, "Preparing source world on Paper.");
        WorldCloneService.CloneTask task = mainThread.call(
                () -> cloneService.prepare(sourceId, destinationFolder, displayName));
        Exception failure = null;
        WorldRecord cloned = null;
        try {
            reporter.update(30, "Copying world files.");
            cloned = cloneService.executeFilePhase(task);
            reporter.update(85, "Clone published; restoring source runtime state.");
        } catch (Exception exception) {
            failure = exception;
        }
        try {
            mainThread.call(() -> {
                cloneService.finish(task);
                return null;
            });
        } catch (Exception finishFailure) {
            failure = combine(failure, finishFailure);
        }
        if (failure != null) throw failure;
        reporter.update(95, "Clone finalized.");
        return Objects.requireNonNull(cloned, "cloned");
    }

    public WorldRecord deleteWorld(
            WorldId worldId,
            String typedFolderName,
            Progress progress
    ) throws Exception {
        Progress reporter = progressOrNone(progress);
        reporter.update(10, "Verifying delete confirmation and fallback protection.");
        WorldDeleteService.DeleteTask task = mainThread.call(
                () -> deleteService.prepare(worldId, typedFolderName));
        WorldRecord deleted = task.world();
        Exception failure = null;
        try {
            reporter.update(35, "Staging world for reversible deletion.");
            deleteService.executeFilePhase(task);
            reporter.update(90, "Deletion committed; finalizing task.");
        } catch (Exception exception) {
            failure = exception;
        }
        try {
            mainThread.call(() -> {
                deleteService.finish(task);
                return null;
            });
        } catch (Exception finishFailure) {
            failure = combine(failure, finishFailure);
        }
        if (failure != null) throw failure;
        reporter.update(95, "Delete finalized.");
        return deleted;
    }

    public WorldExportService.ExportResult exportWorld(
            WorldId worldId,
            String targetFormat,
            String artifactName,
            Progress progress
    ) throws Exception {
        Progress reporter = progressOrNone(progress);
        reporter.update(10, "Preparing source world on Paper.");
        WorldExportService.ExportTask task = mainThread.call(
                () -> exportService.prepare(worldId, targetFormat, artifactName));
        Exception failure = null;
        WorldExportService.ExportResult result = null;
        try {
            reporter.update(25, "Capturing consistent world snapshot.");
            exportService.captureSnapshot(task);
            reporter.update(45, "Restoring source runtime state.");
            mainThread.call(() -> {
                exportService.resumeSourceAfterSnapshot(task);
                return null;
            });
            reporter.update(60, "Packaging export artifact.");
            result = exportService.processSnapshot(task);
            reporter.update(90, "Export artifact ready; finalizing task.");
        } catch (Exception exception) {
            failure = exception;
        }
        try {
            mainThread.call(() -> {
                exportService.finish(task);
                return null;
            });
        } catch (Exception finishFailure) {
            failure = combine(failure, finishFailure);
        }
        if (failure != null) throw failure;
        reporter.update(95, "Export finalized.");
        return Objects.requireNonNull(result, "result");
    }

    public WorldRecord importWorld(
            String artifactName,
            String destinationFolder,
            String displayName,
            Progress progress
    ) throws Exception {
        Progress reporter = progressOrNone(progress);
        reporter.update(10, "Preparing import.");
        WorldImportService.ImportTask task = importService.prepare(artifactName, destinationFolder, displayName);
        Exception failure = null;
        WorldRecord imported = null;
        try {
            reporter.update(30, "Validating and converting import artifact.");
            imported = importService.executeFilePhase(task);
            reporter.update(90, "Imported world published; finalizing task.");
        } catch (Exception exception) {
            failure = exception;
        }
        try {
            importService.finish(task);
        } catch (Exception finishFailure) {
            failure = combine(failure, finishFailure);
        }
        if (failure != null) throw failure;
        reporter.update(95, "Import finalized.");
        return Objects.requireNonNull(imported, "imported");
    }

    private static Exception combine(Exception primary, Exception secondary) {
        if (primary == null) return secondary;
        primary.addSuppressed(secondary);
        return primary;
    }

    private static Progress progressOrNone(Progress progress) {
        return progress == null ? Progress.NONE : progress;
    }

    @FunctionalInterface
    public interface Progress {
        Progress NONE = (percent, message) -> { };
        void update(int percent, String message);
    }
}
