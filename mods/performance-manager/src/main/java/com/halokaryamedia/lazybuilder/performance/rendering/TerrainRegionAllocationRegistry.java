package com.halokaryamedia.lazybuilder.performance.rendering;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent pure-Java allocation registry for future shared terrain region GPU arenas.
 *
 * One logical arena is owned by one 8x4x8 accounting region and one vanilla terrain layer. The
 * registry owns no GPU resource; it keeps stable allocation handles and models compaction/growth so
 * physical GPU backing can later adopt the same lifecycle without changing section ownership.
 */
public final class TerrainRegionAllocationRegistry<K> {
    private static final int REGION_XZ_SIZE = 8;
    private static final int REGION_Y_SIZE = 4;

    private final IdentityHashMap<K, Entry<K>> entries = new IdentityHashMap<>();
    private final Map<ArenaKey, Arena<K>> arenas = new HashMap<>();
    private long nextSequence;
    private long nextGeneration = 1L;
    private long allocationReuses;
    private long reallocations;
    private long compactions;
    private long arenaGrowths;
    private long allocationFailures;

    public synchronized void associate(K key, int sectionX, int sectionY, int sectionZ, int layerSlot) {
        if (key == null || layerSlot < 0) return;

        ArenaKey nextArena = arenaFor(sectionX, sectionY, sectionZ, layerSlot);
        Entry<K> entry = entries.get(key);
        if (entry == null) {
            entry = new Entry<>(key, nextSequence++, nextArena);
            entries.put(key, entry);
            return;
        }

        if (entry.arenaKey.equals(nextArena)) return;
        releaseAllocation(entry);
        entry.arenaKey = nextArena;
        if (entry.payloadBytes > 0L) ensureAllocation(entry, false);
    }

    public synchronized void recordPayload(K key, long payloadBytes) {
        Entry<K> entry = entries.get(key);
        if (entry == null) return;

        entry.payloadBytes = Math.max(0L, payloadBytes);
        if (entry.payloadBytes == 0L) {
            releaseAllocation(entry);
            return;
        }
        ensureAllocation(entry, true);
    }

    public synchronized void release(K key) {
        Entry<K> entry = entries.remove(key);
        if (entry == null) return;
        releaseAllocation(entry);
    }

    public synchronized void clear() {
        entries.clear();
        arenas.clear();
        nextSequence = 0L;
        nextGeneration = 1L;
        allocationReuses = 0L;
        reallocations = 0L;
        compactions = 0L;
        arenaGrowths = 0L;
        allocationFailures = 0L;
    }

    public synchronized Handle handle(K key) {
        Entry<K> entry = entries.get(key);
        return entry == null ? null : entry.handle;
    }

    public synchronized Snapshot snapshot() {
        long capacity = 0L;
        long used = 0L;
        long free = 0L;
        long fragmented = 0L;
        long largestArena = 0L;
        int allocations = 0;

        for (Arena<K> arena : arenas.values()) {
            TerrainRegionSuballocator.Snapshot allocator = arena.allocator.snapshot();
            capacity += allocator.capacityBytes();
            used += allocator.usedBytes();
            free += allocator.freeBytes();
            fragmented += allocator.fragmentedFreeBytes();
            largestArena = Math.max(largestArena, allocator.capacityBytes());
            allocations += allocator.allocationCount();
        }

        return new Snapshot(
                capacity,
                used,
                free,
                fragmented,
                largestArena,
                arenas.size(),
                allocations,
                allocationReuses,
                reallocations,
                compactions,
                arenaGrowths,
                allocationFailures
        );
    }

    private void ensureAllocation(Entry<K> entry, boolean countReuse) {
        long needed = TerrainRegionArenaPolicy.alignedSize(entry.payloadBytes);
        if (needed <= 0L) return;

        if (entry.handle != null
                && entry.handle.arenaKey.equals(entry.arenaKey)
                && entry.handle.sizeBytes >= needed) {
            entry.handle = entry.handle.withPayload(entry.payloadBytes);
            if (countReuse) allocationReuses++;
            return;
        }

        if (entry.handle != null) {
            releaseAllocation(entry);
            reallocations++;
        }

        Arena<K> arena = arenas.computeIfAbsent(
                entry.arenaKey,
                key -> new Arena<>(key, TerrainRegionArenaPolicy.plannedCapacity(entry.payloadBytes))
        );

        TerrainRegionSuballocator.Allocation allocation = arena.allocator.allocate(entry.key, entry.payloadBytes);
        if (allocation == null) {
            TerrainRegionSuballocator.Snapshot before = arena.allocator.snapshot();
            if (before.freeBytes() >= needed && before.fragmentedFreeBytes() > 0L) {
                compactions++;
                rebuildArena(arena, arena.capacityBytes);
                allocation = arena.allocator.allocate(entry.key, entry.payloadBytes);
            }
        }

        if (allocation == null) {
            long required = totalAlignedPayload(arena, entry);
            long grownCapacity = Math.max(
                    TerrainRegionArenaPolicy.plannedCapacity(required),
                    TerrainRegionArenaPolicy.plannedCapacity(Math.max(required, arena.capacityBytes + 1L))
            );
            if (grownCapacity <= arena.capacityBytes) {
                grownCapacity = arena.capacityBytes + TerrainRegionArenaPolicy.CAPACITY_QUANTUM;
            }
            arenaGrowths++;
            rebuildArena(arena, grownCapacity);
            allocation = arena.allocator.allocate(entry.key, entry.payloadBytes);
        }

        if (allocation == null) {
            allocationFailures++;
            entry.handle = null;
            return;
        }

        arena.members.put(entry.key, Boolean.TRUE);
        entry.handle = handleFor(entry.arenaKey, allocation);
    }

