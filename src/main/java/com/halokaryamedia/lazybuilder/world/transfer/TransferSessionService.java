package com.halokaryamedia.lazybuilder.world.transfer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Event-driven chunk transfer owner for client uploads/downloads.
 *
 * <p>No polling loop, socket worker, or background cleanup thread is owned here.
 * Network adapters call these methods only in response to explicit protocol events.</p>
 */
public final class TransferSessionService {
    private final Path importsRoot;
    private final Path exportsRoot;
    private final Path tempRoot;
    private final TransferPolicy policy;
    private final Map<UUID, UploadSession> uploads = new LinkedHashMap<>();
    private final Map<UUID, DownloadSession> downloads = new LinkedHashMap<>();

    public TransferSessionService(Path importsRoot, Path exportsRoot, Path tempRoot, TransferPolicy policy) {
        this.importsRoot = ownedRoot(importsRoot, "importsRoot");
        this.exportsRoot = ownedRoot(exportsRoot, "exportsRoot");
        this.tempRoot = ownedRoot(tempRoot, "tempRoot");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public synchronized TransferDescriptor beginUpload(
            UUID ownerId,
            String fileName,
            long totalBytes,
            String sha256
    ) throws IOException {
        Objects.requireNonNull(ownerId, "ownerId");
        String safeName = validateImportFileName(fileName);
        String digest = validateSha256(sha256);
        if (totalBytes < 1 || totalBytes > policy.maxUploadBytes()) {
            throw new IllegalArgumentException("Upload size is outside configured limit");
        }
        long ownerUploads = uploads.values().stream().filter(session -> session.ownerId.equals(ownerId)).count();
        if (ownerUploads >= policy.maxConcurrentUploads()) {
            throw new IllegalStateException("Upload limit reached for client");
        }

        Files.createDirectories(importsRoot);
        Files.createDirectories(tempRoot);
        Path target = directChild(importsRoot, safeName);
        if (Files.exists(target)) throw new IOException("Import artifact already exists: " + safeName);

        UUID sessionId = UUID.randomUUID();
        Path partial = directChild(tempRoot, sessionId + ".upload.part");
        Files.deleteIfExists(partial);
        Files.createFile(partial);
        int chunks = chunkCount(totalBytes, policy.chunkBytes());
        UploadSession session = new UploadSession(
                sessionId, ownerId, safeName, target, partial, totalBytes, digest, policy.chunkBytes(), chunks
        );
        uploads.put(sessionId, session);
        return session.descriptor();
    }

    public synchronized UploadProgress acceptUploadChunk(
            UUID ownerId,
            UUID sessionId,
            int chunkIndex,
            byte[] bytes
    ) throws IOException {
        UploadSession session = requireUpload(ownerId, sessionId);
        Objects.requireNonNull(bytes, "bytes");
        if (chunkIndex != session.nextChunkIndex) {
            throw new IllegalArgumentException("Unexpected upload chunk index: " + chunkIndex);
        }
        if (bytes.length < 1 || bytes.length > session.chunkBytes) {
            throw new IllegalArgumentException("Upload chunk size exceeds protocol limit");
        }
        long nextTotal = session.receivedBytes + bytes.length;
        if (nextTotal > session.totalBytes) throw new IOException("Upload exceeded declared size");
        if (chunkIndex < session.totalChunks - 1 && bytes.length != session.chunkBytes) {
            throw new IllegalArgumentException("Non-final upload chunk must use full chunk size");
        }

        try (OutputStream out = Files.newOutputStream(session.partial,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            out.write(bytes);
        }
        session.receivedBytes = nextTotal;
        session.nextChunkIndex++;
        return new UploadProgress(session.receivedBytes, session.totalBytes, session.nextChunkIndex, session.totalChunks);
    }

    public synchronized Path finishUpload(UUID ownerId, UUID sessionId) throws IOException {
        UploadSession session = requireUpload(ownerId, sessionId);
        try {
            if (session.receivedBytes != session.totalBytes || session.nextChunkIndex != session.totalChunks) {
                throw new IOException("Upload is incomplete");
            }
            String actual = sha256(session.partial);
            if (!actual.equals(session.sha256)) throw new IOException("Upload checksum mismatch");
            if (Files.exists(session.target)) throw new IOException("Import artifact already exists: " + session.fileName);
            move(session.partial, session.target);
            uploads.remove(sessionId);
            return session.target;
        } catch (IOException | RuntimeException failure) {
            abortUploadInternal(sessionId);
            throw failure;
        }
    }

    public synchronized void abortUpload(UUID ownerId, UUID sessionId) throws IOException {
        requireUpload(ownerId, sessionId);
        abortUploadInternal(sessionId);
    }

    public synchronized TransferDescriptor beginDownload(UUID ownerId, String fileName) throws IOException {
        Objects.requireNonNull(ownerId, "ownerId");
        String safeName = validateTransferName(fileName);
        long ownerDownloads = downloads.values().stream().filter(session -> session.ownerId.equals(ownerId)).count();
        if (ownerDownloads >= policy.maxConcurrentDownloads()) {
            throw new IllegalStateException("Download limit reached for client");
        }
        Path artifact = directChild(exportsRoot, safeName);
        if (!Files.isRegularFile(artifact) || Files.isSymbolicLink(artifact)) {
            throw new IOException("Export artifact does not exist: " + safeName);
        }
        long size = Files.size(artifact);
        String digest = sha256(artifact);
        UUID sessionId = UUID.randomUUID();
        int chunks = chunkCount(size, policy.chunkBytes());
        DownloadSession session = new DownloadSession(
                sessionId, ownerId, safeName, artifact, size, digest, policy.chunkBytes(), chunks
        );
        downloads.put(sessionId, session);
        return session.descriptor();
    }

    public synchronized DownloadChunk readDownloadChunk(UUID ownerId, UUID sessionId, int chunkIndex) throws IOException {
        DownloadSession session = requireDownload(ownerId, sessionId);
        if (chunkIndex != session.nextChunkIndex) {
            throw new IllegalArgumentException("Unexpected download chunk index: " + chunkIndex);
        }
        if (chunkIndex >= session.totalChunks) throw new IllegalArgumentException("Download chunk index out of range");

        long offset = (long) chunkIndex * session.chunkBytes;
        int expected = (int) Math.min(session.chunkBytes, session.totalBytes - offset);
        byte[] data = new byte[expected];
        try (InputStream in = Files.newInputStream(session.artifact)) {
            in.skipNBytes(offset);
            int read = 0;
            while (read < expected) {
                int count = in.read(data, read, expected - read);
                if (count < 0) throw new IOException("Export artifact changed during download");
                read += count;
            }
        }
        session.nextChunkIndex++;
        boolean last = session.nextChunkIndex == session.totalChunks;
        return new DownloadChunk(chunkIndex, data, last);
    }

    public synchronized void finishDownload(UUID ownerId, UUID sessionId) {
        DownloadSession session = requireDownload(ownerId, sessionId);
        if (session.nextChunkIndex != session.totalChunks) {
            throw new IllegalStateException("Download is incomplete");
        }
        downloads.remove(sessionId);
    }

    public synchronized void abortDownload(UUID ownerId, UUID sessionId) {
        requireDownload(ownerId, sessionId);
        downloads.remove(sessionId);
    }

    public synchronized void abortAllForOwner(UUID ownerId) throws IOException {
        Objects.requireNonNull(ownerId, "ownerId");
        UUID[] uploadIds = uploads.values().stream()
                .filter(session -> session.ownerId.equals(ownerId))
                .map(session -> session.sessionId)
                .toArray(UUID[]::new);
        IOException failure = null;
        for (UUID id : uploadIds) {
            try { abortUploadInternal(id); }
            catch (IOException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        downloads.entrySet().removeIf(entry -> entry.getValue().ownerId.equals(ownerId));
        if (failure != null) throw failure;
    }

    public synchronized int activeUploads() { return uploads.size(); }
    public synchronized int activeDownloads() { return downloads.size(); }

    private UploadSession requireUpload(UUID ownerId, UUID sessionId) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(sessionId, "sessionId");
        UploadSession session = uploads.get(sessionId);
        if (session == null || !session.ownerId.equals(ownerId)) throw new IllegalArgumentException("Upload session not found");
        return session;
    }

    private DownloadSession requireDownload(UUID ownerId, UUID sessionId) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(sessionId, "sessionId");
        DownloadSession session = downloads.get(sessionId);
        if (session == null || !session.ownerId.equals(ownerId)) throw new IllegalArgumentException("Download session not found");
        return session;
    }

    private void abortUploadInternal(UUID sessionId) throws IOException {
        UploadSession session = uploads.remove(sessionId);
        if (session != null) Files.deleteIfExists(session.partial);
    }

    private static int chunkCount(long totalBytes, int chunkBytes) {
        long chunks = totalBytes == 0 ? 0 : ((totalBytes - 1) / chunkBytes) + 1;
        if (chunks > Integer.MAX_VALUE) throw new IllegalArgumentException("Transfer requires too many chunks");
        return (int) chunks;
    }

    private static String validateImportFileName(String value) {
        String name = validateTransferName(value);
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".zip") && !lower.endsWith(".mcworld")) {
            throw new IllegalArgumentException("Import file must be .zip or .mcworld");
        }
        return name;
    }

