package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.world.application.WorldGameMode;
import com.halokaryamedia.lazybuilder.world.application.WorldLocationGateway;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

/** Paper implementation of server-authoritative X/Z map teleport resolution. */
public final class PaperWorldLocationGateway implements WorldLocationGateway {
    private final Server server;

    public PaperWorldLocationGateway(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public ResolvedLocation teleportToSafeSurface(
            UUID playerId,
            WorldRecord record,
            int blockX,
            int blockZ,
            WorldGameMode gameMode
    ) {
        requirePrimaryThread();
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(gameMode, "gameMode");
        Player player = requireOnlinePlayer(playerId);
        World world = requireLoadedWorld(record);

        Location destination;
        if (record.kind() == WorldKind.VOID) {
            destination = PaperSafeLanding.resolveSpawn(world, record);
        } else {
            Location borderProbe = new Location(world, blockX + 0.5D, world.getMinHeight() + 1.0D, blockZ + 0.5D);
            if (!world.getWorldBorder().isInside(borderProbe)) {
                throw new IllegalArgumentException("Requested map location is outside the world border");
            }
            destination = resolveSafeSurface(world, blockX, blockZ, player);
        }

        if (!player.teleport(destination)) {
            throw new IllegalStateException("Paper rejected map teleport for player " + player.getName());
        }
        player.setGameMode(GameMode.valueOf(gameMode.name()));
        return new ResolvedLocation(destination.getX(), destination.getY(), destination.getZ());
    }

    private static Location resolveSafeSurface(World world, int blockX, int blockZ, Player player) {
        int highest = world.getHighestBlockYAt(blockX, blockZ, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int maxFeetY = Math.min(highest + 1, world.getMaxHeight() - 2);
        int minFeetY = world.getMinHeight() + 1;
        for (int feetY = maxFeetY; feetY >= minFeetY; feetY--) {
            if (!PaperSafeLanding.canLand(world, blockX, feetY, blockZ)) continue;
            return new Location(
                    world,
                    blockX + 0.5D,
                    feetY,
                    blockZ + 0.5D,
                    player.getLocation().getYaw(),
                    player.getLocation().getPitch()
            );
        }
        throw new IllegalStateException("No safe surface found at map location X=" + blockX + " Z=" + blockZ);
    }

    private Player requireOnlinePlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Player player = server.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            throw new IllegalStateException("Player is not online: " + playerId);
        }
        return player;
    }

    private World requireLoadedWorld(WorldRecord record) {
        World world = server.getWorld(record.folderName());
        if (world == null) throw new IllegalStateException("Target world is not loaded: " + record.folderName());
        return world;
    }

    private static void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Paper world/location operations must run on the primary server thread");
        }
    }
}
