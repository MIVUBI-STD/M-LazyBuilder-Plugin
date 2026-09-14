package com.halokaryamedia.lazybuilder.world.transfer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event-driven chunk transfer owner for client uploads/downloads.
 *
 * <p>No polling loop, socket worker, or background cleanup thread is owned here.
 * Network adapters call these methods only in response to explicit protocol events.
 * Each active transfer keeps one seekable file channel open so chunk I/O is O(n)
 * over transferred bytes. Stale sessions are expired opportunistically on later
 * requests for the same owner and are always removed on disconnect/shutdown.</p>
 */
public final class TransferSessionService {
    private static final int HASH_BUFFER_BYTES = 64 * 1024;

    private final Path importsRoot;
    private final Path exportsRoot;
    private final Path tempRoot;
    private final TransferPolicy policy;
    private final Clock clock;
    private final Map<UUID, UploadSession> uploads = new ConcurrentHashMap<>();
    private final Map<UUID, DownloadSession> downloads = new ConcurrentHashMap<>();
    /** Completed imports stay bound to the player that uploaded them until review/import cleanup releases ownership. */
    private final Map<String, UUID> completedUploadOwners = new ConcurrentHashMap<>();

    public TransferSessionService(Path importsRoot, Path exportsRoot, Path tempRoot, TransferPolicy policy) {
        this(importsRoot, exportsRoot, tempRoot, policy, Clock.systemUTC());
    }

