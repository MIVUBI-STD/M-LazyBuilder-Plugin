package com.halokaryamedia.lazybuilder.builder.region;

import java.util.List;

/** Region that can provide tighter deterministic chunk work than its global bounds. */
public interface ChunkPlannableRegion extends BuilderRegion {
    List<ChunkWorkUnit> workUnits();
}
