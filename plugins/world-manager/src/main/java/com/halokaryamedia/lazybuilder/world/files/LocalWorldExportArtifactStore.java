package com.halokaryamedia.lazybuilder.world.files;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
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
    private static final int IO_BUFFER_BYTES = 64 * 1024;
    private static final long ZIP_SPACE_RESERVE_BYTES = 16L * 1024L * 1024L;
    private static final long ZIP_ENTRY_OVERHEAD_BYTES = 512L;

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
        requireSafeExportRoot();
        Path target = exportRoot.resolve(safe + type.extension()).normalize();
        if (!exportRoot.equals(target.getParent())) {
            throw new IOException("Export artifact escaped export root");
        }
        if (Files.exists(target)) {
            throw new IOException("Export artifact already exists: " + target.getFileName());
        }

        SourceEstimate estimate = estimateSource(source);
        long requiredFreeBytes = requiredFreeBytes(estimate.bytes(), estimate.files());
        long usableBytes = Files.getFileStore(exportRoot).getUsableSpace();
        if (usableBytes < requiredFreeBytes) {
            throw new IOException("Insufficient disk space for export artifact: required at least "
                    + requiredFreeBytes + " bytes, available " + usableBytes + " bytes");
        }

        Path temporary = exportRoot.resolve("export-" + UUID.randomUUID() + ".tmp");
        try {
            try (var fileOut = Files.newOutputStream(
                         temporary,
                         java.nio.file.StandardOpenOption.CREATE_NEW,
                         java.nio.file.StandardOpenOption.WRITE);
                 var bufferedOut = new BufferedOutputStream(fileOut, IO_BUFFER_BYTES);
                 ZipOutputStream zip = new ZipOutputStream(bufferedOut)) {
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
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    java.nio.file.StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            moveIntoPlace(temporary, target);
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void requireSafeExportRoot() throws IOException {
        if (Files.notExists(exportRoot)) {
            Files.createDirectories(exportRoot);
        }
        if (!Files.isDirectory(exportRoot) || Files.isSymbolicLink(exportRoot)) {
            throw new IOException("Export artifact root is unsafe: " + exportRoot);
        }
    }

    private static SourceEstimate estimateSource(Path source) throws IOException {
        long[] totals = new long[2];
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
                try {
                    totals[0] = Math.addExact(totals[0], attrs.size());
                    totals[1] = Math.addExact(totals[1], 1L);
                } catch (ArithmeticException overflow) {
                    throw new IOException("Export source is too large to estimate safely", overflow);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return new SourceEstimate(totals[0], totals[1]);
    }

    static long requiredFreeBytes(long sourceBytes, long fileCount) throws IOException {
        if (sourceBytes < 0 || fileCount < 0) throw new IllegalArgumentException("Export size estimate must be non-negative");
        try {
            long entryOverhead = Math.multiplyExact(fileCount, ZIP_ENTRY_OVERHEAD_BYTES);
            return Math.addExact(Math.addExact(sourceBytes, entryOverhead), ZIP_SPACE_RESERVE_BYTES);
        } catch (ArithmeticException overflow) {
            throw new IOException("Export source is too large to estimate safely", overflow);
        }
    }

    private static String validateArtifactName(String value) {
        return SafeArtifactName.requirePortable(value, "artifactName");
    }

    private static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target);
        }
    }

    private record SourceEstimate(long bytes, long files) {}
}