    private void releaseAllocation(Entry<K> entry) {
        Handle handle = entry.handle;
        if (handle == null) return;

        Arena<K> arena = arenas.get(handle.arenaKey);
        if (arena != null) {
            arena.allocator.release(entry.key);
            arena.members.remove(entry.key);
            if (arena.members.isEmpty()) arenas.remove(handle.arenaKey);
        }
        entry.handle = null;
    }

    private void rebuildArena(Arena<K> arena, long newCapacity) {
        List<Entry<K>> members = new ArrayList<>();
        for (K key : arena.members.keySet()) {
            Entry<K> entry = entries.get(key);
            if (entry != null && entry.payloadBytes > 0L) members.add(entry);
        }
        members.sort(Comparator.comparingLong(entry -> entry.sequence));

        arena.capacityBytes = Math.max(0L, newCapacity);
        arena.allocator = new TerrainRegionSuballocator<>(arena.capacityBytes);
        arena.members.clear();

        for (Entry<K> member : members) {
            TerrainRegionSuballocator.Allocation allocation = arena.allocator.allocate(member.key, member.payloadBytes);
            if (allocation == null) {
                allocationFailures++;
                member.handle = null;
                continue;
            }
            arena.members.put(member.key, Boolean.TRUE);
            member.handle = handleFor(member.arenaKey, allocation);
        }
    }

    private long totalAlignedPayload(Arena<K> arena, Entry<K> pending) {
        long total = TerrainRegionArenaPolicy.alignedSize(pending.payloadBytes);
        for (K key : arena.members.keySet()) {
            if (key == pending.key) continue;
            Entry<K> entry = entries.get(key);
            if (entry == null) continue;
            long aligned = TerrainRegionArenaPolicy.alignedSize(entry.payloadBytes);
            if (Long.MAX_VALUE - total < aligned) return Long.MAX_VALUE;
            total += aligned;
        }
        return total;
    }

    private Handle handleFor(ArenaKey arenaKey, TerrainRegionSuballocator.Allocation allocation) {
        return new Handle(
                arenaKey,
                allocation.offsetBytes(),
                allocation.sizeBytes(),
                allocation.payloadBytes(),
                nextGeneration++
        );
    }

    static ArenaKey arenaFor(int sectionX, int sectionY, int sectionZ, int layerSlot) {
        return new ArenaKey(
                Math.floorDiv(sectionX, REGION_XZ_SIZE),
                Math.floorDiv(sectionY, REGION_Y_SIZE),
                Math.floorDiv(sectionZ, REGION_XZ_SIZE),
                layerSlot
        );
    }

    private static final class Entry<K> {
        private final K key;
        private final long sequence;
        private ArenaKey arenaKey;
        private long payloadBytes;
        private Handle handle;

        private Entry(K key, long sequence, ArenaKey arenaKey) {
            this.key = key;
            this.sequence = sequence;
            this.arenaKey = arenaKey;
        }
    }

    private static final class Arena<K> {
        private final ArenaKey key;
        private final IdentityHashMap<K, Boolean> members = new IdentityHashMap<>();
        private long capacityBytes;
        private TerrainRegionSuballocator<K> allocator;

        private Arena(ArenaKey key, long capacityBytes) {
            this.key = key;
            this.capacityBytes = Math.max(0L, capacityBytes);
            this.allocator = new TerrainRegionSuballocator<>(this.capacityBytes);
        }
    }

    public record ArenaKey(int regionX, int regionY, int regionZ, int layerSlot) {
    }

    public record Handle(
            ArenaKey arenaKey,
            long offsetBytes,
            long sizeBytes,
            long payloadBytes,
            long generation
    ) {
        private Handle withPayload(long payloadBytes) {
            return new Handle(arenaKey, offsetBytes, sizeBytes, payloadBytes, generation);
        }
    }

    public record Snapshot(
            long plannedCapacityBytes,
            long allocatedBytes,
            long freeBytes,
            long fragmentedFreeBytes,
            long largestArenaBytes,
            int activeArenas,
            int activeAllocations,
            long allocationReuses,
            long reallocations,
            long compactions,
            long arenaGrowths,
            long allocationFailures
    ) {
    }
}
