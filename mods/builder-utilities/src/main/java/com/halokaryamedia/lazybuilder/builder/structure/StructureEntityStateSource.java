package com.halokaryamedia.lazybuilder.builder.structure;

import java.io.IOException;

/**
 * Reads the durable ENTITY extension state for one target slot.
 * Implementations should return {@link EntityExtensionPayload#encode()} bytes.
 */
@FunctionalInterface
public interface StructureEntityStateSource {
    byte[] read(long entityKey, StructurePlacement.WorldPositionD position) throws IOException;
}
