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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldExportServiceTest {
    @TempDir Path tempDir;

    @Test
    void nativeExportRestoresLoadedWorldImmediatelyAfterSnapshot() throws Exception {
        Fixture fixture = fixture(true);

        WorldExportService.ExportTask task = fixture.service.prepare(
                fixture.world.id(), WorldExportService.NATIVE_SERVER_FORMAT, "Build-export");
        assertFalse(fixture.runtime.loaded);
        assertEquals(WorldOperationType.EXPORT, fixture.operations.activeOperation(fixture.world.id()));

        fixture.service.captureSnapshot(task);
        assertFalse(fixture.runtime.loaded);
        assertEquals(1, fixture.files.stageCopyCount);

        fixture.service.resumeSourceAfterSnapshot(task);
        assertTrue(fixture.runtime.loaded);
        assertTrue(task.sourceRestored());
        assertEquals(1, fixture.runtime.unloadCount);
        assertEquals(1, fixture.runtime.loadCount);

        WorldExportService.ExportResult result = fixture.service.processSnapshot(task);
        assertTrue(fixture.runtime.loaded);
        fixture.service.finish(task);

        assertFalse(result.converted());
        assertEquals(ExportArtifactType.JAVA_ZIP, fixture.artifacts.lastType);
        assertTrue(task.completed());
        assertEquals(1, fixture.runtime.loadCount);
        assertFalse(fixture.operations.isBusy(fixture.world.id()));
        assertEquals(WorldCopyProfile.SNAPSHOT, fixture.files.lastProfile);
    }

    @Test
    void liveSnapshotKeepsBuildersInLoadedWorldAndRestoresAutosave() throws Exception {
        Fixture fixture = fixture(true, true, false, false);

        WorldExportService.ExportTask task = fixture.service.prepare(
                fixture.world.id(), WorldExportService.NATIVE_SERVER_FORMAT, "Build-export");

        assertTrue(task.liveSnapshot());
        assertTrue(fixture.runtime.loaded);
        assertFalse(fixture.runtime.autoSave);
        assertEquals(1, fixture.runtime.beginLiveSnapshotCount);
        assertEquals(0, fixture.runtime.unloadCount);

        fixture.service.captureSnapshot(task);
        assertTrue(fixture.runtime.loaded);
        fixture.service.resumeSourceAfterSnapshot(task);

        assertTrue(fixture.runtime.loaded);
        assertTrue(fixture.runtime.autoSave);
        assertTrue(task.sourceRestored());
        assertEquals(1, fixture.runtime.endLiveSnapshotCount);
        assertEquals(0, fixture.runtime.loadCount);
        assertEquals(0, fixture.runtime.unloadCount);

        fixture.service.processSnapshot(task);
        fixture.service.finish(task);
        assertFalse(fixture.operations.isBusy(fixture.world.id()));
    }

    @Test
    void liveSnapshotCopyFailureRestoresAutosaveDuringFinish() {
        Fixture fixture = fixture(true, true, false, true);
        WorldExportService.ExportTask task = fixture.service.prepare(
                fixture.world.id(), WorldExportService.NATIVE_SERVER_FORMAT, "Build-export");

        assertFalse(fixture.runtime.autoSave);
        assertThrows(IllegalStateException.class, () -> fixture.service.captureSnapshot(task));
        assertFalse(fixture.runtime.autoSave);

        fixture.service.finish(task);

        assertTrue(fixture.runtime.loaded);
        assertTrue(fixture.runtime.autoSave);
        assertEquals(1, fixture.runtime.beginLiveSnapshotCount);
        assertEquals(1, fixture.runtime.endLiveSnapshotCount);
        assertFalse(fixture.operations.isBusy(fixture.world.id()));
    }

    @Test
    void exportOnlySettingsSerializeChunkerWorldAndCleanupSettings() throws Exception {
        WorldExportOptions options = new WorldExportOptions(
                WorldGameMode.CREATIVE,
                WorldDifficulty.HARD,
                Map.of("keepinventory", "true", "randomTickSpeed", "3"),
                true
        );

        Path worldSettings = WorldExportService.writeWorldSettings(options);
        Path converterSettings = WorldExportService.writeConverterSettings();
        try {
            String worldJson = Files.readString(worldSettings);
            assertTrue(worldJson.contains("\"GameType\":1"));
            assertTrue(worldJson.contains("\"Difficulty\":3"));
            assertTrue(worldJson.contains("\"keepinventory\":true"));
            assertTrue(worldJson.contains("\"randomTickSpeed\":3"));
            assertEquals("{\"discardEmptyChunks\":true}", Files.readString(converterSettings));
        } finally {
            Files.deleteIfExists(worldSettings);
            Files.deleteIfExists(converterSettings);
        }
    }

    @Test
    void workspaceDefaultsPreserveChunksWithoutInventingWorldOverrides() {
        WorldExportOptions options = WorldExportOptions.workspaceDefaults();
        assertFalse(options.discardEmptyChunks());
        assertFalse(options.requiresConverterPass());
        assertFalse(options.hasWorldOverrides());

        WorldExportOptions legacy = WorldExportOptions.legacyDefaults();
        assertFalse(legacy.discardEmptyChunks());
        assertFalse(legacy.requiresConverterPass());
    }

    @Test
    void exportOptionsRejectMalformedGameRuleKeysAndValues() {
        assertThrows(IllegalArgumentException.class, () -> new WorldExportOptions(
                null, null, Map.of("bad rule", "true"), true));
        assertThrows(IllegalArgumentException.class, () -> new WorldExportOptions(
                null, null, Map.of("keepinventory", "\n"), true));
    }

    @Test
    void externallyReloadedWorldIsRejectedBeforeExportSnapshotCopy() {
        Fixture fixture = fixture(true);
        WorldExportService.ExportTask task = fixture.service.prepare(
                fixture.world.id(), WorldExportService.NATIVE_SERVER_FORMAT, "Build-export");
        fixture.runtime.loaded = true;

        assertThrows(IllegalStateException.class, () -> fixture.service.captureSnapshot(task));
        assertEquals(0, fixture.files.stageCopyCount);

        fixture.runtime.loaded = false;
        fixture.service.finish(task);
        assertTrue(fixture.runtime.loaded);
        assertFalse(fixture.operations.isBusy(fixture.world.id()));
    }

    @Test
    void resumeAfterSnapshotDoesNotReloadSourceThatBecameArchived() throws Exception {
        Fixture fixture = fixture(true);
        WorldExportService.ExportTask task = fixture.service.prepare(
                fixture.world.id(), WorldExportService.NATIVE_SERVER_FORMAT, "Build-export");
        fixture.service.captureSnapshot(task);

        fixture.registry.updateMetadata(fixture.world.withLifecycle(WorldLifecycle.ARCHIVED));
        fixture.service.resumeSourceAfterSnapshot(task);

        assertFalse(fixture.runtime.loaded);
        assertEquals(0, fixture.runtime.loadCount);
        assertFalse(task.sourceRestored());
        fixture.service.finish(task);
        assertFalse(fixture.runtime.loaded);
        assertFalse(fixture.operations.isBusy(fixture.world.id()));
    }

    @Test
    void finishDoesNotReloadSourceThatIsNoLongerManaged() throws Exception {
        Fixture fixture = fixture(true);
        WorldExportService.ExportTask task = fixture.service.prepare(
                fixture.world.id(), WorldExportService.NATIVE_SERVER_FORMAT, "Build-export");
        fixture.service.captureSnapshot(task);

        fixture.registry.remove(fixture.world.id());
        fixture.service.finish(task);

        assertFalse(fixture.runtime.loaded);
        assertEquals(0, fixture.runtime.loadCount);
        assertFalse(fixture.operations.isBusy(fixture.world.id()));
    }

    @Test
    void abandonPreparedExportRestoresOriginallyLoadedSourceAndReleasesLease() {
        Fixture fixture = fixture(true);
        WorldExportService.ExportTask task = fixture.service.prepare(
                fixture.world.id(), WorldExportService.NATIVE_SERVER_FORMAT, "Build-export");

        assertFalse(fixture.runtime.loaded);
        fixture.service.abandon(task);

        assertTrue(fixture.runtime.loaded);
        assertTrue(task.sourceRestored());
        assertEquals(1, fixture.runtime.loadCount);
        assertFalse(fixture.operations.isBusy(fixture.world.id()));
    }

    @Test
    void failedExportCanBeAbandonedWithoutLeavingSourceUnloaded() throws Exception {
        Fixture fixture = fixture(true, true);
        WorldExportService.ExportTask task = fixture.service.prepare(
                fixture.world.id(), WorldExportService.NATIVE_SERVER_FORMAT, "Build-export");
        fixture.service.captureSnapshot(task);

        assertThrows(IllegalStateException.class, () -> fixture.service.processSnapshot(task));
        assertFalse(fixture.runtime.loaded);
        assertTrue(fixture.operations.isBusy(fixture.world.id()));

        fixture.service.abandon(task);

        assertTrue(fixture.runtime.loaded);
        assertTrue(task.sourceRestored());
        assertEquals(1, fixture.runtime.loadCount);
        assertFalse(fixture.operations.isBusy(fixture.world.id()));
    }

    private Fixture fixture(boolean loaded) {
        return fixture(loaded, false);
    }

    private Fixture fixture(boolean loaded, boolean failPackage) {
        return fixture(loaded, false, failPackage, false);
    }

    private Fixture fixture(boolean loaded, boolean hasPlayers, boolean failPackage, boolean failStageCopy) {
        WorldRegistry registry = new WorldRegistry();
        WorldRecord world = new WorldRecord(WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE);
        registry.register(world);
        FakeRuntime runtime = new FakeRuntime(loaded);
        WorldOperationCoordinator operations = new WorldOperationCoordinator();
        WorldRuntimeService runtimeService = new WorldRuntimeService(
                registry, runtime, operations, ignored -> hasPlayers);
        FakeFiles files = new FakeFiles(tempDir, failStageCopy);
        FakeArtifacts artifacts = new FakeArtifacts(tempDir, failPackage);
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
        return new Fixture(registry, world, runtime, operations, files, artifacts, service);
    }

    private record Fixture(
            WorldRegistry registry,
            WorldRecord world,
            FakeRuntime runtime,
            WorldOperationCoordinator operations,
            FakeFiles files,
            FakeArtifacts artifacts,
            WorldExportService service
    ) { }

    private static final class FakeRuntime implements WorldRuntimeGateway {
        private boolean loaded;
        private boolean autoSave = true;
        int loadCount;
        int unloadCount;
        int beginLiveSnapshotCount;
        int endLiveSnapshotCount;
        FakeRuntime(boolean loaded) { this.loaded = loaded; }
        @Override public void createNewWorld(WorldRecord world, BuildReadyPolicy policy) {}
        @Override public void rollbackCreatedWorld(WorldRecord world) {}
        @Override public boolean isLoaded(WorldRecord world) { return loaded; }
        @Override public void loadWorld(WorldRecord world) { loaded = true; loadCount++; }
        @Override public void unloadWorld(WorldRecord world) { loaded = false; unloadCount++; }
        @Override public boolean beginLiveSnapshot(WorldRecord world) {
            beginLiveSnapshotCount++;
            boolean previous = autoSave;
            autoSave = false;
            return previous;
        }
        @Override public void endLiveSnapshot(WorldRecord world, boolean previousAutoSave) {
            endLiveSnapshotCount++;
            autoSave = previousAutoSave;
        }
        @Override public void teleportPlayerToSpawn(UUID playerId, WorldRecord world) {}
    }

    private static final class FakeFiles implements WorldFileRepository {
        private final Path root;
        private final boolean failStageCopy;
        private WorldCopyProfile lastProfile;
        private int stageCopyCount;
        FakeFiles(Path root, boolean failStageCopy) {
            this.root = root;
            this.failStageCopy = failStageCopy;
        }
        @Override public Path stageCopy(WorldRecord source, UUID operationId, WorldCopyProfile profile) throws IOException {
            stageCopyCount++;
            lastProfile = profile;
            if (failStageCopy) throw new IOException("snapshot copy failed");
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
        private final boolean failPackage;
        private ExportArtifactType lastType;
        FakeArtifacts(Path root, boolean failPackage) {
            this.root = root;
            this.failPackage = failPackage;
        }
        @Override public Path packageDirectory(Path sourceDirectory, String artifactName, ExportArtifactType type) throws IOException {
            lastType = type;
            if (failPackage) throw new IOException("export packaging failed");
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
