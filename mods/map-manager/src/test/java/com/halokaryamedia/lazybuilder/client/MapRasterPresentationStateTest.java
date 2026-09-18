package com.halokaryamedia.lazybuilder.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MapRasterPresentationStateTest {
    @Test
    void resetContentDropsImageAndForcesRefresh() {
        MapRasterPresentationState state = new MapRasterPresentationState();
        state.colors = new int[] {1, 2, 3, 4};
        state.columns = 2;
        state.rows = 2;
        state.contentDirty = false;
        state.worldTime = 120L;

        assertTrue(state.hasImage());

        state.resetContent();

        assertFalse(state.hasImage());
        assertTrue(state.contentDirty);
        assertEquals(Long.MIN_VALUE, state.worldTime);
    }

    @Test
    void invalidateLayoutForcesViewportMismatch() {
        MapRasterPresentationState state = new MapRasterPresentationState();
        state.left = 1;
        state.top = 2;
        state.right = 3;
        state.bottom = 4;

        state.invalidateLayout();

        assertEquals(Integer.MIN_VALUE, state.left);
        assertEquals(Integer.MIN_VALUE, state.top);
        assertEquals(Integer.MIN_VALUE, state.right);
        assertEquals(Integer.MIN_VALUE, state.bottom);
    }
}
