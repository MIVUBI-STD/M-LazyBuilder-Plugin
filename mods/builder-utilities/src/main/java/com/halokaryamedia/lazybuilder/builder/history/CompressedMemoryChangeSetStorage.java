package com.halokaryamedia.lazybuilder.builder.history;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class CompressedMemoryChangeSetStorage extends MemoryChangeSetStorage {
    private final int compressionLevel;

    public CompressedMemoryChangeSetStorage() {
        this(Deflater.BEST_SPEED);
    }

    public CompressedMemoryChangeSetStorage(int compressionLevel) {
        if (compressionLevel < Deflater.NO_COMPRESSION || compressionLevel > Deflater.BEST_COMPRESSION) {
            throw new IllegalArgumentException("compressionLevel is outside Deflater range");
        }
        this.compressionLevel = compressionLevel;
    }

    @Override
    public HistoryStorageTier tier() {
        return HistoryStorageTier.COMPRESSED_MEMORY;
    }

    @Override
    protected OutputStream wrapOutput(ByteArrayOutputStream buffer) {
        return new DeflaterOutputStream(buffer, new Deflater(compressionLevel));
    }

    @Override
    protected InputStream wrapInput(byte[] bytes) throws IOException {
        return new InflaterInputStream(new ByteArrayInputStream(bytes));
    }
}
