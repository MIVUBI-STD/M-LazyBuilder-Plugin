package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.world.transfer.TransferDescriptor;
import com.halokaryamedia.lazybuilder.world.transfer.TransferPolicy;
import com.halokaryamedia.lazybuilder.world.transfer.TransferSessionService;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

/**
 * Streams authenticated desktop uploads into the canonical World-Manager import inbox.
 *
 * <p>This adapter deliberately reuses {@link TransferSessionService}; it owns no second
 * transfer store, worker, or filesystem path.</p>
 */
public final class LocalControlImportUploadService {
    private static final UUID DESKTOP_OWNER = UUID.nameUUIDFromBytes(
            "lazybuilder-desktop-local-control".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    );

    private final TransferSessionService transfers;
    private final TransferPolicy policy;

    public LocalControlImportUploadService(TransferSessionService transfers, TransferPolicy policy) {
        this.transfers = Objects.requireNonNull(transfers, "transfers");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public UploadResult upload(String fileName, long totalBytes, String sha256, InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        if (totalBytes < 1 || totalBytes > policy.maxUploadBytes()) {
            throw new IllegalArgumentException("Upload size is outside configured limit");
        }

        TransferDescriptor descriptor = transfers.beginUpload(DESKTOP_OWNER, fileName, totalBytes, sha256);
        boolean completed = false;
        try {
            long remaining = totalBytes;
            for (int chunkIndex = 0; chunkIndex < descriptor.totalChunks(); chunkIndex++) {
                int expected = (int) Math.min(descriptor.chunkBytes(), remaining);
                byte[] chunk = input.readNBytes(expected);
                if (chunk.length != expected) throw new IOException("Upload body ended before declared Content-Length");
                transfers.acceptUploadChunk(DESKTOP_OWNER, descriptor.sessionId(), chunkIndex, chunk);
                remaining -= chunk.length;
            }
            if (remaining != 0 || input.read() != -1) {
                throw new IOException("Upload body does not match declared Content-Length");
            }
            var artifact = transfers.finishUpload(DESKTOP_OWNER, descriptor.sessionId());
            completed = true;
            return new UploadResult(artifact.getFileName().toString(), totalBytes);
        } finally {
            if (!completed) {
                try {
                    transfers.abortUpload(DESKTOP_OWNER, descriptor.sessionId());
                } catch (IOException | RuntimeException ignored) {
                    // Failed transfers may already have cleaned their session.
                }
            }
        }
    }

    public void stop() {
        try {
            transfers.abortAllForOwner(DESKTOP_OWNER);
        } catch (IOException ignored) {
            // Shutdown cleanup is best-effort; session temp files are already bounded and isolated.
        }
    }

    public record UploadResult(String fileName, long totalBytes) {}
}
