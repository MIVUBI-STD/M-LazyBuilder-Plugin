package com.halokaryamedia.lazybuilder.utilities.feature.worldsafety;

import com.halokaryamedia.lazybuilder.utilities.feature.UtilityFeature;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * Builder-safe world protections that suppress destructive vanilla side effects without
 * creating a second world lifecycle authority.
 */
public final class WorldSafetyFeature implements UtilityFeature, Listener {
    public static final String ID = "world-safety";

    private final JavaPlugin plugin;
    private final WorldSafetySettings settings;
    private boolean enabled;

    public WorldSafetyFeature(JavaPlugin plugin, WorldSafetySettings settings) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() {
        if (enabled) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        enabled = true;
    }

    @Override
    public void disable() {
        if (!enabled) return;
        HandlerList.unregisterAll(this);
        enabled = false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!settings.explosions()) return;
        event.blockList().clear();
        event.setYield(0.0F);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!settings.explosions()) return;
        event.blockList().clear();
        event.setYield(0.0F);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (settings.leavesDecay()) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerTrample(PlayerInteractEvent event) {
        if (!settings.farmlandTrample()) return;
        if (event.getAction() != Action.PHYSICAL) return;
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.FARMLAND) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTrample(EntityChangeBlockEvent event) {
        if (!settings.farmlandTrample()) return;
        if (event.getBlock().getType() != Material.FARMLAND) return;
        if (event.getTo() != Material.DIRT) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDragonEggInteract(PlayerInteractEvent event) {
        if (!settings.dragonEggTeleport()) return;
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.DRAGON_EGG) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;

        // Deny only vanilla interaction with the egg. Inventory/item handling remains untouched.
        event.setUseInteractedBlock(Event.Result.DENY);
    }
}
