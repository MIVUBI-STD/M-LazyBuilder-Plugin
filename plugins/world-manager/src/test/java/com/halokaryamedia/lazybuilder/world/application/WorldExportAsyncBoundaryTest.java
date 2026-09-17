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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WorldExportAsyncBoundaryTest {
    @TempDir Path tempDir;

    @Test
    void validatedAsyncCaptureDoesNotReadRuntimeGateway() throws Exception {
        Path worlds = tempDir.resolve("worlds");
        Path source = worlds.resolve("Build");
        Files.createDirectories(source.resolve("region"));
        Files.writeString(source.resolve("level.dat"), "level");
        Files.writeString(source.resolve("region/r.0.0.mca"), "region");

        WorldRegistry registry = new WorldRegistry();
        WorldRecord world = new WorldRecord(
                WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE);
        registry.register(world);

        GuardedRuntime runtime = new GuardedRuntime();
        WorldOperationCoordinator operations = new WorldOperationCoordinator();
        WorldRuntimeService runtimeService = new WorldRuntimeService(registry, runtime, operations);
        LocalWorldFileRepository files = new LocalWorldFileRepository(worlds, tempDir.resolve("work"));
        LocalWorldExportArtifactStore artifacts = new LocalWorldExportArtifactStore(tempDir.resolve("exports"));
        EmptyRuntimeStore store = new EmptyRuntimeStore();
        ConverterAdapter converter = runtimeArtifact -> { throw new IOException("runtime probe must not run"); };
        ConversionReleaseSource releases = Optional::empty;
        ConversionUpdateService updates = new ConversionUpdateService(
                ConversionRuntimePolicy.defaults(),
                store,
                releases,
                (release, directory) -> { throw new IOException("runtime download must not run"); },
                converter,
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
                converter,
                new ConversionJobCoordinator()
        );

        WorldExportService.ExportTask task = service.prepare(
                world.id(), WorldExportService.NATIVE_SERVER_FORMAT, "async-boundary");
        service.validateSnapshotSourceForAsyncCapture(task);

        runtime.rejectRuntimeReads = true;
        assertDoesNotThrow(() -> service.captureSnapshotAfterValidation(task));
        runtime.rejectRuntimeReads = false;
        service.finish(task);

        assertFalse(operations.isBusy(world.id()));
    }

    private static final class GuardedRuntime implements WorldRuntimeGateway {
        private boolean rejectRuntimeReads;

        @Override public void createNewWorld(WorldRecord world, BuildReadyPolicy policy) { }
        @Override public void rollbackCreatedWorld(WorldRecord world) { }
        @Override public boolean isLoaded(WorldRecord world) {
            if (rejectRuntimeReads) throw new AssertionError("async capture touched runtime state");
            return false;
        }
        @Override public boolean hasPlayers(WorldRecord world) {
            if (rejectRuntimeReads) throw new AssertionError("async capture touched player state");
            return false;
        }
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
