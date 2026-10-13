package com.halokaryamedia.lazybuilder.world.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import org.junit.jupiter.api.Assumptions;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalWorldExportArtifactStoreTest {
    @TempDir Path tempDir;

    @Test
    void packagesFreshArtifactAndRejectsExistingTarget() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("source"));
        Files.writeString(source.resolve("level.dat"), "level");
        Path exports = tempDir.resolve("exports");
        LocalWorldExportArtifactStore store = new LocalWorldExportArtifactStore(exports);

        Path artifact = store.packageDirectory(
                source,
                "Build",
                ExportArtifactType.JAVA_ZIP
        );

        assertTrue(Files.isRegularFile(artifact));
        try (ZipFile zip = new ZipFile(artifact.toFile())) {
            assertTrue(zip.getEntry("level.dat") != null);
        }
        assertThrows(java.io.IOException.class, () ->
                store.packageDirectory(
                        source,
                        "Build",
                        ExportArtifactType.JAVA_ZIP
                ));
    }

    @Test
    void rejectsNonRegularSourceEntryBeforeZipReading() throws Exception {
        Path directory = Files.createDirectory(tempDir.resolve("not-regular"));
        BasicFileAttributes directoryAttributes = Files.readAttributes(
                directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        assertThrows(java.io.IOException.class,
                () -> LocalWorldExportArtifactStore.requireRegularSourceFile(directory, directoryAttributes));

        Path ordinaryFile = Files.writeString(tempDir.resolve("ordinary.dat"), "data");
        BasicFileAttributes fileAttributes = Files.readAttributes(
                ordinaryFile, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        assertDoesNotThrow(() ->
                LocalWorldExportArtifactStore.requireRegularSourceFile(ordinaryFile, fileAttributes));
    }

    @Test
    void linkedFileCannotBePackagedOrPublishCompletedExport() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("source-with-link"));
        Files.writeString(source.resolve("level.dat"), "level");
        Path outside = Files.writeString(tempDir.resolve("outside-file.dat"), "secret");
        try {
            Files.createSymbolicLink(source.resolve("linked.dat"), outside);
        } catch (java.io.IOException | UnsupportedOperationException | SecurityException unsupported) {
            Assumptions.assumeTrue(false, "Symbolic links unavailable in test environment");
        }

        Path exports = tempDir.resolve("linked-file-exports");
        LocalWorldExportArtifactStore store = new LocalWorldExportArtifactStore(exports);
        assertThrows(java.io.IOException.class,
                () -> store.packageDirectory(source, "Protected", ExportArtifactType.JAVA_ZIP));
        assertFalse(Files.exists(exports.resolve("Protected.zip")));
    }

    @Test
    void finalPublicationRefusesExistingDestinationEvenAfterEarlierPreflight() throws Exception {
        // Model a second export publishing between exists(target) and rename.
        Path exports = Files.createDirectory(tempDir.resolve("race-exports"));
        Path staged = exports.resolve("export-candidate.tmp");
        Path published = exports.resolve("Build.zip");
        Files.writeString(staged, "new candidate");
        Files.writeString(published, "previously committed");

        assertThrows(FileAlreadyExistsException.class,
                () -> LocalWorldExportArtifactStore.moveIntoPlace(staged, published));
        assertEquals("previously committed", Files.readString(published));
        assertEquals("new candidate", Files.readString(staged),
                "caller still owns temporary file for finally cleanup");
    }

    @Test
    void parallelPublishersCannotReplaceAnotherCompletedExport() throws Exception {
        Path exports = Files.createDirectory(tempDir.resolve("parallel-exports"));
        LocalWorldExportArtifactStore store = new LocalWorldExportArtifactStore(exports);
        Path target = exports.resolve("Build.zip");
        Path first = exports.resolve("first.tmp");
        Path second = exports.resolve("second.tmp");
        Files.writeString(first, "first-complete");
        Files.writeString(second, "second-complete");
        CountDownLatch go = new CountDownLatch(1);
        Callable<Boolean> firstPublisher = () -> {
            go.await();
            try { store.publishIfAbsent(first, target); return true; }
            catch (FileAlreadyExistsException collision) { return false; }
        };
        Callable<Boolean> secondPublisher = () -> {
            go.await();
            try { store.publishIfAbsent(second, target); return true; }
            catch (FileAlreadyExistsException collision) { return false; }
        };
        try (var workers = Executors.newFixedThreadPool(2)) {
            var resultA = workers.submit(firstPublisher);
            var resultB = workers.submit(secondPublisher);
            go.countDown();
            boolean firstWon = resultA.get(3, TimeUnit.SECONDS);
            boolean secondWon = resultB.get(3, TimeUnit.SECONDS);
            assertTrue(firstWon != secondWon, "exactly one final export may publish");
            assertEquals(firstWon ? "first-complete" : "second-complete", Files.readString(target));
        }
    }

    @Test
    void rejectsNonDirectoryExportRoot() throws Exception {
        Path root = tempDir.resolve("exports-as-file");
        Files.writeString(root, "not-a-directory");
        Path source = Files.createDirectory(tempDir.resolve("source-two"));
        Files.writeString(source.resolve("level.dat"), "level");

        LocalWorldExportArtifactStore store = new LocalWorldExportArtifactStore(root);
        assertThrows(java.io.IOException.class, () ->
                store.packageDirectory(
                        source,
                        "Build",
                        ExportArtifactType.JAVA_ZIP
                ));
    }
}
