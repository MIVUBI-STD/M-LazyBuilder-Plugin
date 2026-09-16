package com.halokaryamedia.lazybuilder.utilities.telemetry;

import com.halokaryamedia.lazybuilder.utility.telemetry.UtilityTelemetryWireProtocol;
import com.sun.management.OperatingSystemMXBean;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.ManagementFactory;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded server telemetry producer for Compact Debug clients that explicitly subscribe. */
public final class PaperUtilityTelemetryAdapter implements PluginMessageListener, Listener {
    private static final long PUSH_PERIOD_TICKS = 40L;

    private final JavaPlugin plugin;
    private final Set<UUID> subscribers = ConcurrentHashMap.newKeySet();
    private BukkitTask pushTask;
    private boolean started;

    public PaperUtilityTelemetryAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (started) return;
        plugin.getServer().getMessenger().registerIncomingPluginChannel(
                plugin,
                UtilityTelemetryWireProtocol.CHANNEL,
                this
        );
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(
                plugin,
                UtilityTelemetryWireProtocol.CHANNEL
        );
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        pushTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::pushSnapshots,
                PUSH_PERIOD_TICKS,
                PUSH_PERIOD_TICKS
        );
        started = true;
    }

    public void stop() {
        if (!started) return;
        if (pushTask != null) {
            pushTask.cancel();
            pushTask = null;
        }
        HandlerList.unregisterAll(this);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(
                plugin,
                UtilityTelemetryWireProtocol.CHANNEL,
                this
        );
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(
                plugin,
                UtilityTelemetryWireProtocol.CHANNEL
        );
        subscribers.clear();
        started = false;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!started || !UtilityTelemetryWireProtocol.CHANNEL.equals(channel)) return;
        try {
            UtilityTelemetryWireProtocol.Request request = UtilityTelemetryWireProtocol.decodeRequest(message);
            if (request instanceof UtilityTelemetryWireProtocol.Subscribe) {
                subscribers.add(player.getUniqueId());
                Sample sample = sampleServer();
                sendSnapshot(player, sample);
            }
        } catch (Exception exception) {
            plugin.getLogger().fine("Rejected invalid utility telemetry request from " + player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        subscribers.remove(event.getPlayer().getUniqueId());
    }

    private void pushSnapshots() {
        if (!started || subscribers.isEmpty()) return;
        Sample sample = sampleServer();
        for (UUID playerId : Set.copyOf(subscribers)) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                subscribers.remove(playerId);
                continue;
            }
            sendSnapshot(player, sample);
        }
    }

    private void sendSnapshot(Player player, Sample sample) {
        byte[] payload = UtilityTelemetryWireProtocol.snapshot(
                player.getWorld().getName(),
                sample.cpuPercent(),
                sample.usedMemoryBytes(),
                sample.maxMemoryBytes()
        );
        player.sendPluginMessage(plugin, UtilityTelemetryWireProtocol.CHANNEL, payload);
    }

    private static Sample sampleServer() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();

        double cpuPercent = Double.NaN;
        java.lang.management.OperatingSystemMXBean base = ManagementFactory.getOperatingSystemMXBean();
        if (base instanceof OperatingSystemMXBean bean) {
            double load = bean.getProcessCpuLoad();
            if (load >= 0.0D) cpuPercent = Math.min(100.0D, load * 100.0D);
        }
        return new Sample(cpuPercent, usedMemory, maxMemory);
    }

    private record Sample(double cpuPercent, long usedMemoryBytes, long maxMemoryBytes) {
    }
}
