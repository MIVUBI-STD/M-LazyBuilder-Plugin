package com.halokaryamedia.lazybuilder.builder.history;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Streaming binary codec for History v2.
 *
 * <p>A changeset is valid only when a commit footer is present. Truncated streams
 * are rejected instead of being treated as partially committed history.</p>
 */
public final class ChangeSetCodec {
    private static final int MAGIC = 0x4c424832; // LBH2
    private static final int VERSION = 1;
    private static final int CHUNK_MARKER = 1;
    private static final int COMMIT_MARKER = 127;
    private static final int MAX_PALETTE_SIZE = 1_000_000;
    private static final int MAX_CHANGES_PER_CHUNK = 16_777_216;

    private ChangeSetCodec() {
    }

    public static StreamWriter openWriter(OutputStream output, String operationId) throws IOException {
        Objects.requireNonNull(output, "output");
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId must be non-blank");
        }
        DataOutputStream data = new DataOutputStream(output);
        data.writeInt(MAGIC);
        data.writeInt(VERSION);
        data.writeUTF(operationId);
        return new StreamWriter(data, operationId);
    }

    public static Header inspect(InputStream input) throws IOException {
        DataInputStream data = new DataInputStream(Objects.requireNonNull(input, "input"));
        Header header = readHeader(data);
        long changes = scan(data, null, null);
        return new Header(header.operationId(), changes);
    }

    public static Header replay(InputStream input, ReplayDirection direction, BlockChangeConsumer consumer)
            throws IOException {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(consumer, "consumer");
        DataInputStream data = new DataInputStream(Objects.requireNonNull(input, "input"));
        Header header = readHeader(data);
        long changes = scan(data, direction, consumer);
        return new Header(header.operationId(), changes);
    }

    private static Header readHeader(DataInputStream data) throws IOException {
        try {
            if (data.readInt() != MAGIC) {
                throw new IOException("Invalid History v2 magic");
            }
            int version = data.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported History v2 version: " + version);
            }
            String operationId = data.readUTF();
            if (operationId.isBlank()) {
                throw new IOException("History operation id is blank");
            }
            return new Header(operationId, -1);
        } catch (EOFException e) {
            throw incomplete(e);
        }
    }

    private static long scan(DataInputStream data, ReplayDirection direction, BlockChangeConsumer consumer)
            throws IOException {
        long observedChanges = 0;
        try {
            while (true) {
                int marker = data.readUnsignedByte();
                if (marker == COMMIT_MARKER) {
                    long committedChanges = data.readLong();
                    if (committedChanges != observedChanges) {
                        throw new IOException("History change count mismatch: expected "
                                + committedChanges + " but decoded " + observedChanges);
                    }
                    return observedChanges;
                }
                if (marker != CHUNK_MARKER) {
                    throw new IOException("Unknown History v2 frame marker: " + marker);
                }
                ChunkChangeSet chunk = readChunk(data);
                observedChanges = Math.addExact(observedChanges, chunk.size());
                if (consumer != null) {
                    replayChunk(chunk, direction, consumer);
                }
            }
        } catch (EOFException e) {
            throw incomplete(e);
        } catch (ArithmeticException e) {
            throw new IOException("History change count overflow", e);
        }
    }

    private static void replayChunk(ChunkChangeSet chunk, ReplayDirection direction, BlockChangeConsumer consumer) {
        long[] positions = chunk.positions();
        if (direction == ReplayDirection.REDO) {
            for (int i = 0; i < positions.length; i++) {
                emit(chunk, positions[i], i, false, consumer);
            }
            return;
        }
        for (int i = positions.length - 1; i >= 0; i--) {
            emit(chunk, positions[i], i, true, consumer);
        }
    }

    private static void emit(ChunkChangeSet chunk, long packed, int index, boolean before,
                             BlockChangeConsumer consumer) {
        consumer.accept(
                chunk.chunkX(),
                chunk.chunkZ(),
                LocalBlockPosition.localX(packed),
                LocalBlockPosition.y(packed),
                LocalBlockPosition.localZ(packed),
                before ? chunk.beforeState(index) : chunk.afterState(index)
        );
    }

    private static ChunkChangeSet readChunk(DataInputStream data) throws IOException {
        int chunkX = data.readInt();
        int chunkZ = data.readInt();
        int paletteSize = readBoundedCount(data, "palette", MAX_PALETTE_SIZE);
        List<String> palette = new ArrayList<>(paletteSize);
        for (int i = 0; i < paletteSize; i++) {
            String state = data.readUTF();
            if (state.isBlank()) {
                throw new IOException("History palette contains blank state");
            }
            palette.add(state);
        }
        int changeCount = readBoundedCount(data, "changes", MAX_CHANGES_PER_CHUNK);
        long[] positions = new long[changeCount];
        int[] before = new int[changeCount];
        int[] after = new int[changeCount];
        for (int i = 0; i < changeCount; i++) {
            positions[i] = data.readLong();
            before[i] = data.readInt();
            after[i] = data.readInt();
        }
        try {
            return new ChunkChangeSet(chunkX, chunkZ, palette, positions, before, after);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid History chunk frame", e);
        }
    }

    private static int readBoundedCount(DataInputStream data, String label, int max) throws IOException {
        int value = data.readInt();
        if (value < 0 || value > max) {
            throw new IOException("Invalid History " + label + " count: " + value);
        }
        return value;
    }

    private static IOException incomplete(EOFException cause) {
        return new IOException("Incomplete History v2 changeset: commit footer is missing", cause);
    }

    public record Header(String operationId, long changeCount) {
    }

    public static final class StreamWriter {
        private final DataOutputStream data;
        private final String operationId;
        private long changeCount;
        private boolean finished;

        private StreamWriter(DataOutputStream data, String operationId) {
            this.data = data;
            this.operationId = operationId;
        }

        public String operationId() {
            return operationId;
        }

        public long changeCount() {
            return changeCount;
        }

        public void append(ChunkChangeSet chunk) throws IOException {
            Objects.requireNonNull(chunk, "chunk");
            ensureOpen();
            data.writeByte(CHUNK_MARKER);
            data.writeInt(chunk.chunkX());
            data.writeInt(chunk.chunkZ());
            data.writeInt(chunk.palette().size());
            for (String state : chunk.palette()) {
                data.writeUTF(state);
            }
            long[] positions = chunk.positions();
            int[] before = chunk.beforeStates();
            int[] after = chunk.afterStates();
            data.writeInt(positions.length);
            for (int i = 0; i < positions.length; i++) {
                data.writeLong(positions[i]);
                data.writeInt(before[i]);
                data.writeInt(after[i]);
            }
            try {
                changeCount = Math.addExact(changeCount, positions.length);
            } catch (ArithmeticException e) {
                throw new IOException("History change count overflow", e);
            }
        }

        public long commit() throws IOException {
            ensureOpen();
            data.writeByte(COMMIT_MARKER);
            data.writeLong(changeCount);
            data.flush();
            finished = true;
            return changeCount;
        }

        public void abort() {
            finished = true;
        }

        private void ensureOpen() {
            if (finished) {
                throw new IllegalStateException("History stream writer is already finished");
            }
        }
    }
}
