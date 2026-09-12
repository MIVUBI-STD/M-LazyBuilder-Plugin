package com.halokaryamedia.lazybuilder.world.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuildReadyPolicyTest {
    @Test
    void defaultsMatchApprovedBuilderSafeProfile() {
        BuildReadyPolicy policy = BuildReadyPolicy.defaults();

        assertFalse(policy.structuresEnabled());
        assertFalse(policy.naturalMobSpawning());
        assertEquals(WorldGameMode.CREATIVE, policy.defaultGameMode());
        assertEquals(WorldDifficulty.NORMAL, policy.difficulty());
        assertFalse(policy.pvpEnabled());
        assertEquals(WorldWeather.CLEAR, policy.weather());
        assertFalse(policy.weatherCycle());
        assertFalse(policy.daylightCycle());
        assertEquals(BuildReadyPolicy.DAY_TIME_TICKS, policy.timeOfDayTicks());
        assertFalse(policy.fireTick());
        assertFalse(policy.mobGriefing());
        assertEquals(0, policy.randomTickSpeed());
        assertFalse(policy.patrolSpawning());
        assertFalse(policy.wanderingTraderSpawning());
        assertFalse(policy.insomniaEnabled());
        assertFalse(policy.wardenSpawning());
        assertFalse(policy.raidsEnabled());
        assertFalse(policy.spawnChunksPersistent());
    }

    @Test
    void rejectsInvalidTimeAndRandomTickValues() {
        BuildReadyPolicy defaults = BuildReadyPolicy.defaults();

        assertThrows(IllegalArgumentException.class, () -> new BuildReadyPolicy(
                defaults.structuresEnabled(), defaults.naturalMobSpawning(), defaults.defaultGameMode(),
                defaults.difficulty(), defaults.pvpEnabled(), defaults.weather(), defaults.weatherCycle(),
                defaults.daylightCycle(), 24000L, defaults.fireTick(), defaults.mobGriefing(),
                defaults.randomTickSpeed(), defaults.patrolSpawning(), defaults.wanderingTraderSpawning(),
                defaults.insomniaEnabled(), defaults.wardenSpawning(), defaults.raidsEnabled(),
                defaults.spawnChunksPersistent()
        ));

        assertThrows(IllegalArgumentException.class, () -> new BuildReadyPolicy(
                defaults.structuresEnabled(), defaults.naturalMobSpawning(), defaults.defaultGameMode(),
                defaults.difficulty(), defaults.pvpEnabled(), defaults.weather(), defaults.weatherCycle(),
                defaults.daylightCycle(), defaults.timeOfDayTicks(), defaults.fireTick(), defaults.mobGriefing(),
                -1, defaults.patrolSpawning(), defaults.wanderingTraderSpawning(), defaults.insomniaEnabled(),
                defaults.wardenSpawning(), defaults.raidsEnabled(), defaults.spawnChunksPersistent()
        ));
    }
}
