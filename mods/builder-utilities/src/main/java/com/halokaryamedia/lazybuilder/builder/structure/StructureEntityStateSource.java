package com.halokaryamedia.lazybuilder.builder.structure;

import java.io.IOException;

@FunctionalInterface
public interface StructureEntityStateSource {
    byte[] read(long entityKey, StructurePlacement.WorldPositionD position) throws IOException;
}
