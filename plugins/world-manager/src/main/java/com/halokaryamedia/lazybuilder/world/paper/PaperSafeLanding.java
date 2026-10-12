package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Objects;
import java.util.Set;

/**
 * Paper-owned landing safety shared by world-spawn and map teleport.
 * Reads blocks but never creates platforms, rewrites spawn or mutates worlds.
 */
final class PaperSafeLanding {
    private static final Set<Material> HAZARDS = Set.of(
            Material.CACTUS,
            Material.MAGMA_BLOCK,
            Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.LAVA,
            Material.POWDER_SNOW,
            Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE
    );

    private PaperSafeLanding() { }

    static Location resolveSpawn(World world, WorldRecord record) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(record, "record");
        Location recorded = world.getSpawnLocation();
        int x = recorded.getBlockX();
        int y = recorded.getBlockY();
        int z = recorded.getBlockZ();
        int min = world.getMinHeight() + 1;
        int max = world.getMaxHeight() - 2;

        var near = SafeSpawnSearch.nearby(x, y, z, min, max,
                (px, py, pz) -> canLand(world, px, py, pz));
        if (near.isPresent()) return centered(recorded, near.get());

        // An imported Java world's stored Y may be obsolete or underground.
        // A tightly bounded surface search is safer than an arbitrary distant
        // teleport. Void worlds have an intentional platform and must not
        // silently fall back to some unrelated generated surface.
        if (record.kind() != WorldKind.VOID) {
            var surface = SafeSpawnSearch.surface(x, z, min, max,
                    (px, pz) -> world.getHighestBlockYAt(
                            px, pz, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1,
                    (px, py, pz) -> canLand(world, px, py, pz));
            if (surface.isPresent()) return centered(recorded, surface.get());
        }
        throw new IllegalStateException(
                "No safe landing was found near the world spawn. "
                        + "The player was not moved and the saved spawn was not changed. "
                        + "Check the world spawn and surrounding blocks.");
    }

    /** Never accept liquid, harmful materials, or occupied player headroom. */
    static boolean canLand(World world, int x, int feetY, int z) {
        if (feetY <= world.getMinHeight() || feetY >= world.getMaxHeight() - 1) return false;
        Location destination = new Location(world, x + 0.5D, feetY, z + 0.5D);
        if (!world.getWorldBorder().isInside(destination)) return false;

        Block floor = world.getBlockAt(x, feetY - 1, z);
        Block feet = world.getBlockAt(x, feetY, z);
        Block head = world.getBlockAt(x, feetY + 1, z);
        if (!floor.getType().isSolid() || floor.isPassable() || HAZARDS.contains(floor.getType())) {
            return false;
        }
        return isClear(feet) && isClear(head);
    }

    private static boolean isClear(Block block) {
        return block.isPassable() && !block.isLiquid() && !HAZARDS.contains(block.getType());
    }

    private static Location centered(Location recorded, SafeSpawnSearch.Position position) {
        return new Location(recorded.getWorld(),
                position.x() + 0.5D, position.y(), position.z() + 0.5D,
                recorded.getYaw(), recorded.getPitch());
    }
}
