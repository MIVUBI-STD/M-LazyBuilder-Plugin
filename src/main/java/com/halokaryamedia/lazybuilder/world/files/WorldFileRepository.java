package com.halokaryamedia.lazybuilder.world.files;

import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/** Path-safe filesystem boundary for World Manager file operations. */
public interface WorldFileRepository {
    Path stageCopy(WorldRecord source, UUID operationId, WorldCopyProfile profile) throws IOException;

    /** Returns a non-existing, owned direct workspace path reserved by caller identity. */
    default Path reserveWorkspace(UUID operationId) throws IOException {
        throw new UnsupportedOperationException("Workspace reservation is not supported by this repository");
    }

    void publishStagedWorld(Path stagedWorld, String destinationFolder) throws IOException;

    void deleteWorld(WorldRecord world) throws IOException;

    void deleteWorkspace(Path workspace) throws IOException;
}
