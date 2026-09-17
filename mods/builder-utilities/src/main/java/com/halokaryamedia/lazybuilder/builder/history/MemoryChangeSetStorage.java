package com.halokaryamedia.lazybuilder.builder.history;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

/**
 * Streaming in-memory History v2 backend. The compressed variant uses the same
 * codec through a compression wrapper supplied by subclasses.
 */
public class MemoryChangeSetStorage implements ChangeSetStorage {
    @Override
    public HistoryStorageTier tier() {
        return HistoryStorageTier.MEMORY;
    }

    protected ByteArrayOutputStream createBuffer() {
        return new ByteArrayOutputStream();
    }

    protected java.io.OutputStream wrapOutput(ByteArrayOutputStream buffer) throws IOException {
        return buffer;
    }

    protected java.io.InputStream wrapInput(byte[] bytes) throws IOException {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public ChangeSetWriter begin(String operationId) throws IOException {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId must be non-blank");
        }
        ByteArrayOutputStream buffer = createBuffer();
        java.io.OutputStream output = wrapOutput(buffer);
        ChangeSetCodec.StreamWriter codec = ChangeSetCodec.openWriter(output, operationId);
        return new Writer(operationId, tier(), buffer, output, codec, this::openStoredInput);
    }

    private java.io.InputStream openStoredInput(byte[] bytes) {
        try {
            return wrapInput(bytes);
        } catch (IOException e) {
            throw new HistoryReadException(e);
        }
    }

    private static final class Writer implements ChangeSetWriter {
        private final String operationId;
        private final HistoryStorageTier tier;
        private final ByteArrayOutputStream buffer;
        private final java.io.OutputStream output;
        private final ChangeSetCodec.StreamWriter codec;
        private final Function<byte[], java.io.InputStream> inputFactory;
        private boolean finished;

        private Writer(String operationId, HistoryStorageTier tier, ByteArrayOutputStream buffer,
                       java.io.OutputStream output, ChangeSetCodec.StreamWriter codec,
                       Function<byte[], java.io.InputStream> inputFactory) {
            this.operationId = operationId;
            this.tier = tier;
            this.buffer = buffer;
            this.output = output;
            this.codec = codec;
            this.inputFactory = inputFactory;
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
            output.close();
            finished = true;
            byte[] bytes = buffer.toByteArray();
            return new MemoryStoredChangeSet(operationId, tier, changes, extensions, bytes, inputFactory);
        }

        @Override
        public void abort() throws IOException {
            if (finished) {
                return;
            }
            codec.abort();
            output.close();
            finished = true;
        }

        private void ensureOpen() {
            if (finished) {
                throw new IllegalStateException("ChangeSetWriter is already finished");
            }
        }
    }

    private record MemoryStoredChangeSet(
            String operationId,
            HistoryStorageTier storageTier,
            long changeCount,
            long extensionCount,
            byte[] bytes,
            Function<byte[], java.io.InputStream> inputFactory
    ) implements StoredChangeSet {
        private MemoryStoredChangeSet {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(storageTier, "storageTier");
            bytes = bytes.clone();
            Objects.requireNonNull(inputFactory, "inputFactory");
        }

        @Override
        public void replayAll(ReplayDirection direction, HistoryReplayConsumer consumer) throws IOException {
            try (java.io.InputStream input = inputFactory.apply(bytes)) {
                ChangeSetCodec.Header header = ChangeSetCodec.replay(input, direction, consumer);
                validate(header);
            } catch (HistoryReadException e) {
                throw e.ioCause();
            }
        }

        @Override
        public void visitChunks(ChunkChangeSetVisitor visitor) throws IOException {
            try (java.io.InputStream input = inputFactory.apply(bytes)) {
                ChangeSetCodec.Header header = ChangeSetCodec.visitChunks(input, visitor);
                validate(header);
            } catch (HistoryReadException e) {
                throw e.ioCause();
            }
        }

        private void validate(ChangeSetCodec.Header header) throws IOException {
            if (!header.operationId().equals(operationId)
                    || header.changeCount() != changeCount
                    || header.extensionCount() != extensionCount) {
                throw new IOException("Stored History metadata mismatch");
            }
        }
    }

    private static final class HistoryReadException extends RuntimeException {
        private final IOException ioCause;

        private HistoryReadException(IOException cause) {
            super(cause);
            this.ioCause = cause;
        }

        private IOException ioCause() {
            return ioCause;
        }
    }
}
