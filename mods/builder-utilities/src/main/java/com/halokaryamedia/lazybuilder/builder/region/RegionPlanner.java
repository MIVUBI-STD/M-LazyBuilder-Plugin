package com.halokaryamedia.lazybuilder.builder.region;

import java.util.List;

public interface RegionPlanner {
    List<ChunkWorkUnit> plan(BuilderRegion region);
}
