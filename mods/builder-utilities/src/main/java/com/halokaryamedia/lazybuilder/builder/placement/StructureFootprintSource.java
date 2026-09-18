package com.halokaryamedia.lazybuilder.builder.placement;

@FunctionalInterface
public interface StructureFootprintSource {
    StructureFootprint footprintFor(String sourceId);
}
