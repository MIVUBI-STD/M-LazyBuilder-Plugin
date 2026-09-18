package com.halokaryamedia.lazybuilder.builder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Exact packaged Builder artifact fingerprint used to scope persisted runtime proof. */
final class BuilderBuildFingerprint {
    private BuilderBuildFingerprint() {}

    static String current() {
        try {
            Path source = Path.of(BuilderBuildFingerprint.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            if (Files.isRegularFile(source)) {
                return "sha256:" + sha256(source);
            }
            return "development";
        } catch (RuntimeException | URISyntaxException | IOException e) {
            return "unavailable";
        }
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
