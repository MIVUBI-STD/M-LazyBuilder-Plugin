package com.halokaryamedia.lazybuilder.world.conversion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/** Local current/previous/candidate runtime store under World Manager data. */
public final class LocalConversionRuntimeStore implements ConversionRuntimeStore {
    private static final String ARTIFACT = "converter.jar";
    private static final String MANIFEST = "runtime.properties";
    private static final String LAST_CHECK = "last-update-check.txt";

    private final Path root;

    public LocalConversionRuntimeStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            recoverInterruptedPromotion();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to recover conversion runtime state", exception);
        }
    }

    @Override public Optional<InstalledRuntime> current() throws IOException { return readSlot("current"); }
    @Override public Optional<InstalledRuntime> previous() throws IOException { return readSlot("previous"); }
    @Override public Optional<InstalledRuntime> candidate() throws IOException { return readSlot("candidate"); }

    @Override
    public void stageCandidate(Path artifact, ConversionRuntimeManifest manifest) throws IOException {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(manifest, "manifest");
        requireManifestCompatibility(manifest);
        verifyArtifactDigest(artifact, manifest);

        discardCandidate();
        Path slot = slot("candidate");
        Files.createDirectories(slot);
        Files.copy(artifact, slot.resolve(ARTIFACT), StandardCopyOption.REPLACE_EXISTING);
        verifyArtifactDigest(slot.resolve(ARTIFACT), manifest);
        writeManifest(slot.resolve(MANIFEST), manifest);
    }

    @Override
    public void promoteCandidate() throws IOException {
        Path candidate = slot("candidate");
        requireVerified(candidate);
        deleteTree(slot("previous"));
        if (Files.exists(slot("current"))) {
            move(slot("current"), slot("previous"));
        }
        move(candidate, slot("current"));
    }

    @Override
    public void rollbackToPrevious() throws IOException {
        Path previous = slot("previous");
        requireVerified(previous);
        deleteTree(slot("candidate"));
        Path failed = slot("candidate");
        if (Files.exists(slot("current"))) {
            move(slot("current"), failed);
        }
        move(previous, slot("current"));
        deleteTree(failed);
    }

    @Override public void discardCandidate() throws IOException { deleteTree(slot("candidate")); }

    @Override
    public Optional<Instant> lastUpdateCheck() throws IOException {
        Path marker = root.resolve(LAST_CHECK);
        if (Files.notExists(marker)) return Optional.empty();
        return Optional.of(Instant.parse(Files.readString(marker, StandardCharsets.UTF_8).strip()));
    }

    @Override
    public void recordUpdateCheck(Instant instant) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve(LAST_CHECK), instant.toString(), StandardCharsets.UTF_8);
    }

    private void recoverInterruptedPromotion() throws IOException {
        if (Files.notExists(root)) return;
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) {
            throw new IOException("Conversion runtime root is unsafe");
        }

        Path current = slot("current");
        Path previous = slot("previous");
        Path candidate = slot("candidate");

        if (Files.exists(current)) {
            requireVerified(current);
            if (Files.exists(candidate)) {
                try {
                    requireVerified(candidate);
                } catch (IOException invalidCandidate) {
                    deleteTree(candidate);
                }
            }
            return;
        }

        if (complete(candidate)) {
            try {
                requireVerified(candidate);
                move(candidate, current);
                return;
            } catch (IOException invalidCandidate) {
                deleteTree(candidate);
            }
        } else if (Files.exists(candidate)) {
            deleteTree(candidate);
        }

        if (complete(previous)) {
            requireVerified(previous);
            move(previous, current);
        }
    }

    private Optional<InstalledRuntime> readSlot(String name) throws IOException {
        Path slot = slot(name);
        if (Files.notExists(slot)) return Optional.empty();
        return Optional.of(requireVerified(slot));
    }

    private Path slot(String name) {
        Path target = root.resolve(name).normalize();
        if (!root.equals(target.getParent())) throw new IllegalArgumentException("Runtime slot escaped store root");
        return target;
    }

    private static boolean complete(Path slot) {
        return Files.isDirectory(slot)
                && !Files.isSymbolicLink(slot)
                && Files.isRegularFile(slot.resolve(ARTIFACT))
                && !Files.isSymbolicLink(slot.resolve(ARTIFACT))
                && Files.isRegularFile(slot.resolve(MANIFEST))
                && !Files.isSymbolicLink(slot.resolve(MANIFEST));
    }

    private static void requireComplete(Path slot) throws IOException {
        if (!complete(slot)) {
            throw new IOException("Incomplete conversion runtime slot: " + slot.getFileName());
        }
    }

    private static InstalledRuntime requireVerified(Path slot) throws IOException {
        requireComplete(slot);
        ConversionRuntimeManifest manifest = readManifest(slot.resolve(MANIFEST));
        requireManifestCompatibility(manifest);
        Path artifact = slot.resolve(ARTIFACT);
        verifyArtifactDigest(artifact, manifest);
        return new InstalledRuntime(artifact, manifest);
    }

    private static void requireManifestCompatibility(ConversionRuntimeManifest manifest) throws IOException {
        if (manifest.adapterContract() != ConverterAdapter.ADAPTER_CONTRACT) {
            throw new IOException(
                    "Conversion runtime adapter contract " + manifest.adapterContract()
                            + " does not match required contract " + ConverterAdapter.ADAPTER_CONTRACT);
        }
    }

    private static void verifyArtifactDigest(
            Path artifact,
            ConversionRuntimeManifest manifest
    ) throws IOException {
        String actual = sha256(artifact);
        if (!actual.equalsIgnoreCase(manifest.sha256())) {
            throw new IOException(
                    "Conversion runtime SHA-256 mismatch for " + artifact.getFileName());
        }
    }

    private static String sha256(Path artifact) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(artifact)) {
                byte[] buffer = new byte[64 * 1024];
                for (int read; (read = in.read(buffer)) >= 0;) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void writeManifest(Path path, ConversionRuntimeManifest manifest) throws IOException {
        Properties p = new Properties();
        p.setProperty("version", manifest.version());
        p.setProperty("adapterContract", Integer.toString(manifest.adapterContract()));
        p.setProperty("sha256", manifest.sha256());
        p.setProperty("installedAt", manifest.installedAt().toString());
        p.setProperty("supportedFormats", String.join("\n", manifest.supportedFormats()));
        try (var out = Files.newOutputStream(path)) { p.store(out, "LazyBuilder conversion runtime"); }
    }

    private static ConversionRuntimeManifest readManifest(Path path) throws IOException {
        Properties p = new Properties();
        try (var in = Files.newInputStream(path)) { p.load(in); }
        List<String> formats = new ArrayList<>();
        String raw = p.getProperty("supportedFormats", "");
        if (!raw.isBlank()) raw.lines().map(String::strip).filter(s -> !s.isEmpty()).forEach(formats::add);
        return new ConversionRuntimeManifest(
                required(p, "version"),
                Integer.parseInt(required(p, "adapterContract")),
                required(p, "sha256"),
                Instant.parse(required(p, "installedAt")),
                formats
        );
    }

    private static String required(Properties p, String key) throws IOException {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Missing runtime manifest property: " + key);
        return value.strip();
    }

    private static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target); }
    }

    private static void deleteTree(Path root) throws IOException {
        if (Files.notExists(root)) return;
        if (Files.isSymbolicLink(root)) {
            Files.delete(root);
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
