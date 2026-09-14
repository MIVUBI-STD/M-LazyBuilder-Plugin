package com.halokaryamedia.lazybuilder.world.files;

import java.io.IOException;
import java.nio.file.Path;

/** Bounded import-artifact boundary owned by World Manager. */
public interface WorldImportArtifactStore {
    StagedImport stageArchive(String artifactName, Path workspace) throws IOException;

    /**
     * Reads bounded presentation metadata from an uploaded import artifact without
     * publishing or mutating a managed world.
     */
    default ImportInspection inspectArtifact(String artifactName) throws IOException {
        throw new IOException("Import inspection is not supported by this artifact store");
    }

    void sanitizeConvertedWorld(Path worldDirectory) throws IOException;

    /**
     * Removes one validated inbox artifact after a managed-world import has
     * committed successfully. Failed imports intentionally keep the artifact so
     * the caller may retry without re-uploading it.
     *
     * <p>The default no-op keeps lightweight test doubles/source adapters
     * compatible. The local production store overrides this with real bounded
     * deletion.</p>
     */
    default void deleteArtifact(String artifactName) throws IOException { }

    enum DetectedEdition { JAVA, BEDROCK }

    record ImportInspection(String artifactName, DetectedEdition edition, String sourceVersion, String suggestedName) {
        public ImportInspection {
            artifactName = requireText(artifactName, "artifactName");
            java.util.Objects.requireNonNull(edition, "edition");
            sourceVersion = requireText(sourceVersion, "sourceVersion");
            suggestedName = requireText(suggestedName, "suggestedName");
        }

        private static String requireText(String value, String label) {
            java.util.Objects.requireNonNull(value, label);
            String normalized = value.strip();
            if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
            return normalized;
        }
    }

    record StagedImport(Path worldDirectory, DetectedEdition edition, String trustedFormat) {
        public StagedImport {
            worldDirectory = java.util.Objects.requireNonNull(worldDirectory, "worldDirectory")
                    .toAbsolutePath().normalize();
            java.util.Objects.requireNonNull(edition, "edition");
            if (trustedFormat != null) trustedFormat = trustedFormat.strip().toUpperCase(java.util.Locale.ROOT);
        }
    }
}
