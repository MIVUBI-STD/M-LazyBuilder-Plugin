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
