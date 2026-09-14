package com.halokaryamedia.lazybuilder.utilities.feature.buildhelpers;

import com.halokaryamedia.lazybuilder.utilities.feature.UtilityFeature;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Objects;

/** Small builder interactions that stay independent from world lifecycle and editing plugins. */
public final class BuildHelpersFeature implements UtilityFeature, Listener {
    public static final String ID = "build-helpers";
    private static final String BUILD_PERMISSION = "lazybuilder.utilities.build";

    private final JavaPlugin plugin;
    private final BuildHelpersSettings settings;
    private boolean enabled;

    public BuildHelpersFeature(JavaPlugin plugin, BuildHelpersSettings settings) {
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIronDoorInteract(PlayerInteractEvent event) {
        if (!settings.ironDoorToggle()) return;
        if (!acceptsHand(event.getHand())) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.hasPermission(BUILD_PERMISSION)) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.IRON_DOOR) return;
        if (!(block.getBlockData() instanceof Openable openable)) return;

        openable.setOpen(!openable.isOpen());
        block.setBlockData(openable, true);
        event.setUseInteractedBlock(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDoubleSlabBreak(BlockBreakEvent event) {
        if (!settings.doubleSlabBreak()) return;
        Player player = event.getPlayer();
        if (!player.hasPermission(BUILD_PERMISSION)) return;
        if (settings.requireSneakForSlab() && !player.isSneaking()) return;
        Block block = event.getBlock();
        BlockData data = block.getBlockData();
        if (!(data instanceof Slab slab) || slab.getType() != Slab.Type.DOUBLE) return;

        event.setCancelled(true);
        Slab remaining = (Slab) data.clone();
        remaining.setType(Slab.Type.BOTTOM);
        block.setBlockData(remaining, true);
        if (dropsSlabFor(player.getGameMode())) {
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(block.getType(), 1));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGlazedTerracottaRotate(PlayerInteractEvent event) {
        if (!settings.glazedTerracottaRotate()) return;
        if (!acceptsHand(event.getHand())) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.hasPermission(BUILD_PERMISSION)) return;
        if (settings.requireSneakForRotate() && !player.isSneaking()) return;
        Block block = event.getClickedBlock();
        if (block == null || !isGlazedTerracotta(block.getType())) return;
        if (!(block.getBlockData() instanceof Directional directional)) return;

        directional.setFacing(clockwise(directional.getFacing()));
        block.setBlockData(directional, true);
        event.setUseInteractedBlock(Event.Result.DENY);
    }

    static boolean acceptsHand(EquipmentSlot hand) {
        return hand == EquipmentSlot.HAND;
    }

    static boolean dropsSlabFor(GameMode mode) {
        return mode != GameMode.CREATIVE;
    }

    static BlockFace clockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> face;
        };
    }

    private static boolean isGlazedTerracotta(Material material) {
        return material.name().toLowerCase(Locale.ROOT).endsWith("_glazed_terracotta");
    }
}