    TransferSessionService(Path importsRoot, Path exportsRoot, Path tempRoot, TransferPolicy policy, Clock clock) {
        this.importsRoot = ownedRoot(importsRoot, "importsRoot");
        this.exportsRoot = ownedRoot(exportsRoot, "exportsRoot");
        this.tempRoot = ownedRoot(tempRoot, "tempRoot");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public TransferDescriptor beginUpload(UUID ownerId, String fileName, long totalBytes, String sha256) throws IOException {
        Objects.requireNonNull(ownerId, "ownerId");
        expireIdleSessions(ownerId);
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
        if (Files.getFileStore(tempRoot).getUsableSpace() < totalBytes) {
            throw new IOException("Insufficient disk space for declared upload size");
        }
        Path target = directChild(importsRoot, safeName);
        if (Files.exists(target)) throw new IOException("Import artifact already exists: " + safeName);

        UUID sessionId = UUID.randomUUID();
        Path partial = directChild(tempRoot, sessionId + ".upload.part");
        Files.deleteIfExists(partial);
        FileChannel channel = null;
        try {
            channel = FileChannel.open(partial, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            int chunks = chunkCount(totalBytes, policy.chunkBytes());
            UploadSession session = new UploadSession(
                    sessionId, ownerId, safeName, target, partial, totalBytes, digest,
                    policy.chunkBytes(), chunks, channel, newSha256Digest(), clock.instant()
            );
            uploads.put(sessionId, session);
            return session.descriptor();
        } catch (IOException | RuntimeException failure) {
            closeQuietly(channel);
            Files.deleteIfExists(partial);
            throw failure;
        }
    }

    public UploadProgress acceptUploadChunk(UUID ownerId, UUID sessionId, int chunkIndex, byte[] bytes) throws IOException {
        expireIdleSessions(ownerId);
        UploadSession session = requireUpload(ownerId, sessionId);
        Objects.requireNonNull(bytes, "bytes");
        synchronized (session) {
            requireUpload(ownerId, sessionId, session);
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

            writeFully(session.channel, ByteBuffer.wrap(bytes), session.receivedBytes);
            session.digest.update(bytes);
            session.receivedBytes = nextTotal;
            session.nextChunkIndex++;
            session.lastActivity = clock.instant();
            return new UploadProgress(session.receivedBytes, session.totalBytes, session.nextChunkIndex, session.totalChunks);
        }
    }

    public Path finishUpload(UUID ownerId, UUID sessionId) throws IOException {
        expireIdleSessions(ownerId);
        UploadSession session = requireUpload(ownerId, sessionId);
        synchronized (session) {
            requireUpload(ownerId, sessionId, session);
            try {
                if (session.receivedBytes != session.totalBytes || session.nextChunkIndex != session.totalChunks) {
                    throw new IOException("Upload is incomplete");
                }
                session.channel.force(false);
                session.channel.close();
                String actual = HexFormat.of().formatHex(session.digest.digest());
                if (!actual.equals(session.sha256)) throw new IOException("Upload checksum mismatch");
                if (Files.exists(session.target)) throw new IOException("Import artifact already exists: " + session.fileName);
                move(session.partial, session.target);
                uploads.remove(sessionId, session);
                completedUploadOwners.put(session.fileName, session.ownerId);
                return session.target;
            } catch (IOException | RuntimeException failure) {
                try { abortUploadInternal(sessionId, session); }
                catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                throw failure;
            }
        }
    }

    /** True only for a completed upload produced by this runtime for the same player. */
    public boolean ownsCompletedUpload(UUID ownerId, String fileName) {
        Objects.requireNonNull(ownerId, "ownerId");
        String safeName = validateImportFileName(fileName);
        return ownerId.equals(completedUploadOwners.get(safeName));
    }

    /** Release the transient ownership claim after discard or successful import. */
    public void releaseCompletedUpload(UUID ownerId, String fileName) {
        Objects.requireNonNull(ownerId, "ownerId");
        String safeName = validateImportFileName(fileName);
        completedUploadOwners.remove(safeName, ownerId);
    }

    public void abortUpload(UUID ownerId, UUID sessionId) throws IOException {
        UploadSession session = requireUpload(ownerId, sessionId);
        synchronized (session) {
            requireUpload(ownerId, sessionId, session);
            abortUploadInternal(sessionId, session);
        }
    }

    public TransferDescriptor beginDownload(UUID ownerId, String fileName) throws IOException {
        Objects.requireNonNull(ownerId, "ownerId");
        expireIdleSessions(ownerId);
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
        FileChannel channel = null;
        try {
            channel = FileChannel.open(artifact, StandardOpenOption.READ);
            DownloadSession session = new DownloadSession(
                    sessionId, ownerId, safeName, artifact, size, digest,
                    policy.chunkBytes(), chunks, channel, clock.instant()
            );
            downloads.put(sessionId, session);
            return session.descriptor();
        } catch (IOException | RuntimeException failure) {
            closeQuietly(channel);
            throw failure;
        }
    }

    public DownloadChunk readDownloadChunk(UUID ownerId, UUID sessionId, int chunkIndex) throws IOException {
        expireIdleSessions(ownerId);
        DownloadSession session = requireDownload(ownerId, sessionId);
        synchronized (session) {
            requireDownload(ownerId, sessionId, session);
            if (chunkIndex != session.nextChunkIndex) {
                throw new IllegalArgumentException("Unexpected download chunk index: " + chunkIndex);
            }
            if (chunkIndex >= session.totalChunks) throw new IllegalArgumentException("Download chunk index out of range");

            long offset = (long) chunkIndex * session.chunkBytes;
            int expected = (int) Math.min(session.chunkBytes, session.totalBytes - offset);
            byte[] data = new byte[expected];
            readFully(session.channel, ByteBuffer.wrap(data), offset);
            session.nextChunkIndex++;
            session.lastActivity = clock.instant();
            boolean last = session.nextChunkIndex == session.totalChunks;
            return new DownloadChunk(chunkIndex, data, last);
        }
    }

    public void finishDownload(UUID ownerId, UUID sessionId) throws IOException {
        expireIdleSessions(ownerId);
        DownloadSession session = requireDownload(ownerId, sessionId);
        synchronized (session) {
            requireDownload(ownerId, sessionId, session);
            if (session.nextChunkIndex != session.totalChunks) {
                throw new IllegalStateException("Download is incomplete");
            }
            downloads.remove(sessionId, session);
            session.channel.close();
        }
    }

    public void abortDownload(UUID ownerId, UUID sessionId) throws IOException {
        DownloadSession session = requireDownload(ownerId, sessionId);
        synchronized (session) {
            requireDownload(ownerId, sessionId, session);
            downloads.remove(sessionId, session);
            session.channel.close();
        }
    }

    public void abortAllForOwner(UUID ownerId) throws IOException {
        Objects.requireNonNull(ownerId, "ownerId");
        IOException failure = null;
        for (UploadSession session : uploads.values().toArray(UploadSession[]::new)) {
            if (!session.ownerId.equals(ownerId)) continue;
            synchronized (session) {
                if (uploads.get(session.sessionId) != session) continue;
                try { abortUploadInternal(session.sessionId, session); }
                catch (IOException exception) { failure = combine(failure, exception); }
            }
        }
        for (DownloadSession session : downloads.values().toArray(DownloadSession[]::new)) {
            if (!session.ownerId.equals(ownerId)) continue;
            synchronized (session) {
                if (!downloads.remove(session.sessionId, session)) continue;
                try { session.channel.close(); }
                catch (IOException exception) { failure = combine(failure, exception); }
            }
        }
        if (failure != null) throw failure;
    }

    public int activeUploads() { return uploads.size(); }
    public int activeDownloads() { return downloads.size(); }

    private void expireIdleSessions(UUID ownerId) throws IOException {
        Instant now = clock.instant();
        IOException failure = null;
        for (UploadSession session : uploads.values().toArray(UploadSession[]::new)) {
            if (!session.ownerId.equals(ownerId) || !expired(session.lastActivity, now)) continue;
            synchronized (session) {
                if (uploads.get(session.sessionId) != session || !expired(session.lastActivity, now)) continue;
                try { abortUploadInternal(session.sessionId, session); }
                catch (IOException exception) { failure = combine(failure, exception); }
            }
        }
        for (DownloadSession session : downloads.values().toArray(DownloadSession[]::new)) {
            if (!session.ownerId.equals(ownerId) || !expired(session.lastActivity, now)) continue;
            synchronized (session) {
                if (downloads.get(session.sessionId) != session || !expired(session.lastActivity, now)) continue;
                downloads.remove(session.sessionId, session);
                try { session.channel.close(); }
                catch (IOException exception) { failure = combine(failure, exception); }
            }
        }
        if (failure != null) throw failure;
    }

    private boolean expired(Instant lastActivity, Instant now) {
        return !lastActivity.plus(policy.idleTimeout()).isAfter(now);
    }

    private UploadSession requireUpload(UUID ownerId, UUID sessionId) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(sessionId, "sessionId");
        UploadSession session = uploads.get(sessionId);
        if (session == null || !session.ownerId.equals(ownerId)) throw new IllegalArgumentException("Upload session not found");
        return session;
    }

    private void requireUpload(UUID ownerId, UUID sessionId, UploadSession expected) {
        if (uploads.get(sessionId) != expected || !expected.ownerId.equals(ownerId)) {
            throw new IllegalArgumentException("Upload session not found");
        }
    }

    private DownloadSession requireDownload(UUID ownerId, UUID sessionId) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(sessionId, "sessionId");
        DownloadSession session = downloads.get(sessionId);
        if (session == null || !session.ownerId.equals(ownerId)) throw new IllegalArgumentException("Download session not found");
        return session;
    }

