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
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

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
    private final Properties properties = new Properties();

    public WorldTransferPreferences() {
        this.path = FabricLoader.getInstance().getConfigDir().resolve("lazybuilder-world-transfer.properties");
        load();
    }

    public synchronized String exportFormat() {
        return properties.getProperty(key("export-format"), DEFAULT_EXPORT_FORMAT);
    }

    public synchronized void setExportFormat(String format) {
        String normalized = normalizeFormat(format);
        if (normalized.isBlank()) return;
        properties.setProperty(key("export-format"), normalized);
        rememberPreferredFormat(normalized);
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

        // Give new installations two useful quick choices without inventing versions:
        // the first Java and first Bedrock formats actually advertised by the server.
        available.stream().filter(value -> value.startsWith("JAVA_")).findFirst().ifPresent(ordered::add);
        available.stream().filter(value -> value.startsWith("BEDROCK_")).findFirst().ifPresent(ordered::add);
        ordered.addAll(available);
        return List.copyOf(ordered);
    }

    public synchronized List<String> preferredFormats() {
        String stored = properties.getProperty(key("preferred-formats"), "");
        if (stored.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String part : stored.split(",")) {
            String normalized = normalizeFormat(part);
            if (!normalized.isBlank() && !result.contains(normalized)) result.add(normalized);
            if (result.size() >= MAX_PREFERRED_FORMATS) break;
        }
        return List.copyOf(result);
    }

    public synchronized void rememberPreferredFormat(String format) {
        String normalized = normalizeFormat(format);
        if (normalized.isBlank()) return;
        LinkedHashSet<String> next = new LinkedHashSet<>();
        next.add(normalized);
        next.addAll(preferredFormats());
        List<String> limited = next.stream().limit(MAX_PREFERRED_FORMATS).toList();
        properties.setProperty(key("preferred-formats"), String.join(",", limited));
    }

    public synchronized boolean isPreferredFormat(String format) {
        return preferredFormats().contains(normalizeFormat(format));
    }

    public synchronized void resetExportFormat() {
        properties.remove(key("export-format"));
        save();
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
            identity = client.getCurrentServerEntry().address.strip().toLowerCase(Locale.ROOT);
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(identity.getBytes(StandardCharsets.UTF_8));
    }

    private static String normalizeFormat(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private void load() {
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
