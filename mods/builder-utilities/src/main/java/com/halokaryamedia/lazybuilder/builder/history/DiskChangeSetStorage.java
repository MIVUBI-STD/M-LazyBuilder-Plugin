package com.halokaryamedia.lazybuilder.builder.history;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Durable History v2 storage.
 *
 * <p>Writes remain under a .incomplete name until the stream has a valid commit
 * footer, has been forced to disk, and is atomically published in the same
 * directory. Interrupted files remain discoverable for explicit recovery.</p>
 */
public final class DiskChangeSetStorage implements ChangeSetStorage {
    private static final String INCOMPLETE_SUFFIX = ".lbh2.incomplete";
    private static final String COMMITTED_SUFFIX = ".lbh2";

    private final Path directory;
    private final java.util.Set<Path> ownedCommittedPaths =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public DiskChangeSetStorage(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
    }

    @Override
    public HistoryStorageTier tier() {
        return HistoryStorageTier.DISK;
    }

    public Path directory() {
        return directory;
    }

    @Override
    public ChangeSetWriter begin(String operationId) throws IOException {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId must be non-blank");
        }
        Files.createDirectories(directory);
        String id = UUID.randomUUID().toString();
        Path staging = directory.resolve(id + INCOMPLETE_SUFFIX);
        Path committed = directory.resolve(id + COMMITTED_SUFFIX);
        FileChannel channel = FileChannel.open(staging, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        ChangeSetCodec.StreamWriter codec;
        try {
            codec = ChangeSetCodec.openWriter(Channels.newOutputStream(channel), operationId);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                channel.close();
            } finally {
                Files.deleteIfExists(staging);
            }
            throw failure;
        }
        return new Writer(operationId, staging, committed, channel, codec, ownedCommittedPaths);
    }

    public List<Path> listIncomplete() throws IOException {
        return listPathsWithSuffix(INCOMPLETE_SUFFIX);
    }

    public List<Path> listCommitted() throws IOException {
        return listPathsWithSuffix(COMMITTED_SUFFIX);
    }

    /**
     * Promotes staging files that already contain a valid committed footer/checksum.
     * This closes the crash window between fsync/validation and the final atomic rename.
     * Invalid or truncated staging files remain quarantined under .incomplete.
     */
    public List<Path> promoteRecoverableIncomplete() throws IOException {
        List<Path> promoted = new java.util.ArrayList<>();
        for (Path staging : listIncomplete()) {
            try (InputStream input = Files.newInputStream(staging)) {
                ChangeSetCodec.inspect(input);
            } catch (IOException invalidOrIncomplete) {
                continue;
            }

            String fileName = staging.getFileName().toString();
            String committedName = fileName.substring(
                    0, fileName.length() - ".incomplete".length());
            Path committed = staging.resolveSibling(committedName);
            try {
                Files.move(staging, committed, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("History directory does not support atomic recovery promotion", e);
            }
            promoted.add(committed);
        }
        return List.copyOf(promoted);
    }

    /**
     * Reopens committed History files left on disk, typically after an unclean
     * shutdown. Each file is checksum/count validated before it is returned.
     *
     * <p>The caller owns returned change sets and may reconcile them against
     * world state before choosing to resume, publish to timeline, or discard.</p>
     */
    public List<StoredChangeSet> recoverCommitted() throws IOException {
        List<StoredChangeSet> recovered = new java.util.ArrayList<>();
        try {
            for (Path path : listCommitted()) {
                if (ownedCommittedPaths.contains(path)) continue;
                ChangeSetCodec.Header header;
                try (InputStream input = Files.newInputStream(path)) {
                    header = ChangeSetCodec.inspect(input);
                }
                if (!ownedCommittedPaths.add(path)) continue;
                recovered.add(new DiskStoredChangeSet(
                        header.operationId(),
                        header.changeCount(),
                        header.extensionCount(),
                        path,
                        ownedCommittedPaths
                ));
            }
            return List.copyOf(recovered);
        } catch (IOException | RuntimeException failure) {
            for (StoredChangeSet set : recovered) {
                try {
                    set.close();
                } catch (IOException suppressed) {
                    failure.addSuppressed(suppressed);
                }
            }
            throw failure;
        }
    }

    private List<Path> listPathsWithSuffix(String suffix) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        try (Stream<Path> paths = Files.list(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .toList();
        }
    }

    private static final class Writer implements ChangeSetWriter {
        private final String operationId;
        private final Path staging;
        private final Path committed;
        private final FileChannel channel;
        private final ChangeSetCodec.StreamWriter codec;
        private final java.util.Set<Path> ownedCommittedPaths;
        private boolean finished;

        private Writer(
                String operationId,
                Path staging,
                Path committed,
                FileChannel channel,
                ChangeSetCodec.StreamWriter codec,
                java.util.Set<Path> ownedCommittedPaths
        ) {
            this.operationId = operationId;
            this.staging = staging;
            this.committed = committed;
            this.channel = channel;
            this.codec = codec;
            this.ownedCommittedPaths = ownedCommittedPaths;
        }

        @Override
        public void append(ChunkChangeSet chunk) throws IOException {
            ensureOpen();
            codec.append(chunk);
        }

        @Override
        public void appendExtension(HistoryExtensionFrame frame) throws IOException {
            ensureOpen();
            codec.appendExtension(frame);
        }

        @Override
        public StoredChangeSet commit() throws IOException {
            ensureOpen();
            long changes = codec.commit();
            long extensions = codec.extensionCount();
            channel.force(true);
            channel.close();
            finished = true;

            try (InputStream input = Files.newInputStream(staging)) {
                ChangeSetCodec.Header header = ChangeSetCodec.inspect(input);
                if (!header.operationId().equals(operationId)
                        || header.changeCount() != changes
                        || header.extensionCount() != extensions) {
                    throw new IOException("Staged History metadata mismatch");
                }
            }

            try {
                Files.move(staging, committed, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("History directory does not support atomic commit", e);
            }
            ownedCommittedPaths.add(committed);
            return new DiskStoredChangeSet(
                    operationId, changes, extensions, committed, ownedCommittedPaths);
        }

        @Override
        public void abort() throws IOException {
            if (finished) {
                return;
            }
            codec.abort();
            channel.close();
            finished = true;
            Files.deleteIfExists(staging);
        }

        private void ensureOpen() {
            if (finished) {
                throw new IllegalStateException("ChangeSetWriter is already finished");
            }
        }
    }

    private record DiskStoredChangeSet(
            String operationId,
            long changeCount,
            long extensionCount,
            Path path,
            java.util.Set<Path> ownedCommittedPaths
    ) implements StoredChangeSet {
        private DiskStoredChangeSet {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(ownedCommittedPaths, "ownedCommittedPaths");
        }

        @Override
        public HistoryStorageTier storageTier() {
            return HistoryStorageTier.DISK;
        }

        @Override
        public void replayAll(ReplayDirection direction, HistoryReplayConsumer consumer) throws IOException {
            if (direction == ReplayDirection.UNDO) {
                replayExtensions(direction, consumer);
                replayBlocks(direction, consumer);
            } else {
                replayBlocks(direction, consumer);
                replayExtensions(direction, consumer);
            }
        }

        private void replayBlocks(ReplayDirection direction, HistoryReplayConsumer consumer) throws IOException {
            try (InputStream blocks = Files.newInputStream(path)) {
                validate(ChangeSetCodec.replayBlocks(blocks, direction, consumer));
            }
        }

        private void replayExtensions(ReplayDirection direction, HistoryReplayConsumer consumer) throws IOException {
            try (InputStream extensions = Files.newInputStream(path)) {
                validate(ChangeSetCodec.replayExtensions(extensions, direction, consumer));
            }
        }

        @Override
        public void visitChunks(ChunkChangeSetVisitor visitor) throws IOException {
            try (InputStream input = Files.newInputStream(path)) {
                ChangeSetCodec.Header header = ChangeSetCodec.visitChunks(input, visitor);
                validate(header);
            }
        }

        @Override
        public void visitExtensions(HistoryExtensionVisitor visitor) throws IOException {
            try (InputStream input = Files.newInputStream(path)) {
                validate(ChangeSetCodec.visitExtensions(input, visitor));
            }
        }

        private void validate(ChangeSetCodec.Header header) throws IOException {
            if (!header.operationId().equals(operationId)
                    || header.changeCount() != changeCount
                    || header.extensionCount() != extensionCount) {
                throw new IOException("Stored History metadata mismatch");
            }
        }

        @Override
        public boolean preserveForRecovery() {
            ownedCommittedPaths.remove(path);
            return Files.exists(path);
        }

        @Override
        public void close() throws IOException {
            try {
                Files.deleteIfExists(path);
            } finally {
                ownedCommittedPaths.remove(path);
            }
        }
    }
}
