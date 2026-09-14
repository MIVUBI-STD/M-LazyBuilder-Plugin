package com.halokaryamedia.lazybuilder.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves World-Manager runtime storage without making the Paper plugin a second workspace owner.
 *
 * <p>The desktop launcher supplies {@code LAZYBUILDER_WORKSPACE_ROOT} and launches Paper with
 * {@code --universe <workspace>/world-system/worlds}. Manual/plugin-only launches retain the
 * historical plugin-data layout unless Paper is already using the canonical world container.</p>
 */
public record WorldStorageLayout(
        Path worldsRoot,
        Path registryPath,
        Path importsRoot,
        Path exportsRoot,
        Path backupsRoot,
        Path archivesRoot,
        Path workRoot,
        Path transferRoot,
        Path conversionRoot,
        boolean canonical
) {
    public static final String WORKSPACE_ENV = "LAZYBUILDER_WORKSPACE_ROOT";

    public static WorldStorageLayout resolve(
            Path worldContainer,
            Path pluginDataFolder,
            String configuredWorkspaceRoot
    ) {
        Objects.requireNonNull(worldContainer, "worldContainer");
        Objects.requireNonNull(pluginDataFolder, "pluginDataFolder");

        Path normalizedWorldContainer = worldContainer.toAbsolutePath().normalize();
        Path normalizedPluginData = pluginDataFolder.toAbsolutePath().normalize();

        if (configuredWorkspaceRoot != null && !configuredWorkspaceRoot.isBlank()) {
            Path workspace = Path.of(configuredWorkspaceRoot).toAbsolutePath().normalize();
            return canonical(workspace, normalizedWorldContainer, true);
        }

        Path parent = normalizedWorldContainer.getParent();
        if (normalizedWorldContainer.getFileName() != null
                && normalizedWorldContainer.getFileName().toString().equals("worlds")
                && parent != null
                && parent.getFileName() != null
                && parent.getFileName().toString().equals("world-system")
                && parent.getParent() != null) {
            return canonical(parent.getParent(), normalizedWorldContainer, false);
        }

        Path legacyRoot = normalizedPluginData.resolve("world");
        return new WorldStorageLayout(
                normalizedWorldContainer,
                legacyRoot.resolve("registry.yml"),
                legacyRoot.resolve("imports"),
                legacyRoot.resolve("exports"),
                legacyRoot.resolve("backups"),
                legacyRoot.resolve("archives"),
                legacyRoot.resolve("work"),
                legacyRoot.resolve("transfer"),
                legacyRoot.resolve("runtime").resolve("converter"),
                false
        );
    }

    private static WorldStorageLayout canonical(
            Path workspace,
            Path actualWorldContainer,
            boolean strictWorldContainer
    ) {
        Path worldSystem = workspace.resolve("world-system").normalize();
        Path worlds = worldSystem.resolve("worlds").normalize();
        if (strictWorldContainer && !actualWorldContainer.equals(worlds)) {
            throw new IllegalStateException(
                    "LazyBuilder workspace expects Paper world container " + worlds
                            + " but Paper is using " + actualWorldContainer
            );
        }

        Path toolsCache = workspace.resolve("tools").resolve("lazybuilder").resolve("cache").normalize();
        Path work = worldSystem.resolve("work");
        return new WorldStorageLayout(
                worlds,
                worldSystem.resolve("registry.yml"),
                worldSystem.resolve("imports"),
                worldSystem.resolve("exports"),
                worldSystem.resolve("backups"),
                worldSystem.resolve("archives"),
                work,
                work.resolve("transfer"),
                toolsCache.resolve("converter"),
                true
        );
    }

    public void ensureDirectories() {
        try {
            Files.createDirectories(worldsRoot);
            Files.createDirectories(importsRoot);
            Files.createDirectories(exportsRoot);
            Files.createDirectories(backupsRoot);
            Files.createDirectories(archivesRoot);
            Files.createDirectories(workRoot);
            Files.createDirectories(transferRoot);
            Files.createDirectories(conversionRoot);
            Path registryParent = registryPath.getParent();
            if (registryParent != null) Files.createDirectories(registryParent);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create LazyBuilder World-Manager storage directories", exception);
        }
    }
}
