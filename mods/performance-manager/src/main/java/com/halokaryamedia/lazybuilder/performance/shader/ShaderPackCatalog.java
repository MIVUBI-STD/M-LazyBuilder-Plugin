package com.halokaryamedia.lazybuilder.performance.shader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/** Discovers folder and ZIP shader packs without depending on Iris. */
public final class ShaderPackCatalog {
    private final Path directory;
    private volatile String lastScanError = "";
    private volatile List<String> invalidEntries = List.of();

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
                List<ShaderPackDescriptor> raw = new ArrayList<>();
                List<String> invalid = new ArrayList<>();

                entries.forEach(path -> {
                    if (!looksLikePack(path)) return;
                    try {
                        String issue = candidateIssue(path);
                        if (issue.isBlank()) {
                            raw.add(descriptor(path));
                        } else {
                            invalid.add(fileName(path) + ": " + issue);
                        }
                    } catch (RuntimeException error) {
                        String message = error.getMessage();
                        invalid.add(
                                fileName(path)
                                        + ": "
                                        + (message == null || message.isBlank()
                                        ? error.getClass().getSimpleName()
                                        : message)
                        );
                    }
                });

                Map<String, Integer> idCounts = new HashMap<>();
                for (ShaderPackDescriptor pack : raw) {
                    idCounts.merge(pack.id(), 1, Integer::sum);
                }

                List<ShaderPackDescriptor> result = new ArrayList<>();
                for (ShaderPackDescriptor pack : raw) {
                    if (idCounts.getOrDefault(pack.id(), 0) > 1) {
                        invalid.add(
                                fileName(pack.path())
                                        + ": duplicate shader pack id '" + pack.id() + "'"
                        );
                        continue;
                    }
                    result.add(pack);
                }
                result.sort(Comparator.comparing(
                        ShaderPackDescriptor::displayName,
                        String.CASE_INSENSITIVE_ORDER
                ));
                invalid.sort(String.CASE_INSENSITIVE_ORDER);
                invalidEntries = List.copyOf(invalid);
                lastScanError = "";
                return List.copyOf(result);
            }
        } catch (IOException | RuntimeException error) {
            invalidEntries = List.of();
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

    public List<String> invalidEntries() {
        return invalidEntries;
    }

    private boolean looksLikePack(Path path) {
        if (Files.isDirectory(path)) {
            return Files.isDirectory(path.resolve("shaders"))
                    || Files.isRegularFile(path.resolve("shader.properties"));
        }
        String name = fileName(path).toLowerCase(Locale.ROOT);
        return Files.isRegularFile(path) && name.endsWith(".zip");
    }

    private String candidateIssue(Path path) {
        if (Files.isDirectory(path)) {
            if (!Files.isRegularFile(path.resolve("shaders/terrain.vsh"))) {
                return "missing shaders/terrain.vsh";
            }
            if (!Files.isRegularFile(path.resolve("shaders/terrain.fsh"))) {
                return "missing shaders/terrain.fsh";
            }
            return "";
        }

        String name = fileName(path).toLowerCase(Locale.ROOT);
        if (!Files.isRegularFile(path) || !name.endsWith(".zip")) {
            return "unsupported pack source";
        }

        try (ZipFile zip = new ZipFile(path.toFile())) {
            if (zip.getEntry("shaders/terrain.vsh") == null) {
                return "missing shaders/terrain.vsh";
            }
            if (zip.getEntry("shaders/terrain.fsh") == null) {
                return "missing shaders/terrain.fsh";
            }
            return "";
        } catch (IOException error) {
            String message = error.getMessage();
            return "invalid ZIP"
                    + (message == null || message.isBlank() ? "" : " (" + message + ")");
        }
    }

    private ShaderPackDescriptor descriptor(Path path) {
        String fileName = fileName(path);
        boolean zip = Files.isRegularFile(path);
        String display = zip && fileName.toLowerCase(Locale.ROOT).endsWith(".zip")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
        ShaderPackDescriptor draft = new ShaderPackDescriptor(
                persistentId(fileName),
                display,
                path,
                zip ? ShaderPackDescriptor.Kind.ZIP : ShaderPackDescriptor.Kind.DIRECTORY
        );
        try (ShaderPackSource.Session source = ShaderPackSource.openSession(draft)) {
            ShaderPackManifest manifest = ShaderPackManifest.load(source, display);
            String manifestName = manifest.name().isBlank() ? display : manifest.name();
            String resolvedId = manifest.id().isBlank() ? draft.id() : manifest.id();
            return new ShaderPackDescriptor(
                    resolvedId,
                    manifestName,
                    path,
                    draft.kind(),
                    manifest
            );
        } catch (IOException error) {
            throw new IllegalArgumentException(
                    "invalid shader.properties: "
                            + (error.getMessage() == null ? "unable to read manifest" : error.getMessage()),
                    error
            );
        }
    }

    static String persistentId(String fileName) {
        String base = stableId(fileName);
        return base + "-" + nameFingerprint(fileName);
    }

    static String sourceDerivedId(ShaderPackDescriptor descriptor) {
        if (descriptor == null || descriptor.path() == null) return "";
        return persistentId(fileName(descriptor.path()));
    }

    static String legacyId(ShaderPackDescriptor descriptor) {
        if (descriptor == null || descriptor.path() == null) return "";
        return stableId(fileName(descriptor.path()));
    }

    private static String nameFingerprint(String fileName) {
        String stableName = fileName == null ? "" : fileName;
        String uuid = UUID.nameUUIDFromBytes(
                stableName.getBytes(StandardCharsets.UTF_8)
        ).toString();
        return uuid.substring(0, 8);
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
