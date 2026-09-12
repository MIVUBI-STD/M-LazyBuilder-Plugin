package com.halokaryamedia.lazybuilder.world.conversion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/** Lazy stable-only update orchestration with checksum and compatibility gates. */
public final class ConversionUpdateService {
    private final ConversionRuntimePolicy policy;
    private final ConversionRuntimeStore store;
    private final ConversionReleaseSource releaseSource;
    private final ConversionReleaseSource.ArtifactDownloader downloader;
    private final ConverterAdapter adapter;
    private final Path downloadRoot;
    private final Clock clock;

    public ConversionUpdateService(ConversionRuntimePolicy policy, ConversionRuntimeStore store,
                                   ConversionReleaseSource releaseSource,
                                   ConversionReleaseSource.ArtifactDownloader downloader,
                                   ConverterAdapter adapter, Path downloadRoot, Clock clock) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.store = Objects.requireNonNull(store, "store");
        this.releaseSource = Objects.requireNonNull(releaseSource, "releaseSource");
        this.downloader = Objects.requireNonNull(downloader, "downloader");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.downloadRoot = Objects.requireNonNull(downloadRoot, "downloadRoot").toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized UpdateResult checkIfDue() throws IOException {
        Instant now = clock.instant();
        if (policy.updateMode() == ConversionRuntimePolicy.UpdateMode.MANUAL) return UpdateResult.NOT_DUE;
        if (store.lastUpdateCheck().map(last -> last.plus(policy.minimumCheckInterval()).isAfter(now)).orElse(false)) {
            return UpdateResult.NOT_DUE;
        }
        store.recordUpdateCheck(now);
        var release = releaseSource.latestStable();
        if (release.isEmpty()) return UpdateResult.NO_RELEASE;
        var current = store.current();
        if (current.isPresent() && current.get().manifest().version().equals(release.get().version())) return UpdateResult.UP_TO_DATE;
        if (policy.updateMode() == ConversionRuntimePolicy.UpdateMode.NOTIFY_ONLY) return UpdateResult.UPDATE_AVAILABLE;

        Files.createDirectories(downloadRoot);
        Path downloaded = downloader.download(release.get(), downloadRoot);
        try {
            String digest = sha256(downloaded);
            if (policy.checksumRequired() && !digest.equalsIgnoreCase(release.get().sha256())) {
                throw new IOException("Conversion runtime checksum mismatch");
            }
            ConverterAdapter.ConverterProbe probe = adapter.probe(downloaded);
            if (policy.compatibilityProbeRequired() && !probe.runtimeVersion().equals(release.get().version())) {
                throw new IOException("Conversion runtime probe version mismatch");
            }
            ConversionRuntimeManifest manifest = new ConversionRuntimeManifest(
                    release.get().version(), ConverterAdapter.ADAPTER_CONTRACT, digest, now, probe.supportedFormats());
            store.stageCandidate(downloaded, manifest);
            store.promoteCandidate();
            return UpdateResult.UPDATED;
        } catch (IOException | RuntimeException failure) {
            try { store.discardCandidate(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        } finally {
            Files.deleteIfExists(downloaded);
        }
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = in.read(buffer)) >= 0;) if (read > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public enum UpdateResult { NOT_DUE, NO_RELEASE, UP_TO_DATE, UPDATE_AVAILABLE, UPDATED }
}
