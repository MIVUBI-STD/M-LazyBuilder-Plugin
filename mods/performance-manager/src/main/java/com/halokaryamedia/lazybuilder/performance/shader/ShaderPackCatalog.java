package com.halokaryamedia.lazybuilder.performance.shader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/** Discovers folder and ZIP shader packs without depending on Iris. */
public final class ShaderPackCatalog {
    private final Path directory;
    private volatile String lastScanError = "";

    public ShaderPackCatalog(Path directory) {
        this.directory = directory;
    }

    public Path directory() {
        return directory;
    }

    public List<ShaderPackDescriptor> scan() {
        try {
            Files.createDirectories(directory);
            try (Stream<Path> entries = Files.list(directory)) {
                List<ShaderPackDescriptor> result = entries
                        .filter(this::isCandidate)
                        .map(this::descriptor)
                        .sorted(Comparator.comparing(
                                ShaderPackDescriptor::displayName,
                                String.CASE_INSENSITIVE_ORDER
                        ))
                        .toList();
                lastScanError = "";
                return result;
            }
        } catch (IOException error) {
            String message = error.getMessage();
            lastScanError = message == null || message.isBlank()
                    ? error.getClass().getSimpleName()
                    : message;
            return List.of();
        }
    }

    public String lastScanError() {
        return lastScanError;
    }

    private boolean isCandidate(Path path) {
        if (Files.isDirectory(path)) {
            return Files.isRegularFile(path.resolve("shaders/terrain.vsh"))
                    && Files.isRegularFile(path.resolve("shaders/terrain.fsh"));
        }

        String name = fileName(path).toLowerCase(Locale.ROOT);
        if (!Files.isRegularFile(path) || !name.endsWith(".zip")) return false;

        try (ZipFile zip = new ZipFile(path.toFile())) {
            return zip.getEntry("shaders/terrain.vsh") != null
                    && zip.getEntry("shaders/terrain.fsh") != null;
        } catch (IOException error) {
            return false;
        }
    }

    private ShaderPackDescriptor descriptor(Path path) {
        String fileName = fileName(path);
        boolean zip = Files.isRegularFile(path);
        String display = zip && fileName.toLowerCase(Locale.ROOT).endsWith(".zip")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
        ShaderPackDescriptor draft = new ShaderPackDescriptor(
                stableId(fileName),
                display,
                path,
                zip ? ShaderPackDescriptor.Kind.ZIP : ShaderPackDescriptor.Kind.DIRECTORY
        );
        try {
            ShaderPackManifest manifest = ShaderPackManifest.load(
                    ShaderPackSource.open(draft),
                    display
            );
            String manifestName = manifest.name().isBlank() ? display : manifest.name();
            return new ShaderPackDescriptor(
                    draft.id(),
                    manifestName,
                    path,
                    draft.kind(),
                    manifest
            );
        } catch (IOException ignored) {
            return draft;
        }
    }

    static String stableId(String fileName) {
        String normalized = fileName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "shader-pack" : normalized;
    }

    private static String fileName(Path path) {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }
}
