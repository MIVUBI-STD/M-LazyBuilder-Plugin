package com.halokaryamedia.lazybuilder.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldNavigationPreferencesTest {
    @TempDir Path tempDir;

    @Test
    void predictableTemporarySymlinkCannotOverwriteAnUnrelatedFile() throws Exception {
        Path path = tempDir.resolve("navigation.properties");
        Path unrelated = tempDir.resolve("unrelated.properties");
        Files.writeString(unrelated, "keep me");
        Path predictable = tempDir.resolve("navigation.properties.tmp");
        try {
            Files.createSymbolicLink(predictable, unrelated);
        } catch (UnsupportedOperationException | IOException unavailable) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links unavailable");
        }

        UUID world = UUID.randomUUID();
        WorldNavigationPreferences preferences = new WorldNavigationPreferences(path, () -> "server-a");
        preferences.togglePinned(world);

        assertEquals("keep me", Files.readString(unrelated));
        assertTrue(Files.isSymbolicLink(predictable));
        assertTrue(new WorldNavigationPreferences(path, () -> "server-a").isPinned(world));
    }

    @Test
    void unsafeNavigationTargetIsNeitherReadNorOverwritten() throws Exception {
        Path path = tempDir.resolve("navigation.properties");
        Path unrelated = tempDir.resolve("unrelated.properties");
        Files.writeString(unrelated, "keep me");
        try {
            Files.createSymbolicLink(path, unrelated);
        } catch (UnsupportedOperationException | IOException unavailable) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links unavailable");
        }

        UUID world = UUID.randomUUID();
        WorldNavigationPreferences preferences = new WorldNavigationPreferences(path, () -> "server-a");
        preferences.togglePinned(world);
        preferences.reload();

        assertEquals("keep me", Files.readString(unrelated));
        assertTrue(Files.isSymbolicLink(path));
        assertFalse(preferences.isPinned(world));
    }

    @Test
    void returnedListsRemainMutableWithoutExposingCachedState() {
        Path path = tempDir.resolve("navigation.properties");
        WorldNavigationPreferences preferences = new WorldNavigationPreferences(path, () -> "server-a");
        UUID pinned = UUID.randomUUID();
        UUID recent = UUID.randomUUID();
        UUID localOnly = UUID.randomUUID();

        preferences.togglePinned(pinned);
        preferences.recordVisited(recent);

        List<UUID> pinnedCopy = preferences.pinned();
        pinnedCopy.clear();
        pinnedCopy.add(localOnly);
        List<UUID> recentCopy = preferences.recent();
        recentCopy.clear();
        recentCopy.add(localOnly);

        assertTrue(preferences.isPinned(pinned));
        assertFalse(preferences.isPinned(localOnly));
        assertEquals(List.of(pinned), preferences.pinned());
        assertEquals(List.of(recent), preferences.recent());
    }

    @Test
    void persistedNavigationPreferencesRemainServerScoped() {
        Path path = tempDir.resolve("navigation.properties");
        UUID world = UUID.randomUUID();

        WorldNavigationPreferences serverA = new WorldNavigationPreferences(path, () -> "server-a");
        serverA.togglePinned(world);
        serverA.recordVisited(world);

        WorldNavigationPreferences reloadedA = new WorldNavigationPreferences(path, () -> "server-a");
        WorldNavigationPreferences serverB = new WorldNavigationPreferences(path, () -> "server-b");

        assertTrue(reloadedA.isPinned(world));
        assertEquals(List.of(world), reloadedA.recent());
        assertFalse(serverB.isPinned(world));
        assertTrue(serverB.recent().isEmpty());
    }
}
