package com.halokaryamedia.lazybuilder.world.files;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
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
                        requireRegularSourceFile(file, attrs);
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
            publishIfAbsent(temporary, target);
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
                requireRegularSourceFile(file, attrs);
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

    /**
     * Both preflight estimation and archive writing must reject links and
     * non-regular entries. Opening a FIFO/device could block the export worker.
     */
    static void requireRegularSourceFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (attrs.isSymbolicLink() || Files.isSymbolicLink(file)) {
            throw new IOException("Symbolic links are not supported in export sources: " + file);
        }
        if (!attrs.isRegularFile()) {
            throw new IOException("Export source contains a non-regular file: " + file);
        }
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

    /**
     * Final publication is serialized for this World Manager export store.
     * Different worlds may build ZIPs concurrently; only the last existence
     * check and no-replace move are guarded against competing LazyBuilder
     * requests. No new worker, queue, or global export manager is created.
     */
    synchronized void publishIfAbsent(Path staged, Path target) throws IOException {
        if (Files.exists(target)) throw new FileAlreadyExistsException(target.toString());
        moveIntoPlace(staged, target);
    }

    /**
     * Publish without replacing another completed export.
     *
     * ATOMIC_MOVE permits provider-specific replacement of an existing target,
     * even without REPLACE_EXISTING. In particular, a target created after the
     * existence preflight can be silently replaced on Linux filesystems.
     * The ordinary move with no replace option fails instead. Both paths are
     * direct children of the same canonical export directory.
     */
    static void moveIntoPlace(Path source, Path target) throws IOException {
        Files.move(source, target);
    }

    private record SourceEstimate(long bytes, long files) {}
}
