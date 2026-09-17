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
        return new Writer(operationId, staging, committed, channel, codec);
    }

    public List<Path> listIncomplete() throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(INCOMPLETE_SUFFIX))
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
        private boolean finished;

        private Writer(String operationId, Path staging, Path committed, FileChannel channel,
                       ChangeSetCodec.StreamWriter codec) {
            this.operationId = operationId;
            this.staging = staging;
            this.committed = committed;
            this.channel = channel;
            this.codec = codec;
        }

        @Override
        public void append(ChunkChangeSet chunk) throws IOException {
            ensureOpen();
            codec.append(chunk);
        }

        @Override
        public StoredChangeSet commit() throws IOException {
            ensureOpen();
            long changes = codec.commit();
            channel.force(true);
            channel.close();
            finished = true;

            try (InputStream input = Files.newInputStream(staging)) {
                ChangeSetCodec.Header header = ChangeSetCodec.inspect(input);
                if (!header.operationId().equals(operationId) || header.changeCount() != changes) {
                    throw new IOException("Staged History metadata mismatch");
                }
            }

            try {
                Files.move(staging, committed, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("History directory does not support atomic commit", e);
            }
            return new DiskStoredChangeSet(operationId, changes, committed);
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

    private record DiskStoredChangeSet(String operationId, long changeCount, Path path)
            implements StoredChangeSet {
        private DiskStoredChangeSet {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(path, "path");
        }

        @Override
        public HistoryStorageTier storageTier() {
            return HistoryStorageTier.DISK;
        }

        @Override
        public void replay(ReplayDirection direction, BlockChangeConsumer consumer) throws IOException {
            try (InputStream input = Files.newInputStream(path)) {
                ChangeSetCodec.Header header = ChangeSetCodec.replay(input, direction, consumer);
                if (!header.operationId().equals(operationId) || header.changeCount() != changeCount) {
                    throw new IOException("Stored History metadata mismatch");
                }
            }
        }

        @Override
        public void close() throws IOException {
            Files.deleteIfExists(path);
        }
    }
}
