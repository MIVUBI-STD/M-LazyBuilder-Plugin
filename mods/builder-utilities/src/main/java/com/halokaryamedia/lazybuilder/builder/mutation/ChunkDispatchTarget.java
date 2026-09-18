package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import java.io.IOException;

@FunctionalInterface
public interface ChunkDispatchTarget {
    ChunkDispatchOutcome dispatch(ChunkChangeSet chunk) throws IOException;
}
