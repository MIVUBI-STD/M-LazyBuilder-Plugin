package com.halokaryamedia.lazybuilder.world.files;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

/** Region-granular filesystem copier used only by area snapshot staging. */
final class AreaSnapshotTreeCopier {
    private static final Set<String> REGION_DIRECTORIES = Set.of("region", "entities", "poi");
    private static final Set<String> DIMENSION_DIRECTORIES = Set.of("DIM-1", "DIM1", "dimensions");
    private static final String OVERWORLD = "minecraft:overworld";

    private AreaSnapshotTreeCopier() { }

    static long estimateRootBytes(Path source, AreaCopySelection selection, long entryOverhead) throws IOException {
        return walkEstimate(source, selection, entryOverhead, true);
    }

    static long estimateDimensionBytes(Path source, AreaCopySelection selection, long entryOverhead) throws IOException {
        return walkEstimate(source, selection, entryOverhead, false);
    }

    static void copyRoot(Path source, Path destination, AreaCopySelection selection) throws IOException {
        walkCopy(source, destination, selection, true);
    }

    static void copyDimension(Path source, Path destination, AreaCopySelection selection) throws IOException {
        walkCopy(source, destination, selection, false);
    }

    private static long walkEstimate(
            Path source,
            AreaCopySelection selection,
            long entryOverhead,
            boolean rootTree
    ) throws IOException {
        selection.requireActive();
        long[] total = {0L};
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                selection.requireActive();
                requireSafe(directory);
                Path relative = source.relativize(directory);
                if (shouldSkipDirectory(relative, selection, rootTree)) return FileVisitResult.SKIP_SUBTREE;
                total[0] = add(total[0], entryOverhead);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                selection.requireActive();
                requireSafe(file);
                Path relative = source.relativize(file);
                if (!shouldCopyFile(relative, selection, rootTree)) return FileVisitResult.CONTINUE;
                total[0] = add(total[0], entryOverhead);
                total[0] = add(total[0], attrs.size());
                return FileVisitResult.CONTINUE;
            }
        });
        return total[0];
    }

    private static void walkCopy(
            Path source,
            Path destination,
            AreaCopySelection selection,
            boolean rootTree
    ) throws IOException {
        selection.requireActive();
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                selection.requireActive();
                requireSafe(directory);
                Path relative = source.relativize(directory);
                if (shouldSkipDirectory(relative, selection, rootTree)) return FileVisitResult.SKIP_SUBTREE;
                Files.createDirectories(destination.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                selection.requireActive();
                requireSafe(file);
                Path relative = source.relativize(file);
                if (!shouldCopyFile(relative, selection, rootTree)) return FileVisitResult.CONTINUE;
                Path target = destination.resolve(relative);
                Path parent = target.getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean shouldSkipDirectory(Path relative, AreaCopySelection selection, boolean rootTree) {
        if (relative.getNameCount() == 0) return false;
        String first = relative.getName(0).toString();
        if (rootTree && DIMENSION_DIRECTORIES.contains(first)) return true;
        return rootTree && !OVERWORLD.equals(selection.dimensionId()) && REGION_DIRECTORIES.contains(first);
    }

    private static boolean shouldCopyFile(Path relative, AreaCopySelection selection, boolean rootTree) {
        if (relative.getNameCount() == 1 && relative.getFileName().toString().equals("session.lock")) return false;
        if (relative.getNameCount() < 2) return true;
        String first = relative.getName(0).toString();
        if (!REGION_DIRECTORIES.contains(first)) return true;
        if (rootTree && !OVERWORLD.equals(selection.dimensionId())) return false;
        return includesRegionFile(relative.getFileName().toString(), selection);
    }

    private static boolean includesRegionFile(String fileName, AreaCopySelection selection) {
        if (!fileName.startsWith("r.") || !fileName.endsWith(".mca")) return true;
        String body = fileName.substring(2, fileName.length() - 4);
        String[] parts = body.split("\\.", -1);
        if (parts.length != 2) return true;
        try {
            return selection.includesRegion(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (NumberFormatException malformed) {
            return true;
        }
    }

    private static void requireSafe(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("Symbolic links are not supported in managed world copies: " + path);
        }
    }

    private static long add(long current, long increment) throws IOException {
        try {
            return Math.addExact(current, increment);
        } catch (ArithmeticException overflow) {
            throw new IOException("Managed world copy size exceeds supported range", overflow);
        }
    }
}
