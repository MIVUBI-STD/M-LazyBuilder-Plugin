package com.halokaryamedia.lazybuilder.builder.axiom;

import net.minecraft.client.MinecraftClient;
import com.halokaryamedia.lazybuilder.builder.net.BuilderExtensionClientNetworking;
import com.halokaryamedia.lazybuilder.builder.wire.BuilderExtensionWireProtocol;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.WorldSavePath;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/** Stable privacy-preserving world/dimension identity for durable Builder recovery. */
public final class AxiomWorldScope {
    private AxiomWorldScope() {}

    public static String currentScopeId() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = Objects.requireNonNull(
                client.world, "Minecraft client world is unavailable");
        return scopeId(client, world);
    }

    public static String currentScopeId(ClientWorld world) {
        return scopeId(MinecraftClient.getInstance(), Objects.requireNonNull(world, "world"));
    }

    static String scopeId(MinecraftClient client, ClientWorld world) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(world, "world");

        String endpoint;
        BuilderExtensionWireProtocol.WorldIdentity serverWorld = null;
        if (client.isInSingleplayer()) {
            var server = client.getServer();
            if (server == null) {
                throw new IllegalStateException(
                        "Singleplayer save identity is unavailable; Builder durable operations are blocked");
            }
            endpoint = singleplayerEndpoint(server.getSavePath(WorldSavePath.ROOT)
                    .toAbsolutePath().normalize().toString());
        } else {
            ServerInfo info = client.getCurrentServerEntry();
            String preferred = info == null ? null : info.address;
            var address = client.getNetworkHandler() == null
                    ? null
                    : client.getNetworkHandler().getConnection().getAddress();
            String fallback = address == null ? null : address.toString();
            endpoint = remoteEndpoint(preferred, fallback);
            serverWorld = BuilderExtensionClientNetworking.serverWorldIdentity();
            if (serverWorld == null) {
                throw new IllegalStateException(
                        "Server-authoritative Paper world identity is unavailable");
            }
        }

        String dimension = world.getRegistryKey().getValue().toString();
        return serverWorld == null
                ? digest(endpoint + "|" + dimension)
                : remoteScopeId(endpoint, serverWorld, dimension);
    }

    /** Never let two unknown singleplayer saves share the same durable History scope. */
    static String singleplayerEndpoint(String saveIdentity) {
        if (saveIdentity == null || saveIdentity.isBlank()) {
            throw new IllegalStateException(
                    "Singleplayer save identity is unavailable; Builder durable operations are blocked");
        }
        return "singleplayer|" + saveIdentity;
    }

    /** Unknown endpoints cannot own recoverable journals or share a synthetic scope. */
    static String remoteEndpoint(String preferred, String fallback) {
        String address = preferred != null && !preferred.isBlank() ? preferred : fallback;
        if (address == null || address.isBlank()) {
            throw new IllegalStateException(
                    "Remote server identity is unavailable; Builder durable operations are blocked");
        }
        return "remote|" + address.trim().toLowerCase(Locale.ROOT);
    }

    static String remoteScopeId(
            String endpoint,
            BuilderExtensionWireProtocol.WorldIdentity world,
            String dimension
    ) {
        return digest(endpoint + "|paper-world-v2|" + world.worldId()
                + "|" + world.worldKey() + "|" + dimension);
    }

    private static String digest(String identity) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
