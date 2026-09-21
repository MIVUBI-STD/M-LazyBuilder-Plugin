package com.halokaryamedia.lazybuilder.performance.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FirstPartyShaderRuntimeTest {
    @TempDir Path temp;

    @Test
    void selectionDoesNotPretendRenderingIsReady() throws IOException {
        Path pack = temp.resolve("NativePack");
        Files.createDirectories(pack.resolve("shaders"));
        Files.writeString(pack.resolve("shaders/terrain.vsh"), "#version 150\nvoid main(){}\n");
        Files.writeString(pack.resolve("shaders/terrain.fsh"), "#version 150\nvoid main(){}\n");

        FirstPartyShaderRuntime runtime = new FirstPartyShaderRuntime(temp);
        String id = runtime.snapshot().packIds().get(0);

        assertTrue(runtime.select(id));

        FirstPartyShaderRuntime.Snapshot snapshot = runtime.snapshot();
        assertEquals(id, snapshot.selectedPackId());
        assertFalse(snapshot.compiledReady());
        assertFalse(snapshot.renderingReady());
        assertFalse(snapshot.terrainIntegrated());
    }

    @Test
    void missingSelectionFailsWithoutChangingActiveState() {
        FirstPartyShaderRuntime runtime = new FirstPartyShaderRuntime(temp);

        assertFalse(runtime.select("missing"));
        assertEquals("selection-error", runtime.snapshot().stage());
        assertFalse(runtime.snapshot().renderingReady());
    }
}
