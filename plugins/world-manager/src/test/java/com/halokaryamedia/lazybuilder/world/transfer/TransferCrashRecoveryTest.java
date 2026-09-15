package com.halokaryamedia.lazybuilder.world.transfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferCrashRecoveryTest {
    @TempDir Path tempDir;

    @Test
    void removesOnlyTransferOwnedPartialUploads() throws Exception {
        Path transfer = Files.createDirectory(tempDir.resolve("transfer"));
        Path owned = Files.writeString(
                transfer.resolve(UUID.randomUUID() + ".upload.part"),
                "partial"
        );
        Path unrelated = Files.writeString(transfer.resolve("keep.upload.part"), "keep");
        Path ordinary = Files.writeString(transfer.resolve("notes.txt"), "keep");
        Path directory = Files.createDirectory(transfer.resolve(UUID.randomUUID() + ".upload.part"));

        int recovered = TransferCrashRecovery.recover(transfer);

        assertEquals(1, recovered);
        assertFalse(Files.exists(owned));
        assertTrue(Files.exists(unrelated));
        assertTrue(Files.exists(ordinary));
        assertTrue(Files.isDirectory(directory));
    }

    @Test
    void missingTransferRootIsSafeNoOp() throws Exception {
        assertEquals(0, TransferCrashRecovery.recover(tempDir.resolve("missing")));
    }

    @Test
    void ownershipPatternRequiresUuidAndExactSuffix() {
        String id = UUID.randomUUID().toString();
        assertTrue(TransferCrashRecovery.isOwnedInterruptedUpload(id + ".upload.part"));
        assertFalse(TransferCrashRecovery.isOwnedInterruptedUpload("not-a-uuid.upload.part"));
        assertFalse(TransferCrashRecovery.isOwnedInterruptedUpload(id + ".upload.part.bak"));
        assertFalse(TransferCrashRecovery.isOwnedInterruptedUpload(null));
    }
}
