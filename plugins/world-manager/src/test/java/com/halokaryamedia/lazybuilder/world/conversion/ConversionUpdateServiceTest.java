package com.halokaryamedia.lazybuilder.world.conversion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversionUpdateServiceTest {
    @TempDir Path tempDir;

    @Test
    void verifiedCandidateBecomesCurrentAndFormatsComeFromProbe() throws Exception {
        Path sourceArtifact = tempDir.resolve("source.jar");
        Files.writeString(sourceArtifact, "one");
        String digest = "7692c3ad3540bb803c020b3aee66cd8887123234ea0c6e7143c0add73ff431ed";
        ConversionRelease release = new ConversionRelease("1.0.0", URI.create("https://example.invalid/runtime.jar"), digest);
        LocalConversionRuntimeStore store = new LocalConversionRuntimeStore(tempDir.resolve("runtime"));
        Clock clock = Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC);

        ConversionUpdateService service = new ConversionUpdateService(
                automaticPolicy(),
                store,
                () -> Optional.of(release),
                (ignored, destination) -> {
                    Files.createDirectories(destination);
                    Path copy = destination.resolve("download.jar");
                    Files.copy(sourceArtifact, copy);
                    return copy;
                },
                artifact -> new ConverterAdapter.ConverterProbe("1.0.0", List.of("JAVA_1_21_4", "BEDROCK_1_21")),
                tempDir.resolve("downloads"),
                clock
        );

        assertEquals(ConversionUpdateService.UpdateResult.UPDATED, service.checkIfDue());
        ConversionRuntimeManifest manifest = store.current().orElseThrow().manifest();
        assertEquals("1.0.0", manifest.version());
        assertEquals(List.of("JAVA_1_21_4", "BEDROCK_1_21"), manifest.supportedFormats());
        assertEquals(ConversionUpdateService.UpdateResult.NOT_DUE, service.checkIfDue());
    }

    @Test
    void checksumMismatchNeverPromotesCandidate() throws Exception {
        Path sourceArtifact = tempDir.resolve("source.jar");
        Files.writeString(sourceArtifact, "tampered");
        ConversionRelease release = new ConversionRelease(
                "1.0.0",
                URI.create("https://example.invalid/runtime.jar"),
                "7692c3ad3540bb803c020b3aee66cd8887123234ea0c6e7143c0add73ff431ed"
        );
        LocalConversionRuntimeStore store = new LocalConversionRuntimeStore(tempDir.resolve("runtime"));
        ConversionUpdateService service = new ConversionUpdateService(
                automaticPolicy(), store, () -> Optional.of(release),
                (ignored, destination) -> {
                    Files.createDirectories(destination);
                    Path copy = destination.resolve("download.jar");
                    Files.copy(sourceArtifact, copy);
                    return copy;
                },
                artifact -> new ConverterAdapter.ConverterProbe("1.0.0", List.of("JAVA_1_21_4")),
                tempDir.resolve("downloads"),
                Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC)
        );

        assertThrows(java.io.IOException.class, service::checkIfDue);
        assertEquals(Optional.empty(), store.current());
        assertEquals(Optional.empty(), store.candidate());
    }

    @Test
    void bootstrapRetriesImmediatelyAfterTransientMetadataFailure() throws Exception {
        Path sourceArtifact = tempDir.resolve("source.jar");
        Files.writeString(sourceArtifact, "one");
        String digest = "7692c3ad3540bb803c020b3aee66cd8887123234ea0c6e7143c0add73ff431ed";
        ConversionRelease release = new ConversionRelease("1.0.0", URI.create("https://example.invalid/runtime.jar"), digest);
        LocalConversionRuntimeStore store = new LocalConversionRuntimeStore(tempDir.resolve("runtime"));
        AtomicInteger attempts = new AtomicInteger();

        ConversionUpdateService service = new ConversionUpdateService(
                automaticPolicy(),
                store,
                () -> {
                    if (attempts.incrementAndGet() == 1) throw new IOException("temporary network failure");
                    return Optional.of(release);
                },
                (ignored, destination) -> {
                    Files.createDirectories(destination);
                    Path copy = destination.resolve("download.jar");
                    Files.copy(sourceArtifact, copy);
                    return copy;
                },
                artifact -> new ConverterAdapter.ConverterProbe("1.0.0", List.of("JAVA_1_21_4")),
                tempDir.resolve("downloads"),
                Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC)
        );

        assertThrows(IOException.class, service::checkIfDue);
        assertEquals(ConversionUpdateService.UpdateResult.UPDATED, service.checkIfDue());
        assertEquals(2, attempts.get());
        assertEquals("1.0.0", store.current().orElseThrow().manifest().version());
    }

    @Test
    void rejectsOutOfRootDownloaderResultWithoutDeletingOrProbingIt() throws Exception {
        Path unrelated = tempDir.resolve("important-unrelated.jar");
        Files.writeString(unrelated, "preserve");
        AtomicInteger probeCalls = new AtomicInteger();
        LocalConversionRuntimeStore store = new LocalConversionRuntimeStore(tempDir.resolve("runtime"));
        ConversionUpdateService service = new ConversionUpdateService(
                automaticPolicy(), store,
                () -> Optional.of(new ConversionRelease(
                        "1.0.0", URI.create("https://example.invalid/runtime.jar"),
                        "7692c3ad3540bb803c020b3aee66cd8887123234ea0c6e7143c0add73ff431ed")),
                (release, destination) -> unrelated,
                artifact -> {
                    probeCalls.incrementAndGet();
                    return new ConverterAdapter.ConverterProbe("1.0.0", List.of("JAVA_1_21_4"));
                },
                tempDir.resolve("downloads"),
                Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC)
        );

        assertThrows(IOException.class, service::checkIfDue);
        assertEquals("preserve", Files.readString(unrelated));
        assertEquals(0, probeCalls.get(), "foreign artifact must never reach the runtime probe");
        assertTrue(store.current().isEmpty());
        assertFalse(Files.exists(tempDir.resolve("downloads").resolve("important-unrelated.jar")));
    }

    @Test
    void rejectsDirectoryAsDownloadedArtifactWithoutDeletingIt() throws Exception {
        Path downloadRoot = tempDir.resolve("downloads");
        Path directory = downloadRoot.resolve("converter.jar");
        LocalConversionRuntimeStore store = new LocalConversionRuntimeStore(tempDir.resolve("runtime"));
        ConversionUpdateService service = new ConversionUpdateService(
                automaticPolicy(), store,
                () -> Optional.of(new ConversionRelease(
                        "1.0.0", URI.create("https://example.invalid/runtime.jar"),
                        "7692c3ad3540bb803c020b3aee66cd8887123234ea0c6e7143c0add73ff431ed")),
                (release, destination) -> {
                    Files.createDirectory(directory);
                    return directory;
                },
                artifact -> new ConverterAdapter.ConverterProbe("1.0.0", List.of("JAVA_1_21_4")),
                downloadRoot,
                Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC)
        );

        assertThrows(IOException.class, service::checkIfDue);
        assertTrue(Files.isDirectory(directory), "unsafe returned directory must not be removed");
        assertTrue(store.current().isEmpty());
    }

    private static ConversionRuntimePolicy automaticPolicy() {
        return new ConversionRuntimePolicy(
                ConversionRuntimePolicy.UpdateMode.AUTOMATIC_STABLE,
                Duration.ofHours(24),
                true,
                true,
                true,
                true,
                2,
                1
        );
    }
}
