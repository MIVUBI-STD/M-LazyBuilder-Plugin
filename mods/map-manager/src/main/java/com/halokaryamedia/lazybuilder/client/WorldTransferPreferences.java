package com.halokaryamedia.lazybuilder.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Small client-owned preference store for World Manager transfer defaults.
 *
 * <p>Preferences are scoped by server identity and are presentation defaults only;
 * they never become server world metadata or business-logic authority.</p>
 */
public final class WorldTransferPreferences {
    public static final String DEFAULT_EXPORT_FORMAT = "JAVA_1_21_4";
    private static final int MAX_PREFERRED_FORMATS = 4;

    private final Path path;
    private final Supplier<String> serverIdentity;
    private final Properties properties = new Properties();
    private String cachedPreferredRaw;
    private List<String> cachedPreferred = List.of();

    public WorldTransferPreferences() {
        this(
                FabricLoader.getInstance().getConfigDir().resolve("lazybuilder-world-transfer.properties"),
                ClientServerIdentity::encodedCurrent
        );
    }

    WorldTransferPreferences(Path path, Supplier<String> serverIdentity) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity");
        load();
    }

    public synchronized String exportFormat() {
        return properties.getProperty(key("export-format"), DEFAULT_EXPORT_FORMAT);
    }

    public synchronized void setExportFormat(String format) {
        String normalized = normalizeFormat(format);
        if (normalized.isBlank()) return;

        String exportKey = key("export-format");
        String preferredKey = key("preferred-formats");
        String previousExport = properties.getProperty(exportKey);
        String previousPreferred = properties.getProperty(preferredKey, "");

        properties.setProperty(exportKey, normalized);
        rememberPreferredFormat(normalized);

        String nextPreferred = properties.getProperty(preferredKey, "");
        if (normalized.equals(previousExport) && nextPreferred.equals(previousPreferred)) return;
        save();
    }

    /**
     * Returns export versions ordered for quick access: remembered versions first,
     * then every remaining server-supported version in its canonical order.
     */
    public synchronized List<String> preferredFirst(List<String> supported) {
        if (supported == null || supported.isEmpty()) return List.of();
        LinkedHashSet<String> available = new LinkedHashSet<>();
        for (String format : supported) {
            String normalized = normalizeFormat(format);
            if (!normalized.isBlank()) available.add(normalized);
        }
        if (available.isEmpty()) return List.of();

        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String preferred : preferredFormats()) {
            if (available.contains(preferred)) ordered.add(preferred);
        }

        String last = normalizeFormat(exportFormat());
        if (available.contains(last)) ordered.add(last);

        available.stream().filter(value -> value.startsWith("JAVA_")).findFirst().ifPresent(ordered::add);
        available.stream().filter(value -> value.startsWith("BEDROCK_")).findFirst().ifPresent(ordered::add);
        ordered.addAll(available);
        return List.copyOf(ordered);
    }

    public synchronized List<String> preferredFormats() {
        String stored = properties.getProperty(key("preferred-formats"), "");
        if (!stored.equals(cachedPreferredRaw)) {
            cachedPreferredRaw = stored;
            cachedPreferred = parsePreferredFormats(stored);
        }
        return cachedPreferred;
    }

    public synchronized void rememberPreferredFormat(String format) {
        String normalized = normalizeFormat(format);
        if (normalized.isBlank()) return;
        LinkedHashSet<String> next = new LinkedHashSet<>();
        next.add(normalized);
        next.addAll(preferredFormats());
        List<String> limited = next.stream().limit(MAX_PREFERRED_FORMATS).toList();
        String encoded = String.join(",", limited);
        properties.setProperty(key("preferred-formats"), encoded);
        cachedPreferredRaw = encoded;
        cachedPreferred = List.copyOf(limited);
    }

    public synchronized boolean isPreferredFormat(String format) {
        return preferredFormats().contains(normalizeFormat(format));
    }

    public synchronized void resetExportFormat() {
        if (properties.remove(key("export-format")) == null) return;
        save();
    }

    private String key(String suffix) {
        String identity = Objects.requireNonNullElse(serverIdentity.get(), "").strip();
        if (identity.isBlank()) identity = ClientServerIdentity.encode("singleplayer");
        return "server." + identity + "." + suffix;
    }

    private static String normalizeFormat(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private static List<String> parsePreferredFormats(String stored) {
        if (stored == null || stored.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String part : stored.split(",")) {
            String normalized = normalizeFormat(part);
            if (!normalized.isBlank() && !result.contains(normalized)) result.add(normalized);
            if (result.size() >= MAX_PREFERRED_FORMATS) break;
        }
        return List.copyOf(result);
    }

    private void load() {
        cachedPreferredRaw = null;
        cachedPreferred = List.of();
        if (!Files.isRegularFile(path)) return;
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException ignored) {
            properties.clear();
        }
    }

    private void save() {
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            try (OutputStream output = Files.newOutputStream(temp)) {
                properties.store(output, "LazyBuilder World Manager transfer defaults");
            }
            try {
                Files.move(temp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Preference persistence must never block World Manager usage.
        }
    }
}
