package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
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
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

/**
 * Opt-in, per-managed-world mob performance policy.
 *
 * Only AI changes made by this controller are reversible. Legacy NoAI entities
 * without its PDC lease are never re-enabled by guessing their original state.
 * Entity unload and plugin disable release owned leases before persistence.
 */
public final class BuildPerformanceController implements Listener {
    public static final String DECORATIVE_TAG = "lazybuilder_decorative";
    public static final String GAMEPLAY_AI_TAG = "lazybuilder_gameplay_ai";
    private static final String ENABLED_WORLDS = "world-manager.build-performance.enabled-worlds";

    private final JavaPlugin plugin;
    private final WorldRegistry registry;
    private final NamespacedKey aiLeaseKey;
    private final Set<String> enabledWorldFolders;
    private final boolean allMobAiSuppression;
    private final boolean villagerAiSuppression;
    private final boolean breedingSuppression;
    private final boolean scanLoadedEntitiesOnEnable;

    public BuildPerformanceController(JavaPlugin plugin, WorldRegistry registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.aiLeaseKey = new NamespacedKey(plugin, "managed_ai_suppressed");
        Set<String> configured;
        try {
            configured = normalizeEnabledWorldFolders(plugin.getConfig().get(ENABLED_WORLDS));
        } catch (IllegalArgumentException invalid) {
            plugin.getLogger().warning("Invalid " + ENABLED_WORLDS + ": " + invalid.getMessage()
                    + ". Aggressive mob policy disabled for all worlds.");
            configured = Set.of();
        }
        this.enabledWorldFolders = configured;
        // Existing toggles describe the profile, never activation scope.
        // Configurations shipped before enabled-worlds remain inactive until an
        // operator explicitly lists individual managed world folder names.
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

        // A one-time loaded-entity pass always releases stale LazyBuilder leases,
        // even if startup suppression scanning was disabled or the world is no
        // longer managed/allowlisted. This is not a recurring entity scanner.
        int reconciled = 0;
        for (World world : Bukkit.getWorlds()) {
            boolean canSuppress = scanLoadedEntitiesOnEnable && isEnabledManagedWorld(world);
            reconciled += reconcileBatch(world.getEntities(), canSuppress, "plugin startup");
        }
        if (reconciled > 0) {
            plugin.getLogger().info("Reconciled LazyBuilder-owned mob AI for "
                    + reconciled + " loaded entities.");
        }
    }

    public void stop() {
        HandlerList.unregisterAll(this);
        int released = 0;
        for (World world : Bukkit.getWorlds()) {
            // Do not let one failed Mob block remaining lease restoration or shutdown cleanup.
            released += reconcileBatch(world.getEntities(), false, "plugin shutdown");
        }
        if (released > 0) {
            plugin.getLogger().info("Released " + released
                    + " loaded LazyBuilder-owned AI leases before plugin shutdown.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        try {
            reconcileAi(event.getEntity(), isEnabledManagedWorld(event.getLocation().getWorld()));
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not apply a LazyBuilder mob AI lease on spawn", failure);
        }
    }

    @EventHandler
    public void onEntitiesLoad    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        reconcileBatch(event.getEntities(), isEnabledManagedWorld(event.getWorld()), "entity load");
    }

    @EventHandler
    public void onEntitiesUnload    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        // Attempt owned AI restoration before serialization. A failed entity
        // retains its PDC marker for the next load/recovery attempt.
        reconcileBatch(event.getEntities(), false, "entity unload");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreed    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (breedingSuppression && isEnabledManagedWorld(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    private int reconcileBatch(Iterable<? extends Entity> entities, boolean canSuppress, String phase) {
        int changed = 0;
        int failures = 0;
        RuntimeException firstFailure = null;
        for (Entity entity : entities) {
            try {
                if (reconcileAi(entity, canSuppress) != AiAction.NONE) changed++;
            } catch (RuntimeException failure) {
                failures++;
                if (firstFailure == null) firstFailure = failure;
            }
        }
        if (firstFailure != null) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not reconcile " + failures + " LazyBuilder mob AI leases during " + phase
                            + "; affected entities retain their marker for later recovery.",
                    firstFailure);
        }
        return changed;
    }

