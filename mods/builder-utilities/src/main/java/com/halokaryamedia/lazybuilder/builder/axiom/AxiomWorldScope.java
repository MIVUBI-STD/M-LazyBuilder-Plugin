package com.halokaryamedia.lazybuilder.builder.axiom;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;

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
        if (client.isInSingleplayer()) {
            var server = client.getServer();
            String levelName = server == null
                    ? "<singleplayer>"
                    : server.getSaveProperties().getLevelName();
            endpoint = "singleplayer|" + levelName;
        } else {
            ServerInfo info = client.getCurrentServerEntry();
            if (info != null && info.address != null && !info.address.isBlank()) {
                endpoint = "remote|" + info.address.toLowerCase(Locale.ROOT);
            } else if (client.getNetworkHandler() != null) {
                endpoint = "remote|"
                        + client.getNetworkHandler().getConnection().getAddress().toString();
            } else {
                endpoint = "remote|<unknown>";
            }
        }

        String dimension = world.getRegistryKey().getValue().toString();
        return digest(endpoint + "|" + dimension);
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
