package com.halokaryamedia.lazybuilder.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.BitSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Disk codec and atomic persistence for one sparse map-surface region. */
final class MapSurfaceRegionStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapSurfaceRegionStore.class);
    private static final int FORMAT_VERSION = 5;
    private static final int REGION_CAPACITY = 128 * 128;

    private MapSurfaceRegionStore() {}

    static ClientMapSurfaceCache.RegionSnapshot read(Path source) {
        if (source == null || !Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            return ClientMapSurfaceCache.RegionSnapshot.EMPTY;
        }
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Unsafe LazyBuilder map region cache path: " + source);
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(source))))) {
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                // Treat newer and unknown region formats as unsupported, not
                // empty: overwriting them would discard already explored data.
                throw new IOException("Unsupported LazyBuilder map region format: " + version);
            }
            int count = in.readInt();
            if (count < 0 || count > REGION_CAPACITY) {
                throw new IOException("Invalid LazyBuilder map region entry count: " + count);
            }

            BitSet seen = new BitSet(REGION_CAPACITY);
            int[] indices = new int[count];
            int[] colors = new int[count];
            int[] heights = new int[count];
            for (int i = 0; i < count; i++) {
                int index = in.readUnsignedShort();
                if (index >= REGION_CAPACITY || seen.get(index)) {
                    throw new IOException("Invalid or repeated LazyBuilder map region index: " + index);
                }
                seen.set(index);
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
        Path temporary = null;
        try {
            if (parent == null) throw new IOException("Map region destination has no parent");
            Files.createDirectories(parent);
            if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Unsafe map region storage directory: " + parent);
            }
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Refusing unsafe map region destination: " + destination);
            }
            // Unique same-directory staging prevents two callers and legacy .tmp
            // files from overwriting each other or touching unrelated user data.
            temporary = Files.createTempFile(parent, "surface-region-", ".tmp");
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
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException error) {
            LOGGER.warn("Could not persist LazyBuilder map region: {}", destination, error);
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupFailure) {
                    LOGGER.debug("Could not clean map region staging: {}", temporary, cleanupFailure);
                }
            }
        }
    }

    static Path regionFile(Path directory, long regionKey) {
        int regionX = (int) (regionKey >> 32);
        int regionZ = (int) regionKey;
        return directory.resolve("r." + regionX + "." + regionZ + ".surface.gz");
    }
}