    private void requireDownload(UUID ownerId, UUID sessionId, DownloadSession expected) {
        if (downloads.get(sessionId) != expected || !expected.ownerId.equals(ownerId)) {
            throw new IllegalArgumentException("Download session not found");
        }
    }

    private void abortUploadInternal(UUID sessionId, UploadSession session) throws IOException {
        uploads.remove(sessionId, session);
        IOException failure = null;
        try { session.channel.close(); }
        catch (IOException exception) { failure = exception; }
        try { Files.deleteIfExists(session.partial); }
        catch (IOException exception) { failure = combine(failure, exception); }
        if (failure != null) throw failure;
    }

    private static void writeFully(FileChannel channel, ByteBuffer source, long position) throws IOException {
        long cursor = position;
        while (source.hasRemaining()) {
            int written = channel.write(source, cursor);
            if (written < 0) throw new IOException("Upload channel closed while writing");
            if (written == 0) continue;
            cursor += written;
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer target, long position) throws IOException {
        long cursor = position;
        while (target.hasRemaining()) {
            int read = channel.read(target, cursor);
            if (read < 0) throw new IOException("Export artifact changed during download");
            if (read == 0) continue;
            cursor += read;
        }
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

    private static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest digest = newSha256Digest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[HASH_BUFFER_BYTES];
            for (int read; (read = in.read(buffer)) >= 0;) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target); }
    }

    private static IOException combine(IOException first, IOException next) {
        if (first == null) return next;
        first.addSuppressed(next);
        return first;
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel == null) return;
        try { channel.close(); } catch (IOException ignored) { }
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
        private final FileChannel channel;
        private final MessageDigest digest;
        private long receivedBytes;
        private int nextChunkIndex;
        private Instant lastActivity;

        private UploadSession(UUID sessionId, UUID ownerId, String fileName, Path target, Path partial,
                              long totalBytes, String sha256, int chunkBytes, int totalChunks,
                              FileChannel channel, MessageDigest digest, Instant lastActivity) {
            this.sessionId = sessionId;
            this.ownerId = ownerId;
            this.fileName = fileName;
            this.target = target;
            this.partial = partial;
            this.totalBytes = totalBytes;
            this.sha256 = sha256;
            this.chunkBytes = chunkBytes;
            this.totalChunks = totalChunks;
            this.channel = channel;
            this.digest = digest;
            this.lastActivity = lastActivity;
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
        private final FileChannel channel;
        private int nextChunkIndex;
        private Instant lastActivity;

        private DownloadSession(UUID sessionId, UUID ownerId, String fileName, Path artifact,
                                long totalBytes, String sha256, int chunkBytes, int totalChunks,
                                FileChannel channel, Instant lastActivity) {
            this.sessionId = sessionId;
            this.ownerId = ownerId;
            this.fileName = fileName;
            this.artifact = artifact;
            this.totalBytes = totalBytes;
            this.sha256 = sha256;
            this.chunkBytes = chunkBytes;
            this.totalChunks = totalChunks;
            this.channel = channel;
            this.lastActivity = lastActivity;
        }

        private TransferDescriptor descriptor() {
            return new TransferDescriptor(sessionId, fileName, totalBytes, chunkBytes, totalChunks, sha256);
        }
    }
}
