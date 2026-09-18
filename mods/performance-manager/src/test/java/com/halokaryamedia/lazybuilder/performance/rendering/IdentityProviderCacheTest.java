package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

final class IdentityProviderCacheTest {
    @Test
    void lookupUsesIdentityInsteadOfEquals() {
        IdentityProviderCache<Key, String> cache = new IdentityProviderCache<>();
        Key registered = new Key(7);
        Key equalButDistinct = new Key(7);

        cache.register("provider", new Key[]{registered});

        assertEquals(1, cache.size());
        assertEquals("provider", cache.get(registered));
        assertNull(cache.get(equalButDistinct));
    }

    @Test
    void laterRegistrationReplacesProviderForSameIdentity() {
        IdentityProviderCache<Object, Object> cache = new IdentityProviderCache<>();
        Object key = new Object();
        Object first = new Object();
        Object second = new Object();

        cache.register(first, new Object[]{key});
        cache.register(second, new Object[]{key});

        assertSame(second, cache.get(key));
        assertEquals(1, cache.size());
    }

    private record Key(int value) {
    }
}
