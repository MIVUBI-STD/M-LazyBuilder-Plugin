package com.halokaryamedia.lazybuilder.world.files;

import java.io.IOException;
import java.nio.file.Path;

/** Bounded import-artifact boundary owned by World Manager. */
public interface WorldImportArtifactStore {
    StagedImport stageArchive(String artifactName, Path workspace) throws IOException;

    void sanitizeConvertedWorld(Path worldDirectory) throws IOException;

    enum DetectedEdition { JAVA, BEDROCK }

    record StagedImport(Path worldDirectory, DetectedEdition edition, String trustedFormat) {
        public StagedImport {
            worldDirectory = java.util.Objects.requireNonNull(worldDirectory, "worldDirectory")
                    .toAbsolutePath().normalize();
            java.util.Objects.requireNonNull(edition, "edition");
            if (trustedFormat != null) trustedFormat = trustedFormat.strip().toUpperCase(java.util.Locale.ROOT);
        }
    }
}
