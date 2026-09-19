package com.halokaryamedia.lazybuilder.world.conversion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalConversionRuntimeStoreTest {
    @TempDir Path tempDir;

    @Test
    void promotesCandidateAndRetainsPreviousForRollback() throws Exception {
        LocalConversionRuntimeStore store = new LocalConversionRuntimeStore(tempDir.resolve("runtime"));
        Path first = tempDir.resolve("first.jar");
        Path second = tempDir.resolve("second.jar");
        Files.writeString(first, "one");
        Files.writeString(second, "two");

        store.stageCandidate(first, manifest("1.0.0", "7692c3ad3540bb803c020b3aee66cd8887123234ea0c6e7143c0add73ff431ed"));
        store.promoteCandidate();
        store.stageCandidate(second, manifest("1.1.0", "3fc4ccfe745870e2c0d99f71f30ff0656c8dedd41cc1d7d3d376b0db6e25dba9"));
        store.promoteCandidate();

        assertEquals("1.1.0", store.current().orElseThrow().manifest().version());
        assertEquals("1.0.0", store.previous().orElseThrow().manifest().version());
        assertFalse(store.candidate().isPresent());

        store.rollbackToPrevious();
        assertEquals("1.0.0", store.current().orElseThrow().manifest().version());
        assertFalse(store.previous().isPresent());
    }

    @Test
    void recoversPromotionInterruptedAfterCurrentMovedToPrevious() throws Exception {
        Path root = tempDir.resolve("runtime");
        LocalConversionRuntimeStore store = new LocalConversionRuntimeStore(root);
        Path first = tempDir.resolve("first.jar");
        Path second = tempDir.resolve("second.jar");
        Files.writeString(first, "one");
        Files.writeString(second, "two");

        store.stageCandidate(first, manifest("1.0.0", "7692c3ad3540bb803c020b3aee66cd8887123234ea0c6e7143c0add73ff431ed"));
        store.promoteCandidate();
        store.stageCandidate(second, manifest("1.1.0", "3fc4ccfe745870e2c0d99f71f30ff0656c8dedd41cc1d7d3d376b0db6e25dba9"));

        Files.move(root.resolve("current"), root.resolve("previous"));

        LocalConversionRuntimeStore restarted = new LocalConversionRuntimeStore(root);
        assertEquals("1.1.0", restarted.current().orElseThrow().manifest().version());
        assertEquals("1.0.0", restarted.previous().orElseThrow().manifest().version());
        assertFalse(restarted.candidate().isPresent());
    }

    @Test
    void restoresPreviousWhenInterruptedCandidateIsIncomplete() throws Exception {
        Path root = tempDir.resolve("runtime");
        LocalConversionRuntimeStore store = new LocalConversionRuntimeStore(root);
        Path first = tempDir.resolve("first.jar");
        Files.writeString(first, "one");

        store.stageCandidate(first, manifest("1.0.0", "7692c3ad3540bb803c020b3aee66cd8887123234ea0c6e7143c0add73ff431ed"));
        store.promoteCandidate();
        Files.move(root.resolve("current"), root.resolve("previous"));
        Files.createDirectories(root.resolve("candidate"));
        Files.writeString(root.resolve("candidate/converter.jar"), "partial");

        LocalConversionRuntimeStore restarted = new LocalConversionRuntimeStore(root);
        assertEquals("1.0.0", restarted.current().orElseThrow().manifest().version());
        assertFalse(restarted.previous().isPresent());
        assertFalse(restarted.candidate().isPresent());
    }

    @Test
    void tamperedCurrentRuntimeIsRejectedOnReadAndRestart() throws Exception {
        Path root = tempDir.resolve("runtime-tampered");
        LocalConversionRuntimeStore store = new LocalConversionRuntimeStore(root);
        Path artifact = tempDir.resolve("verified.jar");
        Files.writeString(artifact, "one");
        store.stageCandidate(
                artifact,
                manifest("1.0.0", "7692c3ad3540bb803c020b3aee66cd8887123234ea0c6e7143c0add73ff431ed"));
        store.promoteCandidate();

        Files.writeString(root.resolve("current/converter.jar"), "tampered");

        assertThrows(java.io.IOException.class, store::current);
        assertThrows(IllegalStateException.class,
                () -> new LocalConversionRuntimeStore(root));
    }

    @Test
    void stageRejectsManifestWithWrongAdapterContract() throws Exception {
        LocalConversionRuntimeStore store =
                new LocalConversionRuntimeStore(tempDir.resolve("runtime-wrong-contract"));
        Path artifact = tempDir.resolve("contract.jar");
        Files.writeString(artifact, "one");
        ConversionRuntimeManifest wrong = new ConversionRuntimeManifest(
                "1.0.0",
                ConverterAdapter.ADAPTER_CONTRACT - 1,
                "7692c3ad3540bb803c020b3aee66cd8887123234ea0c6e7143c0add73ff431ed",
                Instant.EPOCH,
                List.of("JAVA_1_21_4")
        );

        assertThrows(java.io.IOException.class,
                () -> store.stageCandidate(artifact, wrong));
    }

    @Test
    void malformedOrOversizedUpdateCheckMarkerDoesNotBlockFreshCheck() throws Exception {
        Path root = tempDir.resolve("runtime-update-marker");
        LocalConversionRuntimeStore store = new LocalConversionRuntimeStore(root);
        Files.createDirectories(root);
        Path marker = root.resolve("last-update-check.txt");

        Files.writeString(marker, "not-an-instant");
        assertTrue(store.lastUpdateCheck().isEmpty());

        Files.write(marker, new byte[1024]);
        assertTrue(store.lastUpdateCheck().isEmpty());
    }

    @Test
    void runtimeSlotRejectsUnexpectedExtraEntries() throws Exception {
        Path root = tempDir.resolve("runtime-extra-entry");
        LocalConversionRuntimeStore store = new LocalConversionRuntimeStore(root);
        Path artifact = tempDir.resolve("extra-entry.jar");
        Files.writeString(artifact, "one");
        store.stageCandidate(
                artifact,
                manifest("1.0.0", "7692c3ad3540bb803c020b3aee66cd8887123234ea0c6e7143c0add73ff431ed"));
        store.promoteCandidate();

        Files.writeString(root.resolve("current/unexpected.txt"), "unexpected");

        assertThrows(java.io.IOException.class, store::current);
        assertThrows(IllegalStateException.class,
                () -> new LocalConversionRuntimeStore(root));
    }

    @Test
    void persistsLastUpdateCheck() throws Exception {
        LocalConversionRuntimeStore store = new LocalConversionRuntimeStore(tempDir.resolve("runtime"));
        Instant now = Instant.parse("2026-09-12T00:00:00Z");
        assertTrue(store.lastUpdateCheck().isEmpty());
        store.recordUpdateCheck(now);
        assertEquals(now, store.lastUpdateCheck().orElseThrow());
    }

    private static ConversionRuntimeManifest manifest(String version, String digest) {
        return new ConversionRuntimeManifest(
                version,
                ConverterAdapter.ADAPTER_CONTRACT,
                digest,
                Instant.EPOCH,
                List.of("JAVA_1_21_4"));
    }
}
