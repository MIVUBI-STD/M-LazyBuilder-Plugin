package com.halokaryamedia.lazybuilder.world.registry;

import java.io.IOException;
import java.util.List;

/** Durable persistence boundary for the canonical WorldRegistry. */
public interface WorldRegistryPersistence {
    List<WorldRecord> load() throws IOException;

    void save(List<WorldRecord> worlds) throws IOException;
}
