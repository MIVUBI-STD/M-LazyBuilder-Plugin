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
    @Test
    void resourceReloadFailureBecomesActionableTerrainHealth() {
        FirstPartyShaderRuntime runtime = new FirstPartyShaderRuntime(temp);

        runtime.recordTerrainReloadCompletion(false, "reload exploded");

        assertEquals("terrain-reload-error", runtime.snapshot().stage());
        assertTrue(runtime.snapshot().lastError().contains("reload exploded"));
    }

    @Test
    void successfulUnrelatedReloadDoesNotInventShaderFailure() {
        FirstPartyShaderRuntime runtime = new FirstPartyShaderRuntime(temp);

        runtime.recordTerrainReloadCompletion(true, "");

        assertTrue(runtime.snapshot().lastError().isBlank());
    }
    @Test
    void staleTerrainReloadCallbacksCannotMutateNewerGeneration() {
        FirstPartyShaderRuntime runtime = new FirstPartyShaderRuntime(temp);
        long staleGeneration = runtime.currentTerrainGeneration();

        runtime.shutdown();
        long currentGeneration = runtime.currentTerrainGeneration();
        assertTrue(currentGeneration > staleGeneration);

        runtime.invalidateTerrainIntegrationForResourceReload(staleGeneration);
        runtime.recordTerrainCompile(staleGeneration, true, true, "");
        runtime.recordTerrainProgramLinked(staleGeneration);
        runtime.recordTerrainReloadCompletion(
                staleGeneration,
                false,
                "stale failure",
                "first-party-compile-fallback"
        );

        FirstPartyShaderRuntime.Snapshot snapshot = runtime.snapshot();
        assertEquals("stopped", snapshot.stage());
        assertTrue(snapshot.lastError().isBlank());
        assertFalse(snapshot.terrainIntegrated());
    }
    @Test
    void pendingSelectionDoesNotOverwriteLastKnownGoodConfig() throws IOException {
        Path packs = temp.resolve("packs");
        Path config = temp.resolve("config");
        Path first = packs.resolve("First");
        Path second = packs.resolve("Second");
        Files.createDirectories(first.resolve("shaders"));
        Files.createDirectories(second.resolve("shaders"));

        for (Path pack : java.util.List.of(first, second)) {
            Files.writeString(pack.resolve("shaders/terrain.vsh"), "#version 150\nvoid main(){}\n");
            Files.writeString(pack.resolve("shaders/terrain.fsh"), "#version 150\nvoid main(){}\n");
        }

        ShaderPackCatalog catalog = new ShaderPackCatalog(packs);
        var discovered = catalog.scan();
        String firstId = discovered.stream()
                .filter(pack -> pack.displayName().equals("First"))
                .findFirst()
                .orElseThrow()
                .id();
        String secondId = discovered.stream()
                .filter(pack -> pack.displayName().equals("Second"))
                .findFirst()
                .orElseThrow()
                .id();

        ShaderRuntimeConfigStore store = new ShaderRuntimeConfigStore(config);
        store.save(new ShaderRuntimePreferences(firstId, true));

        FirstPartyShaderRuntime runtime = new FirstPartyShaderRuntime(packs, config);
        assertTrue(runtime.select(secondId));

        ShaderRuntimePreferences persisted = store.load();
        assertEquals(firstId, persisted.selectedPackId());
        assertTrue(persisted.enabled());
        assertEquals(secondId, runtime.snapshot().selectedPackId());
    }
}
