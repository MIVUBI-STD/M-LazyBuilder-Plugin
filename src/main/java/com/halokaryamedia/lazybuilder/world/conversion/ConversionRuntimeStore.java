package com.halokaryamedia.lazybuilder.world.conversion;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/** Durable store for current/previous/candidate converter runtimes. */
public interface ConversionRuntimeStore {
    Optional<InstalledRuntime> current() throws IOException;

    Optional<InstalledRuntime> previous() throws IOException;

    Optional<InstalledRuntime> candidate() throws IOException;

    void stageCandidate(Path artifact, ConversionRuntimeManifest manifest) throws IOException;

    void promoteCandidate() throws IOException;

    void rollbackToPrevious() throws IOException;

    void discardCandidate() throws IOException;

    Optional<Instant> lastUpdateCheck() throws IOException;

    void recordUpdateCheck(Instant instant) throws IOException;

    record InstalledRuntime(Path artifact, ConversionRuntimeManifest manifest) {}
}
