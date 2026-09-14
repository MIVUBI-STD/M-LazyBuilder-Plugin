package com.halokaryamedia.lazybuilder.world.files;

import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    private static final String PENDING_PUBLISH_MARKER = ".lazybuilder-publish-pending";

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
            try { deleteTree(destination); }
            catch (IOException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
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
        Path destination = reserveDeleteWorkspace(operationId, world.folderName());
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
        requireSafeWorkspaceRoot();
        int recovered = 0;
        try (var children = Files.list(workspaceRoot)) {
            for (Path child : children.toList()) {
                if (!isRecoverableTransientName(child.getFileName().toString())) continue;
                Path target = requireDirectWorkspace(child);
                if (Files.isSymbolicLink(target)) Files.deleteIfExists(target);
                else deleteTree(target);
                recovered++;
            }
        }
        return recovered;
    }

    @Override
    public DeleteRecovery recoverDeleteWorkspaces(Collection<WorldRecord> managedWorlds) throws IOException {
        Objects.requireNonNull(managedWorlds, "managedWorlds");
        if (Files.notExists(workspaceRoot)) return new DeleteRecovery(0, 0, 0);
        requireSafeWorkspaceRoot();

        Set<String> managedFolders = new HashSet<>();
        for (WorldRecord world : managedWorlds) managedFolders.add(world.folderName());

        int restored = 0;
        int discarded = 0;
        int preserved = 0;
        try (var children = Files.list(workspaceRoot)) {
            for (Path child : children.toList()) {
                String name = child.getFileName().toString();
                if (!name.endsWith(DELETE_SUFFIX)) continue;
                Optional<DeleteWorkspaceIdentity> identity = parseDeleteWorkspaceName(name);
                Path staged = requireDirectWorkspace(child);
                if (identity.isEmpty() || Files.isSymbolicLink(staged) || !Files.isDirectory(staged)) {
                    preserved++;
                    continue;
                }

                String folderName = identity.get().folderName();
                if (managedFolders.contains(folderName)) {
                    Path destination = worldPath(folderName);
                    if (Files.exists(destination)) {
                        preserved++;
                        continue;
                    }
                    moveDirectory(staged, destination);
                    restored++;
                } else {
                    deleteTree(staged);
                    discarded++;
                }
            }
        }
        return new DeleteRecovery(restored, discarded, preserved);
    }

    @Override
    public PublishRecovery recoverPublishedWorlds(Collection<WorldRecord> managedWorlds) throws IOException {
        Objects.requireNonNull(managedWorlds, "managedWorlds");
        if (Files.notExists(worldRoot)) return new PublishRecovery(0, 0);
        if (!Files.isDirectory(worldRoot) || Files.isSymbolicLink(worldRoot)) {
            throw new IOException("World container is unsafe");
        }
        Set<String> managedFolders = new HashSet<>();
        for (WorldRecord world : managedWorlds) managedFolders.add(world.folderName());

        int finalized = 0;
        int discarded = 0;
        try (var children = Files.list(worldRoot)) {
            for (Path child : children.toList()) {
                if (!Files.isDirectory(child) || Files.isSymbolicLink(child)) continue;
                Path marker = child.resolve(PENDING_PUBLISH_MARKER);
                if (Files.notExists(marker)) continue;
                if (!Files.isRegularFile(marker) || Files.isSymbolicLink(marker)) {
                    throw new IOException("Pending publication marker is unsafe in " + child.getFileName());
                }
                String folderName = child.getFileName().toString();
                if (managedFolders.contains(folderName)) {
                    Files.delete(marker);
                    finalized++;
                } else {
                    deleteTree(child);
                    discarded++;
                }
            }
        }
        return new PublishRecovery(finalized, discarded);
    }

    @Override
    public void markPublishedWorldCommitted(String destinationFolder) throws IOException {
        Path destination = worldPath(destinationFolder);
        if (!Files.isDirectory(destination) || Files.isSymbolicLink(destination)) {
            throw new IOException("Published world is missing or unsafe: " + destinationFolder);
        }
        Path marker = destination.resolve(PENDING_PUBLISH_MARKER);
        if (Files.exists(marker) && (!Files.isRegularFile(marker) || Files.isSymbolicLink(marker))) {
            throw new IOException("Pending publication marker is unsafe in " + destinationFolder);
        }
        Files.deleteIfExists(marker);
    }

    @Override
    public ManagedWorldAudit auditManagedWorldFolders(Collection<WorldRecord> managedWorlds) throws IOException {
        Objects.requireNonNull(managedWorlds, "managedWorlds");
        List<String> missing = new ArrayList<>();
        List<String> unsafe = new ArrayList<>();
        for (WorldRecord world : managedWorlds) {
            Path target = worldPath(world.folderName());
            if (Files.notExists(target)) missing.add(world.folderName());
            else if (!Files.isDirectory(target) || Files.isSymbolicLink(target)) unsafe.add(world.folderName());
        }
        return new ManagedWorldAudit(missing, unsafe);
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

        String sourceName = source.getFileName().toString();
        boolean transactionalPublish = sourceName.endsWith(WORK_SUFFIX) || sourceName.endsWith(COPY_SUFFIX);
        if (transactionalPublish) {
            Path marker = source.resolve(PENDING_PUBLISH_MARKER);
            if (Files.exists(marker)) throw new IOException("Staged world already contains a publication marker");
            Files.createFile(marker);
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
        if (Files.isSymbolicLink(target)) Files.delete(target);
        else deleteTree(target);
    }

    private Path reserveTypedWorkspace(UUID operationId, String suffix) throws IOException {
        Objects.requireNonNull(operationId, "operationId");
        Files.createDirectories(workspaceRoot);
        requireSafeWorkspaceRoot();
        Path destination = workspacePath(operationId + suffix);
        if (Files.exists(destination)) throw new IOException("Workspace already exists: " + destination.getFileName());
        return destination;
    }

    private Path reserveDeleteWorkspace(UUID operationId, String folderName) throws IOException {
        Objects.requireNonNull(operationId, "operationId");
        String safeFolder = validateSingleName(folderName, "world folder");
        Files.createDirectories(workspaceRoot);
        requireSafeWorkspaceRoot();
        String encodedFolder = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(safeFolder.getBytes(StandardCharsets.UTF_8));
        Path destination = workspacePath(operationId + "." + encodedFolder + DELETE_SUFFIX);
        if (Files.exists(destination)) throw new IOException("Workspace already exists: " + destination.getFileName());
        return destination;
    }

    private void requireSafeWorkspaceRoot() throws IOException {
        if (!Files.isDirectory(workspaceRoot) || Files.isSymbolicLink(workspaceRoot)) {
            throw new IOException("World Manager work root is unsafe");
        }
    }

    private static boolean isRecoverableTransientName(String name) {
        String suffix;
        if (name.endsWith(WORK_SUFFIX)) suffix = WORK_SUFFIX;
        else if (name.endsWith(COPY_SUFFIX)) suffix = COPY_SUFFIX;
        else return false;
        String id = name.substring(0, name.length() - suffix.length());
        try { UUID.fromString(id); return true; }
        catch (IllegalArgumentException ignored) { return false; }
    }

    private static Optional<DeleteWorkspaceIdentity> parseDeleteWorkspaceName(String name) {
        if (!name.endsWith(DELETE_SUFFIX)) return Optional.empty();
        String stem = name.substring(0, name.length() - DELETE_SUFFIX.length());
        int separator = stem.indexOf('.');
        if (separator <= 0 || separator == stem.length() - 1) return Optional.empty();
        try {
            UUID operationId = UUID.fromString(stem.substring(0, separator));
            String folderName = new String(
                    Base64.getUrlDecoder().decode(stem.substring(separator + 1)), StandardCharsets.UTF_8);
            folderName = validateSingleName(folderName, "world folder");
            return Optional.of(new DeleteWorkspaceIdentity(operationId, folderName));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
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
        try { Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(source, destination); }
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

    private record DeleteWorkspaceIdentity(UUID operationId, String folderName) { }
}
