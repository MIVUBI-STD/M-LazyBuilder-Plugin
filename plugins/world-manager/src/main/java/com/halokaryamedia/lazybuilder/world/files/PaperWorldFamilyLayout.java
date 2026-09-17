package com.halokaryamedia.lazybuilder.world.files;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

/**
 * Stateless filesystem transformer between canonical Java dimension layout and
 * the Bukkit/Paper 1.21.4 world-family layout.
 *
 * <p>The repository remains transaction owner. This helper only moves dimension
 * payloads and companion level metadata inside paths already owned by that
 * repository.</p>
 */
final class PaperWorldFamilyLayout {
    static final String NETHER_SUFFIX = "_nether";
    static final String END_SUFFIX = "_the_end";
    static final String NETHER_DIRECTORY = "DIM-1";
    static final String END_DIRECTORY = "DIM1";
    static final String FAMILY_PENDING_MARKER = ".lazybuilder-family-publish-pending";

    private static final List<DimensionLayout> DIMENSIONS = List.of(
            new DimensionLayout(NETHER_SUFFIX, NETHER_DIRECTORY),
            new DimensionLayout(END_SUFFIX, END_DIRECTORY)
    );

    private PaperWorldFamilyLayout() { }

    /** Fails before root publication if a sibling name is already owned by unrelated data. */
    static void requireFreshFamilyDestinations(Path worldRoot, String baseFolder) throws IOException {
        for (DimensionLayout layout : DIMENSIONS) {
            Path sibling = directChild(worldRoot, baseFolder + layout.suffix());
            if (Files.exists(sibling)) {
                throw new IOException("Paper dimension destination already exists: " + sibling.getFileName());
            }
        }
    }

    static void publishCanonicalDimensions(
            Path worldRoot,
            String baseFolder,
            boolean transactional
    ) throws IOException {
        Path root = directChild(worldRoot, baseFolder);
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) {
            throw new IOException("Published world root is missing or unsafe: " + baseFolder);
        }
        Path levelDat = root.resolve("level.dat");
        if (!Files.isRegularFile(levelDat) || Files.isSymbolicLink(levelDat)) {
            throw new IOException("Published world is missing safe level.dat: " + baseFolder);
        }

