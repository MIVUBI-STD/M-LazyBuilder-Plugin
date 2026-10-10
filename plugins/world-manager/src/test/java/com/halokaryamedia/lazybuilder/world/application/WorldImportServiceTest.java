package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.conversion.ConversionJobCoordinator;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionRuntimeManifest;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionRuntimePolicy;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionRuntimeStore;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionUpdateService;
import com.halokaryamedia.lazybuilder.world.conversion.ConverterAdapter;
import com.halokaryamedia.lazybuilder.world.files.WorldCopyProfile;
import com.halokaryamedia.lazybuilder.world.files.WorldFileRepository;
import com.halokaryamedia.lazybuilder.world.files.WorldImportArtifactStore;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldImportServiceTest {
    @TempDir Path tempDir;

    @Test
    void trustedNativeImportPublishesFreshActiveRecordWithoutConverter() throws Exception {
        WorldRegistry registry = new WorldRegistry();
        MemoryPersistence persistence = new MemoryPersistence();
        FakeFiles files = new FakeFiles(tempDir);
        FakeImports imports = new FakeImports(WorldImportArtifactStore.DetectedEdition.JAVA, WorldImportService.TARGET_FORMAT);
        FakeStore runtimeStore = new FakeStore();
        ConverterAdapter converter = runtimeArtifact -> { throw new AssertionError("native import must not probe converter"); };
        ConversionUpdateService updates = updates(runtimeStore, converter);
        WorldImportService service = new WorldImportService(
                registry, persistence, files, imports,
                runtimeStore, updates, converter, new ConversionJobCoordinator()
        );

        WorldImportService.ImportTask task = service.prepare("Build.zip", "ImportedBuild", "Imported Build");
        WorldRecord imported = service.executeFilePhase(task);
        service.finish(task);

        assertTrue(task.completed());
        assertEquals("ImportedBuild", imported.folderName());
        assertEquals(com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle.ACTIVE, imported.lifecycle());
        assertEquals(1, persistence.saved.size());
        assertTrue(files.published);
        assertEquals(List.of("Build.zip"), imports.deletedArtifacts);
        assertTrue(imports.cleanupMarkers.isEmpty());
    }

    @Test
    void committedImportRetriesTransientArtifactCleanupWithoutFailingWorld() throws Exception {
        WorldRegistry registry = new WorldRegistry();
        MemoryPersistence persistence = new MemoryPersistence();
        FakeFiles files = new FakeFiles(tempDir);
        FakeImports imports = new FakeImports(WorldImportArtifactStore.DetectedEdition.JAVA, WorldImportService.TARGET_FORMAT);
        imports.deleteFailuresRemaining = 1;
        FakeStore runtimeStore = new FakeStore();
        ConverterAdapter converter = runtimeArtifact -> { throw new AssertionError("native import must not probe converter"); };
        ConversionUpdateService updates = updates(runtimeStore, converter);
        WorldImportService service = new WorldImportService(
                registry, persistence, files, imports,
                runtimeStore, updates, converter, new ConversionJobCoordinator()
        );

        WorldImportService.ImportTask task = service.prepare("Build.zip", "ImportedBuild", "Imported Build");
        WorldRecord imported = service.executeFilePhase(task);

        assertTrue(task.completed());
        assertTrue(task.artifactCleanupFailure() != null);
        assertEquals(1, service.pendingCommittedArtifactCleanupCount());
        assertEquals(Set.of("Build.zip"), imports.cleanupMarkers);
        assertEquals("ImportedBuild", imported.folderName());
        assertEquals(1, registry.all().size(), "cleanup failure must not roll back a committed world");

        service.finish(task);

        assertNull(task.artifactCleanupFailure());
        assertEquals(0, service.pendingCommittedArtifactCleanupCount());
        assertTrue(imports.cleanupMarkers.isEmpty());
        assertEquals(2, imports.deleteAttempts);
        assertEquals(List.of("Build.zip"), imports.deletedArtifacts);
        assertEquals(1, registry.all().size());
    }

    @Test
    void committedCleanupMarkerRecoversAfterServiceRestart() throws Exception {
        MemoryPersistence persistence = new MemoryPersistence();
        FakeFiles files = new FakeFiles(tempDir);
        FakeImports imports = new FakeImports(WorldImportArtifactStore.DetectedEdition.JAVA, WorldImportService.TARGET_FORMAT);
        imports.deleteFailuresRemaining = 2;
        FakeStore runtimeStore = new FakeStore();
        ConverterAdapter converter = runtimeArtifact -> { throw new AssertionError("native import must not probe converter"); };
        ConversionUpdateService updates = updates(runtimeStore, converter);

        WorldImportService first = new WorldImportService(
                new WorldRegistry(), persistence, files, imports,
                runtimeStore, updates, converter, new ConversionJobCoordinator()
        );
        WorldImportService.ImportTask task = first.prepare("Build.zip", "ImportedBuild", "Imported Build");
        first.executeFilePhase(task);
        first.finish(task);

        assertTrue(task.completed());
        assertTrue(task.artifactCleanupFailure() != null);
        assertEquals(Set.of("Build.zip"), imports.cleanupMarkers);
        assertEquals(1, first.pendingCommittedArtifactCleanupCount());

        WorldImportService restarted = new WorldImportService(
                new WorldRegistry(), persistence, new FakeFiles(tempDir.resolve("restart")), imports,
                runtimeStore, updates, converter, new ConversionJobCoordinator()
        );
        restarted.recoverPendingCommittedArtifactCleanup();

        assertEquals(0, restarted.pendingCommittedArtifactCleanupCount());
        assertTrue(imports.cleanupMarkers.isEmpty());
        assertEquals(List.of("Build.zip"), imports.deletedArtifacts);
        assertEquals(3, imports.deleteAttempts);
    }

    @Test
    void globalCleanupCannotDeleteClaimedActiveImportSource() {
        WorldRegistry registry = new WorldRegistry();
        MemoryPersistence persistence = new MemoryPersistence();
        FakeFiles files = new FakeFiles(tempDir);
        FakeImports imports = new FakeImports(WorldImportArtifactStore.DetectedEdition.JAVA, WorldImportService.TARGET_FORMAT);
        FakeStore runtimeStore = new FakeStore();
        ConverterAdapter converter = runtimeArtifact -> { throw new AssertionError("must not convert"); };
        WorldImportService service = new WorldImportService(registry, persistence, files, imports,
                runtimeStore, updates(runtimeStore, converter), converter, new ConversionJobCoordinator());
        WorldImportService.ImportTask task = service.prepare("Held.zip", "HeldWorld", "Held");
        imports.cleanupMarkers.add("Held.zip");

        service.recoverPendingCommittedArtifactCleanup();
        assertFalse(imports.deletedArtifacts.contains("Held.zip"));
        assertEquals(1, service.pendingCommittedArtifactCleanupCount());

        service.finish(task);
        service.retryPendingCommittedArtifactCleanup();
        assertTrue(imports.deletedArtifacts.contains("Held.zip"));
    }

    @Test
    void pendingCommittedCleanupBlocksAnotherImportOfSameSource() {
        WorldRegistry registry = new WorldRegistry();
        MemoryPersistence persistence = new MemoryPersistence();
        FakeFiles files = new FakeFiles(tempDir);
        FakeImports imports = new FakeImports(WorldImportArtifactStore.DetectedEdition.JAVA, WorldImportService.TARGET_FORMAT);
        imports.cleanupMarkers.add("Committed.zip");
        imports.deleteFailuresRemaining = 1;
        FakeStore runtimeStore = new FakeStore();
        ConverterAdapter converter = runtimeArtifact -> { throw new AssertionError("must not convert"); };
        WorldImportService service = new WorldImportService(registry, persistence, files, imports,
                runtimeStore, updates(runtimeStore, converter), converter, new ConversionJobCoordinator());

        service.recoverPendingCommittedArtifactCleanup();
        assertThrows(IllegalStateException.class,
                () -> service.prepare("Committed.zip", "Fresh", "Fresh"));
        assertEquals(0, registry.all().size());
    }

    @Test
    void prepareReadsDurableCommittedCleanupMarkersEvenWithoutStartupRecovery() {
        WorldRegistry registry = new WorldRegistry();
        MemoryPersistence persistence = new MemoryPersistence();
        FakeFiles files = new FakeFiles(tempDir);
        FakeImports imports = new FakeImports(WorldImportArtifactStore.DetectedEdition.JAVA, WorldImportService.TARGET_FORMAT);
        imports.cleanupMarkers.add("Stale.zip");
        FakeStore runtimeStore = new FakeStore();
        ConverterAdapter converter = runtimeArtifact -> { throw new AssertionError("conversion not expected"); };
        WorldImportService service = new WorldImportService(registry, persistence, files, imports,
                runtimeStore, updates(runtimeStore, converter), converter, new ConversionJobCoordinator());

        assertThrows(IllegalStateException.class,
                () -> service.prepare("Stale.zip", "NewWorld", "New World"));
        assertEquals(0, registry.all().size(), "committed source must not publish a second world");
        assertEquals(1, service.pendingCommittedArtifactCleanupCount());
    }

    @Test
    void failedDurableCleanupMarkerReadRejectsImportBeforeDestinationReservation() {
        WorldRegistry registry = new WorldRegistry();
        FakeImports imports = new FakeImports(WorldImportArtifactStore.DetectedEdition.JAVA, WorldImportService.TARGET_FORMAT);
        imports.failCleanupMarkerRead = true;
        WorldImportService service = nativeService(registry, imports);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.prepare("Uncertain.zip", "Uncertain", "Uncertain"));
        assertTrue(failure.getMessage().contains("Could not verify pending committed import cleanup"));
        assertEquals(0, registry.all().size());
        assertEquals(0, imports.deleteAttempts);
        // A read failure must not hold the source reservation forever.
        imports.failCleanupMarkerRead = false;
        WorldImportService.ImportTask retried = service.prepare("Uncertain.zip", "Uncertain", "Uncertain");
        service.finish(retried);
    }

    @Test
    void committedImportMarkerReadIsCheckedBeforeAnotherWorldCanBeCreated() throws Exception {
        WorldRegistry registry = new WorldRegistry();
        FakeImports imports = new FakeImports(WorldImportArtifactStore.DetectedEdition.JAVA, WorldImportService.TARGET_FORMAT);
        imports.cleanupMarkers.add("Old.zip");
        // Startup recovered the marker but deletion keeps failing.
        imports.deleteFailuresRemaining = 3;
        WorldImportService service = nativeService(registry, imports);
        service.recoverPendingCommittedArtifactCleanup();

        assertThrows(IllegalStateException.class, () -> service.prepare("Old.zip", "Duplicate", "Duplicate"));
        assertTrue(registry.all().isEmpty());
        assertEquals(Set.of("Old.zip"), imports.cleanupMarkers);
    }

    @Test
    void failedInspectionDiscardsUnusableUploadedArtifact() {
        WorldRegistry registry = new WorldRegistry();
        MemoryPersistence persistence = new MemoryPersistence();
        FakeFiles files = new FakeFiles(tempDir);
        FakeImports imports = new FakeImports(WorldImportArtifactStore.DetectedEdition.JAVA, null);
        imports.failInspection = true;
        FakeStore runtimeStore = new FakeStore();
        ConverterAdapter converter = runtimeArtifact -> { throw new AssertionError("inspection must not probe converter"); };
        ConversionUpdateService updates = updates(runtimeStore, converter);
        WorldImportService service = new WorldImportService(
                registry, persistence, files, imports,
                runtimeStore, updates, converter, new ConversionJobCoordinator()
        );

        assertThrows(IllegalStateException.class, () -> service.inspect("Broken.zip"));
        assertEquals(List.of("Broken.zip"), imports.deletedArtifacts);
    }

    @Test
    void explicitDiscardRemovesReviewedArtifact() {
        WorldRegistry registry = new WorldRegistry();
        MemoryPersistence persistence = new MemoryPersistence();
        FakeFiles files = new FakeFiles(tempDir);
        FakeImports imports = new FakeImports(WorldImportArtifactStore.DetectedEdition.JAVA, WorldImportService.TARGET_FORMAT);
        FakeStore runtimeStore = new FakeStore();
        ConverterAdapter converter = runtimeArtifact -> { throw new AssertionError("discard must not probe converter"); };
        ConversionUpdateService updates = updates(runtimeStore, converter);
        WorldImportService service = new WorldImportService(
                registry, persistence, files, imports,
                runtimeStore, updates, converter, new ConversionJobCoordinator()
        );

        service.discard("Reviewed.zip");

        assertEquals(List.of("Reviewed.zip"), imports.deletedArtifacts);
        assertFalse(files.published);
    }

    @Test
    void sameSourceArchiveCannotEnterTwoImportsEvenWithDifferentDestinationFolders() {
        FakeImports imports = new FakeImports(WorldImportArtifactStore.DetectedEdition.JAVA, WorldImportService.TARGET_FORMAT);
        WorldImportService service = nativeService(new WorldRegistry(), imports);

        WorldImportService.ImportTask first = service.prepare("Build.zip", "FirstWorld", "First World");
        try {
            IllegalStateException duplicate = assertThrows(IllegalStateException.class,
                    () -> service.prepare("Build.zip", "SecondWorld", "Second World"));
            assertTrue(duplicate.getMessage().contains("already in use"));
            assertThrows(IllegalStateException.class,
                    () -> service.prepare("build.ZIP", "ThirdWorld", "Third World"),
                    "case-only aliases must not bypass source exclusivity on Windows");
            assertTrue(imports.deletedArtifacts.isEmpty());
        } finally {
            service.finish(first);
        }

        WorldImportService.ImportTask next = service.prepare("Build.zip", "SecondWorld", "Second World");
        service.finish(next);
    }

    @Test
    void inspectingAndDiscardingClaimedImportCannotDeleteItsInput() {
        FakeImports imports = new FakeImports(WorldImportArtifactStore.DetectedEdition.JAVA, WorldImportService.TARGET_FORMAT);
        WorldImportService service = nativeService(new WorldRegistry(), imports);
        WorldImportService.ImportTask task = service.prepare("Build.zip", "Destination", "Destination");

        assertThrows(IllegalStateException.class, () -> service.inspect("Build.zip"));
        assertThrows(IllegalStateException.class, () -> service.discard("BUILD.ZIP"));
        assertTrue(imports.deletedArtifacts.isEmpty(), "claimed import must keep its archive");

        service.finish(task);
        service.discard("Build.zip");
        assertEquals(List.of("Build.zip"), imports.deletedArtifacts);
    }

    @Test
    void rejectedDestinationAndFailedInspectionReleaseArchiveClaims() {
        FakeImports imports = new FakeImports(WorldImportArtifactStore.DetectedEdition.JAVA, WorldImportService.TARGET_FORMAT);
        WorldRegistry registry = new WorldRegistry();
        WorldImportService service = nativeService(registry, imports);

        assertThrows(IllegalArgumentException.class,
                () -> service.prepare("Build.zip", "", "Invalid destination"));
        WorldImportService.ImportTask first = service.prepare("Build.zip", "Reserved", "Reserved");
        try {
            assertThrows(IllegalStateException.class,
                    () -> service.prepare("Other.zip", "Reserved", "Conflicting folder"));
            WorldImportService.ImportTask other = service.prepare("Other.zip", "Other", "Other");
            service.finish(other);
        } finally {
            service.finish(first);
        }

        imports.failInspection = true;
        assertThrows(IllegalStateException.class, () -> service.inspect("Invalid.zip"));
        imports.failInspection = false;
        WorldImportService.ImportTask retry = service.prepare("Invalid.zip", "Retry", "Retry");
        service.finish(retry);
    }

    @Test
    void finishingOldImportAgainCannotReleaseNewImportClaim() {
        FakeImports imports = new FakeImports(WorldImportArtifactStore.DetectedEdition.JAVA, WorldImportService.TARGET_FORMAT);
        WorldImportService service = nativeService(new WorldRegistry(), imports);

        WorldImportService.ImportTask old = service.prepare("Build.zip", "Old", "Old");
        service.finish(old);
        WorldImportService.ImportTask current = service.prepare("Build.zip", "Current", "Current");
        service.finish(old);
        assertThrows(IllegalStateException.class,
                () -> service.prepare("Build.zip", "Unexpected", "Unexpected"),
                "old completion must not unlock the current source owner");
        service.finish(current);
        WorldImportService.ImportTask next = service.prepare("Build.zip", "Next", "Next");
        service.finish(next);
    }

    @Test
    void failedConversionCleansEveryOwnedWorkspaceButKeepsArtifactForRetry() throws Exception {
        WorldRegistry registry = new WorldRegistry();
        MemoryPersistence persistence = new MemoryPersistence();
        FakeFiles files = new FakeFiles(tempDir);
        FakeImports imports = new FakeImports(WorldImportArtifactStore.DetectedEdition.JAVA, null);

        Path runtimeArtifact = tempDir.resolve("converter.jar");
        Files.writeString(runtimeArtifact, "runtime");
        ConversionRuntimeManifest manifest = new ConversionRuntimeManifest(
                "1.0.0",
                ConverterAdapter.ADAPTER_CONTRACT,
                "0".repeat(64),
                Instant.parse("2026-09-12T00:00:00Z"),
                List.of(WorldImportService.TARGET_FORMAT)
        );
        FakeStore runtimeStore = new FakeStore(Optional.of(
                new ConversionRuntimeStore.InstalledRuntime(runtimeArtifact, manifest)
        ));
        ConverterAdapter converter = new ConverterAdapter() {
            @Override
            public ConverterProbe probe(Path runtimeArtifact) {
                return new ConverterProbe("1.0.0", List.of(WorldImportService.TARGET_FORMAT));
            }

            @Override
            public ConversionResult convert(Path runtimeArtifact, ConversionRequest request) throws IOException {
                Files.createDirectories(request.outputDirectory());
                Files.writeString(request.outputDirectory().resolve("partial.bin"), "partial");
                throw new IOException("conversion failed after writing output");
            }
        };
        ConversionUpdateService updates = new ConversionUpdateService(
                ConversionRuntimePolicy.defaults(), runtimeStore, Optional::empty,
                (release, directory) -> { throw new AssertionError("installed runtime should be reused"); },
                converter, tempDir.resolve("downloads"),
                Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC)
        );
        WorldImportService service = new WorldImportService(
                registry, persistence, files, imports,
                runtimeStore, updates, converter, new ConversionJobCoordinator()
        );

        WorldImportService.ImportTask task = service.prepare("Old.zip", "ImportedBuild", "Imported Build");
        assertThrows(IllegalStateException.class, () -> service.executeFilePhase(task));
        service.finish(task);

        assertFalse(files.published);
        assertTrue(imports.deletedArtifacts.isEmpty(), "valid source artifact must remain available for retry");
        for (Path workspace : files.reserved) {
            assertFalse(Files.exists(workspace), "workspace should be cleaned: " + workspace);
        }
    }

    @Test
    void persistenceFailureRollsBackPublishedWorldAndKeepsArtifactForRetry() throws Exception {
        WorldRegistry registry = new WorldRegistry();
        MemoryPersistence persistence = new MemoryPersistence();
        persistence.failSave = true;
        FakeFiles files = new FakeFiles(tempDir);
        FakeImports imports = new FakeImports(WorldImportArtifactStore.DetectedEdition.JAVA, WorldImportService.TARGET_FORMAT);
        FakeStore runtimeStore = new FakeStore();
        ConverterAdapter converter = runtimeArtifact -> { throw new AssertionError("native import must not probe converter"); };
        ConversionUpdateService updates = updates(runtimeStore, converter);
        WorldImportService service = new WorldImportService(
                registry, persistence, files, imports,
                runtimeStore, updates, converter, new ConversionJobCoordinator()
        );

        WorldImportService.ImportTask task = service.prepare("Build.zip", "ImportedBuild", "Imported Build");
        assertThrows(IllegalStateException.class, () -> service.executeFilePhase(task));
        service.finish(task);

        assertTrue(registry.all().isEmpty(), "failed publish commit must not leave a managed record");
        assertFalse(Files.exists(tempDir.resolve("ImportedBuild")), "published directory must be rolled back");
        assertTrue(imports.deletedArtifacts.isEmpty(), "source artifact must remain available for retry");
        assertTrue(imports.cleanupMarkers.isEmpty(), "uncommitted import must never gain committed-cleanup authority");
    }

    private WorldImportService nativeService(WorldRegistry registry, FakeImports imports) {
        FakeStore store = new FakeStore();
        ConverterAdapter converter = runtimeArtifact -> {
            throw new AssertionError("native source reservation must not load the converter");
        };
        return new WorldImportService(
                registry, new MemoryPersistence(), new FakeFiles(tempDir), imports,
                store, updates(store, converter), converter, new ConversionJobCoordinator()
        );
    }

    private ConversionUpdateService updates(FakeStore runtimeStore, ConverterAdapter converter) {
        return new ConversionUpdateService(
                ConversionRuntimePolicy.defaults(), runtimeStore, Optional::empty,
                (release, directory) -> { throw new AssertionError("native import must not download runtime"); },
                converter, tempDir.resolve("downloads"),
                Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private static final class MemoryPersistence implements WorldRegistryPersistence {
        List<WorldRecord> saved = List.of();
        boolean failSave;
        @Override public List<WorldRecord> load() { return saved; }
        @Override public void save(List<WorldRecord> worlds) {
            if (failSave) throw new IllegalStateException("persistence failed");
            saved = List.copyOf(worlds);
        }
    }

    private static final class FakeFiles implements WorldFileRepository {
        private final Path root;
        private final List<Path> reserved = new ArrayList<>();
        boolean published;
        FakeFiles(Path root) { this.root = root; }
        @Override public Path stageCopy(WorldRecord source, UUID operationId, WorldCopyProfile profile) { throw new UnsupportedOperationException(); }
        @Override public Path stageDelete(WorldRecord world, UUID operationId) { throw new UnsupportedOperationException(); }
        @Override public Path reserveWorkspace(UUID operationId) {
            Path workspace = root.resolve(operationId.toString());
            reserved.add(workspace);
            return workspace;
        }
        @Override public void publishStagedWorld(Path stagedWorld, String destinationFolder) throws IOException {
            Files.move(stagedWorld, root.resolve(destinationFolder));
            published = true;
        }
        @Override public void deleteWorld(WorldRecord world) throws IOException {
            Path path = root.resolve(world.folderName());
            if (Files.exists(path)) try (var walk = Files.walk(path)) {
                for (Path item : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(item);
            }
        }
        @Override public void deleteWorkspace(Path workspace) throws IOException {
            if (Files.notExists(workspace)) return;
            try (var walk = Files.walk(workspace)) {
                for (Path item : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(item);
            }
        }
    }

    private static final class FakeImports implements WorldImportArtifactStore {
        private final DetectedEdition edition;
        private final String trustedFormat;
        private final List<String> deletedArtifacts = new ArrayList<>();
        private final Set<String> cleanupMarkers = new HashSet<>();
        private boolean failInspection;
        private boolean failCleanupMarkerRead;
        private int deleteFailuresRemaining;
        private int deleteAttempts;

        private FakeImports(DetectedEdition edition, String trustedFormat) {
            this.edition = edition;
            this.trustedFormat = trustedFormat;
        }

        @Override public ImportInspection inspectArtifact(String artifactName) throws IOException {
            if (failInspection) throw new IOException("invalid archive");
            String sourceVersion = trustedFormat == null ? "Unknown version" : "Java Edition 1.21.4";
            return new ImportInspection(artifactName, edition, sourceVersion, "Imported World");
        }
        @Override public StagedImport stageArchive(String artifactName, Path workspace) throws IOException {
            Files.createDirectories(workspace.getParent());
            Files.createDirectory(workspace);
            Files.writeString(workspace.resolve("level.dat"), "level");
            return new StagedImport(workspace, edition, trustedFormat);
        }
        @Override public void sanitizeConvertedWorld(Path worldDirectory) { }
        @Override public void deleteArtifact(String artifactName) throws IOException {
            deleteAttempts++;
            if (deleteFailuresRemaining > 0) {
                deleteFailuresRemaining--;
                throw new IOException("transient delete failure");
            }
            deletedArtifacts.add(artifactName);
        }
        @Override public void markCommittedCleanupPending(String artifactName) { cleanupMarkers.add(artifactName); }
        @Override public void clearCommittedCleanupPending(String artifactName) { cleanupMarkers.remove(artifactName); }
        @Override public List<String> pendingCommittedCleanupArtifacts() throws IOException {
            if (failCleanupMarkerRead) throw new IOException("marker read failed");
            return List.copyOf(cleanupMarkers);
        }
    }

    private static final class FakeStore implements ConversionRuntimeStore {
        private final Optional<InstalledRuntime> current;
        FakeStore() { this(Optional.empty()); }
        FakeStore(Optional<InstalledRuntime> current) { this.current = current; }
        @Override public Optional<InstalledRuntime> current() { return current; }
        @Override public Optional<InstalledRuntime> previous() { return Optional.empty(); }
        @Override public Optional<InstalledRuntime> candidate() { return Optional.empty(); }
        @Override public void stageCandidate(Path artifact, ConversionRuntimeManifest manifest) { }
        @Override public void promoteCandidate() { }
        @Override public void rollbackToPrevious() { }
        @Override public void discardCandidate() { }
        @Override public Optional<Instant> lastUpdateCheck() { return current.isPresent() ? Optional.of(Instant.parse("2026-09-12T00:00:00Z")) : Optional.empty(); }
        @Override public void recordUpdateCheck(Instant instant) { }
    }
}
