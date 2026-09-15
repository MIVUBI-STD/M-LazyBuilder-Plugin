package com.halokaryamedia.lazybuilder.world.transfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletedUploadOwnershipRecoveryTest {
    @TempDir Path tempDir;

    @Test
    void completedUploadOwnershipSurvivesServiceRecreationUntilReleased() throws Exception {
        Path imports = tempDir.resolve("imports");
        Path exports = tempDir.resolve("exports");
        Path transfer = tempDir.resolve("transfer");
        TransferPolicy policy = new TransferPolicy(4, 1024, 1, 1);
        UUID owner = UUID.randomUUID();
        byte[] content = new byte[]{1, 2, 3, 4};

        TransferSessionService first = new TransferSessionService(imports, exports, transfer, policy);
        TransferDescriptor upload = first.beginUpload(owner, "restart.zip", content.length, sha256(content));
        first.acceptUploadChunk(owner, upload.sessionId(), 0, content);
        first.finishUpload(owner, upload.sessionId());

        TransferSessionService restarted = new TransferSessionService(imports, exports, transfer, policy);
        assertTrue(restarted.ownsCompletedUpload(owner, "restart.zip"));
        assertTrue(restarted.claimCompletedUpload(owner, "restart.zip"));

        restarted.releaseCompletedUpload(owner, "restart.zip");
        TransferSessionService afterRelease = new TransferSessionService(imports, exports, transfer, policy);
        assertFalse(afterRelease.ownsCompletedUpload(owner, "restart.zip"));
        assertTrue(Files.exists(imports.resolve("restart.zip")),
                "releasing ownership must not itself delete a reviewed artifact");
    }

    @Test
    void orphanOwnershipMarkerIsRemovedInsteadOfRestored() throws Exception {
        Path imports = tempDir.resolve("imports");
        Path exports = tempDir.resolve("exports");
        Path transfer = tempDir.resolve("transfer");
        Path ownership = transfer.resolve(".completed-uploads");
        Files.createDirectories(ownership);

        String fileName = "missing.mcworld";
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(fileName.getBytes(StandardCharsets.UTF_8));
        Path marker = ownership.resolve(encoded + ".owner");
        UUID owner = UUID.randomUUID();
        Files.writeString(marker, owner.toString(), StandardCharsets.UTF_8);

        TransferSessionService restarted = new TransferSessionService(
                imports, exports, transfer, new TransferPolicy(4, 1024, 1, 1));

        assertFalse(restarted.ownsCompletedUpload(owner, fileName));
        assertFalse(Files.exists(marker));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
