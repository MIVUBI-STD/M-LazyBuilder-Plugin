package com.halokaryamedia.lazybuilder.builder.history;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;
import java.util.zip.CRC32;

/**
 * Streaming binary codec for History v2.
 *
 * <p>Version 2 adds a CRC32 commit checksum while retaining read compatibility
 * with version 1. A committed operation permits at most one block frame per
 * chunk and one extension frame per extension key, which makes replay order
 * deterministic without buffering entire large histories.</p>
 */
public final class ChangeSetCodec {
    private static final int MAGIC = 0x4c424832; // LBH2
    private static final int VERSION = 2;
    private static final int MIN_SUPPORTED_VERSION = 1;
    private static final int CHUNK_MARKER = 1;
    private static final int EXTENSION_MARKER = 2;
    private static final int COMMIT_MARKER = 127;
    private static final int MAX_PALETTE_SIZE = 1_000_000;
    private static final int MAX_CHANGES_PER_CHUNK = 16_777_216;
    private static final int MAX_EXTENSION_PAYLOAD_BYTES = 16 * 1024 * 1024;

    private ChangeSetCodec() {}

    public static StreamWriter openWriter(OutputStream output, String operationId) throws IOException {
        Objects.requireNonNull(output, "output");
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId must be non-blank");
        }
        CRC32 crc = new CRC32();
        CheckedOutputStream checked = new CheckedOutputStream(output, crc);
        DataOutputStream data = new DataOutputStream(checked);
        data.writeInt(MAGIC);
        data.writeInt(VERSION);
        data.writeUTF(operationId);
        return new StreamWriter(data, crc, operationId);
    }

    public static Header inspect(InputStream input) throws IOException {
        ScanContext context = openInput(input);
        Counts counts = scan(context, null, null, ReplayPhase.NONE);
        return new Header(context.header.operationId, counts.changes, counts.extensions);
    }

    public static Header replay(InputStream input, ReplayDirection direction, HistoryReplayConsumer consumer)
            throws IOException {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(consumer, "consumer");
        ScanContext context = openInput(input);
        Counts counts = scan(context, direction, consumer, ReplayPhase.ALL);
        return new Header(context.header.operationId, counts.changes, counts.extensions);
    }

    public static Header replayBlocks(
            InputStream input,
            ReplayDirection direction,
            HistoryReplayConsumer consumer
    ) throws IOException {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(consumer, "consumer");
        ScanContext context = openInput(input);
        Counts counts = scan(context, direction, consumer, ReplayPhase.BLOCKS);
        return new Header(context.header.operationId, counts.changes, counts.extensions);
    }

    public static Header replayExtensions(
            InputStream input,
            ReplayDirection direction,
            HistoryReplayConsumer consumer
    ) throws IOException {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(consumer, "consumer");
        ScanContext context = openInput(input);
        Counts counts = scan(context, direction, consumer, ReplayPhase.EXTENSIONS);
        return new Header(context.header.operationId, counts.changes, counts.extensions);
    }

    public static Header visitChunks(InputStream input, ChunkChangeSetVisitor visitor) throws IOException {
        Objects.requireNonNull(visitor, "visitor");
        ScanContext context = openInput(input);
        DataInputStream data = context.data;
        long observedChanges = 0;
        long observedExtensions = 0;
        boolean callbacksEnabled = true;
        Set<Long> seenChunks = new HashSet<>();
        Set<ExtensionKey> seenExtensions = new HashSet<>();

        try {
            while (true) {
                int marker = data.readUnsignedByte();
                if (marker == COMMIT_MARKER) {
                    long committedChanges = data.readLong();
                    long committedExtensions = data.readLong();
                    verifyFooter(context, committedChanges, committedExtensions,
                            observedChanges, observedExtensions);
                    return new Header(context.header.operationId, observedChanges, observedExtensions);
                }
                if (marker == CHUNK_MARKER) {
                    ChunkChangeSet chunk = readChunk(data);
                    requireUniqueChunk(seenChunks, chunk.chunkX(), chunk.chunkZ());
                    observedChanges = Math.addExact(observedChanges, chunk.size());
                    if (callbacksEnabled) callbacksEnabled = visitor.visit(chunk);
                    continue;
                }
                if (marker == EXTENSION_MARKER) {
                    HistoryExtensionFrame frame = readExtension(data);
                    requireUniqueExtension(seenExtensions, frame);
                    observedExtensions = Math.addExact(observedExtensions, 1);
                    continue;
                }
                throw new IOException("Unknown History v2 frame marker: " + marker);
            }
        } catch (EOFException e) {
            throw incomplete(e);
        } catch (ArithmeticException e) {
            throw new IOException("History count overflow", e);
        }
    }

    private static ScanContext openInput(InputStream input) throws IOException {
        CRC32 crc = new CRC32();
        CheckedInputStream checked = new CheckedInputStream(Objects.requireNonNull(input, "input"), crc);
        DataInputStream data = new DataInputStream(checked);
        ReadHeader header = readHeader(data);
        return new ScanContext(data, crc, header);
    }

    private static ReadHeader readHeader(DataInputStream data) throws IOException {
        try {
            if (data.readInt() != MAGIC) throw new IOException("Invalid History v2 magic");
            int version = data.readInt();
            if (version < MIN_SUPPORTED_VERSION || version > VERSION) {
                throw new IOException("Unsupported History v2 version: " + version);
            }
            String operationId = data.readUTF();
            if (operationId.isBlank()) throw new IOException("History operation id is blank");
            return new ReadHeader(operationId, version);
        } catch (EOFException e) {
            throw incomplete(e);
        }
    }

    private static Counts scan(
            ScanContext context,
            ReplayDirection direction,
            HistoryReplayConsumer consumer,
            ReplayPhase phase
    ) throws IOException {
        DataInputStream data = context.data;
        long observedChanges = 0;
        long observedExtensions = 0;
        Set<Long> seenChunks = new HashSet<>();
        Set<ExtensionKey> seenExtensions = new HashSet<>();

        try {
            while (true) {
                int marker = data.readUnsignedByte();
                if (marker == COMMIT_MARKER) {
                    long committedChanges = data.readLong();
                    long committedExtensions = data.readLong();
                    verifyFooter(context, committedChanges, committedExtensions,
                            observedChanges, observedExtensions);
                    return new Counts(observedChanges, observedExtensions);
                }
                if (marker == CHUNK_MARKER) {
                    ChunkChangeSet chunk = readChunk(data);
                    requireUniqueChunk(seenChunks, chunk.chunkX(), chunk.chunkZ());
                    observedChanges = Math.addExact(observedChanges, chunk.size());
                    if (consumer != null && (phase == ReplayPhase.ALL || phase == ReplayPhase.BLOCKS)) {
                        replayChunk(chunk, direction, consumer);
                    }
                    continue;
                }
                if (marker == EXTENSION_MARKER) {
                    HistoryExtensionFrame frame = readExtension(data);
                    requireUniqueExtension(seenExtensions, frame);
                    observedExtensions = Math.addExact(observedExtensions, 1);
                    if (consumer != null && (phase == ReplayPhase.ALL || phase == ReplayPhase.EXTENSIONS)) {
                        consumer.acceptExtension(frame, frame.payload(direction));
                    }
                    continue;
                }
                throw new IOException("Unknown History v2 frame marker: " + marker);
            }
        } catch (EOFException e) {
            throw incomplete(e);
        } catch (ArithmeticException e) {
            throw new IOException("History count overflow", e);
        }
    }

    private static void verifyFooter(
            ScanContext context,
            long committedChanges,
            long committedExtensions,
            long observedChanges,
            long observedExtensions
    ) throws IOException {
        if (committedChanges != observedChanges || committedExtensions != observedExtensions) {
            throw new IOException("History footer count mismatch");
        }
        if (context.header.version >= 2) {
            long computed = context.crc.getValue();
            long stored = context.data.readLong();
            if (stored != computed) {
                throw new IOException("History checksum mismatch");
            }
        }
    }

    private static void requireUniqueChunk(Set<Long> seen, int chunkX, int chunkZ) throws IOException {
        long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
        if (!seen.add(key)) {
            throw new IOException("Duplicate History chunk frame for " + chunkX + "," + chunkZ);
        }
    }

    private static void requireUniqueExtension(Set<ExtensionKey> seen, HistoryExtensionFrame frame)
            throws IOException {
        ExtensionKey key = new ExtensionKey(
                frame.typeId(), frame.chunkX(), frame.chunkZ(), frame.localKey());
        if (!seen.add(key)) {
            throw new IOException("Duplicate History extension frame: " + frame.typeId());
        }
    }

    private static void replayChunk(
            ChunkChangeSet chunk,
            ReplayDirection direction,
            HistoryReplayConsumer consumer
    ) {
        long[] positions = chunk.positions();
        if (direction == ReplayDirection.REDO) {
            for (int i = 0; i < positions.length; i++) emit(chunk, positions[i], i, false, consumer);
        } else {
            for (int i = positions.length - 1; i >= 0; i--) emit(chunk, positions[i], i, true, consumer);
        }
    }

    private static void emit(
            ChunkChangeSet chunk,
            long packed,
            int index,
            boolean before,
            HistoryReplayConsumer consumer
    ) {
        consumer.acceptBlock(
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
            if (state.isBlank()) throw new IOException("History palette contains blank state");
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

    private static HistoryExtensionFrame readExtension(DataInputStream data) throws IOException {
        String typeId = data.readUTF();
        int chunkX = data.readInt();
        int chunkZ = data.readInt();
        long localKey = data.readLong();
        byte[] before = readPayload(data);
        byte[] after = readPayload(data);
        try {
            return new HistoryExtensionFrame(typeId, chunkX, chunkZ, localKey, before, after);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid History extension frame", e);
        }
    }

    private static byte[] readPayload(DataInputStream data) throws IOException {
        int length = readBoundedCount(data, "extension payload", MAX_EXTENSION_PAYLOAD_BYTES);
        byte[] payload = new byte[length];
        data.readFully(payload);
        return payload;
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

    private enum ReplayPhase { NONE, ALL, BLOCKS, EXTENSIONS }
    private record Counts(long changes, long extensions) {}
    private record ReadHeader(String operationId, int version) {}
    private record ScanContext(DataInputStream data, CRC32 crc, ReadHeader header) {}
    private record ExtensionKey(String typeId, int chunkX, int chunkZ, long localKey) {}

    public record Header(String operationId, long changeCount, long extensionCount) {}

    public static final class StreamWriter {
        private final DataOutputStream data;
        private final CRC32 crc;
        private final String operationId;
        private final Set<Long> seenChunks = new HashSet<>();
        private final Set<ExtensionKey> seenExtensions = new HashSet<>();
        private long changeCount;
        private long extensionCount;
        private WriterState state = WriterState.OPEN;

        private StreamWriter(DataOutputStream data, CRC32 crc, String operationId) {
            this.data = data;
            this.crc = crc;
            this.operationId = operationId;
        }

        public String operationId() { return operationId; }
        public long changeCount() { return changeCount; }
        public long extensionCount() { return extensionCount; }
        public WriterState state() { return state; }

        public void append(ChunkChangeSet chunk) throws IOException {
            Objects.requireNonNull(chunk, "chunk");
            ensureOpen();
            requireUniqueChunk(seenChunks, chunk.chunkX(), chunk.chunkZ());

            data.writeByte(CHUNK_MARKER);
            data.writeInt(chunk.chunkX());
            data.writeInt(chunk.chunkZ());
            data.writeInt(chunk.palette().size());
            for (String state : chunk.palette()) data.writeUTF(state);

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

        public void appendExtension(HistoryExtensionFrame frame) throws IOException {
            Objects.requireNonNull(frame, "frame");
            ensureOpen();
            requireUniqueExtension(seenExtensions, frame);

            byte[] before = frame.beforePayload();
            byte[] after = frame.afterPayload();
            if (before.length > MAX_EXTENSION_PAYLOAD_BYTES || after.length > MAX_EXTENSION_PAYLOAD_BYTES) {
                throw new IllegalArgumentException(
                        "History extension payload exceeds " + MAX_EXTENSION_PAYLOAD_BYTES + " bytes");
            }

            data.writeByte(EXTENSION_MARKER);
            data.writeUTF(frame.typeId());
            data.writeInt(frame.chunkX());
            data.writeInt(frame.chunkZ());
            data.writeLong(frame.localKey());
            writePayload(before);
            writePayload(after);

            try {
                extensionCount = Math.addExact(extensionCount, 1);
            } catch (ArithmeticException e) {
                throw new IOException("History extension count overflow", e);
            }
        }

        private void writePayload(byte[] payload) throws IOException {
            data.writeInt(payload.length);
            data.write(payload);
        }

        public long commit() throws IOException {
            ensureOpen();
            data.writeByte(COMMIT_MARKER);
            data.writeLong(changeCount);
            data.writeLong(extensionCount);
            long checksum = crc.getValue();
            data.writeLong(checksum);
            data.flush();
            state = WriterState.COMMITTED;
            return changeCount;
        }

        public void abort() {
            if (state == WriterState.OPEN) state = WriterState.ABORTED;
        }

        private void ensureOpen() {
            if (state != WriterState.OPEN) {
                throw new IllegalStateException("History stream writer is already finished: " + state);
            }
        }
    }

    public enum WriterState {
        OPEN,
        COMMITTED,
        ABORTED
    }
}
