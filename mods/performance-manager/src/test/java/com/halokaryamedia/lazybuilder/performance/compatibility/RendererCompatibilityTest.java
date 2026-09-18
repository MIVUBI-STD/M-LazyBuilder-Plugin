package com.halokaryamedia.lazybuilder.performance.compatibility;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RendererCompatibilityTest {
    @Test
    void fabricIndigoIsSafeDefaultWhenNoCustomRendererOwnsFrapi() {
        RendererCompatibility.Snapshot snapshot = new RendererCompatibility.Snapshot(false, false, List.of());

        assertFalse(snapshot.customRendererPresent());
        assertTrue(snapshot.firstPartyChunkPipelineSafe());
        assertTrue(snapshot.terrainSubmissionSafe());
        assertEquals("fabric-indigo", snapshot.ownerSummary());
    }

    @Test
    void customRendererOwnsChunkPipelineAndIdsAreDeterministic() {
        RendererCompatibility.Snapshot snapshot = new RendererCompatibility.Snapshot(
                false,
                false,
                List.of("sodium", "canvas")
        );

        assertTrue(snapshot.customRendererPresent());
        assertFalse(snapshot.firstPartyChunkPipelineSafe());
        assertFalse(snapshot.terrainSubmissionSafe());
        assertEquals("canvas+sodium", snapshot.ownerSummary());
    }

    @Test
    void irisAlwaysOwnsTerrainSubmissionEvenWithoutCustomRendererMarker() {
        RendererCompatibility.Snapshot snapshot = new RendererCompatibility.Snapshot(true, false, List.of());

        assertTrue(snapshot.firstPartyChunkPipelineSafe());
        assertFalse(snapshot.terrainSubmissionSafe());
        assertEquals("iris+fabric-indigo", snapshot.ownerSummary());
    }

    @Test
    void uncertaintyFailsOpenByDisablingFirstPartyChunkOwnership() {
        RendererCompatibility.Snapshot snapshot = new RendererCompatibility.Snapshot(true, true, List.of());

        assertFalse(snapshot.firstPartyChunkPipelineSafe());
        assertFalse(snapshot.terrainSubmissionSafe());
        assertEquals("iris+compatibility-uncertain", snapshot.ownerSummary());
    }
}
