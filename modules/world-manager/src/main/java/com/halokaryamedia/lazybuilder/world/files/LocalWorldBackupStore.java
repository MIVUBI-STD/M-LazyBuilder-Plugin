package com.halokaryamedia.lazybuilder.world.files;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Local compressed backup store reusing the canonical ZIP artifact implementation. */
public final class LocalWorldBackupStore implements WorldBackupStore {
    private final WorldExportArtifactStore archiveStore;

    public LocalWorldBackupStore(Path backupRoot) {
        this.archiveStore = new LocalWorldExportArtifactStore(
                Objects.requireNonNull(backupRoot, "backupRoot")
        );
    }

    @Override
    public Path createBackup(Path stagedWorld, String backupId) throws IOException {
        return archiveStore.packageDirectory(stagedWorld, backupId, ExportArtifactType.JAVA_ZIP);
    }
}
