package com.halokaryamedia.lazybuilder.world.files;

import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Local filesystem implementation for managed-world copies and deletion.
 *
 * <p>Methods perform synchronous filesystem I/O and therefore must be invoked by
 * request handlers from an appropriate worker context, never from an idle loop.</p>
 */
public final class LocalWorldFileRepository implements WorldFileRepository {
    private static final Set<String> DUPLICATE_EXCLUDED_DIRECTORIES = Set.of("playerdata", "advancements", "stats");
    private static final String WORK_SUFFIX = ".work";
    private static final String COPY_SUFFIX = ".copy";
    private static final String DELETE_SUFFIX = ".delete";

    private final Path worldRoot;
    private final Path workspaceRoot;

    public LocalWorldFileRepository(Path worldRoot, Path workspaceRoot) {
        this.worldRoot = Objects.requireNonNull(worldRoot, "worldRoot").toAbsolutePath().normalize();
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
    }

    @Override
    public Path stageCopy(WorldRecord source, UUID operationId, WorldCopyProfile profile) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(profile, "profile");

        Path sourcePath = worldPath(source.folderName());
        if (!Files.isDirectory(sourcePath) || Files.isSymbolicLink(sourcePath)) {
            throw new IOException("Managed world folder is missing or unsafe: " + source.folderName());
        }

        Path destination = reserveTypedWorkspace(operationId, COPY_SUFFIX);
        try {
            copyTree(sourcePath, destination, profile);
            return destination;
        } catch (IOException | RuntimeException exception) {
            try {
                deleteTree(destination);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    @Override
    public Path stageDelete(WorldRecord world, UUID operationId) throws IOException {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(operationId, "operationId");
        Path source = worldPath(world.folderName());
        if (!Files.isDirectory(source) || Files.isSymbolicLink(source)) {
            throw new IOException("Managed world folder is missing or unsafe: " + world.folderName());
        }
        // Delete staging must remain distinguishable from disposable workspaces. If the
        // process stops after this move, the staged directory can be the only copy left.
        Path destination = reserveTypedWorkspace(operationId, DELETE_SUFFIX);
        moveDirectory(source, destination);
        return destination;
    }

    @Override
    public Path reserveWorkspace(UUID operationId) throws IOException {
        return reserveTypedWorkspace(operationId, WORK_SUFFIX);
    }

    @Override
    public int recoverTransientWorkspaces() throws IOException {
        if (Files.notExists(workspaceRoot)) return 0;
        if (!Files.isDirectory(workspaceRoot) || Files.isSymbolicLink(workspaceRoot)) {
            throw new IOException("World Manager work root is unsafe");
        }

        int recovered = 0;
        try (var children = Files.list(workspaceRoot)) {
            for (Path child : children.toList()) {
                String name = child.getFileName().toString();
                if (!isRecoverableTransientName(name)) continue;
                Path target = requireDirectWorkspace(child);
                if (Files.isSymbolicLink(target)) {
                    Files.deleteIfExists(target);
                } else {
                    deleteTree(target);
                }
                recovered++;
            }
        }
        return recovered;
    }

    @Override
    public void publishStagedWorld(Path stagedWorld, String destinationFolder) throws IOException {
        Path source = requireDirectWorkspace(stagedWorld);
        Path destination = worldPath(destinationFolder);
        if (!Files.isDirectory(source) || Files.isSymbolicLink(source)) {
            throw new IOException("Staged world is missing or unsafe: " + source);
        }
        if (Files.exists(destination)) {
            throw new IOException("Destination world already exists: " + destinationFolder);
        }
        moveDirectory(source, destination);
    }

    @Override
    public void deleteWorld(WorldRecord world) throws IOException {
        Objects.requireNonNull(world, "world");
        Path target = worldPath(world.folderName());
        if (Files.notExists(target)) return;
        if (Files.isSymbolicLink(target)) {
            throw new IOException("Refusing to recursively delete symbolic-link world root: " + world.folderName());
        }
        deleteTree(target);
    }

    @Override
    public void deleteWorkspace(Path workspace) throws IOException {
        Path target = requireDirectWorkspace(workspace);
        if (Files.notExists(target)) return;
        if (Files.isSymbolicLink(target)) {
            Files.delete(target);
            return;
        }
        deleteTree(target);
    }

    private Path reserveTypedWorkspace(UUID operationId, String suffix) throws IOException {
        Objects.requireNonNull(operationId, "operationId");
        Files.createDirectories(workspaceRoot);
        if (!Files.isDirectory(workspaceRoot) || Files.isSymbolicLink(workspaceRoot)) {
            throw new IOException("World Manager work root is unsafe");
        }
        Path destination = workspacePath(operationId + suffix);
        if (Files.exists(destination)) {
            throw new IOException("Workspace already exists: " + destination.getFileName());
        }
        return destination;
    }

    private static boolean isRecoverableTransientName(String name) {
        String suffix;
        if (name.endsWith(WORK_SUFFIX)) suffix = WORK_SUFFIX;
        else if (name.endsWith(COPY_SUFFIX)) suffix = COPY_SUFFIX;
        else return false;
        String id = name.substring(0, name.length() - suffix.length());
        try {
            UUID.fromString(id);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void copyTree(Path source, Path destination, WorldCopyProfile profile) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(directory)) {
                    throw new IOException("Symbolic links are not supported in managed world copies: " + directory);
                }
                Path relative = source.relativize(directory);
                if (profile == WorldCopyProfile.DUPLICATE && relative.getNameCount() == 1
                        && DUPLICATE_EXCLUDED_DIRECTORIES.contains(relative.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(destination.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(file)) {
                    throw new IOException("Symbolic links are not supported in managed world copies: " + file);
                }
                Path relative = source.relativize(file);
                if (shouldSkipFile(relative, profile)) return FileVisitResult.CONTINUE;
                Files.copy(file, destination.resolve(relative), StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean shouldSkipFile(Path relative, WorldCopyProfile profile) {
        if (relative.getNameCount() == 1 && relative.getFileName().toString().equals("session.lock")) return true;
        return profile == WorldCopyProfile.DUPLICATE
                && relative.getNameCount() == 1
                && relative.getFileName().toString().equals("uid.dat");
    }

    private static void moveDirectory(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination);
        }
    }

    private Path worldPath(String folderName) {
        String safe = validateSingleName(folderName, "world folder");
        Path target = worldRoot.resolve(safe).normalize();
        if (!worldRoot.equals(target.getParent())) {
            throw new IllegalArgumentException("World folder must remain directly inside the world container");
        }
        return target;
    }

    private Path workspacePath(String name) {
        String safe = validateSingleName(name, "workspace");
        Path target = workspaceRoot.resolve(safe).normalize();
        if (!workspaceRoot.equals(target.getParent())) {
            throw new IllegalArgumentException("Workspace must remain directly inside the World Manager work root");
        }
        return target;
    }

    private Path requireDirectWorkspace(Path path) {
        Objects.requireNonNull(path, "path");
        Path target = path.toAbsolutePath().normalize();
        if (!workspaceRoot.equals(target.getParent())) {
            throw new IllegalArgumentException("Path is not an owned World Manager workspace: " + path);
        }
        return target;
    }

    private static String validateSingleName(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank() || !value.equals(value.strip()) || value.equals(".") || value.equals("..")
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + " must be one safe directory name");
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
