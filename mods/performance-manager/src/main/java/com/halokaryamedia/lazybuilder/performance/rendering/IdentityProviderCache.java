package com.halokaryamedia.lazybuilder.performance.rendering;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Copy-on-write identity cache for rare registrations and hot lock-free reads.
 *
 * Minecraft registry objects are identity-owned. Registration is uncommon compared with render
 * lookups, so replacing an immutable snapshot avoids synchronization in the meshing hot path.
 */
public final class IdentityProviderCache<K, V> {
    private volatile Map<K, V> snapshot = Map.of();

    public V get(K key) {
        return snapshot.get(key);
    }

    public synchronized void register(V value, K[] keys) {
        if (value == null || keys == null || keys.length == 0) return;

        IdentityHashMap<K, V> updated = new IdentityHashMap<>(snapshot);
        for (K key : keys) {
            if (key != null) updated.put(key, value);
        }
        snapshot = updated;
    }

    int size() {
        return snapshot.size();
    }
}
