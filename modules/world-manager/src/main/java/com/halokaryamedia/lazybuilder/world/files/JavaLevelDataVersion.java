package com.halokaryamedia.lazybuilder.world.files;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;
import java.util.zip.GZIPInputStream;

/** Minimal bounded reader for Java level.dat DataVersion. */
final class JavaLevelDataVersion {
    static final int JAVA_1_21_4 = 4189;

    private static final long MAX_DECOMPRESSED_BYTES = 16L * 1024L * 1024L;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_COLLECTION_LENGTH = 1_000_000;

    private JavaLevelDataVersion() {
    }

    static OptionalInt read(Path levelDat) {
        try (InputStream raw = Files.newInputStream(levelDat);
             InputStream gzip = new GZIPInputStream(raw);
             DataInputStream in = new DataInputStream(new LimitedInputStream(gzip, MAX_DECOMPRESSED_BYTES))) {
            int rootType = in.readUnsignedByte();
            if (rootType != 10) return OptionalInt.empty();
            in.readUTF(); // root name
            Integer version = readCompound(in, 0);
            return version == null ? OptionalInt.empty() : OptionalInt.of(version);
        } catch (IOException | RuntimeException malformed) {
            // Version detection is a trust gate, not a validity parser. Any malformed,
            // oversized, or unreadable level.dat simply loses the native fast path and
            // is normalized through the converter instead.
            return OptionalInt.empty();
        }
    }

    private static Integer readCompound(DataInputStream in, int depth) throws IOException {
        requireDepth(depth);
        while (true) {
            int type;
            try {
                type = in.readUnsignedByte();
            } catch (EOFException eof) {
                throw new IOException("Unexpected end of level.dat compound", eof);
            }
            if (type == 0) return null;
            String name = in.readUTF();
            if (type == 3) {
                int value = in.readInt();
                if ("DataVersion".equals(name)) return value;
                continue;
            }
            if (type == 10) {
                Integer nested = readCompound(in, depth + 1);
                if (nested != null) return nested;
                continue;
            }
            skipPayload(in, type, depth + 1);
        }
    }

    private static void skipPayload(DataInputStream in, int type, int depth) throws IOException {
        requireDepth(depth);
        switch (type) {
            case 0 -> { }
            case 1 -> in.skipNBytes(1);
            case 2 -> in.skipNBytes(2);
            case 3, 5 -> in.skipNBytes(4);
            case 4, 6 -> in.skipNBytes(8);
            case 7 -> in.skipNBytes(readLength(in, "byte array"));
            case 8 -> in.readUTF();
            case 9 -> {
                int elementType = in.readUnsignedByte();
                int length = readLength(in, "list");
                if (elementType == 0 && length != 0) throw new IOException("Invalid NBT list type");
                for (int i = 0; i < length; i++) skipPayload(in, elementType, depth + 1);
            }
            case 10 -> readCompound(in, depth + 1);
            case 11 -> in.skipNBytes(scaledLength(readLength(in, "int array"), Integer.BYTES));
            case 12 -> in.skipNBytes(scaledLength(readLength(in, "long array"), Long.BYTES));
            default -> throw new IOException("Unsupported NBT tag type: " + type);
        }
    }

    private static int readLength(DataInputStream in, String label) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_COLLECTION_LENGTH) {
            throw new IOException("Invalid " + label + " length: " + length);
        }
        return length;
    }

    private static long scaledLength(int length, int width) throws IOException {
        try {
            return Math.multiplyExact((long) length, width);
        } catch (ArithmeticException overflow) {
            throw new IOException("NBT collection length overflow", overflow);
        }
    }

    private static void requireDepth(int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT nesting depth exceeds limit");
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private long consumed;

        private LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            ensureAvailable(1);
            int value = super.read();
            if (value >= 0) consumed++;
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) return 0;
            ensureAvailable(1);
            int allowed = (int) Math.min(length, limit - consumed);
            int read = super.read(bytes, offset, allowed);
            if (read > 0) consumed += read;
            return read;
        }

        @Override
        public long skip(long count) throws IOException {
            if (count <= 0) return 0;
            ensureAvailable(1);
            long allowed = Math.min(count, limit - consumed);
            long skipped = super.skip(allowed);
            if (skipped > 0) consumed += skipped;
            return skipped;
        }

        private void ensureAvailable(long requested) throws IOException {
            if (requested > 0 && consumed >= limit) {
                throw new IOException("level.dat exceeds bounded decompressed size");
            }
        }
    }
}