    private boolean isEnabledManagedWorld(World world) {
        return world != null
                && enabledWorldFolders.contains(world.getName())
                && registry.findByFolderName(world.getName()).isPresent();
    }

    private AiAction reconcileAi(Entity entity, boolean canSuppress) {
        if (!(entity instanceof Mob mob)) return AiAction.NONE;
        Set<String> tags = canSuppress ? mob.getScoreboardTags() : Set.of();
        boolean suppressRequested = canSuppress
                && !tags.contains(GAMEPLAY_AI_TAG)
                && (allMobAiSuppression
                    || (villagerAiSuppression && mob instanceof Villager)
                    || tags.contains(DECORATIVE_TAG));
        return applyDecision(mob, suppressRequested);
    }

    private AiAction applyDecision(Mob mob, boolean suppressRequested) {
        return applyAiLeaseDecision(mob, aiLeaseKey, suppressRequested);
    }

    /** Exact PDC/AI transition, exercised with a narrow Mob proxy in tests. */
    static AiAction applyAiLeaseDecision(Mob mob, NamespacedKey leaseKey, boolean suppressRequested) {
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(leaseKey, "leaseKey");
        PersistentDataContainer data = mob.getPersistentDataContainer();
        boolean leased = data.has(leaseKey, PersistentDataType.BYTE);
        AiAction decision = decideAiAction(leased, mob.hasAI(), suppressRequested);
        switch (decision) {
            case SUPPRESS -> {
                // Persist lease first: interruption before setAI can only leave
                // a harmless marker on an AI-enabled entity, not an orphaned NoAI.
                data.set(leaseKey, PersistentDataType.BYTE, (byte) 1);
                try {
                    mob.setAI(false);
                } catch (RuntimeException failure) {
                    try {
                        data.remove(leaseKey);
                    } catch (RuntimeException cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                    throw failure;
                }
            }
            case RESTORE -> {
                mob.setAI(true);
                data.remove(leaseKey);
            }
            case CLEAR_LEASE -> data.remove(leaseKey);
            case NONE -> { }
        }
        return decision;
    }

    enum AiAction { NONE, SUPPRESS, RESTORE, CLEAR_LEASE }

    /** Pure state decision, separately regression-tested without a Paper server. */
    static AiAction decideAiAction(boolean leased, boolean hasAi, boolean suppressionRequested) {
        if (leased) {
            if (hasAi) return AiAction.CLEAR_LEASE;
            return suppressionRequested ? AiAction.NONE : AiAction.RESTORE;
        }
        // A NoAI mob with no LazyBuilder lease may be deliberate decoration or
        // a legacy user state; never turn it on or assert ownership.
        return hasAi && suppressionRequested ? AiAction.SUPPRESS : AiAction.NONE;
    }

    /** No wildcard/global scope: a missing or malformed list always fails closed. */
    static Set<String> normalizeEnabledWorldFolders(Object configured) {
        if (configured == null) return Set.of();
        if (!(configured instanceof List<?> values)) {
            throw new IllegalArgumentException("Expected a YAML list of exact managed world folder names");
        }
        Set<String> folders = new HashSet<>();
        for (Object value : values) {
            if (!(value instanceof String folder)
                    || folder.isBlank()
                    || !folder.equals(folder.strip())
                    || folder.equals("*")
                    || folder.chars().anyMatch(ch -> Character.isISOControl(ch))) {
                throw new IllegalArgumentException("Every entry must be a nonblank exact world folder name (no wildcard)");
            }
            folders.add(folder);
        }
        return Set.copyOf(folders);
    }
}
