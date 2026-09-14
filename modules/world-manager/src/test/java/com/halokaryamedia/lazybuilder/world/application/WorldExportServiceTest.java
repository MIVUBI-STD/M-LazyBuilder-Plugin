package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.conversion.ConversionJobCoordinator;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionReleaseSource;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionRuntimeManifest;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionRuntimePolicy;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionRuntimeStore;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionUpdateService;
import com.halokaryamedia.lazybuilder.world.conversion.ConverterAdapter;
import com.halokaryamedia.lazybuilder.world.files.ExportArtifactType;
import com.halokaryamedia.lazybuilder.world.files.WorldCopyProfile;
import com.halokaryamedia.lazybuilder.world.files.WorldExportArtifactStore;
import com.halokaryamedia.lazybuilder.world.files.WorldFileRepository;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldExportServiceTest {
    @TempDir Path tempDir;

    @Test
    void nativeExportRestoresLoadedWorldImmediatelyAfterSnapshot() throws Exception {
        WorldRegistry registry = new WorldRegistry();
        WorldRecord world = new WorldRecord(WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE);
        registry.register(world);
        FakeRuntime runtime = new FakeRuntime(true);
        WorldOperationCoordinator operations = new WorldOperationCoordinator();
        WorldRuntimeService runtimeService = new WorldRuntimeService(registry, runtime, operations);
        FakeFiles files = new FakeFiles(tempDir);
        FakeArtifacts artifacts = new FakeArtifacts(tempDir);
        FakeStore store = new FakeStore();
        ConverterAdapter converter = runtimeArtifact -> { throw new IOException("must not probe"); };
        ConversionReleaseSource source = () -> Optional.empty();
        ConversionUpdateService updates = new ConversionUpdateService(
                ConversionRuntimePolicy.defaults(), store, source,
                (release, directory) -> { throw new IOException("must not download"); },
                converter, tempDir.resolve("downloads"),
                Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC)
        );
        WorldExportService service = new WorldExportService(
                registry, runtimeService, operations, files, artifacts,
                store, updates, converter, new ConversionJobCoordinator()
        );

        WorldExportService.ExportTask task = service.prepare(
                world.id(), WorldExportService.NATIVE_SERVER_FORMAT, "Build-export");
        assertFalse(runtime.loaded);

        service.captureSnapshot(task);
        assertFalse(runtime.loaded);

        service.resumeSourceAfterSnapshot(task);
        assertTrue(runtime.loaded);
        assertTrue(task.sourceRestored());
        assertEquals(1, runtime.unloadCount);
        assertEquals(1, runtime.loadCount);

        WorldExportService.ExportResult result = service.processSnapshot(task);
        assertTrue(runtime.loaded);
        service.finish(task);

        assertFalse(result.converted());
        assertEquals(ExportArtifactType.JAVA_ZIP, artifacts.lastType);
        assertTrue(task.completed());
        assertEquals(1, runtime.loadCount);
        assertFalse(operations.isBusy(world.id()));
        assertEquals(WorldCopyProfile.SNAPSHOT, files.lastProfile);
    }

    private static final class FakeRuntime implements WorldRuntimeGateway {
        private boolean loaded;
        int loadCount;
        int unloadCount;
        FakeRuntime(boolean loaded) { this.loaded = loaded; }
        @Override public void createNewWorld(WorldRecord world, BuildReadyPolicy policy) {}
        @Override public void rollbackCreatedWorld(WorldRecord world) {}
        @Override public boolean isLoaded(WorldRecord world) { return loaded; }
        @Override public void loadWorld(WorldRecord world) { loaded = true; loadCount++; }
        @Override public void unloadWorld(WorldRecord world) { loaded = false; unloadCount++; }
        @Override public void teleportPlayerToSpawn(UUID playerId, WorldRecord world) {}
    }

    private static final class FakeFiles implements WorldFileRepository {
        private final Path root;
        private WorldCopyProfile lastProfile;
        FakeFiles(Path root) { this.root = root; }
        @Override public Path stageCopy(WorldRecord source, UUID operationId, WorldCopyProfile profile) throws IOException {
            lastProfile = profile;
            Path path = root.resolve(operationId.toString());
            Files.createDirectory(path);
            Files.writeString(path.resolve("level.dat"), "data");
            return path;
        }
        @Override public Path stageDelete(WorldRecord world, UUID operationId) { return root.resolve(operationId.toString()); }
        @Override public Path reserveWorkspace(UUID operationId) { return root.resolve(operationId.toString()); }
        @Override public void publishStagedWorld(Path stagedWorld, String destinationFolder) {}
        @Override public void deleteWorld(WorldRecord world) {}
        @Override public void deleteWorkspace(Path workspace) throws IOException {
            if (Files.notExists(workspace)) return;
            try (var paths = Files.walk(workspace)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static final class FakeArtifacts implements WorldExportArtifactStore {
        private final Path root;
        private ExportArtifactType lastType;
        FakeArtifacts(Path root) { this.root = root; }
        @Override public Path packageDirectory(Path sourceDirectory, String artifactName, ExportArtifactType type) throws IOException {
            lastType = type;
            Path artifact = root.resolve(artifactName + type.extension());
            Files.writeString(artifact, "artifact");
            return artifact;
        }
    }

    private static final class FakeStore implements ConversionRuntimeStore {
        @Override public Optional<InstalledRuntime> current() { return Optional.empty(); }
        @Override public Optional<InstalledRuntime> previous() { return Optional.empty(); }
        @Override public Optional<InstalledRuntime> candidate() { return Optional.empty(); }
        @Override public void stageCandidate(Path artifact, ConversionRuntimeManifest manifest) {}
        @Override public void promoteCandidate() {}
        @Override public void rollbackToPrevious() {}
        @Override public void discardCandidate() {}
        @Override public Optional<Instant> lastUpdateCheck() { return Optional.empty(); }
        @Override public void recordUpdateCheck(Instant instant) {}
    }
}
