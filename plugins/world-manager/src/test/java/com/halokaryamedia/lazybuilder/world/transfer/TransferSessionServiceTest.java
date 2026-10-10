package com.halokaryamedia.lazybuilder.world.transfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferSessionServiceTest {
    @TempDir Path tempDir;

    @Test
    void uploadRequiresOrderedChunksAndPublishesOnlyAfterChecksumMatches() throws Exception {
        Path imports = tempDir.resolve("imports");
        Path exports = tempDir.resolve("exports");
        Path transfer = tempDir.resolve("transfer");
        TransferSessionService service = new TransferSessionService(
                imports, exports, transfer, new TransferPolicy(4, 1024, 1, 1)
        );
        UUID owner = UUID.randomUUID();
        byte[] content = "abcdefghij".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String digest = sha256(content);

        TransferDescriptor descriptor = service.beginUpload(owner, "world.zip", content.length, digest);
        assertEquals(3, descriptor.totalChunks());
        assertThrows(IllegalArgumentException.class,
                () -> service.acceptUploadChunk(owner, descriptor.sessionId(), 1, new byte[]{1}));

        service.acceptUploadChunk(owner, descriptor.sessionId(), 0, new byte[]{'a','b','c','d'});
        service.acceptUploadChunk(owner, descriptor.sessionId(), 1, new byte[]{'e','f','g','h'});
        service.acceptUploadChunk(owner, descriptor.sessionId(), 2, new byte[]{'i','j'});
        Path published = service.finishUpload(owner, descriptor.sessionId());

        assertEquals(imports.resolve("world.zip").toAbsolutePath().normalize(), published);
        assertArrayEquals(content, Files.readAllBytes(published));
        assertTrue(service.ownsCompletedUpload(owner, "world.zip"));
        assertFalse(service.ownsCompletedUpload(UUID.randomUUID(), "world.zip"));
        service.releaseCompletedUpload(owner, "world.zip");
        assertFalse(service.ownsCompletedUpload(owner, "world.zip"));
        assertEquals(0, service.activeUploads());
        assertFalse(Files.exists(transfer.resolve(descriptor.sessionId() + ".upload.part")));
    }

    @Test
    void checksumFailureDeletesPartialAndDoesNotPublish() throws Exception {
        TransferSessionService service = new TransferSessionService(
                tempDir.resolve("imports"), tempDir.resolve("exports"), tempDir.resolve("transfer"),
                new TransferPolicy(4, 1024, 1, 1)
        );
        UUID owner = UUID.randomUUID();
        TransferDescriptor descriptor = service.beginUpload(owner, "bad.mcworld", 4, "0".repeat(64));
        service.acceptUploadChunk(owner, descriptor.sessionId(), 0, new byte[]{1,2,3,4});

        assertThrows(java.io.IOException.class, () -> service.finishUpload(owner, descriptor.sessionId()));
        assertEquals(0, service.activeUploads());
        assertFalse(Files.exists(tempDir.resolve("imports/bad.mcworld")));
        assertFalse(service.ownsCompletedUpload(owner, "bad.mcworld"));
    }

    @Test
    void unclaimedCompletedUploadIsDiscardedByDisconnectCleanup() throws Exception {
        Path imports = tempDir.resolve("imports");
        TransferSessionService service = new TransferSessionService(
                imports, tempDir.resolve("exports"), tempDir.resolve("transfer"),
                new TransferPolicy(4, 1024, 1, 1)
        );
        UUID owner = UUID.randomUUID();
        byte[] content = new byte[]{1,2,3,4};
        TransferDescriptor descriptor = service.beginUpload(owner, "orphan.zip", content.length, sha256(content));
        service.acceptUploadChunk(owner, descriptor.sessionId(), 0, content);
        service.finishUpload(owner, descriptor.sessionId());

        assertTrue(Files.exists(imports.resolve("orphan.zip")));
        service.abortAllForOwner(owner);

        assertFalse(Files.exists(imports.resolve("orphan.zip")));
        assertFalse(service.ownsCompletedUpload(owner, "orphan.zip"));
    }

    @Test
    void claimedCompletedUploadSurvivesTransferDisconnectUntilReviewReleasesIt() throws Exception {
        Path imports = tempDir.resolve("imports");
        TransferSessionService service = new TransferSessionService(
                imports, tempDir.resolve("exports"), tempDir.resolve("transfer"),
                new TransferPolicy(4, 1024, 1, 1)
        );
        UUID owner = UUID.randomUUID();
        byte[] content = new byte[]{5,6,7,8};
        TransferDescriptor descriptor = service.beginUpload(owner, "review.zip", content.length, sha256(content));
        service.acceptUploadChunk(owner, descriptor.sessionId(), 0, content);
        service.finishUpload(owner, descriptor.sessionId());

        assertTrue(service.claimCompletedUpload(owner, "review.zip"));
        assertFalse(service.claimCompletedUpload(owner, "review.zip"));
        service.abortAllForOwner(owner);

        assertTrue(Files.exists(imports.resolve("review.zip")));
        assertTrue(service.ownsCompletedUpload(owner, "review.zip"));
        service.releaseCompletedUpload(owner, "review.zip");
        assertFalse(service.ownsCompletedUpload(owner, "review.zip"));
    }

    @Test
    void discardCompletedUploadRejectsForeignAndClaimedArtifacts() throws Exception {
        Path imports = tempDir.resolve("imports");
        TransferSessionService service = new TransferSessionService(
                imports, tempDir.resolve("exports"), tempDir.resolve("transfer"),
                new TransferPolicy(4, 1024, 1, 1));
        UUID owner = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        byte[] content = new byte[]{10,11,12,13};

        TransferDescriptor first = service.beginUpload(owner, "owned.zip", content.length, sha256(content));
        service.acceptUploadChunk(owner, first.sessionId(), 0, content);
        service.finishUpload(owner, first.sessionId());

        assertFalse(service.discardUnclaimedCompletedUpload(foreign, "owned.zip"));
        assertTrue(Files.isRegularFile(imports.resolve("owned.zip")));
        assertTrue(service.claimCompletedUpload(owner, "owned.zip"));
        assertFalse(service.discardUnclaimedCompletedUpload(owner, "owned.zip"),
                "active review or imported task must retain ownership");
        assertTrue(Files.isRegularFile(imports.resolve("owned.zip")));
        assertThrows(IllegalArgumentException.class,
                () -> service.discardUnclaimedCompletedUpload(owner, "../owned.zip"));
    }

    @Test
    void discardUnclaimedCompletedUploadIsIdempotentAndRemovesItsOwnedFile() throws Exception {
        Path imports = tempDir.resolve("imports");
        TransferSessionService service = new TransferSessionService(
                imports, tempDir.resolve("exports"), tempDir.resolve("transfer"),
                new TransferPolicy(4, 1024, 1, 1));
        UUID owner = UUID.randomUUID();
        byte[] content = new byte[]{1,2,3,4};
        TransferDescriptor upload = service.beginUpload(owner, "unused.mcworld", content.length, sha256(content));
        service.acceptUploadChunk(owner, upload.sessionId(), 0, content);
        service.finishUpload(owner, upload.sessionId());

        assertTrue(service.discardUnclaimedCompletedUpload(owner, "unused.mcworld"));
        assertFalse(Files.exists(imports.resolve("unused.mcworld")));
        assertFalse(service.ownsCompletedUpload(owner, "unused.mcworld"));
        assertFalse(service.discardUnclaimedCompletedUpload(owner, "unused.mcworld"));
    }

    @Test
    void failedImportClaimReturnsToOriginalOwnerForRetry() throws Exception {
        Path imports = tempDir.resolve("imports");
        TransferSessionService service = new TransferSessionService(
                imports, tempDir.resolve("exports"), tempDir.resolve("transfer"),
                new TransferPolicy(4, 1024, 1, 1));
        UUID owner = UUID.randomUUID();
        byte[] bytes = {1, 2, 3, 4};
        TransferDescriptor descriptor = service.beginUpload(owner, "retry.zip", bytes.length, sha256(bytes));
        service.acceptUploadChunk(owner, descriptor.sessionId(), 0, bytes);
        service.finishUpload(owner, descriptor.sessionId());

        assertTrue(service.claimCompletedUpload(owner, "retry.zip"));
        assertFalse(service.discardUnclaimedCompletedUpload(owner, "retry.zip"));
        assertFalse(service.returnCompletedUploadToOwner(UUID.randomUUID(), "retry.zip"));
        assertTrue(service.returnCompletedUploadToOwner(owner, "retry.zip"));
        assertFalse(service.returnCompletedUploadToOwner(owner, "retry.zip"));
        assertTrue(Files.exists(imports.resolve("retry.zip")));
        assertTrue(service.claimCompletedUpload(owner, "retry.zip"));
        assertFalse(service.discardUnclaimedCompletedUpload(owner, "retry.zip"));
    }

    @Test
    void admittedImportSurvivesTransferRestartWithoutBecomingDiscardable() throws Exception {
        Path imports = tempDir.resolve("imports");
        Path exports = tempDir.resolve("exports");
        Path transfers = tempDir.resolve("transfer");
        TransferPolicy policy = new TransferPolicy(4, 1024, 1, 1);
        TransferSessionService service = new TransferSessionService(imports, exports, transfers, policy);
        UUID owner = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        byte[] body = {1, 2, 3, 4};
        TransferDescriptor upload = service.beginUpload(owner, "admitted.zip", body.length, sha256(body));
        service.acceptUploadChunk(owner, upload.sessionId(), 0, body);
        service.finishUpload(owner, upload.sessionId());
        assertTrue(service.claimCompletedUploadForImport(owner, "admitted.zip", requestId, "WorldOne", "World One"));

        TransferSessionService recovered = new TransferSessionService(imports, exports, transfers, policy);
        assertFalse(recovered.discardUnclaimedCompletedUpload(owner, "admitted.zip"));
        assertThrows(IllegalStateException.class, () ->
                recovered.claimCompletedUploadForImport(owner, "admitted.zip", UUID.randomUUID(), "WorldOne", "World One"));
        assertThrows(IllegalStateException.class, () ->
                recovered.claimCompletedUploadForImport(owner, "admitted.zip", requestId, "Changed", "World One"));
        assertTrue(recovered.claimCompletedUploadForImport(owner, "admitted.zip", requestId, "WorldOne", "World One"),
                "the original request may resume only after ownership recovery");
        assertTrue(Files.isRegularFile(imports.resolve("admitted.zip")));
        recovered.abortAllForOwner(owner);
        assertTrue(Files.isRegularFile(imports.resolve("admitted.zip")));
    }

    @Test
    void rejectedTaskAdmissionCanReleaseDurableOwnershipBeforeRetry() throws Exception {
        Path imports = tempDir.resolve("imports");
        Path exports = tempDir.resolve("exports");
        Path transfers = tempDir.resolve("transfer");
        TransferPolicy policy = new TransferPolicy(4, 1024, 1, 1);
        TransferSessionService service = new TransferSessionService(imports, exports, transfers, policy);
        UUID owner = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        byte[] body = {1, 2, 3, 4};
        TransferDescriptor upload = service.beginUpload(owner, "rejected.zip", body.length, sha256(body));
        service.acceptUploadChunk(owner, upload.sessionId(), 0, body);
        service.finishUpload(owner, upload.sessionId());
        assertTrue(service.claimCompletedUploadForImport(owner, "rejected.zip", requestId, "WorldTwo", "World Two"));
        assertTrue(service.cancelUnacceptedImport(owner, "rejected.zip", requestId));

        TransferSessionService recovered = new TransferSessionService(imports, exports, transfers, policy);
        assertTrue(recovered.claimCompletedUploadForImport(owner, "rejected.zip",
                UUID.randomUUID(), "WorldTwo", "World Two"));
    }

    @Test
    void downloadIsExplicitSequentialAndBoundToOwner() throws Exception {
        Path exports = tempDir.resolve("exports");
        Files.createDirectories(exports);
        byte[] content = "download-data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(exports.resolve("Build.zip"), content);
        TransferSessionService service = new TransferSessionService(
                tempDir.resolve("imports"), exports, tempDir.resolve("transfer"),
                new TransferPolicy(5, 1024, 1, 1)
        );
        UUID owner = UUID.randomUUID();
        TransferDescriptor descriptor = service.beginDownload(owner, "Build.zip");
        assertEquals(3, descriptor.totalChunks());
        assertEquals(sha256(content), descriptor.sha256());
        assertThrows(IllegalArgumentException.class,
                () -> service.readDownloadChunk(UUID.randomUUID(), descriptor.sessionId(), 0));

        TransferSessionService.DownloadChunk first = service.readDownloadChunk(owner, descriptor.sessionId(), 0);
        TransferSessionService.DownloadChunk second = service.readDownloadChunk(owner, descriptor.sessionId(), 1);
        TransferSessionService.DownloadChunk third = service.readDownloadChunk(owner, descriptor.sessionId(), 2);
        assertArrayEquals("downl".getBytes(java.nio.charset.StandardCharsets.UTF_8), first.bytes());
        assertArrayEquals("oad-d".getBytes(java.nio.charset.StandardCharsets.UTF_8), second.bytes());
        assertArrayEquals("ata".getBytes(java.nio.charset.StandardCharsets.UTF_8), third.bytes());
        assertTrue(third.last());
        service.finishDownload(owner, descriptor.sessionId());
        assertEquals(0, service.activeDownloads());
    }

    @Test
    void limitsAndSafeNamesAreEnforced() throws Exception {
        TransferSessionService service = new TransferSessionService(
                tempDir.resolve("imports"), tempDir.resolve("exports"), tempDir.resolve("transfer"),
                new TransferPolicy(4, 8, 1, 1)
        );
        UUID owner = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> service.beginUpload(owner, "../world.zip", 4, "0".repeat(64)));
        assertThrows(IllegalArgumentException.class,
                () -> service.beginUpload(owner, "world.exe", 4, "0".repeat(64)));
        assertThrows(IllegalArgumentException.class,
                () -> service.beginUpload(owner, "world.zip", 9, "0".repeat(64)));

        TransferDescriptor descriptor = service.beginUpload(owner, "world.zip", 4, sha256(new byte[]{1,2,3,4}));
        assertThrows(IllegalStateException.class,
                () -> service.beginUpload(owner, "other.zip", 4, "0".repeat(64)));
        service.abortUpload(owner, descriptor.sessionId());
        assertEquals(0, service.activeUploads());
    }

    @Test
    void idleUploadIsReclaimedOnNextOwnerRequestWithoutPolling() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-12T00:00:00Z"));
        Path transfer = tempDir.resolve("transfer");
        TransferSessionService service = new TransferSessionService(
                tempDir.resolve("imports"), tempDir.resolve("exports"), transfer,
                new TransferPolicy(4, 1024, 1, 1, Duration.ofSeconds(30)),
                clock
        );
        UUID owner = UUID.randomUUID();
        TransferDescriptor stale = service.beginUpload(owner, "stale.zip", 4, sha256(new byte[]{1,2,3,4}));
        service.acceptUploadChunk(owner, stale.sessionId(), 0, new byte[]{1,2,3,4});
        assertEquals(1, service.activeUploads());

        clock.advance(Duration.ofSeconds(31));
        TransferDescriptor replacement = service.beginUpload(
                owner, "replacement.zip", 4, sha256(new byte[]{5,6,7,8}));

        assertEquals(1, service.activeUploads());
        assertFalse(Files.exists(transfer.resolve(stale.sessionId() + ".upload.part")));
        assertThrows(IllegalArgumentException.class,
                () -> service.finishUpload(owner, stale.sessionId()));
        service.abortUpload(owner, replacement.sessionId());
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
