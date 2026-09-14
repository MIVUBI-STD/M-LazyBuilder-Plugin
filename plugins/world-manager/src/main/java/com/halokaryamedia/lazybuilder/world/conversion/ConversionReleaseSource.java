package com.halokaryamedia.lazybuilder.world.conversion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public interface ConversionReleaseSource {
    Optional<ConversionRelease> latestStable() throws IOException;

    interface ArtifactDownloader {
        Path download(ConversionRelease release, Path destinationDirectory) throws IOException;
    }
}
