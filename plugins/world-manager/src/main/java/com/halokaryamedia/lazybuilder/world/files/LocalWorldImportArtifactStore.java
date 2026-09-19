package com.halokaryamedia.lazybuilder.world.files;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** Path-safe ZIP/.mcworld staging for World Manager imports. */
public final class LocalWorldImportArtifactStore implements WorldImportArtifactStore {
    private static final String TRANSFER_MARKER = ".lazybuilder-transfer.properties";
    private static final String COMMITTED_CLEANUP_DIR = ".committed-cleanup";
    private static final String CLEANUP_MARKER_SUFFIX = ".pending";
    private static final String JAVA_1_21_4 = "JAVA_1_21_4";
    private static final int IO_BUFFER_BYTES = 64 * 1024;
    private static final int MAX_INSPECT_LEVEL_DAT_BYTES = 16 * 1024 * 1024;
    private static final long IMPORT_SPACE_RESERVE_BYTES = 16L * 1024L * 1024L;
    private static final long IMPORT_ENTRY_OVERHEAD_BYTES = 256L;

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
    public ImportInspection inspectArtifact(String artifactName) throws IOException {
        requireSafeImportsRoot();
        Path artifact = resolveArtifact(artifactName);
        List<String> entryNames = new ArrayList<>();
        ZipEntry selectedLevelDat = null;
        String levelPath = null;
        int selectedLevelDepth = Integer.MAX_VALUE;
        long entries = 0;
        ArchiveNamespace namespace = new ArchiveNamespace();
        try (ZipFile zip = new ZipFile(artifact.toFile())) {
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (++entries > maxEntries) throw new IOException("Import archive exceeds file-count limit");
                String name = validateArchiveEntryName(entry.getName(), entry.isDirectory());
                if (name.isBlank()) continue;
                namespace.register(name, entry.isDirectory());
                entryNames.add(name);
                if (entry.isDirectory() || !isLevelDat(name)) continue;
                int depth = pathDepth(name);
                if (depth < selectedLevelDepth) {
                    selectedLevelDepth = depth;
                    levelPath = name;
                    selectedLevelDat = entry;
                } else if (depth == selectedLevelDepth && !name.equals(levelPath)) {
                    throw new IOException(
                            "Import archive contains multiple world roots at the same depth: "
                                    + levelPath + " and " + name);
                }
            }
            if (levelPath == null || selectedLevelDat == null) {
                throw new IOException("Import archive does not contain a Minecraft level.dat");
            }
            byte[] levelDat = readInspectionEntry(zip, selectedLevelDat);
            String rootPrefix = levelPath.substring(0, levelPath.length() - "level.dat".length());
            boolean bedrock = entryNames.stream().anyMatch(name -> name.startsWith(rootPrefix + "db/"));
            DetectedEdition edition = bedrock ? DetectedEdition.BEDROCK : DetectedEdition.JAVA;
            String version = "Unknown";
            if (edition == DetectedEdition.JAVA) {
                OptionalInt dataVersion = JavaLevelDataVersion.read(new ByteArrayInputStream(levelDat));
                if (dataVersion.isPresent()) {
                    version = dataVersion.getAsInt() == JavaLevelDataVersion.JAVA_1_21_4
                            ? "1.21.4" : "DataVersion " + dataVersion.getAsInt();
                }
            }
            return new ImportInspection(artifact.getFileName().toString(), edition, version,
                    suggestedName(artifact.getFileName().toString()));
        }
    }

    private static byte[] readInspectionEntry(ZipFile zip, ZipEntry entry) throws IOException {
        byte[] buffer = new byte[IO_BUFFER_BYTES];
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int total = 0;
        try (InputStream in = zip.getInputStream(entry)) {
            for (int read; (read = in.read(buffer)) >= 0;) {
                if (read == 0) continue;
                total += read;
                if (total > MAX_INSPECT_LEVEL_DAT_BYTES) throw new IOException("Import level.dat exceeds inspection limit");
                bytes.write(buffer, 0, read);
            }
        }
        return bytes.toByteArray();
    }

    @Override
    public StagedImport stageArchive(String artifactName, Path workspace) throws IOException {
        requireSafeImportsRoot();
        Path artifact = resolveArtifact(artifactName);
        Path target = Objects.requireNonNull(workspace, "workspace").toAbsolutePath().normalize();
        if (Files.exists(target)) throw new IOException("Import workspace already exists: " + target.getFileName());

        Path workspaceRoot = target.getParent();
        if (workspaceRoot == null || !Files.isDirectory(workspaceRoot) || Files.isSymbolicLink(workspaceRoot)) {
            throw new IOException("Import workspace root is missing or unsafe");
        }
        ArchiveEstimate estimate = estimateArchive(artifact);
        long requiredFreeBytes = requiredFreeBytes(estimate.uncompressedBytes(), estimate.entries());
        long usableBytes = Files.getFileStore(workspaceRoot).getUsableSpace();
        if (usableBytes < requiredFreeBytes) {
            throw new IOException("Insufficient disk space to extract import: required at least "
                    + requiredFreeBytes + " bytes, available " + usableBytes + " bytes");
        }

        Files.createDirectories(target);
        boolean success = false;
        try {
            extractBounded(artifact, target);
            normalizeSingleRoot(target);
            Path levelDat = target.resolve("level.dat");
            if (!Files.isRegularFile(levelDat)) throw new IOException("Import archive does not contain a Minecraft level.dat");
            DetectedEdition edition = Files.isDirectory(target.resolve("db")) ? DetectedEdition.BEDROCK : DetectedEdition.JAVA;
            String trustedFormat = edition == DetectedEdition.JAVA
                    && JavaLevelDataVersion.read(levelDat).orElse(-1) == JavaLevelDataVersion.JAVA_1_21_4
                    ? JAVA_1_21_4 : null;
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
        requireSafeImportsRoot();
        Path artifact = resolveArtifactPath(artifactName);
        if (Files.exists(artifact) && (!Files.isRegularFile(artifact) || Files.isSymbolicLink(artifact))) {
            throw new IOException("Import artifact is unsafe: " + artifact.getFileName());
        }
        Files.deleteIfExists(artifact);
    }

    @Override
    public void markCommittedCleanupPending(String artifactName) throws IOException {
        requireSafeImportsRoot();
        String safe = validateArtifactName(artifactName);
        Path directory = ensureCleanupMarkerDirectory();
        Path marker = cleanupMarkerPath(directory, safe);
        try { Files.createFile(marker); }
        catch (FileAlreadyExistsException ignored) { }
    }

    @Override
    public void clearCommittedCleanupPending(String artifactName) throws IOException {
        requireSafeImportsRoot();
        String safe = validateArtifactName(artifactName);
        Path directory = existingCleanupMarkerDirectory();
        if (directory == null) return;
        Files.deleteIfExists(cleanupMarkerPath(directory, safe));
    }

    @Override
    public List<String> pendingCommittedCleanupArtifacts() throws IOException {
        requireSafeImportsRoot();
        Path directory = existingCleanupMarkerDirectory();
        if (directory == null) return List.of();
        List<String> pending = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            for (Path marker : stream.sorted().toList()) {
                if (!Files.isRegularFile(marker) || Files.isSymbolicLink(marker)) continue;
                String markerName = marker.getFileName().toString();
                if (!markerName.endsWith(CLEANUP_MARKER_SUFFIX)) continue;
                String encoded = markerName.substring(0, markerName.length() - CLEANUP_MARKER_SUFFIX.length());
                try {
                    String value = validateArtifactName(new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8));
                    if (cleanupMarkerPath(directory, value).equals(marker.toAbsolutePath().normalize())) pending.add(value);
                } catch (IllegalArgumentException ignored) { }
            }
        }
        return List.copyOf(pending);
    }

    private void requireSafeImportsRoot() throws IOException {
        if (Files.notExists(importsRoot)) {
            Files.createDirectories(importsRoot);
        }
        if (!Files.isDirectory(importsRoot) || Files.isSymbolicLink(importsRoot)) {
            throw new IOException("Import artifact root is unsafe: " + importsRoot);
        }
    }

    private Path resolveArtifact(String artifactName) throws IOException {
        Path path = resolveArtifactPath(artifactName);
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
            throw new IOException("Import artifact is missing or unsafe: " + path.getFileName());
        }
        return path;
    }

    private Path resolveArtifactPath(String artifactName) {
        String safe = validateArtifactName(artifactName);
        Path path = importsRoot.resolve(safe).normalize();
        if (!importsRoot.equals(path.getParent())) throw new IllegalArgumentException("Import artifact escaped owned root");
        return path;
    }

    private Path cleanupMarkerDirectory() {
        return importsRoot.resolve(COMMITTED_CLEANUP_DIR).toAbsolutePath().normalize();
    }

    private Path ensureCleanupMarkerDirectory() throws IOException {
        Path directory = cleanupMarkerDirectory();
        if (Files.exists(directory)) {
            if (!Files.isDirectory(directory) || Files.isSymbolicLink(directory)) throw new IOException("Import cleanup marker directory is unsafe");
        } else {
            Files.createDirectories(directory);
        }
        return directory;
    }

    private Path existingCleanupMarkerDirectory() throws IOException {
        Path directory = cleanupMarkerDirectory();
        if (Files.notExists(directory)) return null;
        if (!Files.isDirectory(directory) || Files.isSymbolicLink(directory)) throw new IOException("Import cleanup marker directory is unsafe");
        return directory;
    }

    private static Path cleanupMarkerPath(Path directory, String artifactName) {
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(artifactName.getBytes(StandardCharsets.UTF_8));
        return directory.resolve(encoded + CLEANUP_MARKER_SUFFIX).toAbsolutePath().normalize();
    }

    private ArchiveEstimate estimateArchive(Path archive) throws IOException {
        long entries = 0;
        long totalBytes = 0;
        ArchiveNamespace namespace = new ArchiveNamespace();
        String levelPath = null;
        int levelDepth = Integer.MAX_VALUE;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (++entries > maxEntries) throw new IOException("Import archive exceeds file-count limit");
                String name = validateArchiveEntryName(entry.getName(), entry.isDirectory());
                if (name.isBlank()) continue;
                namespace.register(name, entry.isDirectory());
                if (!entry.isDirectory() && isLevelDat(name)) {
                    int depth = pathDepth(name);
                    if (depth < levelDepth) {
                        levelDepth = depth;
                        levelPath = name;
                    } else if (depth == levelDepth && !name.equals(levelPath)) {
                        throw new IOException(
                                "Import archive contains multiple world roots at the same depth: "
                                        + levelPath + " and " + name);
                    }
                }
                if (entry.isDirectory()) continue;
                long size = entry.getSize();
                if (size < 0) throw new IOException("Import archive contains an entry with unknown uncompressed size");
                try {
                    totalBytes = Math.addExact(totalBytes, size);
                } catch (ArithmeticException overflow) {
                    throw new IOException("Import archive is too large to estimate safely", overflow);
                }
                if (totalBytes > maxUncompressedBytes) {
                    throw new IOException("Import archive exceeds uncompressed-size limit");
                }
            }
        }
        return new ArchiveEstimate(totalBytes, entries);
    }

    static long requiredFreeBytes(long uncompressedBytes, long entries) throws IOException {
        if (uncompressedBytes < 0 || entries < 0) throw new IllegalArgumentException("Import size estimate must be non-negative");
        try {
            long entryOverhead = Math.multiplyExact(entries, IMPORT_ENTRY_OVERHEAD_BYTES);
            return Math.addExact(Math.addExact(uncompressedBytes, entryOverhead), IMPORT_SPACE_RESERVE_BYTES);
        } catch (ArithmeticException overflow) {
            throw new IOException("Import archive is too large to estimate safely", overflow);
        }
    }

    private void extractBounded(Path archive, Path target) throws IOException {
        long entries = 0;
        long totalBytes = 0;
        ArchiveNamespace namespace = new ArchiveNamespace();
        byte[] buffer = new byte[IO_BUFFER_BYTES];
        try (InputStream fileIn = Files.newInputStream(archive);
             BufferedInputStream bufferedIn = new BufferedInputStream(fileIn, IO_BUFFER_BYTES);
             ZipInputStream zip = new ZipInputStream(bufferedIn)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                if (++entries > maxEntries) throw new IOException("Import archive exceeds file-count limit");
                String name = validateArchiveEntryName(entry.getName(), entry.isDirectory());
                if (name.isBlank()) continue;
                namespace.register(name, entry.isDirectory());
                Path output = target.resolve(name).normalize();
                if (!output.startsWith(target)) throw new IOException("Import archive contains path traversal");
                if (entry.isDirectory()) { Files.createDirectories(output); continue; }
                Path parent = output.getParent();
                if (parent != null) Files.createDirectories(parent);
                try (var fileOut = Files.newOutputStream(output); var out = new BufferedOutputStream(fileOut, IO_BUFFER_BYTES)) {
                    for (int read; (read = zip.read(buffer)) >= 0;) {
                        if (read == 0) continue;
                        totalBytes += read;
                        if (totalBytes > maxUncompressedBytes) throw new IOException("Import archive exceeds uncompressed-size limit");
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
                || Files.isSymbolicLink(children.getFirst())
                || !Files.isRegularFile(children.getFirst().resolve("level.dat"))) return;

        Path nested = children.getFirst();
        Path macMetadata = target.resolve("__MACOSX");
        if (Files.exists(macMetadata)) {
            if (Files.isSymbolicLink(macMetadata)) Files.delete(macMetadata);
            else deleteTree(macMetadata);
        }

        List<Path> nestedChildren;
        try (var stream = Files.list(nested)) {
            nestedChildren = stream.toList();
        }
        for (Path child : nestedChildren) {
            Path destination = target.resolve(child.getFileName()).normalize();
            if (!target.equals(destination.getParent()) || Files.exists(destination)) {
                throw new IOException("Import normalization destination is unsafe or already exists: " + child.getFileName());
            }
            Files.move(child, destination);
        }
        Files.delete(nested);
    }

    private static void sanitizeIdentity(Path root) throws IOException {
        Files.deleteIfExists(root.resolve("session.lock"));
        Files.deleteIfExists(root.resolve("uid.dat"));
        Files.deleteIfExists(root.resolve(TRANSFER_MARKER));
    }

    private static String normalizeEntryName(String value) {
        return value == null ? "" : value.replace('\\', '/');
    }

    private static String validateArchiveEntryName(String value, boolean directory) throws IOException {
        String normalized = normalizeEntryName(value);
        while (directory && normalized.endsWith("/") && !normalized.isEmpty()) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank()) return "";

        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            throw new IOException("Import archive contains an absolute path: " + normalized);
        }

        String[] components = normalized.split("/", -1);
        StringBuilder canonical = new StringBuilder();
        for (String component : components) {
            if (component.isEmpty() || component.equals(".") || component.equals("..")) {
                throw new IOException("Import archive contains an unsafe path component: " + normalized);
            }
            if (component.indexOf(':') >= 0
                    || component.endsWith(".")
                    || component.endsWith(" ")
                    || component.chars().anyMatch(Character::isISOControl)
                    || isWindowsReservedComponent(component)) {
                throw new IOException("Import archive contains a Windows-unsafe path component: " + component);
            }
            if (!canonical.isEmpty()) canonical.append('/');
            canonical.append(component);
        }
        return canonical.toString();
    }

    private static boolean isWindowsReservedComponent(String component) {
        String upper = component.toUpperCase(Locale.ROOT);
        int dot = upper.indexOf('.');
        String base = dot < 0 ? upper : upper.substring(0, dot);
        if (base.equals("CON") || base.equals("PRN") || base.equals("AUX") || base.equals("NUL")) {
            return true;
        }
        if (base.length() == 4 && (base.startsWith("COM") || base.startsWith("LPT"))) {
            char suffix = base.charAt(3);
            return suffix >= '1' && suffix <= '9';
        }
        return false;
    }

    private static boolean isLevelDat(String name) { return name.equals("level.dat") || name.endsWith("/level.dat"); }
    private static int pathDepth(String name) {
        int depth = 0;
        for (int i = 0; i < name.length(); i++) if (name.charAt(i) == '/') depth++;
        return depth;
    }

    private static String suggestedName(String artifactName) {
        String value = artifactName.strip();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mcworld")) value = value.substring(0, value.length() - 8);
        else if (lower.endsWith(".zip")) value = value.substring(0, value.length() - 4);
        value = value.strip();
        return value.isEmpty() ? "Imported World" : value;
    }

    private static String validateArtifactName(String value) {
        String safe = validateSingleName(value);
        String lower = safe.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".zip") && !lower.endsWith(".mcworld")) throw new IllegalArgumentException("Import artifact must be .zip or .mcworld");
        return safe;
    }

    private static String validateSingleName(String value) {
        Objects.requireNonNull(value, "artifactName");
        if (value.isBlank() || !value.equals(value.strip()) || value.equals(".") || value.equals("..")
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("artifactName must be one safe file name");
        }
        return value;
    }

    private static final class ArchiveNamespace {
        private final Map<String, NamespaceEntry> entries = new HashMap<>();

        void register(String path, boolean directory) throws IOException {
            String[] components = path.split("/");
            StringBuilder prefix = new StringBuilder();
            for (int index = 0; index < components.length; index++) {
                if (!prefix.isEmpty()) prefix.append('/');
                prefix.append(components[index]);
                boolean terminal = index == components.length - 1;
                boolean currentDirectory = !terminal || directory;
                registerOne(prefix.toString(), currentDirectory, terminal);
            }
        }

        private void registerOne(String path, boolean directory, boolean terminal) throws IOException {
            String key = path.toLowerCase(Locale.ROOT);
            NamespaceEntry existing = entries.get(key);
            if (existing == null) {
                entries.put(key, new NamespaceEntry(path, directory, terminal));
                return;
            }
            if (!existing.path().equals(path)) {
                throw new IOException(
                        "Import archive contains a case-insensitive path collision: "
                                + existing.path() + " vs " + path);
            }
            if (existing.directory() != directory) {
                throw new IOException(
                        "Import archive contains a file/directory path collision: " + path);
            }
            if (!directory && terminal && existing.terminal()) {
                throw new IOException("Import archive contains a duplicate file entry: " + path);
            }
            if (terminal) {
                entries.put(key, new NamespaceEntry(path, directory, true));
            }
        }
    }

    private record NamespaceEntry(String path, boolean directory, boolean terminal) {}

    private static void deleteTree(Path root) throws IOException {
        if (Files.notExists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) throw exception;
                Files.deleteIfExists(directory); return FileVisitResult.CONTINUE;
            }
        });
    }

    private record ArchiveEstimate(long uncompressedBytes, long entries) {}
}
