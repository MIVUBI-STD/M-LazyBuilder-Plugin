package com.halokaryamedia.lazybuilder.performance.shader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
                    if (isCandidate(path)) {
                        raw.add(descriptor(path));
                    } else {
                        invalid.add(fileName(path));
                    }
                });

                List<ShaderPackDescriptor> result = resolveIdCollisions(raw);
                result.sort(Comparator.comparing(
                        ShaderPackDescriptor::displayName,
                        String.CASE_INSENSITIVE_ORDER
                ));
                invalid.sort(String.CASE_INSENSITIVE_ORDER);
                invalidEntries = List.copyOf(invalid);
                lastScanError = "";
                return List.copyOf(result);
            }
        } catch (IOException error) {
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
        try (ShaderPackSource.Session source = ShaderPackSource.openSession(draft)) {
            ShaderPackManifest manifest = ShaderPackManifest.load(source, display);
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

    private static List<ShaderPackDescriptor> resolveIdCollisions(
            List<ShaderPackDescriptor> descriptors
    ) {
        if (descriptors == null || descriptors.isEmpty()) return new ArrayList<>();

        Map<String, Integer> counts = new HashMap<>();
        for (ShaderPackDescriptor descriptor : descriptors) {
            counts.merge(descriptor.id(), 1, Integer::sum);
        }

        List<ShaderPackDescriptor> resolved = new ArrayList<>(descriptors.size());
        for (ShaderPackDescriptor descriptor : descriptors) {
            if (counts.getOrDefault(descriptor.id(), 0) <= 1) {
                resolved.add(descriptor);
                continue;
            }

            String suffix = pathFingerprint(descriptor.path());
            resolved.add(new ShaderPackDescriptor(
                    descriptor.id() + "-" + suffix,
                    descriptor.displayName(),
                    descriptor.path(),
                    descriptor.kind(),
                    descriptor.manifest()
            ));
        }
        return resolved;
    }

    private static String pathFingerprint(Path path) {
        String normalized = path.toAbsolutePath().normalize().toString();
        String uuid = UUID.nameUUIDFromBytes(
                normalized.getBytes(StandardCharsets.UTF_8)
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
