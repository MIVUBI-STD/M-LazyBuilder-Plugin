package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.world.application.BuildReadyPolicy;
import com.halokaryamedia.lazybuilder.world.application.GameRuleSetting;
import com.halokaryamedia.lazybuilder.world.application.GameRuleValueType;
import com.halokaryamedia.lazybuilder.world.application.WorldDifficulty;
import com.halokaryamedia.lazybuilder.world.application.WorldGameMode;
import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeGateway;
import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeSettings;
import com.halokaryamedia.lazybuilder.world.application.WorldSpawnSetting;
import com.halokaryamedia.lazybuilder.world.application.WorldWeather;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Paper/Bukkit implementation of the World Manager runtime boundary. */
public final class PaperWorldRuntimeGateway implements WorldRuntimeGateway {
    private final Server server;
    private final Path worldRoot;
    private final Supplier<String> fallbackWorldName;

    public PaperWorldRuntimeGateway(Server server, Supplier<String> fallbackWorldName) {
        this.server = Objects.requireNonNull(server, "server");
        this.worldRoot = server.getWorldContainer().toPath().toAbsolutePath().normalize();
        this.fallbackWorldName = Objects.requireNonNull(fallbackWorldName, "fallbackWorldName");
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
            applyBuildReadyToWorld(world, policy);
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

    @Override
    public boolean isLoaded(WorldRecord record) {
        Objects.requireNonNull(record, "record");
        return server.getWorld(record.folderName()) != null;
    }

    @Override
    public void loadWorld(WorldRecord record) {
        requirePrimaryThread();
        Objects.requireNonNull(record, "record");
        if (isLoaded(record)) {
            return;
        }

        Path target = worldPath(record.folderName());
        if (!Files.isDirectory(target)) {
            throw new IllegalStateException("Managed world folder is missing: " + record.folderName());
        }

        WorldCreator creator = new WorldCreator(record.folderName());
        if (record.kind() == WorldKind.VOID) {
            creator.generator(VoidChunkGenerator.INSTANCE);
        }

        World loaded = server.createWorld(creator);
        if (loaded == null) {
            throw new IllegalStateException("Paper failed to load world: " + record.folderName());
        }
    }

    @Override
    public void unloadWorld(WorldRecord record) {
        requirePrimaryThread();
        Objects.requireNonNull(record, "record");
        World target = server.getWorld(record.folderName());
        if (target == null) {
            return;
        }

        World fallback = resolveFallbackWorld();
        if (fallback.getUID().equals(target.getUID())) {
            throw new IllegalStateException("Cannot unload the configured fallback world: " + target.getName());
        }

        Location destination = fallback.getSpawnLocation();
        for (Player player : target.getPlayers()) {
            if (!player.teleport(destination)) {
                throw new IllegalStateException("Could not move player " + player.getName() + " to fallback world");
            }
        }

        target.save();
        if (!server.unloadWorld(target, true)) {
            throw new IllegalStateException("Paper refused to unload world: " + record.folderName());
        }
    }

    @Override
    public void teleportPlayerToSpawn(UUID playerId, WorldRecord record) {
        teleportPlayerToSpawnInternal(playerId, record, null);
    }

    @Override
    public void teleportPlayerToSpawn(UUID playerId, WorldRecord record, WorldGameMode gameMode) {
        teleportPlayerToSpawnInternal(playerId, record, Objects.requireNonNull(gameMode, "gameMode"));
    }

    @Override
    public WorldRuntimeSettings readSettings(WorldRecord record) {
        requirePrimaryThread();
        World world = requireLoadedWorld(record);
        Location spawn = world.getSpawnLocation();
        List<GameRuleSetting> rules = new ArrayList<>();
        for (GameRule<?> rule : GameRule.values()) {
            Object value = world.getGameRuleValue(rule);
            if (value == null) {
                continue;
            }
            GameRuleValueType type;
            if (rule.getType().equals(Boolean.class)) {
                type = GameRuleValueType.BOOLEAN;
            } else if (rule.getType().equals(Integer.class)) {
                type = GameRuleValueType.INTEGER;
            } else {
                continue;
            }
            rules.add(new GameRuleSetting(rule.getName(), type, String.valueOf(value)));
        }
        rules.sort(Comparator.comparing(GameRuleSetting::name));

        return new WorldRuntimeSettings(
                WorldDifficulty.valueOf(world.getDifficulty().name()),
                world.getPVP(),
                currentWeather(world),
                world.getTime(),
                new WorldSpawnSetting(spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch()),
                rules
        );
    }

    @Override
    public void setDifficulty(WorldRecord record, WorldDifficulty difficulty) {
        requirePrimaryThread();
        requireLoadedWorld(record).setDifficulty(Difficulty.valueOf(Objects.requireNonNull(difficulty, "difficulty").name()));
    }

    @Override
    public void setPvp(WorldRecord record, boolean enabled) {
        requirePrimaryThread();
        requireLoadedWorld(record).setPVP(enabled);
    }

    @Override
    public void setTime(WorldRecord record, long ticks) {
        requirePrimaryThread();
        requireLoadedWorld(record).setTime(ticks);
    }

    @Override
    public void setWeather(WorldRecord record, WorldWeather weather) {
        requirePrimaryThread();
        applyWeather(requireLoadedWorld(record), Objects.requireNonNull(weather, "weather"));
    }

    @Override
    public void setGameRule(WorldRecord record, String ruleName, String value) {
        requirePrimaryThread();
        World world = requireLoadedWorld(record);
        GameRule<?> raw = GameRule.getByName(Objects.requireNonNull(ruleName, "ruleName"));
        if (raw == null) {
            throw new IllegalArgumentException("Unknown gamerule: " + ruleName);
        }
        String normalized = Objects.requireNonNull(value, "value").strip();
        if (raw.getType().equals(Boolean.class)) {
            if (!normalized.equalsIgnoreCase("true") && !normalized.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("Boolean gamerule requires true or false: " + ruleName);
            }
            setRule(world, raw, Boolean.class, Boolean.parseBoolean(normalized));
            return;
        }
        if (raw.getType().equals(Integer.class)) {
            try {
                setRule(world, raw, Integer.class, Integer.parseInt(normalized));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Integer gamerule requires a valid integer: " + ruleName, exception);
            }
            return;
        }
        throw new IllegalArgumentException("Unsupported gamerule type: " + raw.getType().getName());
    }

    @Override
    public void setSpawnToPlayer(UUID playerId, WorldRecord record) {
        requirePrimaryThread();
        Player player = requireOnlinePlayer(playerId);
        World world = requireLoadedWorld(record);
        if (!player.getWorld().getUID().equals(world.getUID())) {
            throw new IllegalStateException("Player must be inside the target world to set its spawn");
        }
        Location location = player.getLocation();
        if (!world.setSpawnLocation(location.getBlockX(), location.getBlockY(), location.getBlockZ())) {
            throw new IllegalStateException("Paper rejected world spawn update: " + record.folderName());
        }
    }

    @Override
    public void applyBuildReady(WorldRecord record, BuildReadyPolicy policy) {
        requirePrimaryThread();
        World world = requireLoadedWorld(record);
        applyBuildReadyToWorld(world, Objects.requireNonNull(policy, "policy"));
        world.save();
    }

    private void teleportPlayerToSpawnInternal(UUID playerId, WorldRecord record, WorldGameMode gameMode) {
        requirePrimaryThread();
        Player player = requireOnlinePlayer(playerId);
        World target = requireLoadedWorld(record);
        if (!player.teleport(target.getSpawnLocation())) {
            throw new IllegalStateException("Paper rejected teleport for player " + player.getName());
        }
        if (gameMode != null) {
            player.setGameMode(GameMode.valueOf(gameMode.name()));
        }
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
        Objects.requireNonNull(record, "record");
        World world = server.getWorld(record.folderName());
        if (world == null) {
            throw new IllegalStateException("Target world is not loaded: " + record.folderName());
        }
        return world;
    }

    private World resolveFallbackWorld() {
        String configured = fallbackWorldName.get();
        if (configured != null && !configured.isBlank()) {
            World fallback = server.getWorld(configured.strip());
            if (fallback == null) {
                throw new IllegalStateException("Configured fallback world is not loaded: " + configured.strip());
            }
            return fallback;
        }

        if (server.getWorlds().isEmpty()) {
            throw new IllegalStateException("No loaded world is available as fallback");
        }
        return server.getWorlds().get(0);
    }

    private void applyBuildReadyToWorld(World world, BuildReadyPolicy policy) {
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

    private static WorldWeather currentWeather(World world) {
        if (world.isThundering()) {
            return WorldWeather.THUNDER;
        }
        if (world.hasStorm()) {
            return WorldWeather.RAIN;
        }
        return WorldWeather.CLEAR;
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
    private static <T> void setRule(World world, GameRule<?> raw, Class<T> type, T value) {
        if (!raw.getType().equals(type)) {
            throw new IllegalStateException("Unexpected gamerule type for " + raw.getName() + ": " + raw.getType().getName());
        }
        if (!world.setGameRule((GameRule<T>) raw, value)) {
            throw new IllegalStateException("Paper rejected gamerule update: " + raw.getName());
        }
    }

    @SuppressWarnings("deprecation")
    private static void setBooleanRule(World world, String name, boolean value) {
        GameRule<?> raw = GameRule.getByName(name);
        if (raw == null) {
            throw new IllegalStateException("Required gamerule is unavailable: " + name);
        }
        setRule(world, raw, Boolean.class, value);
    }

    @SuppressWarnings("deprecation")
    private static void setIntegerRule(World world, String name, int value) {
        GameRule<?> raw = GameRule.getByName(name);
        if (raw == null) {
            throw new IllegalStateException("Required gamerule is unavailable: " + name);
        }
        setRule(world, raw, Integer.class, value);
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
