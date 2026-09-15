package com.halokaryamedia.lazybuilder.world.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** Removes only LazyBuilder-owned temporary ZIP artifacts left by a hard process stop. */
public final class ArtifactTempRecovery {
    private static final String PREFIX = "export-";
    private static final String SUFFIX = ".tmp";

    private ArtifactTempRecovery() { }

    public static int recover(Path... artifactRoots) throws IOException {
        Objects.requireNonNull(artifactRoots, "artifactRoots");
        int recovered = 0;
        for (Path rootValue : artifactRoots) {
            Path root = Objects.requireNonNull(rootValue, "artifactRoot").toAbsolutePath().normalize();
            if (Files.notExists(root)) continue;
            if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) {
                throw new IOException("Artifact root is unsafe: " + root);
            }
            try (var children = Files.list(root)) {
                for (Path child : children.toList()) {
                    if (!isOwnedTemporaryName(child.getFileName().toString())) continue;
                    Path direct = child.toAbsolutePath().normalize();
                    if (!root.equals(direct.getParent())) continue;
                    if (Files.isDirectory(direct) && !Files.isSymbolicLink(direct)) continue;
                    Files.deleteIfExists(direct);
                    recovered++;
                }
            }
        }
        return recovered;
    }

    static boolean isOwnedTemporaryName(String name) {
        if (name == null || !name.startsWith(PREFIX) || !name.endsWith(SUFFIX)) return false;
        String id = name.substring(PREFIX.length(), name.length() - SUFFIX.length());
        try {
            UUID.fromString(id);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
