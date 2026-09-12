package com.halokaryamedia.lazybuilder.world.files;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Local bounded export artifact store. No watcher or background worker is owned here. */
public final class LocalWorldExportArtifactStore implements WorldExportArtifactStore {
    private final Path exportRoot;

    public LocalWorldExportArtifactStore(Path exportRoot) {
        this.exportRoot = Objects.requireNonNull(exportRoot, "exportRoot").toAbsolutePath().normalize();
    }

    @Override
    public Path packageDirectory(Path sourceDirectory, String artifactName, ExportArtifactType type) throws IOException {
        Objects.requireNonNull(type, "type");
        Path source = Objects.requireNonNull(sourceDirectory, "sourceDirectory").toAbsolutePath().normalize();
        if (!Files.isDirectory(source) || Files.isSymbolicLink(source)) {
            throw new IOException("Export source is missing or unsafe: " + source);
        }

        String safe = validateArtifactName(artifactName);
        Files.createDirectories(exportRoot);
        Path target = exportRoot.resolve(safe + type.extension()).normalize();
        if (!exportRoot.equals(target.getParent())) {
            throw new IOException("Export artifact escaped export root");
        }
        if (Files.exists(target)) {
            throw new IOException("Export artifact already exists: " + target.getFileName());
        }

        Path temporary = exportRoot.resolve("export-" + UUID.randomUUID() + ".tmp");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
                Files.walkFileTree(source, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                        if (Files.isSymbolicLink(directory)) {
                            throw new IOException("Symbolic links are not supported in export sources: " + directory);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (Files.isSymbolicLink(file)) {
                            throw new IOException("Symbolic links are not supported in export sources: " + file);
                        }
                        String entryName = source.relativize(file).toString().replace('\\', '/');
                        ZipEntry entry = new ZipEntry(entryName);
                        entry.setTime(attrs.lastModifiedTime().toMillis());
                        zip.putNextEntry(entry);
                        Files.copy(file, zip);
                        zip.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            moveIntoPlace(temporary, target);
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String validateArtifactName(String value) {
        Objects.requireNonNull(value, "artifactName");
        if (value.isBlank() || !value.equals(value.strip()) || value.equals(".") || value.equals("..")
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("artifactName must be one safe file base name");
        }
        return value;
    }

    private static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target);
        }
    }
}
