package com.halokaryamedia.lazybuilder.world.files;

import java.io.IOException;
import java.nio.file.Path;

/** Canonical storage boundary for compressed managed-world backups. */
public interface WorldBackupStore {
    /** Package one staged world snapshot and return the created backup artifact path. */
    Path createBackup(Path stagedWorld, String backupId) throws IOException;
}
