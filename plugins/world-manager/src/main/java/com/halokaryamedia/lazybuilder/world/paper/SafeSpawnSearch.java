package com.halokaryamedia.lazybuilder.world.paper;

import java.util.Objects;
import java.util.Optional;

/**
 * Bounded, deterministic candidate search for an unsafe world spawn.
 * Contains no Bukkit state; the Paper adapter owns actual block safety.
 */
final class SafeSpawnSearch {
    static final int HORIZONTAL_RADIUS = 3;
    static final int VERTICAL_RADIUS = 3;

    private SafeSpawnSearch() { }

    record Position(int x, int y, int z) { }

    @FunctionalInterface
    interface Safety {
        boolean canStand(int x, int feetY, int z);
    }

    @FunctionalInterface
    interface Surface {
        int feetY(int x, int z);
    }

    static Optional<Position> nearby(int x, int y, int z,
            int minFeetY, int maxFeetY, Safety safety) {
        Objects.requireNonNull(safety, "safety");
        if (minFeetY > maxFeetY) return Optional.empty();
        for (int radius = 0; radius <= HORIZONTAL_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int v = 0; v <= VERTICAL_RADIUS; v++) {
                        int above = y + v;
                        if (above >= minFeetY && above <= maxFeetY && safety.canStand(x + dx, above, z + dz)) {
                            return Optional.of(new Position(x + dx, above, z + dz));
                        }
                        if (v != 0) {
                            int below = y - v;
                            if (below >= minFeetY && below <= maxFeetY && safety.canStand(x + dx, below, z + dz)) {
                                return Optional.of(new Position(x + dx, below, z + dz));
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    static Optional<Position> surface(int x, int z, int minFeetY, int maxFeetY,
            Surface heights, Safety safety) {
        Objects.requireNonNull(heights, "heights");
        Objects.requireNonNull(safety, "safety");
        if (minFeetY > maxFeetY) return Optional.empty();
        for (int radius = 0; radius <= HORIZONTAL_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int feetY = heights.feetY(x + dx, z + dz);
                    if (feetY >= minFeetY && feetY <= maxFeetY
                            && safety.canStand(x + dx, feetY, z + dz)) {
                        return Optional.of(new Position(x + dx, feetY, z + dz));
                    }
                }
            }
        }
        return Optional.empty();
    }
}
