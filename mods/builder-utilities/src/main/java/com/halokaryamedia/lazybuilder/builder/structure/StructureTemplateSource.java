package com.halokaryamedia.lazybuilder.builder.structure;

@FunctionalInterface
public interface StructureTemplateSource {
    StructureTemplate resolve(String sourceId);
}
