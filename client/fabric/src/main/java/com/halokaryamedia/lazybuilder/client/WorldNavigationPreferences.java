package com.halokaryamedia.lazybuilder.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** Client-owned per-server navigation preferences. Never persisted as world metadata. */
public final class WorldNavigationPreferences {
    private static final int MAX_RECENT = 5;

    private final Path path;
    private final Properties properties = new Properties();

    public WorldNavigationPreferences() {
        this.path = FabricLoader.getInstance().getConfigDir().resolve("lazybuilder-world-navigation.properties");
        load();
    }

    public synchronized boolean isPinned(UUID worldId) {
        return pinned().contains(worldId);
    }

    public synchronized void togglePinned(UUID worldId) {
        Set<UUID> values = new LinkedHashSet<>(pinned());
        if (!values.remove(worldId)) values.add(worldId);
        properties.setProperty(key("pinned"), join(values));
        save();
    }

    public synchronized List<UUID> pinned() {
        return parse(properties.getProperty(key("pinned"), ""));
    }

    /** Recent means the player was authoritatively observed inside the managed world. */
    public synchronized void recordVisited(UUID worldId) {
        List<UUID> values = new ArrayList<>(recent());
        if (!values.isEmpty() && values.get(0).equals(worldId)) return;
        values.remove(worldId);
        values.add(0, worldId);
        if (values.size() > MAX_RECENT) values = new ArrayList<>(values.subList(0, MAX_RECENT));
        properties.setProperty(key("recent"), join(values));
        save();
    }

    public synchronized List<UUID> recent() {
        return parse(properties.getProperty(key("recent"), ""));
    }

    private String key(String suffix) {
        return "server." + encodedServerIdentity() + "." + suffix;
    }

    private static String encodedServerIdentity() {
        MinecraftClient client = MinecraftClient.getInstance();
        String identity = "singleplayer";
        if (client != null && client.getCurrentServerEntry() != null
                && client.getCurrentServerEntry().address != null
                && !client.getCurrentServerEntry().address.isBlank()) {
            identity = client.getCurrentServerEntry().address.strip().toLowerCase(java.util.Locale.ROOT);
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(identity.getBytes(StandardCharsets.UTF_8));
    }

    private static List<UUID> parse(String value) {
        List<UUID> result = new ArrayList<>();
        if (value == null || value.isBlank()) return result;
        for (String part : value.split(",")) {
            try { result.add(UUID.fromString(part.strip())); }
            catch (IllegalArgumentException ignored) { }
        }
        return result;
    }

    private static String join(Iterable<UUID> values) {
        StringBuilder out = new StringBuilder();
        for (UUID value : values) {
            if (!out.isEmpty()) out.append(',');
            out.append(value);
        }
        return out.toString();
    }

    private void load() {
        if (!Files.isRegularFile(path)) return;
        try (InputStream input = Files.newInputStream(path)) { properties.load(input); }
        catch (IOException ignored) { properties.clear(); }
    }

    private void save() {
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            try (OutputStream output = Files.newOutputStream(temp)) {
                properties.store(output, "LazyBuilder World Manager navigation preferences");
            }
            try {
                Files.move(temp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Navigation preferences must never block normal World Manager usage.
        }
    }
}
