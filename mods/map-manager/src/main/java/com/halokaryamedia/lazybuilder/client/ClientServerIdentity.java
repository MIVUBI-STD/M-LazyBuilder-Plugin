package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/** One client owner for stable per-server preference and presentation-cache scoping. */
final class ClientServerIdentity {
    private static final int MAX_INLINE_IDENTITY = 72;

    private static String cachedIdentitySource;
    private static String cachedEncodedIdentity;
    private static String cachedMapServer;
    private static String cachedMapLocalScope;
    private static String cachedMapScope;

    private ClientServerIdentity() { }

    static String encodedCurrent() {
        MinecraftClient client = MinecraftClient.getInstance();
        String identity = "singleplayer";
        if (client != null && client.getCurrentServerEntry() != null
                && client.getCurrentServerEntry().address != null
                && !client.getCurrentServerEntry().address.isBlank()) {
            identity = client.getCurrentServerEntry().address;
        }
        return encodeCached(identity);
    }

    static String mapScope(String localScope) {
        return mapScope(encodedCurrent(), localScope);
    }

    static synchronized String mapScope(String encodedServerIdentity, String localScope) {
        String normalized = localScope == null ? "" : localScope.strip();
        if (normalized.isEmpty()) return "";
        String server = encodedServerIdentity == null || encodedServerIdentity.isBlank()
                ? encodeCached("singleplayer")
                : encodedServerIdentity.strip();
        if (server.equals(cachedMapServer) && normalized.equals(cachedMapLocalScope) && cachedMapScope != null) {
            return cachedMapScope;
        }
        String result = "server." + server + "|" + normalized;
        cachedMapServer = server;
        cachedMapLocalScope = normalized;
        cachedMapScope = result;
        return result;
    }

    static synchronized String encodeCached(String identity) {
        String source = identity == null ? "" : identity;
        if (source.equals(cachedIdentitySource) && cachedEncodedIdentity != null) {
            return cachedEncodedIdentity;
        }
        String encoded = encode(source);
        cachedIdentitySource = source;
        cachedEncodedIdentity = encoded;
        return encoded;
    }

    static String encode(String identity) {
        String normalized = identity == null || identity.isBlank()
                ? "singleplayer"
                : identity.strip().toLowerCase(Locale.ROOT);
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        if (encoded.length() <= MAX_INLINE_IDENTITY) return encoded;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return "h." + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
