package com.halokaryamedia.lazybuilder.world.files;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Path-safe ZIP/.mcworld staging for World Manager imports. */
public final class LocalWorldImportArtifactStore implements WorldImportArtifactStore {
    private static final String TRANSFER_MARKER = ".lazybuilder-transfer.properties";
    private static final String JAVA_1_21_4 = "JAVA_1_21_4";
    private static final int IO_BUFFER_BYTES = 64 * 1024;

    private final Path importsRoot;
    private final long maxEntries;
    private final long maxUncompressedBytes;

    public LocalWorldImportArtifactStore(Path importsRoot, long maxEntries, long maxUncompressedBytes) {
        this.importsRoot = Objects.requireNonNull(importsRoot, "importsRoot").toAbsolutePath().normalize();
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be positive");
        if (maxUncompressedBytes < 1) throw new IllegalArgumentException("maxUncompressedBytes must be positive");
        this.maxEntries = maxEntries;
        this.maxUncompressedBytes = maxUncompressedBytes;
    }

    @Override
    public StagedImport stageArchive(String artifactName, Path workspace) throws IOException {
        Path artifact = resolveArtifact(artifactName);
        Path target = Objects.requireNonNull(workspace, "workspace").toAbsolutePath().normalize();
        if (Files.exists(target)) throw new IOException("Import workspace already exists: " + target.getFileName());
        Files.createDirectories(target);

        boolean success = false;
        try {
            extractBounded(artifact, target);
            normalizeSingleRoot(target);
            Path levelDat = target.resolve("level.dat");
            if (!Files.isRegularFile(levelDat)) {
                throw new IOException("Import archive does not contain a Minecraft level.dat");
            }

            DetectedEdition edition = Files.isDirectory(target.resolve("db"))
                    ? DetectedEdition.BEDROCK : DetectedEdition.JAVA;

            // Native Java fast-path authority comes from the actual level.dat
            // DataVersion, not from a forgeable transfer-marker properties file.
            String trustedFormat = edition == DetectedEdition.JAVA
                    && JavaLevelDataVersion.read(levelDat).orElse(-1) == JavaLevelDataVersion.JAVA_1_21_4
                    ? JAVA_1_21_4
                    : null;

            sanitizeIdentity(target);
            success = true;
            return new StagedImport(target, edition, trustedFormat);
        } finally {
            if (!success) deleteTree(target);
        }
    }

    @Override
    public void sanitizeConvertedWorld(Path worldDirectory) throws IOException {
        Path root = Objects.requireNonNull(worldDirectory, "worldDirectory").toAbsolutePath().normalize();
        if (!Files.isDirectory(root) || !Files.isRegularFile(root.resolve("level.dat"))) {
            throw new IOException("Converted import is not a valid world directory");
        }
        sanitizeIdentity(root);
    }

    @Override
    public void deleteArtifact(String artifactName) throws IOException {
        Files.deleteIfExists(resolveArtifact(artifactName));
    }

    private Path resolveArtifact(String artifactName) throws IOException {
        String safe = validateSingleName(artifactName);
        String lower = safe.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".zip") && !lower.endsWith(".mcworld")) {
            throw new IOException("Import artifact must be .zip or .mcworld");
        }
        Path path = importsRoot.resolve(safe).normalize();
        if (!importsRoot.equals(path.getParent()) || !Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
            throw new IOException("Import artifact is missing or unsafe: " + safe);
        }
        return path;
    }

    private void extractBounded(Path archive, Path target) throws IOException {
        long entries = 0;
        long totalBytes = 0;
        byte[] buffer = new byte[IO_BUFFER_BYTES];
        try (InputStream fileIn = Files.newInputStream(archive);
             BufferedInputStream bufferedIn = new BufferedInputStream(fileIn, IO_BUFFER_BYTES);
             ZipInputStream zip = new ZipInputStream(bufferedIn)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                if (++entries > maxEntries) throw new IOException("Import archive exceeds file-count limit");
                String name = entry.getName().replace('\\', '/');
                if (name.isBlank()) continue;
                Path output = target.resolve(name).normalize();
                if (!output.startsWith(target)) throw new IOException("Import archive contains path traversal");
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                    continue;
                }
                Path parent = output.getParent();
                if (parent != null) Files.createDirectories(parent);
                try (var fileOut = Files.newOutputStream(output);
                     var out = new BufferedOutputStream(fileOut, IO_BUFFER_BYTES)) {
                    for (int read; (read = zip.read(buffer)) >= 0;) {
                        if (read == 0) continue;
                        totalBytes += read;
                        if (totalBytes > maxUncompressedBytes) {
                            throw new IOException("Import archive exceeds uncompressed-size limit");
                        }
                        out.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private static void normalizeSingleRoot(Path target) throws IOException {
        if (Files.isRegularFile(target.resolve("level.dat"))) return;
        List<Path> children;
        try (var stream = Files.list(target)) {
            children = stream.filter(path -> !path.getFileName().toString().equals("__MACOSX")).toList();
        }
        if (children.size() != 1 || !Files.isDirectory(children.getFirst())
                || !Files.isRegularFile(children.getFirst().resolve("level.dat"))) {
            return;
        }
        Path nested = children.getFirst();
        Path temporary = target.resolveSibling(target.getFileName() + "-normalize");
        if (Files.exists(temporary)) throw new IOException("Import normalization workspace already exists");
        moveDirectory(nested, temporary);
        deleteTree(target);
        moveDirectory(temporary, target);
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target);
        }
    }

    private static void sanitizeIdentity(Path root) throws IOException {
        Files.deleteIfExists(root.resolve("session.lock"));
        Files.deleteIfExists(root.resolve("uid.dat"));
        Files.deleteIfExists(root.resolve(TRANSFER_MARKER));
    }

    private static String validateSingleName(String value) {
        Objects.requireNonNull(value, "artifactName");
        if (value.isBlank() || !value.equals(value.strip()) || value.equals(".") || value.equals("..")
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("artifactName must be one safe file name");
        }
        return value;
    }

    private static void deleteTree(Path root) throws IOException {
        if (Files.notExists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) throw exception;
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
