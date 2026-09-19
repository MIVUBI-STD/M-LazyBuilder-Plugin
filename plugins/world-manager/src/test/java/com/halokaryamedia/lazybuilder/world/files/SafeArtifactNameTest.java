package com.halokaryamedia.lazybuilder.world.files;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafeArtifactNameTest {
    @Test
    void acceptsNormalPortableArtifactNames() {
        assertEquals("Build 01.zip",
                SafeArtifactName.requirePortable("Build 01.zip", "artifactName"));
    }

    @Test
    void rejectsWindowsDeviceAndAdsNames() {
        assertThrows(IllegalArgumentException.class,
                () -> SafeArtifactName.requirePortable("CON.zip", "artifactName"));
        assertThrows(IllegalArgumentException.class,
                () -> SafeArtifactName.requirePortable("NUL.mcworld", "artifactName"));
        assertThrows(IllegalArgumentException.class,
                () -> SafeArtifactName.requirePortable("COM1.jar", "artifactName"));
        assertThrows(IllegalArgumentException.class,
                () -> SafeArtifactName.requirePortable("Build.zip:stream", "artifactName"));
    }

    @Test
    void rejectsTrailingDotSpaceAndExcessiveNames() {
        assertThrows(IllegalArgumentException.class,
                () -> SafeArtifactName.requirePortable("Build. ", "artifactName"));
        assertThrows(IllegalArgumentException.class,
                () -> SafeArtifactName.requirePortable("x".repeat(256), "artifactName"));
    }
}
