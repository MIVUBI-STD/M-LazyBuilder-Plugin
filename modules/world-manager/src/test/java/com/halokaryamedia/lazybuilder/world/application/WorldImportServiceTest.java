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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        ConversionUpdateService updates = new ConversionUpdateService(
                ConversionRuntimePolicy.defaults(), runtimeStore, Optional::empty,
                (release, directory) -> { throw new AssertionError("native import must not download runtime"); },
                converter, tempDir.resolve("downloads"),
                Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC)
        );
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
    }

    @Test
    void failedConversionCleansEveryOwnedWorkspace() throws Exception {
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
        for (Path workspace : files.reserved) {
            assertFalse(Files.exists(workspace), "workspace should be cleaned: " + workspace);
        }
    }

    private static final class MemoryPersistence implements WorldRegistryPersistence {
        List<WorldRecord> saved = List.of();
        @Override public List<WorldRecord> load() { return saved; }
        @Override public void save(List<WorldRecord> worlds) { saved = List.copyOf(worlds); }
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

        private FakeImports(DetectedEdition edition, String trustedFormat) {
            this.edition = edition;
            this.trustedFormat = trustedFormat;
        }

        @Override public StagedImport stageArchive(String artifactName, Path workspace) throws IOException {
            Files.createDirectory(workspace);
            Files.writeString(workspace.resolve("level.dat"), "level");
            return new StagedImport(workspace, edition, trustedFormat);
        }
        @Override public void sanitizeConvertedWorld(Path worldDirectory) { }
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
