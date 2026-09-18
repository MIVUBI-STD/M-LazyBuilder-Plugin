package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;

import java.io.IOException;

public interface HistoryExtensionMutationTarget {
    byte[] read(HistoryExtensionFrame frame) throws IOException;

    void write(HistoryExtensionFrame frame, byte[] payload) throws IOException;
}
