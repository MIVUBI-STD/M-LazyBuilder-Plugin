package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.world.application.BuildReadyPolicy;
import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeGateway;
import com.halokaryamedia.lazybuilder.world.application.WorldWeather;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;

/** Paper/Bukkit implementation of the World Manager runtime creation boundary. */
public final class PaperWorldRuntimeGateway implements WorldRuntimeGateway {
    private final Server server;
    private final Path worldRoot;

    public PaperWorldRuntimeGateway(Server server) {
        this.server = Objects.requireNonNull(server, "server");
        this.worldRoot = server.getWorldContainer().toPath().toAbsolutePath().normalize();
    }

    @Override
    public void createNewWorld(WorldRecord record, BuildReadyPolicy policy) {
        requirePrimaryThread();
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(policy, "policy");

        Path target = worldPath(record.folderName());
        if (server.getWorld(record.folderName()) != null || Files.exists(target)) {
            throw new IllegalArgumentException("World already exists on the server: " + record.folderName());
        }

        WorldCreator creator = new WorldCreator(record.folderName())
                .environment(World.Environment.NORMAL)
                .generateStructures(policy.structuresEnabled())
                .keepSpawnInMemory(policy.spawnChunksPersistent());

        if (record.kind() == WorldKind.FLAT) {
            creator.type(WorldType.FLAT);
        } else if (record.kind() == WorldKind.VOID) {
            creator.type(WorldType.NORMAL).generator(VoidChunkGenerator.INSTANCE);
        } else {
            throw new IllegalArgumentException("Unsupported Create World kind: " + record.kind());
        }

        World world = server.createWorld(creator);
        if (world == null) {
            throw new IllegalStateException("Paper returned no world for: " + record.folderName());
        }

        try {
            applyBuildReady(world, policy);
            if (record.kind() == WorldKind.VOID) {
                createVoidSpawnPlatform(world);
            }
            world.save();
        } catch (RuntimeException exception) {
            try {
                rollbackCreatedWorld(record);
            } catch (RuntimeException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        }
    }

    @Override
    public void rollbackCreatedWorld(WorldRecord record) {
        requirePrimaryThread();
        Objects.requireNonNull(record, "record");

        World loaded = server.getWorld(record.folderName());
        if (loaded != null) {
            if (!loaded.getPlayers().isEmpty()) {
                throw new IllegalStateException("Cannot rollback a new world while players are inside it");
            }
            if (!server.unloadWorld(loaded, false)) {
                throw new IllegalStateException("Paper refused to unload rollback world: " + record.folderName());
            }
        }

        Path target = worldPath(record.folderName());
        if (Files.notExists(target)) {
            return;
        }
        try (var paths = Files.walk(target)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new DeleteFailure(exception);
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to clean rollback world: " + record.folderName(), exception);
        } catch (DeleteFailure failure) {
            throw new IllegalStateException("Failed to clean rollback world: " + record.folderName(), failure.getCause());
        }
    }

    private void applyBuildReady(World world, BuildReadyPolicy policy) {
        world.setDifficulty(Difficulty.valueOf(policy.difficulty().name()));
        world.setPVP(policy.pvpEnabled());
        world.setSpawnFlags(policy.naturalMobSpawning(), policy.naturalMobSpawning());
        world.setKeepSpawnInMemory(policy.spawnChunksPersistent());
        world.setTime(policy.timeOfDayTicks());
        applyWeather(world, policy.weather());

        setBooleanRule(world, "doMobSpawning", policy.naturalMobSpawning());
        setBooleanRule(world, "doWeatherCycle", policy.weatherCycle());
        setBooleanRule(world, "doDaylightCycle", policy.daylightCycle());
        setBooleanRule(world, "doFireTick", policy.fireTick());
        setBooleanRule(world, "mobGriefing", policy.mobGriefing());
        setIntegerRule(world, "randomTickSpeed", policy.randomTickSpeed());
        setBooleanRule(world, "doPatrolSpawning", policy.patrolSpawning());
        setBooleanRule(world, "doTraderSpawning", policy.wanderingTraderSpawning());
        setBooleanRule(world, "doInsomnia", policy.insomniaEnabled());
        setBooleanRule(world, "doWardenSpawning", policy.wardenSpawning());
        setBooleanRule(world, "disableRaids", !policy.raidsEnabled());
    }

    private static void applyWeather(World world, WorldWeather weather) {
        switch (weather) {
            case CLEAR -> {
                world.setStorm(false);
                world.setThundering(false);
            }
            case RAIN -> {
                world.setStorm(true);
                world.setThundering(false);
            }
            case THUNDER -> {
                world.setStorm(true);
                world.setThundering(true);
            }
        }
    }

    private static void createVoidSpawnPlatform(World world) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getBlockAt(x, VoidChunkGenerator.PLATFORM_Y, z).setType(Material.STONE, false);
            }
        }
        if (!world.setSpawnLocation(0, VoidChunkGenerator.SPAWN_Y, 0)) {
            throw new IllegalStateException("Failed to set Void World spawn location");
        }
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    private static <T> void setRule(World world, String name, Class<T> type, T value) {
        GameRule<?> raw = GameRule.getByName(name);
        if (raw == null) {
            throw new IllegalStateException("Required gamerule is unavailable: " + name);
        }
        if (!raw.getType().equals(type)) {
            throw new IllegalStateException("Unexpected gamerule type for " + name + ": " + raw.getType().getName());
        }
        if (!world.setGameRule((GameRule<T>) raw, value)) {
            throw new IllegalStateException("Paper rejected gamerule update: " + name);
        }
    }

    private static void setBooleanRule(World world, String name, boolean value) {
        setRule(world, name, Boolean.class, value);
    }

    private static void setIntegerRule(World world, String name, int value) {
        setRule(world, name, Integer.class, value);
    }

    private Path worldPath(String folderName) {
        Path target = worldRoot.resolve(folderName).normalize();
        if (!worldRoot.equals(target.getParent())) {
            throw new IllegalArgumentException("World folder must remain directly inside the server world container");
        }
        return target;
    }

    private static void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Paper world lifecycle operations must run on the primary server thread");
        }
    }

    private static final class DeleteFailure extends RuntimeException {
        private DeleteFailure(IOException cause) {
            super(cause);
        }
    }
}
