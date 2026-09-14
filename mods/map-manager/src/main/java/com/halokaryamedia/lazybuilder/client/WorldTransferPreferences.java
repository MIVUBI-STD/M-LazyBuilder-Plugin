package com.halokaryamedia.lazybuilder.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;

/**
 * Small client-owned preference store for World Manager transfer defaults.
 *
 * <p>Preferences are scoped by server identity and are presentation defaults only;
 * they never become server world metadata or business-logic authority.</p>
 */
public final class WorldTransferPreferences {
    public static final String DEFAULT_EXPORT_FORMAT = "JAVA_1_21_4";

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
        if (format == null || format.isBlank()) return;
        properties.setProperty(key("export-format"), format.strip().toUpperCase(java.util.Locale.ROOT));
        save();
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
            identity = client.getCurrentServerEntry().address.strip().toLowerCase(java.util.Locale.ROOT);
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(identity.getBytes(StandardCharsets.UTF_8));
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
