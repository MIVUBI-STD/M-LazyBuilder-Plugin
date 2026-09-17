package com.halokaryamedia.lazybuilder.world.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperWorldFamilyLayoutTest {
    @TempDir Path tempDir;

    @Test
    void canonicalDimensionsPublishIntoPaperSiblingWorlds() throws Exception {
        Path worlds = tempDir.resolve("worlds");
        Path root = worlds.resolve("Build");
        Files.createDirectories(root.resolve("DIM-1/region"));
        Files.createDirectories(root.resolve("DIM1/region"));
        byte[] level = new byte[]{1, 2, 3, 4};
        byte[] nether = new byte[]{5, 6, 7};
        byte[] end = new byte[]{8, 9, 10};
        Files.write(root.resolve("level.dat"), level);
        Files.write(root.resolve("DIM-1/region/r.0.0.mca"), nether);
        Files.write(root.resolve("DIM1/region/r.0.0.mca"), end);

        PaperWorldFamilyLayout.publishCanonicalDimensions(worlds, "Build", true);

        assertFalse(Files.exists(root.resolve("DIM-1")));
        assertFalse(Files.exists(root.resolve("DIM1")));
        assertArrayEquals(level, Files.readAllBytes(worlds.resolve("Build_nether/level.dat")));
        assertArrayEquals(level, Files.readAllBytes(worlds.resolve("Build_the_end/level.dat")));
        assertArrayEquals(nether, Files.readAllBytes(worlds.resolve("Build_nether/DIM-1/region/r.0.0.mca")));
        assertArrayEquals(end, Files.readAllBytes(worlds.resolve("Build_the_end/DIM1/region/r.0.0.mca")));
        assertTrue(Files.isRegularFile(worlds.resolve("Build_nether/.lazybuilder-family-publish-pending")));
        assertTrue(Files.isRegularFile(worlds.resolve("Build_the_end/.lazybuilder-family-publish-pending")));

        PaperWorldFamilyLayout.clearFamilyPendingMarkers(worlds, "Build");
        assertFalse(Files.exists(worlds.resolve("Build_nether/.lazybuilder-family-publish-pending")));
        assertFalse(Files.exists(worlds.resolve("Build_the_end/.lazybuilder-family-publish-pending")));
    }

    @Test
    void transactionalPublishResumesOwnedSiblingCreatedBeforeDimensionMove() throws Exception {
        Path worlds = tempDir.resolve("worlds");
        Path root = worlds.resolve("Build");
        Path canonical = root.resolve("DIM-1/region");
        Files.createDirectories(canonical);
        Files.write(root.resolve("level.dat"), new byte[]{1, 2, 3});
        Files.write(canonical.resolve("r.0.0.mca"), new byte[]{4, 5});

        Path sibling = worlds.resolve("Build_nether");
        Files.createDirectories(sibling);
        Files.write(sibling.resolve("level.dat"), new byte[]{1, 2, 3});
        Files.writeString(sibling.resolve(".lazybuilder-family-publish-pending"), "");

        PaperWorldFamilyLayout.publishCanonicalDimensions(worlds, "Build", true);

        assertFalse(Files.exists(root.resolve("DIM-1")));
        assertTrue(Files.isRegularFile(worlds.resolve("Build_nether/DIM-1/region/r.0.0.mca")));
        assertTrue(Files.isRegularFile(worlds.resolve("Build_nether/.lazybuilder-family-publish-pending")));
    }

    @Test
    void stagingConsolidatesPaperSiblingsBackToCanonicalJavaLayout() throws Exception {
        Path worlds = tempDir.resolve("worlds");
        Path staged = tempDir.resolve("staged");
        Files.createDirectories(staged);
        Files.write(staged.resolve("level.dat"), new byte[]{1});
        Files.createDirectories(worlds.resolve("Build_nether/DIM-1/region"));
        Files.createDirectories(worlds.resolve("Build_the_end/DIM1/region"));
        Files.write(worlds.resolve("Build_nether/level.dat"), new byte[]{2});
        Files.write(worlds.resolve("Build_the_end/level.dat"), new byte[]{3});
        Files.write(worlds.resolve("Build_nether/DIM-1/region/r.0.0.mca"), new byte[]{4});
        Files.write(worlds.resolve("Build_the_end/DIM1/region/r.0.0.mca"), new byte[]{5});

        PaperWorldFamilyLayout.consolidateSiblingsForStaging(worlds, "Build", staged);

        assertTrue(Files.isRegularFile(staged.resolve("DIM-1/region/r.0.0.mca")));
        assertTrue(Files.isRegularFile(staged.resolve("DIM1/region/r.0.0.mca")));
        assertFalse(Files.exists(worlds.resolve("Build_nether")));
        assertFalse(Files.exists(worlds.resolve("Build_the_end")));
    }
}
