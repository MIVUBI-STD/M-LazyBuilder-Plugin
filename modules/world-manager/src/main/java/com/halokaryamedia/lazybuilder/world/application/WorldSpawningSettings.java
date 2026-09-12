package com.halokaryamedia.lazybuilder.world.application;

/** Canonical spawning snapshot derived from the loaded Paper world. */
public record WorldSpawningSettings(
        boolean naturalSpawning,
        boolean animals,
        boolean monsters,
        boolean ambient,
        boolean water,
        boolean patrol,
        boolean wanderingTrader,
        boolean insomnia,
        boolean warden,
        boolean raids
) {
}
