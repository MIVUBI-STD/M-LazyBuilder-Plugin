package com.halokaryamedia.lazybuilder.utilities.feature.movement;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MovementPolicyTest {
    @Test
    void mapsFamiliarMultipliersToBukkitFlySpeed() {
        assertEquals(0.1F, MovementFeature.toFlySpeed(1.0D), 0.0001F);
        assertEquals(0.4F, MovementFeature.toFlySpeed(4.0D), 0.0001F);
        assertEquals(1.0F, MovementFeature.toFlySpeed(10.0D), 0.0001F);
    }

    @Test
    void doesNotCreateFakeNoclipSessionForSpectator() {
        assertFalse(MovementFeature.shouldCreateNoclipSession(GameMode.SPECTATOR));
        assertTrue(MovementFeature.shouldCreateNoclipSession(GameMode.CREATIVE));
        assertTrue(MovementFeature.shouldCreateNoclipSession(GameMode.SURVIVAL));
    }
}
