package com.halokaryamedia.lazybuilder.utility.capture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Small filesystem guard for the existing capture/videos folder. */
final class CaptureStoragePaths {
    private CaptureStoragePaths() { }

    static Path createVideoDirectory(Path gameDirectory) throws IOException {
        Path game = Objects.requireNonNull(gameDirectory, "gameDirectory")
                .toAbsolutePath().normalize();
        Path captures = game.resolve("captures");
        Path videos = captures.resolve("videos");
        Files.createDirectories(captures);
        requireDirectDirectory(game, captures);
        Files.createDirectories(videos);
        requireDirectDirectory(captures, videos);
        return videos;
    }

    /** Null means no videos folder exists, not that a redirected folder is safe. */
    static Path existingVideoDirectory(Path gameDirectory) throws IOException {
        Path game = Objects.requireNonNull(gameDirectory, "gameDirectory")
                .toAbsolutePath().normalize();
        Path captures = game.resolve("captures");
        if (!Files.exists(captures, LinkOption.NOFOLLOW_LINKS)) return null;
        requireDirectDirectory(game, captures);
        Path videos = captures.resolve("videos");
        if (!Files.exists(videos, LinkOption.NOFOLLOW_LINKS)) return null;
        requireDirectDirectory(captures, videos);
        return videos;
    }

    private static void requireDirectDirectory(Path parent, Path child) throws IOException {
        if (!Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(child)) {
            throw new IOException("Capture folder is redirected, missing or not a directory: " + child);
        }
        // Compare canonical parent+child rather than raw path spelling, which
        // may differ on Windows due to case or extended-length path prefixes.
        if (!parent.toRealPath().equals(child.toRealPath().getParent())) {
            throw new IOException("Capture folder resolves outside the selected Minecraft profile: " + child);
        }
    }
}
