package com.halokaryamedia.lazybuilder.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Disk codec and atomic persistence for one sparse map-surface region. */
final class MapSurfaceRegionStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapSurfaceRegionStore.class);
    private static final int FORMAT_VERSION = 5;
    private static final int REGION_CAPACITY = 128 * 128;

    private MapSurfaceRegionStore() {}

    static ClientMapSurfaceCache.RegionSnapshot read(Path source) {
        if (source == null || !Files.isRegularFile(source)) {
            return ClientMapSurfaceCache.RegionSnapshot.EMPTY;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(source))))) {
            if (in.readInt() != FORMAT_VERSION) return ClientMapSurfaceCache.RegionSnapshot.EMPTY;
            int count = in.readInt();
            if (count < 0 || count > REGION_CAPACITY) {
                throw new IOException("Invalid LazyBuilder map region entry count: " + count);
            }

            int[] indices = new int[count];
            int[] colors = new int[count];
            int[] heights = new int[count];
            for (int i = 0; i < count; i++) {
                int index = in.readUnsignedShort();
                if (index >= REGION_CAPACITY) {
                    throw new IOException("Invalid LazyBuilder map region index: " + index);
                }
                indices[i] = index;
                colors[i] = in.readInt();
                heights[i] = in.readInt();
            }
            return new ClientMapSurfaceCache.RegionSnapshot(indices, colors, heights, 0L);
        } catch (IOException error) {
            throw new IllegalStateException("Could not read LazyBuilder map region " + source, error);
        }
    }

    static boolean writeWithRetry(Path destination, ClientMapSurfaceCache.RegionSnapshot snapshot) {
        if (write(destination, snapshot)) return true;
        return write(destination, snapshot);
    }

    private static boolean write(Path destination, ClientMapSurfaceCache.RegionSnapshot snapshot) {
        Path parent = destination.getParent();
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        try {
            if (parent != null) Files.createDirectories(parent);
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(temporary))))) {
                out.writeInt(FORMAT_VERSION);
                out.writeInt(snapshot.size());
                for (int i = 0; i < snapshot.size(); i++) {
                    out.writeShort(snapshot.indices()[i]);
                    out.writeInt(snapshot.colors()[i]);
                    out.writeInt(snapshot.heights()[i]);
                }
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException error) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
            LOGGER.warn("Could not persist LazyBuilder map region: {}", destination, error);
            return false;
        }
    }

    static Path regionFile(Path directory, long regionKey) {
        int regionX = (int) (regionKey >> 32);
        int regionZ = (int) regionKey;
        return directory.resolve("r." + regionX + "." + regionZ + ".surface.gz");
    }
}