    private static String validateTransferName(String value) {
        Objects.requireNonNull(value, "fileName");
        if (value.isBlank() || !value.equals(value.strip()) || value.equals(".") || value.equals("..")
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("fileName must be one safe file name");
        }
        return value;
    }

    private static String validateSha256(String value) {
        String digest = Objects.requireNonNull(value, "sha256").strip().toLowerCase(Locale.ROOT);
        if (!digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("sha256 must be hexadecimal");
        return digest;
    }

    private static Path ownedRoot(Path path, String label) {
        return Objects.requireNonNull(path, label).toAbsolutePath().normalize();
    }

    private static Path directChild(Path root, String name) {
        Path target = root.resolve(name).normalize();
        if (!root.equals(target.getParent())) throw new IllegalArgumentException("Transfer path escaped owned root");
        return target;
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = in.read(buffer)) >= 0;) if (read > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target); }
    }

    public record UploadProgress(long receivedBytes, long totalBytes, int nextChunkIndex, int totalChunks) {}

    public record DownloadChunk(int chunkIndex, byte[] bytes, boolean last) {
        public DownloadChunk {
            bytes = Arrays.copyOf(Objects.requireNonNull(bytes, "bytes"), bytes.length);
        }
        @Override public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }
    }

    private static final class UploadSession {
        private final UUID sessionId;
        private final UUID ownerId;
        private final String fileName;
        private final Path target;
        private final Path partial;
        private final long totalBytes;
        private final String sha256;
        private final int chunkBytes;
        private final int totalChunks;
        private long receivedBytes;
        private int nextChunkIndex;

        private UploadSession(UUID sessionId, UUID ownerId, String fileName, Path target, Path partial,
                              long totalBytes, String sha256, int chunkBytes, int totalChunks) {
            this.sessionId = sessionId;
            this.ownerId = ownerId;
            this.fileName = fileName;
            this.target = target;
            this.partial = partial;
            this.totalBytes = totalBytes;
            this.sha256 = sha256;
            this.chunkBytes = chunkBytes;
            this.totalChunks = totalChunks;
        }

        private TransferDescriptor descriptor() {
            return new TransferDescriptor(sessionId, fileName, totalBytes, chunkBytes, totalChunks, sha256);
        }
    }

    private static final class DownloadSession {
        private final UUID sessionId;
        private final UUID ownerId;
        private final String fileName;
        private final Path artifact;
        private final long totalBytes;
        private final String sha256;
        private final int chunkBytes;
        private final int totalChunks;
        private int nextChunkIndex;

        private DownloadSession(UUID sessionId, UUID ownerId, String fileName, Path artifact,
                                long totalBytes, String sha256, int chunkBytes, int totalChunks) {
            this.sessionId = sessionId;
            this.ownerId = ownerId;
            this.fileName = fileName;
            this.artifact = artifact;
            this.totalBytes = totalBytes;
            this.sha256 = sha256;
            this.chunkBytes = chunkBytes;
            this.totalChunks = totalChunks;
        }

        private TransferDescriptor descriptor() {
            return new TransferDescriptor(sessionId, fileName, totalBytes, chunkBytes, totalChunks, sha256);
        }
    }
}
