package com.halokaryamedia.lazybuilder.builder.structure;

@FunctionalInterface
public interface StructureSourceResolver {
    StructureSnapshot resolve(String sourceId);
}
