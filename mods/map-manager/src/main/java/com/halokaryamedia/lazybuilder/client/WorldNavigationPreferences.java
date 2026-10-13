package com.halokaryamedia.lazybuilder.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Client-owned per-server navigation preferences. Never persisted as world metadata. */
public final class WorldNavigationPreferences {
    private static final int MAX_RECENT = 5;
    private static final Properties SHARED_PROPERTIES = new Properties();

    private final Path path;
    private final Supplier<String> serverIdentity;
    private final Properties properties;
    private String cachedPinnedRaw;
    private List<UUID> cachedPinned = List.of();
    private String cachedRecentRaw;
    private List<UUID> cachedRecent = List.of();

    public static WorldNavigationPreferences shared() {
        return SharedHolder.INSTANCE;
    }

    private static final class SharedHolder {
        private static final WorldNavigationPreferences INSTANCE = new WorldNavigationPreferences();
    }

    public WorldNavigationPreferences() {
        this(
                FabricLoader.getInstance().getConfigDir().resolve("lazybuilder-world-navigation.properties"),
                ClientServerIdentity::encodedCurrent,
                SHARED_PROPERTIES
        );
    }

    WorldNavigationPreferences(Path path, Supplier<String> serverIdentity) {
        this(path, serverIdentity, new Properties());
    }

    private WorldNavigationPreferences(Path path, Supplier<String> serverIdentity, Properties properties) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity");
        this.properties = Objects.requireNonNull(properties, "properties");
        reload();
    }

    /** Refreshes the shared in-memory view after another LazyBuilder surface changed navigation preferences. */
    public synchronized void reload() {
        properties.clear();
        invalidateParsedCache();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return;
        try (InputStream input = Files.newInputStream(path)) { properties.load(input); }
        catch (IOException ignored) { properties.clear(); }
    }

    public synchronized boolean isPinned(UUID worldId) {
        refreshPinnedCache();
        return cachedPinned.contains(worldId);
    }

    public synchronized void togglePinned(UUID worldId) {
        refreshPinnedCache();
        Set<UUID> values = new LinkedHashSet<>(cachedPinned);
        if (!values.remove(worldId)) values.add(worldId);
        String encoded = join(values);
        properties.setProperty(key("pinned"), encoded);
        cachedPinnedRaw = encoded;
        cachedPinned = List.copyOf(values);
        save();
    }

    public synchronized List<UUID> pinned() {
        refreshPinnedCache();
        return new ArrayList<>(cachedPinned);
    }

    private void refreshPinnedCache() {
        String raw = properties.getProperty(key("pinned"), "");
        if (!raw.equals(cachedPinnedRaw)) {
            cachedPinnedRaw = raw;
            cachedPinned = List.copyOf(parse(raw));
        }
    }

    /** Recent means the player was authoritatively observed inside the managed world. */
    public synchronized void recordVisited(UUID worldId) {
        refreshRecentCache();
        List<UUID> values = new ArrayList<>(cachedRecent);
        if (!values.isEmpty() && values.get(0).equals(worldId)) return;
        values.remove(worldId);
        values.add(0, worldId);
        if (values.size() > MAX_RECENT) values = new ArrayList<>(values.subList(0, MAX_RECENT));
        String encoded = join(values);
        properties.setProperty(key("recent"), encoded);
        cachedRecentRaw = encoded;
        cachedRecent = List.copyOf(values);
        save();
    }

    public synchronized List<UUID> recent() {
        refreshRecentCache();
        return new ArrayList<>(cachedRecent);
    }

    private void refreshRecentCache() {
        String raw = properties.getProperty(key("recent"), "");
        if (!raw.equals(cachedRecentRaw)) {
            cachedRecentRaw = raw;
            cachedRecent = List.copyOf(parse(raw));
        }
    }

    private String key(String suffix) {
        String identity = Objects.requireNonNullElse(serverIdentity.get(), "").strip();
        if (identity.isBlank()) identity = ClientServerIdentity.encode("singleplayer");
        return "server." + identity + "." + suffix;
    }

    private void invalidateParsedCache() {
        cachedPinnedRaw = null;
        cachedPinned = List.of();
        cachedRecentRaw = null;
        cachedRecent = List.of();
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

    private void save() {
        Path temporary = null;
        try {
            Path parent = path.getParent();
            Files.createDirectories(parent);
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Refusing to replace an unsafe navigation preferences target");
            }
            // Never open a predictable .tmp file: it might be a symlink or
            // another client's pending data.
            temporary = Files.createTempFile(parent, path.getFileName() + ".", ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.WRITE)) {
                properties.store(output, "LazyBuilder World Manager navigation preferences");
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Navigation preferences are best-effort and must not block the UI.
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException ignored) { }
            }
        }
    }
}
