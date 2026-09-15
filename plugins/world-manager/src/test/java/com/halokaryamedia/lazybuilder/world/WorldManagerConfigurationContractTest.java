package com.halokaryamedia.lazybuilder.world;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldManagerConfigurationContractTest {
    @Test
    void packagedDefaultsKeepLocalResourceUseBounded() {
        var stream = WorldManagerConfigurationContractTest.class.getResourceAsStream("/config.yml");
        assertNotNull(stream, "packaged config.yml must exist");

        YamlConfiguration config = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));

        int maxFiles = config.getInt("world-manager.import.max-files");
        int maxUncompressedMb = config.getInt("world-manager.import.max-uncompressed-mb");
        int maxUploadMb = config.getInt("world-manager.transfer.max-upload-mb");
        int chunkBytes = config.getInt("world-manager.transfer.chunk-bytes");

        assertTrue(maxFiles > 0 && maxFiles <= 100_000,
                "default import file-count limit must remain conservative");
        assertTrue(maxUncompressedMb > 0 && maxUncompressedMb <= 16_384,
                "default extraction limit must remain conservative");
        assertTrue(maxUploadMb > 0 && maxUploadMb <= 8_192,
                "default upload limit must remain conservative");
        assertEquals(24_576, chunkBytes,
                "transfer chunk size must stay below the protocol payload ceiling");
    }
}
