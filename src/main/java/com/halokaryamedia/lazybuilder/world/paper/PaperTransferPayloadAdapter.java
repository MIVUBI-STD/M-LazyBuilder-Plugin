package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.LazyBuilderPlugin;
import com.halokaryamedia.lazybuilder.world.transfer.TransferDescriptor;
import com.halokaryamedia.lazybuilder.world.transfer.TransferSessionService;
import com.halokaryamedia.lazybuilder.world.transfer.TransferWireProtocol;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Thin Bukkit plugin-message adapter for the World Manager transfer owner.
 *
 * <p>All file I/O is dispatched to request-scoped async scheduler work. The
 * client uses stop-and-wait semantics, so at most one transfer request per
 * player may be in flight at a time. No polling loop or persistent worker is
 * created.</p>
 */
public final class PaperTransferPayloadAdapter implements PluginMessageListener, Listener {
    public static final String CHANNEL = "lazybuilder:transfer";
    public static final String PERMISSION = "lazybuilder.world.manage";

    private final LazyBuilderPlugin plugin;
    private final TransferSessionService transfers;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<UUID> knownOwners = ConcurrentHashMap.newKeySet();
    private volatile boolean started;

    public PaperTransferPayloadAdapter(LazyBuilderPlugin plugin, TransferSessionService transfers) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.transfers = Objects.requireNonNull(transfers, "transfers");
    }

    public void start() {
        if (started) return;
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        started = true;
    }

    public void stop() {
        if (!started) return;
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        started = false;

        UUID[] owners = knownOwners.toArray(UUID[]::new);
        knownOwners.clear();
        inFlight.clear();
        for (UUID owner : owners) {
            try {
                transfers.abortAllForOwner(owner);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to clean transfer session state for " + owner + " during shutdown", exception);
            }
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!started || !CHANNEL.equals(channel)) return;
        UUID owner = player.getUniqueId();
        if (!player.hasPermission(PERMISSION)) {
            send(player, TransferWireProtocol.error("Missing permission: " + PERMISSION));
            return;
        }

        final TransferWireProtocol.Request request;
        try {
            request = TransferWireProtocol.decodeRequest(message);
        } catch (IOException | RuntimeException exception) {
            send(player, TransferWireProtocol.error(exception.getMessage()));
            return;
        }

        if (!inFlight.add(owner)) {
            send(player, TransferWireProtocol.error("Previous transfer request is still processing"));
            return;
        }
        knownOwners.add(owner);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            byte[] response;
            try {
                response = handle(owner, request);
            } catch (IOException | RuntimeException exception) {
                response = TransferWireProtocol.error(exception.getMessage());
            } finally {
                inFlight.remove(owner);
            }
            byte[] finalResponse = response;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player online = plugin.getServer().getPlayer(owner);
                if (online != null && online.isOnline()) send(online, finalResponse);
            });
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID owner = event.getPlayer().getUniqueId();
        inFlight.remove(owner);
        knownOwners.remove(owner);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                transfers.abortAllForOwner(owner);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to clean transfer session state after disconnect for " + owner, exception);
            }
        });
    }

    private byte[] handle(UUID owner, TransferWireProtocol.Request request) throws IOException {
        return switch (request) {
            case TransferWireProtocol.BeginUpload begin -> {
                TransferDescriptor descriptor = transfers.beginUpload(
                        owner, begin.fileName(), begin.totalBytes(), begin.sha256());
                yield TransferWireProtocol.uploadAccepted(descriptor);
            }
            case TransferWireProtocol.UploadChunk chunk -> {
                var progress = transfers.acceptUploadChunk(
                        owner, chunk.sessionId(), chunk.chunkIndex(), chunk.data());
                yield TransferWireProtocol.uploadProgress(chunk.sessionId(), progress);
            }
            case TransferWireProtocol.FinishUpload finish -> {
                Path artifact = transfers.finishUpload(owner, finish.sessionId());
                yield TransferWireProtocol.uploadFinished(finish.sessionId(), artifact.getFileName().toString());
            }
            case TransferWireProtocol.AbortUpload abort -> {
                transfers.abortUpload(owner, abort.sessionId());
                yield TransferWireProtocol.ack(TransferWireProtocol.opcode(request));
            }
            case TransferWireProtocol.BeginDownload begin -> {
                TransferDescriptor descriptor = transfers.beginDownload(owner, begin.fileName());
                yield TransferWireProtocol.downloadAccepted(descriptor);
            }
            case TransferWireProtocol.DownloadChunkRequest chunk -> {
                var data = transfers.readDownloadChunk(owner, chunk.sessionId(), chunk.chunkIndex());
                yield TransferWireProtocol.downloadChunk(chunk.sessionId(), data);
            }
            case TransferWireProtocol.FinishDownload finish -> {
                transfers.finishDownload(owner, finish.sessionId());
                yield TransferWireProtocol.ack(TransferWireProtocol.opcode(request));
            }
            case TransferWireProtocol.AbortDownload abort -> {
                transfers.abortDownload(owner, abort.sessionId());
                yield TransferWireProtocol.ack(TransferWireProtocol.opcode(request));
            }
        };
    }

    private void send(Player player, byte[] payload) {
        if (payload.length > TransferWireProtocol.MAX_MESSAGE_BYTES) {
            plugin.getLogger().warning("Refusing oversized transfer response for " + player.getUniqueId());
            return;
        }
        player.sendPluginMessage(plugin, CHANNEL, payload);
    }
}
