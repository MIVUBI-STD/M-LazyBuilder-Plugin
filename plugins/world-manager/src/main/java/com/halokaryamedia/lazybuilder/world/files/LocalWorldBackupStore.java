package com.halokaryamedia.lazybuilder.world.files;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.Locale;
import java.util.Objects;

/** Local compressed backup store reusing the canonical ZIP artifact implementation. */
public final class LocalWorldBackupStore implements WorldBackupStore {
    private final WorldExportArtifactStore archiveStore;
    private final Path backupRoot;

    public LocalWorldBackupStore(Path backupRoot) {
        this.backupRoot = Objects.requireNonNull(backupRoot, "backupRoot").toAbsolutePath().normalize();
        this.archiveStore = new LocalWorldExportArtifactStore(this.backupRoot);
    }

    @Override
    public Path requireExistingBackup(String backupFileName) throws IOException {
        String name = SafeArtifactName.requirePortable(backupFileName, "backupFileName");
        if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IOException("World backup must be a ZIP archive");
        }
        // A canonical path is not necessarily text-equal to its original spelling
        // on Windows (case changes or extended-length path prefixes).
        // Validate the backup-root entry, the leaf, and their *canonical* relationship.
        if (!Files.isDirectory(backupRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(backupRoot)) {
            throw new IOException("World backup root is missing or unsafe");
        }
        Path file = backupRoot.resolve(name).normalize();
        if (!backupRoot.equals(file.getParent())
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(file)) {
            throw new IOException("World backup ZIP is missing or unsafe: " + name);
        }
        Path canonicalRoot = backupRoot.toRealPath();
        Path canonicalFile = file.toRealPath();
        if (!canonicalRoot.equals(canonicalFile.getParent())) {
            throw new IOException("World backup ZIP resolves outside its managed folder: " + name);
        }
        return file;
    }

    @Override
    public Path createBackup(Path stagedWorld, String backupId) throws IOException {
        return archiveStore.packageDirectory(stagedWorld, backupId, ExportArtifactType.JAVA_ZIP);
    }
}
