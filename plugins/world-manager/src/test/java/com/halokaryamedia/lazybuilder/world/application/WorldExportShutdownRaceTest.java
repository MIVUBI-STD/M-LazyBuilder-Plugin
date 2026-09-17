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
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldExportShutdownRaceTest {
    @TempDir Path tempDir;

    @Test
    void abandonDuringAsyncCaptureReleasesLeaseAndCleansLateSnapshot() throws Exception {
        WorldRegistry registry = new WorldRegistry();
        WorldRecord world = new WorldRecord(
                WorldId.create(), "Build", "Build", WorldKind.FLAT, WorldLifecycle.ACTIVE);
        registry.register(world);

        WorldOperationCoordinator operations = new WorldOperationCoordinator();
        WorldRuntimeService runtimeService = new WorldRuntimeService(registry, new IdleRuntime(), operations);
        BlockingFileRepository files = new BlockingFileRepository(tempDir.resolve("late-snapshot"));
        EmptyRuntimeStore store = new EmptyRuntimeStore();
        ConverterAdapter converter = runtimeArtifact -> { throw new IOException("conversion must not run"); };
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
        WorldExportArtifactStore artifacts = (source, name, type) -> {
            throw new AssertionError("artifact packaging must not run");
        };
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
                world.id(), WorldExportService.NATIVE_SERVER_FORMAT, "shutdown-race");
        service.validateSnapshotSourceForAsyncCapture(task);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> capture = executor.submit(() -> service.captureSnapshotAfterValidation(task));
            assertTrue(files.captureStarted.await(2, TimeUnit.SECONDS), "snapshot copy did not start");

            service.abandon(task);
            assertFalse(operations.isBusy(world.id()), "abandon must release the world operation lease");

            files.allowCaptureToReturn.countDown();
            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> capture.get(2, TimeUnit.SECONDS)
            );
            assertInstanceOf(IllegalStateException.class, failure.getCause());
            assertTrue(files.workspaceDeleted.await(2, TimeUnit.SECONDS), "late snapshot workspace was not cleaned");
            assertFalse(Files.exists(files.snapshotPath), "late snapshot workspace survived abandon");
        } finally {
            files.allowCaptureToReturn.countDown();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    private static final class BlockingFileRepository implements WorldFileRepository {
        private final Path snapshotPath;
        private final CountDownLatch captureStarted = new CountDownLatch(1);
        private final CountDownLatch allowCaptureToReturn = new CountDownLatch(1);
        private final CountDownLatch workspaceDeleted = new CountDownLatch(1);

        private BlockingFileRepository(Path snapshotPath) {
            this.snapshotPath = snapshotPath;
        }

        @Override
        public Path stageCopy(WorldRecord source, UUID operationId, WorldCopyProfile profile) throws IOException {
            Files.createDirectories(snapshotPath);
            Files.writeString(snapshotPath.resolve("level.dat"), "snapshot");
            captureStarted.countDown();
            try {
                if (!allowCaptureToReturn.await(2, TimeUnit.SECONDS)) {
                    throw new IOException("test snapshot copy was not released");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("test snapshot copy interrupted", interrupted);
            }
            return snapshotPath;
        }

        @Override public Path stageDelete(WorldRecord world, UUID operationId) {
            throw new UnsupportedOperationException();
        }

        @Override public void publishStagedWorld(Path stagedWorld, String destinationFolder) {
            throw new UnsupportedOperationException();
        }

        @Override public void deleteWorld(WorldRecord world) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteWorkspace(Path workspace) throws IOException {
            if (Files.exists(workspace)) {
                try (var paths = Files.walk(workspace)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(path);
                    }
                }
            }
            workspaceDeleted.countDown();
        }
    }

    private static final class IdleRuntime implements WorldRuntimeGateway {
        @Override public void createNewWorld(WorldRecord world, BuildReadyPolicy policy) { }
        @Override public void rollbackCreatedWorld(WorldRecord world) { }
        @Override public boolean isLoaded(WorldRecord world) { return false; }
        @Override public boolean hasPlayers(WorldRecord world) { return false; }
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
