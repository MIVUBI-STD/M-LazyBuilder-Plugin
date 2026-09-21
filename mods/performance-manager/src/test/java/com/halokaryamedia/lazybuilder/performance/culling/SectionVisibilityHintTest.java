package com.halokaryamedia.lazybuilder.performance.culling;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SectionVisibilityHintTest {
    @AfterEach
    void clear() {
        SectionVisibilityHint.clear();
    }

    @Test
    void openSectionHintIsFailOpenOnly() {
        long key = SectionVisibilityHint.key(2, 4, -3);
        SectionVisibilityHint.publish(Set.of(key));

        assertTrue(SectionVisibilityHint.isOpen(32.5D, 64.0D, -47.5D));
        assertFalse(SectionVisibilityHint.isOpen(48.5D, 64.0D, -47.5D));
    }
}
