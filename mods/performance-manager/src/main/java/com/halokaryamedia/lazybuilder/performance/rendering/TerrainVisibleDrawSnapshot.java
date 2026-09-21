package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.client.gl.VertexBuffer;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only per-frame visible terrain draw snapshot for secondary first-party passes.
 *
 * It stores only existing VertexBuffer identity + chunk origin + layer slot. It never
 * copies mesh payloads or becomes another terrain ownership registry.
 */
public final class TerrainVisibleDrawSnapshot {
    private static volatile Snapshot current = Snapshot.EMPTY;

    private TerrainVisibleDrawSnapshot() {
    }

    public static Builder builder(int expectedEntries) {
        return new Builder(expectedEntries);
    }

    public static void publish(Snapshot snapshot) {
        current = snapshot == null ? Snapshot.EMPTY : snapshot;
    }

    public static Snapshot current() {
        return current;
    }

    public static void clear() {
        current = Snapshot.EMPTY;
    }

    public record Entry(
            VertexBuffer buffer,
            int layerSlot,
            int originX,
            int originY,
            int originZ
    ) {
        public boolean usable() {
            return buffer != null && !buffer.isClosed() && layerSlot >= 0 && layerSlot < 5;
        }
    }

    public record Snapshot(long revision, List<Entry> entries) {
        private static final Snapshot EMPTY = new Snapshot(0L, List.of());

        public Snapshot {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }
    }

    public static final class Builder {
        private final ArrayList<Entry> entries;
        private long revision;

        private Builder(int expectedEntries) {
            entries = new ArrayList<>(Math.max(0, expectedEntries));
        }

        public Builder revision(long revision) {
            this.revision = revision;
            return this;
        }

        public void add(
                VertexBuffer buffer,
                int layerSlot,
                int originX,
                int originY,
                int originZ
        ) {
            Entry entry = new Entry(buffer, layerSlot, originX, originY, originZ);
            if (entry.usable()) entries.add(entry);
        }

        public Snapshot finish() {
            return entries.isEmpty()
                    ? new Snapshot(revision, List.of())
                    : new Snapshot(revision, List.copyOf(entries));
        }
    }
}
