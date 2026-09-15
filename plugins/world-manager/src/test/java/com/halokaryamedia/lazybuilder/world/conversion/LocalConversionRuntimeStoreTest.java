package com.halokaryamedia.lazybuilder.world.conversion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void persistsLastUpdateCheck() throws Exception {
        LocalConversionRuntimeStore store = new LocalConversionRuntimeStore(tempDir.resolve("runtime"));
        Instant now = Instant.parse("2026-09-12T00:00:00Z");
        assertTrue(store.lastUpdateCheck().isEmpty());
        store.recordUpdateCheck(now);
        assertEquals(now, store.lastUpdateCheck().orElseThrow());
    }

    private static ConversionRuntimeManifest manifest(String version, String digest) {
        return new ConversionRuntimeManifest(version, 1, digest, Instant.EPOCH, List.of("JAVA_1_21_4"));
    }
}