        for (DimensionLayout layout : DIMENSIONS) {
            Path canonical = root.resolve(layout.directory()).normalize();
            if (!canonical.startsWith(root)) throw new IOException("Canonical dimension escaped world root");
            Path sibling = directChild(worldRoot, baseFolder + layout.suffix());
            Path target = sibling.resolve(layout.directory()).normalize();

            if (Files.notExists(canonical)) {
                if (Files.exists(sibling)) validateOwnedOrCommittedSibling(sibling, target);
                continue;
            }
            if (!Files.isDirectory(canonical) || Files.isSymbolicLink(canonical)) {
                throw new IOException("Canonical dimension path is unsafe: " + canonical);
            }

            boolean siblingCreated = false;
            if (Files.exists(sibling)) {
                if (!transactional || !hasSafeFamilyMarker(sibling)) {
                    throw new IOException("Paper dimension folder already exists: " + sibling.getFileName());
                }
                if (!Files.isDirectory(sibling) || Files.isSymbolicLink(sibling)) {
                    throw new IOException("Paper dimension folder is unsafe: " + sibling.getFileName());
                }
                if (Files.exists(target)) {
                    throw new IOException("Both canonical and published dimension data exist for " + layout.directory());
                }
            } else {
                Files.createDirectory(sibling);
                siblingCreated = true;
            }

            try {
                if (transactional && !Files.exists(sibling.resolve(FAMILY_PENDING_MARKER))) {
                    Files.createFile(sibling.resolve(FAMILY_PENDING_MARKER));
                }
                copyLevelMetadataIfMissing(root, sibling);
                move(canonical, target);
            } catch (IOException | RuntimeException failure) {
                if (siblingCreated) {
                    try {
                        if (Files.exists(target) && Files.notExists(canonical)) move(target, canonical);
                        deleteTree(sibling);
                    } catch (IOException rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
                throw failure;
            }
        }
    }

    private static void validateOwnedOrCommittedSibling(Path sibling, Path target) throws IOException {
        if (!Files.isDirectory(sibling) || Files.isSymbolicLink(sibling)) {
            throw new IOException("Paper dimension folder is unsafe: " + sibling.getFileName());
        }
        if (!Files.isDirectory(target) || Files.isSymbolicLink(target)) {
            throw new IOException("Paper dimension data is missing or unsafe: " + target);
        }
    }

    private static boolean hasSafeFamilyMarker(Path sibling) throws IOException {
        Path marker = sibling.resolve(FAMILY_PENDING_MARKER);
        if (Files.notExists(marker)) return false;
        if (!Files.isRegularFile(marker) || Files.isSymbolicLink(marker)) {
            throw new IOException("Paper family publication marker is unsafe: " + marker);
        }
        return true;
    }

    private static void copyLevelMetadataIfMissing(Path root, Path sibling) throws IOException {
        Path levelDat = root.resolve("level.dat");
        Path siblingLevel = sibling.resolve("level.dat");
        if (Files.notExists(siblingLevel)) {
            Files.copy(levelDat, siblingLevel, StandardCopyOption.COPY_ATTRIBUTES);
        } else if (!Files.isRegularFile(siblingLevel) || Files.isSymbolicLink(siblingLevel)) {
            throw new IOException("Paper dimension level.dat is unsafe: " + siblingLevel);
        }

        Path old = root.resolve("level.dat_old");
        Path siblingOld = sibling.resolve("level.dat_old");
        if (Files.isRegularFile(old) && !Files.isSymbolicLink(old) && Files.notExists(siblingOld)) {
            Files.copy(old, siblingOld, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    /**
     * Moves any existing Paper sibling dimensions into one canonical staged world.
     * The base world itself must already have been moved into {@code stagedRoot}.
     */
    static void consolidateSiblingsForStaging(
            Path worldRoot,
            String baseFolder,
            Path stagedRoot
    ) throws IOException {
        Path stage = Objects.requireNonNull(stagedRoot, "stagedRoot").toAbsolutePath().normalize();
        if (!Files.isDirectory(stage) || Files.isSymbolicLink(stage)) {
            throw new IOException("Staged world root is missing or unsafe: " + stage);
        }

        for (DimensionLayout layout : DIMENSIONS) {
            Path sibling = directChild(worldRoot, baseFolder + layout.suffix());
            if (Files.notExists(sibling)) continue;
            if (!Files.isDirectory(sibling) || Files.isSymbolicLink(sibling)) {
                throw new IOException("Paper dimension folder is unsafe: " + sibling.getFileName());
            }

            Path target = stage.resolve(layout.directory()).normalize();
            if (!target.startsWith(stage)) throw new IOException("Staged dimension escaped workspace");
            if (Files.exists(target)) {
                throw new IOException("Both canonical and Paper dimension data exist for " + layout.directory());
            }

            Path nested = sibling.resolve(layout.directory()).normalize();
            Path source = Files.exists(nested) ? nested : sibling;
            if (!Files.isDirectory(source) || Files.isSymbolicLink(source)) {
                throw new IOException("Paper dimension data is unsafe: " + source);
            }

            if (source.equals(sibling)) {
                move(source, target);
            } else {
                move(source, target);
                deleteTree(sibling);
            }
        }
    }

    static void clearFamilyPendingMarkers(Path worldRoot, String baseFolder) throws IOException {
        for (DimensionLayout layout : DIMENSIONS) {
            Path sibling = directChild(worldRoot, baseFolder + layout.suffix());
            if (Files.notExists(sibling)) continue;
            if (!Files.isDirectory(sibling) || Files.isSymbolicLink(sibling)) {
                throw new IOException("Paper dimension folder is unsafe: " + sibling.getFileName());
            }
            Path marker = sibling.resolve(FAMILY_PENDING_MARKER);
            if (Files.exists(marker) && (!Files.isRegularFile(marker) || Files.isSymbolicLink(marker))) {
                throw new IOException("Paper family publication marker is unsafe: " + marker);
            }
            Files.deleteIfExists(marker);
        }
    }

    static void deleteFamilySiblings(Path worldRoot, String baseFolder) throws IOException {
        IOException failure = null;
        for (DimensionLayout layout : DIMENSIONS) {
            Path sibling = directChild(worldRoot, baseFolder + layout.suffix());
            if (Files.notExists(sibling)) continue;
            try {
                if (Files.isSymbolicLink(sibling)) {
                    throw new IOException("Refusing to delete symbolic-link dimension folder: " + sibling.getFileName());
                }
                deleteTree(sibling);
            } catch (IOException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    static boolean hasPendingSibling(Path worldRoot, String baseFolder) throws IOException {
        for (DimensionLayout layout : DIMENSIONS) {
            Path sibling = directChild(worldRoot, baseFolder + layout.suffix());
            Path marker = sibling.resolve(FAMILY_PENDING_MARKER);
            if (Files.isRegularFile(marker) && !Files.isSymbolicLink(marker)) return true;
        }
        return false;
    }

    private static Path directChild(Path root, String name) {
        Path normalizedRoot = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        if (name == null || name.isBlank() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("World family folder must be one safe name");
        }
        Path child = normalizedRoot.resolve(name).normalize();
        if (!normalizedRoot.equals(child.getParent())) {
            throw new IllegalArgumentException("World family folder escaped world root");
        }
        return child;
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (Files.notExists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record DimensionLayout(String suffix, String directory) { }
}
