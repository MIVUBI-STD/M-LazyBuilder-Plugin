package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.conversion.ConversionJobCoordinator;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionReleaseSource;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionRuntimeManifest;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionRuntimePolicy;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionRuntimeStore;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionUpdateService;
import com.halokaryamedia.lazybuilder.world.conversion.ConverterAdapter;
import com.halokaryamedia.lazybuilder.world.files.LocalWorldExportArtifactStore;
import com.halokaryamedia.lazybuilder.world.files.LocalWorldFileRepository;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldExportRuntimeFreeAreaTest {
    @TempDir Path tempDir;

    @Test
    void nativeAreaCompletesWithoutInstalledConversionRuntime() throws Exception {
        Path worlds = tempDir.resolve("worlds");
        Path source = worlds.resolve("Build");
        Files.createDirectories(source);
        Files.writeString(source.resolve("level.dat"), "level");

        WorldRegistry registry = new WorldRegistry();
        WorldRecord world = new WorldRecord(
                WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE);
        registry.register(world);

        WorldOperationCoordinator operations = new WorldOperationCoordinator();
        WorldRuntimeService runtimeService = new WorldRuntimeService(
                registry, new UnloadedRuntime(), operations);
        LocalWorldFileRepository files = new LocalWorldFileRepository(worlds, tempDir.resolve("work"));
        LocalWorldExportArtifactStore artifacts = new LocalWorldExportArtifactStore(tempDir.resolve("exports"));
        EmptyRuntimeStore store = new EmptyRuntimeStore();
        RuntimeFreeAdapter adapter = new RuntimeFreeAdapter();
        ConversionReleaseSource releases = Optional::empty;
        ConversionUpdateService updates = new ConversionUpdateService(
                ConversionRuntimePolicy.defaults(),
                store,
                releases,
                (release, directory) -> { throw new AssertionError("runtime download must not run"); },
                adapter,
                tempDir.resolve("downloads"),
                Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC)
        );

        WorldExportService service = new WorldExportService(
                registry,
                runtimeService,
                operations,
                files,
                artifacts,
                store,
                updates,
                adapter,
                new ConversionJobCoordinator()
        );

        WorldExportService.ExportTask task = service.prepareArea(
                world.id(),
                WorldExportService.NATIVE_SERVER_FORMAT,
                "native-area",
                WorldAreaSelection.ofCorners("minecraft:overworld", 0, 0, 15, 15),
                WorldExportOptions.legacyDefaults()
        );
        service.captureSnapshot(task);
        WorldExportService.ExportResult result = service.processSnapshot(task);
        service.finish(task);

        assertFalse(result.converted());
        assertTrue(Files.isRegularFile(result.artifact()));
        assertTrue(adapter.runtimeFreeCalled);
        assertFalse(adapter.externalRuntimeCalled);
        assertFalse(operations.isBusy(world.id()));
    }

    private static final class RuntimeFreeAdapter implements ConverterAdapter {
        private boolean runtimeFreeCalled;
        private boolean externalRuntimeCalled;

        @Override
        public ConverterProbe probe(Path runtimeArtifact) {
            throw new AssertionError("runtime probe must not run");
        }

        @Override
        public boolean canConvertWithoutRuntime(ConversionRequest request) {
            return request.keepOriginalNbt() && request.worldSettings() == null;
        }

        @Override
        public ConversionResult convertWithoutRuntime(ConversionRequest request) throws IOException {
            runtimeFreeCalled = true;
            Files.createDirectories(request.outputDirectory());
            Files.copy(request.inputDirectory().resolve("level.dat"), request.outputDirectory().resolve("level.dat"));
            return new ConversionResult("runtime-free");
        }

        @Override
        public ConversionResult convert(Path runtimeArtifact, ConversionRequest request) {
            externalRuntimeCalled = true;
            throw new AssertionError("external runtime conversion must not run");
        }
    }

    private static final class UnloadedRuntime implements WorldRuntimeGateway {
        @Override public void createNewWorld(WorldRecord world, BuildReadyPolicy policy) { }
        @Override public void rollbackCreatedWorld(WorldRecord world) { }
        @Override public boolean isLoaded(WorldRecord world) { return false; }
        @Override public void loadWorld(WorldRecord world) { }
        @Override public void unloadWorld(WorldRecord world) { }
        @Override public void teleportPlayerToSpawn(UUID playerId, WorldRecord world) { }
    }

    private static final class EmptyRuntimeStore implements ConversionRuntimeStore {
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
