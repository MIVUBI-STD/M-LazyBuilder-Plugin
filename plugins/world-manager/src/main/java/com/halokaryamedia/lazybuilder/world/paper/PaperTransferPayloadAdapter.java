package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.world.transfer.TransferDescriptor;
import com.halokaryamedia.lazybuilder.world.transfer.TransferSessionService;
import com.halokaryamedia.lazybuilder.world.transfer.TransferWireProtocol;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Thin Bukkit plugin-message adapter for the World Manager transfer owner.
 *
 * <p>File I/O runs on request-scoped Paper async work. A small per-player FIFO
 * lane preserves protocol order while allowing the client to keep a bounded
 * number of chunks in flight, avoiding one network round trip per 24 KiB chunk.
 * No dedicated worker, polling loop, socket, or permanent executor is created.</p>
 */
public final class PaperTransferPayloadAdapter implements PluginMessageListener, Listener {
    public static final String CHANNEL = "lazybuilder:transfer";
    public static final String PERMISSION = "lazybuilder.world.manage";
    public static final int PIPELINE_WINDOW = TransferWireProtocol.PIPELINE_WINDOW;
    private static final int MAX_QUEUED_REQUESTS = PIPELINE_WINDOW * 2 + 4;

    private final JavaPlugin plugin;
    private final TransferSessionService transfers;
    private final Map<UUID, Queue<TransferWireProtocol.Request>> requestQueues = new ConcurrentHashMap<>();
    private final Set<UUID> draining = ConcurrentHashMap.newKeySet();
    private final Set<UUID> knownOwners = ConcurrentHashMap.newKeySet();
    private volatile boolean started;

    public PaperTransferPayloadAdapter(JavaPlugin plugin, TransferSessionService transfers) {
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
        requestQueues.clear();
        draining.clear();
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

        knownOwners.add(owner);
        Queue<TransferWireProtocol.Request> queue = requestQueues.computeIfAbsent(owner, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            if (queue.size() >= MAX_QUEUED_REQUESTS) {
                send(player, TransferWireProtocol.error("Transfer pipeline window exceeded"));
                return;
            }
            queue.add(request);
        }
        scheduleDrain(owner);
    }

    private void scheduleDrain(UUID owner) {
        if (!started || !draining.add(owner)) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> drain(owner));
    }

    private void drain(UUID owner) {
        try {
            while (started) {
                Queue<TransferWireProtocol.Request> queue = requestQueues.get(owner);
                if (queue == null) return;
                TransferWireProtocol.Request request;
                synchronized (queue) {
                    request = queue.poll();
                    if (request == null) {
                        requestQueues.remove(owner, queue);
                        return;
                    }
                }

                byte[] response;
                try {
                    response = handle(owner, request);
                } catch (IOException | RuntimeException exception) {
                    cleanupFailedSession(owner, request);
                    response = TransferWireProtocol.error(exception.getMessage());
                    synchronized (queue) {
                        queue.clear();
                    }
                }
                sendOnMain(owner, response);
            }
        } finally {
            draining.remove(owner);
            Queue<TransferWireProtocol.Request> queue = requestQueues.get(owner);
            if (started && queue != null) {
                synchronized (queue) {
                    if (!queue.isEmpty()) scheduleDrain(owner);
                }
            }
        }
    }

    private void sendOnMain(UUID owner, byte[] payload) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player online = plugin.getServer().getPlayer(owner);
            if (online != null && online.isOnline()) send(online, payload);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID owner = event.getPlayer().getUniqueId();
        requestQueues.remove(owner);
        draining.remove(owner);
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
                yield TransferWireProtocol.uploadProgress(
                        chunk.sessionId(),
                        progress.receivedBytes(),
                        progress.totalBytes(),
                        progress.nextChunkIndex(),
                        progress.totalChunks()
                );
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
                yield TransferWireProtocol.downloadChunk(
                        chunk.sessionId(), data.chunkIndex(), data.bytes(), data.last());
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

    /** Protocol errors fail closed so stale session state never wedges a client. */
    private void cleanupFailedSession(UUID owner, TransferWireProtocol.Request request) {
        try {
            switch (request) {
                case TransferWireProtocol.UploadChunk chunk -> transfers.abortUpload(owner, chunk.sessionId());
                case TransferWireProtocol.FinishUpload finish -> transfers.abortUpload(owner, finish.sessionId());
                case TransferWireProtocol.DownloadChunkRequest chunk -> transfers.abortDownload(owner, chunk.sessionId());
                case TransferWireProtocol.FinishDownload finish -> transfers.abortDownload(owner, finish.sessionId());
                default -> { }
            }
        } catch (IOException | RuntimeException ignored) {
            // Failing operations may already have removed their own session.
        }
    }

    private void send(Player player, byte[] payload) {
        if (payload.length > TransferWireProtocol.MAX_MESSAGE_BYTES) {
            plugin.getLogger().warning("Refusing oversized transfer response for " + player.getUniqueId());
            return;
        }
        player.sendPluginMessage(plugin, CHANNEL, payload);
    }
}
