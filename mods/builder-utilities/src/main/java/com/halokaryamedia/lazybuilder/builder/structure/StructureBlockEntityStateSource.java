package com.halokaryamedia.lazybuilder.builder.structure;

import java.io.IOException;

@FunctionalInterface
public interface StructureBlockEntityStateSource {
    byte[] read(int worldX, int y, int worldZ) throws IOException;
}
