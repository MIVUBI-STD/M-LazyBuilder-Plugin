package com.halokaryamedia.lazybuilder.world.files;

import java.io.IOException;
import java.nio.file.Path;

/** Bounded server-side store for completed World Manager export artifacts. */
public interface WorldExportArtifactStore {
    Path packageDirectory(Path sourceDirectory, String artifactName, ExportArtifactType type) throws IOException;
}
