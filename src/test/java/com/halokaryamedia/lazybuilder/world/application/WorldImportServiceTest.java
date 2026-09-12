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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldImportServiceTest {
    @TempDir Path tempDir;

    @Test
    void trustedNativeImportPublishesFreshUnloadedRecordWithoutConverter() throws Exception {
        WorldRegistry registry = new WorldRegistry();
        WorldRuntimeStateRegistry states = new WorldRuntimeStateRegistry();
        MemoryPersistence persistence = new MemoryPersistence();
        FakeFiles files = new FakeFiles(tempDir);
        FakeImports imports = new FakeImports();
        FakeStore runtimeStore = new FakeStore();
        ConverterAdapter converter = runtimeArtifact -> { throw new AssertionError("native import must not probe converter"); };
        ConversionUpdateService updates = new ConversionUpdateService(
                ConversionRuntimePolicy.defaults(), runtimeStore, Optional::empty,
                (release, directory) -> { throw new AssertionError("native import must not download runtime"); },
                converter, tempDir.resolve("downloads"),
                Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC)
        );
        WorldImportService service = new WorldImportService(
                registry, persistence, states, files, imports,
                runtimeStore, updates, converter, new ConversionJobCoordinator()
        );

        WorldImportService.ImportTask task = service.prepare("Build.zip", "ImportedBuild", "Imported Build");
        WorldRecord imported = service.executeFilePhase(task);
        service.finish(task);

        assertTrue(task.completed());
        assertEquals("ImportedBuild", imported.folderName());
        assertFalse(imported.autoLoad());
        assertEquals(WorldRuntimeState.UNLOADED, states.get(imported.id()));
        assertEquals(1, persistence.saved.size());
        assertTrue(files.published);
    }

    private static final class MemoryPersistence implements WorldRegistryPersistence {
        List<WorldRecord> saved = List.of();
        @Override public List<WorldRecord> load() { return saved; }
        @Override public void save(List<WorldRecord> worlds) { saved = List.copyOf(worlds); }
    }

    private static final class FakeFiles implements WorldFileRepository {
        private final Path root;
        boolean published;
        FakeFiles(Path root) { this.root = root; }
        @Override public Path stageCopy(WorldRecord source, UUID operationId, WorldCopyProfile profile) { throw new UnsupportedOperationException(); }
        @Override public Path stageDelete(WorldRecord world, UUID operationId) { throw new UnsupportedOperationException(); }
        @Override public Path reserveWorkspace(UUID operationId) { return root.resolve(operationId.toString()); }
        @Override public void publishStagedWorld(Path stagedWorld, String destinationFolder) throws java.io.IOException {
            Files.move(stagedWorld, root.resolve(destinationFolder));
            published = true;
        }
        @Override public void deleteWorld(WorldRecord world) throws java.io.IOException {
            Path path = root.resolve(world.folderName());
            if (Files.exists(path)) try (var walk = Files.walk(path)) {
                for (Path item : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(item);
            }
        }
        @Override public void deleteWorkspace(Path workspace) throws java.io.IOException {
            if (Files.notExists(workspace)) return;
            try (var walk = Files.walk(workspace)) {
                for (Path item : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(item);
            }
        }
    }

    private static final class FakeImports implements WorldImportArtifactStore {
        @Override public StagedImport stageArchive(String artifactName, Path workspace) throws java.io.IOException {
            Files.createDirectory(workspace);
            Files.writeString(workspace.resolve("level.dat"), "level");
            return new StagedImport(workspace, DetectedEdition.JAVA, WorldImportService.TARGET_FORMAT);
        }
        @Override public void sanitizeConvertedWorld(Path worldDirectory) { }
    }

    private static final class FakeStore implements ConversionRuntimeStore {
        @Override public Optional<InstalledRuntime> current() { return Optional.empty(); }
        @Override public Optional<InstalledRuntime> previous() { return Optional.empty(); }
        @Override public Optional<InstalledRuntime> candidate() { return Optional.empty(); }
        @Override public void stageCandidate(Path artifact, ConversionRuntimeManifest manifest) { }
        @Override public void promoteCandidate() { }
        @Override public void rollbackToPrevious() { }
        @Override public void discardCandidate() { }
        @Override public Optional<Instant> lastUpdateCheck() { return Optional.empty(); }
        @Override public void recordUpdateCheck(Instant instant) { }
    }
}
