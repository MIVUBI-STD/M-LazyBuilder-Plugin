package com.halokaryamedia.lazybuilder.world;

import com.halokaryamedia.lazybuilder.world.files.WorldFileRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldManagerRecoveryContractTest {
    @Test
    void resolvedTransactionRecoveryAllowsStartupDiscovery() {
        assertDoesNotThrow(() -> WorldManager.requireResolvedTransactionRecovery(
                new WorldFileRepository.CreateRecovery(1, 2, 0),
                new WorldFileRepository.DeleteRecovery(1, 1, 0)
        ));
    }

    @Test
    void preservedCreateOrDeleteRecoveryBlocksAutomaticDiscovery() {
        IllegalStateException create = assertThrows(IllegalStateException.class, () ->
                WorldManager.requireResolvedTransactionRecovery(
                        new WorldFileRepository.CreateRecovery(0, 0, 1),
                        new WorldFileRepository.DeleteRecovery(0, 0, 0)
                ));
        assertTrue(create.getMessage().contains("Automatic world discovery is blocked"));

        assertThrows(IllegalStateException.class, () ->
                WorldManager.requireResolvedTransactionRecovery(
                        new WorldFileRepository.CreateRecovery(0, 0, 0),
                        new WorldFileRepository.DeleteRecovery(0, 0, 1)
                ));
    }
}
