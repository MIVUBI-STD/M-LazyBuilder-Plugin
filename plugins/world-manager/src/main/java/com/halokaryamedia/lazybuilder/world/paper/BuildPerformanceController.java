package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * Event-driven build-server optimizations for LazyBuilder-managed worlds.
 *
 * <p>This intentionally avoids background scanners. Managed worlds already disable
 * natural spawning and random ticks through {@code BuildReadyPolicy}; this layer only
 * handles entity AI/breeding that can remain expensive after entities already exist or
 * are explicitly spawned by builders.</p>
 */
public final class BuildPerformanceController implements Listener {
    public static final String DECORATIVE_TAG = "lazybuilder_decorative";
    public static final String GAMEPLAY_AI_TAG = "lazybuilder_gameplay_ai";

    private final JavaPlugin plugin;
    private final WorldRegistry registry;
    private final boolean allMobAiSuppression;
    private final boolean villagerAiSuppression;
    private final boolean breedingSuppression;
    private final boolean scanLoadedEntitiesOnEnable;

    public BuildPerformanceController(JavaPlugin plugin, WorldRegistry registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.allMobAiSuppression = plugin.getConfig().getBoolean(
                "world-manager.build-performance.disable-all-mob-ai", true);
        this.villagerAiSuppression = plugin.getConfig().getBoolean(
                "world-manager.build-performance.disable-villager-ai", true);
        this.breedingSuppression = plugin.getConfig().getBoolean(
                "world-manager.build-performance.disable-breeding", true);
        this.scanLoadedEntitiesOnEnable = plugin.getConfig().getBoolean(
                "world-manager.build-performance.scan-loaded-entities-on-enable", true);
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (!scanLoadedEntitiesOnEnable) return;

        int optimized = 0;
        for (World world : Bukkit.getWorlds()) {
            if (!isManagedBuildWorld(world)) continue;
            for (Entity entity : world.getEntities()) {
                if (applyEntityPolicy(entity)) optimized++;
            }
        }
        if (optimized > 0) {
            plugin.getLogger().info("Build performance optimized AI for " + optimized + " loaded entities.");
        }
    }

    public void stop() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!isManagedBuildWorld(event.getLocation().getWorld())) return;
        applyEntityPolicy(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!isManagedBuildWorld(event.getWorld())) return;
        for (Entity entity : event.getChunk().getEntities()) {
            applyEntityPolicy(entity);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!breedingSuppression || !isManagedBuildWorld(event.getEntity().getWorld())) return;
        event.setCancelled(true);
    }

    private boolean applyEntityPolicy(Entity entity) {
        if (!(entity instanceof Mob mob)) return false;

        // Explicit gameplay opt-out always wins, even when the aggressive build profile is enabled.
        if (mob.getScoreboardTags().contains(GAMEPLAY_AI_TAG)) {
            if (!mob.hasAI()) mob.setAI(true);
            return false;
        }

        boolean decorative = mob.getScoreboardTags().contains(DECORATIVE_TAG);
        boolean suppressVillager = villagerAiSuppression && mob instanceof Villager;
        boolean suppress = allMobAiSuppression || suppressVillager || decorative;
        if (!suppress || !mob.hasAI()) return false;

        mob.setAI(false);
        return true;
    }

    private boolean isManagedBuildWorld(World world) {
        return world != null && registry.findByFolderName(world.getName()).isPresent();
    }
}
