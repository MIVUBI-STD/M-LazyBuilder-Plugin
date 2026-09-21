package com.halokaryamedia.lazybuilder.performance.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime authority for LazyBuilder-owned shader packs.
 *
 * Pack selection and source ownership are independent from the active compiled
 * pipeline. A failed reload never replaces the last known-good pipeline.
 */
public final class FirstPartyShaderRuntime {
    private final ShaderPackCatalog catalog;
    private final ShaderRuntimeConfigStore configStore;
    private volatile ShaderRuntimePreferences persisted;
    private volatile List<ShaderPackDescriptor> packs = List.of();
    private volatile String selectedPackId = "";
    private volatile String activePackId = "";
    private volatile String stage = "source-ready";
    private volatile String lastError = "";
    private volatile String catalogError = "";
    private volatile String compileError = "";
    private volatile String terrainError = "";
    private volatile String gbufferError = "";
    private volatile String shadowError = "";
    private volatile String postProcessError = "";
    private volatile String controlError = "";
    private volatile String previewError = "";
    private volatile long revision;
    private volatile long cachedSnapshotMapRevision = Long.MIN_VALUE;
    private volatile Map<String, Object> cachedSnapshotMap = Map.of();
    private volatile long compileRequestGeneration;
    private volatile long catalogRefreshGeneration;
    private volatile Thread preparationThread;
    private volatile Thread catalogRefreshThread;
    private volatile boolean catalogRefreshRequested;
    private volatile boolean stagedOptionsRequireCompile;
    private volatile boolean lastFrameApplied;
    private volatile boolean terrainVertexCompiled;
    private volatile boolean terrainFragmentCompiled;
    private volatile boolean terrainIntegrated;
    private volatile boolean terrainReloadPending;
    private volatile long terrainGeneration;
    private volatile long terrainReloadActiveGeneration = -1L;
    private FirstPartyShaderPipeline pipeline;
    private FirstPartyShaderPipeline terrainRollbackPipeline;
    private String terrainRollbackPackId = "";
    private boolean terrainRollbackIntegrated;
    private String pendingTerrainPackId = "";
    private FirstPartyShaderPostProcessor postProcessor;
    private FirstPartyShaderGBuffer gbuffer;
    private FirstPartyShadowRenderer shadowRenderer;

    public FirstPartyShaderRuntime(Path shaderpacksDirectory, Path configDirectory) {
        this.catalog = new ShaderPackCatalog(shaderpacksDirectory);
        this.configStore = new ShaderRuntimeConfigStore(configDirectory);
        this.persisted = configStore.load();
        this.selectedPackId = persisted.selectedPackId();
        refresh();
        if (!selectedPackId.isBlank()
                && packs.stream().noneMatch(pack -> pack.id().equals(selectedPackId))) {
            selectedPackId = "";
            persisted = persisted.withSelectedPack("");
            configStore.save(persisted);
        }
    }

    /** Test-only/runtime-local constructor with a config directory beside the pack root. */
    FirstPartyShaderRuntime(Path shaderpacksDirectory) {
        this(shaderpacksDirectory, shaderpacksDirectory.resolve(".lazybuilder-test-config"));
    }

    public void refresh() {
        long generation = beginCatalogRefresh();
        CatalogScan scan = scanCatalog();
        applyCatalogScan(generation, scan);
    }

    public void refreshAsync() {
        Thread workerToStart = null;
        synchronized (this) {
            compileRequestGeneration++;
            cancelPreparationLocked();
            catalogRefreshRequested = true;

            if (catalogRefreshThread == null || !catalogRefreshThread.isAlive()) {
                Thread worker = Thread.ofVirtual()
                        .name("LazyBuilder-Shader-Catalog")
                        .unstarted(this::catalogRefreshLoop);
                catalogRefreshThread = worker;
                workerToStart = worker;
                stage = "catalog-refresh-queued";
                revision++;
            } else if (!"catalog-refresh-pending".equals(stage)) {
                stage = "catalog-refresh-pending";
                revision++;
            }
        }
        if (workerToStart != null) workerToStart.start();
    }

    private void catalogRefreshLoop() {
        Thread self = Thread.currentThread();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                long generation;
                synchronized (this) {
                    if (!catalogRefreshRequested) return;
                    catalogRefreshRequested = false;
                    generation = beginCatalogRefreshLocked();
                }

                CatalogScan scan = scanCatalog();
                applyCatalogScan(generation, scan);
            }
        } finally {
            synchronized (this) {
                if (catalogRefreshThread == self) catalogRefreshThread = null;
                if (catalogRefreshRequested && !"stopped".equals(stage)) {
                    refreshAsync();
                }
            }
        }
    }

    private long beginCatalogRefresh() {
        synchronized (this) {
            return beginCatalogRefreshLocked();
        }
    }

    private long beginCatalogRefreshLocked() {
        compileRequestGeneration++;
        cancelPreparationLocked();
        long generation = ++catalogRefreshGeneration;
        stage = "catalog-scanning";
        controlError = "";
        revision++;
        return generation;
    }

    private CatalogScan scanCatalog() {
        try {
            List<ShaderPackDescriptor> scanned = catalog.scan();
            return new CatalogScan(
                    List.copyOf(scanned),
                    catalog.lastScanError()
            );
        } catch (RuntimeException error) {
            return new CatalogScan(List.of(), safeMessage(error));
        }
    }

    private void applyCatalogScan(long generation, CatalogScan scan) {
        synchronized (this) {
            if (generation != catalogRefreshGeneration || "stopped".equals(stage)) return;

            packs = scan.packs();
            catalogError = scan.error();
            migratePersistedPackIdsLocked();

            if (!catalogError.isBlank()) {
                lastError = primaryError();
                stage = "catalog-error";
                revision++;
                return;
            }

            if (!selectedPackId.isBlank()
                    && packs.stream().noneMatch(pack -> pack.id().equals(selectedPackId))) {
                selectedPackId = "";
                persisted = persisted.withSelectedPack("");
                configStore.save(persisted);
            }

            if (!activePackId.isBlank()
                    && packs.stream().noneMatch(pack -> pack.id().equals(activePackId))) {
                String removedActivePackId = activePackId;
                closePipelineLocked();
                activePackId = "";
                terrainVertexCompiled = false;
                terrainFragmentCompiled = false;
                terrainIntegrated = false;
                terrainGeneration++;
                terrainReloadPending = true;

                if (persisted.selectedPackId().equals(removedActivePackId)) {
                    persisted = persisted.withSelectedPack("");
                    configStore.save(persisted);
                }
            }

            catalogError = "";
            lastError = primaryError();
            if (pipeline == null) {
                stage = lastError.isBlank() ? "source-ready" : "degraded";
            } else if (lastError.isBlank()) {
                stage = terrainIntegrated ? "terrain-active" : "compiled";
            } else {
                stage = "degraded";
            }
            revision++;
        }
    }

    public synchronized boolean select(String packId) {
        if (terrainReloadActiveGeneration >= 0L) {
            controlError = "A terrain shader change is still being applied.";
            lastError = primaryError();
            stage = "terrain-reload-busy";
            revision++;
            return false;
        }

        String requested = packId == null ? "" : packId.trim();
        if (requested.isBlank()) {
            compileRequestGeneration++;
            cancelPreparationLocked();
            selectedPackId = "";
            if (pipeline == null && activePackId.isBlank()) {
                persisted = persisted.withSelectedPack("");
                configStore.save(persisted);
            }
            controlError = "";
            lastError = primaryError();
            stage = lastError.isBlank()
                    ? (pipeline == null ? "source-ready" : "compiled")
                    : "degraded";
            revision++;
            return true;
        }

        boolean exists = packs.stream().anyMatch(pack -> pack.id().equals(requested));
        if (!exists) {
            controlError = "Shader pack is no longer available.";
            lastError = primaryError();
            stage = "selection-error";
            revision++;
            return false;
        }

        compileRequestGeneration++;
        cancelPreparationLocked();
        stagedOptionsRequireCompile = false;
        terrainReloadActiveGeneration = -1L;
        pendingTerrainPackId = "";
        clearTerrainRollbackLocked();
        // Selection is a runtime candidate until compile/link/terrain activation succeeds.
        // Persisted active state remains last-known-good so a failed candidate cannot
        // change the next-launch shader configuration.
        selectedPackId = requested;
        controlError = "";
        lastError = primaryError();
        stage = lastError.isBlank()
                ? (requested.equals(activePackId) && pipeline != null ? "compiled" : "selected")
                : "degraded";
        revision++;
        return true;
    }

    public void compileSelected() {
        String requested;
        long generation;
        ShaderPackDescriptor descriptor;
        Map<String, String> defines;

        synchronized (this) {
            if (terrainReloadActiveGeneration >= 0L) {
                controlError = "A terrain shader change is still being applied.";
                lastError = primaryError();
                stage = "terrain-reload-busy";
                revision++;
                return;
            }

            requested = selectedPackId;
            if (requested == null || requested.isBlank()) {
                controlError = "No shader pack selected.";
                lastError = primaryError();
                stage = "selection-error";
                revision++;
                return;
            }

            descriptor = packById(requested);
            if (descriptor == null) {
                controlError = "Shader pack is no longer available.";
                lastError = primaryError();
                stage = "selection-error";
                revision++;
                return;
            }

            generation = ++compileRequestGeneration;
            stagedOptionsRequireCompile = false;
            defines = optionDefines(descriptor);
            stage = "prepare-queued";
            controlError = "";
            compileError = "";
            lastError = primaryError();
            revision++;
        }

        String target = requested;
        long targetGeneration = generation;
        ShaderPackDescriptor targetDescriptor = descriptor;
        Map<String, String> targetDefines = defines;

        Thread worker = Thread.ofVirtual()
                .name("LazyBuilder-Shader-Prepare")
                .unstarted(() -> prepareSelectedOffThread(
                        target,
                        targetGeneration,
                        targetDescriptor,
                        targetDefines
                ));

        synchronized (this) {
            Thread previous = preparationThread;
            preparationThread = worker;
            if (previous != null && previous != Thread.currentThread()) {
                previous.interrupt();
            }
        }
        worker.start();
    }

    private void prepareSelectedOffThread(
            String requestedPackId,
            long generation,
            ShaderPackDescriptor descriptor,
            Map<String, String> defines
    ) {
        Thread self = Thread.currentThread();
        try {
        synchronized (this) {
            if (generation != compileRequestGeneration
                    || !requestedPackId.equals(selectedPackId)) {
                return;
            }
            stage = "preparing";
            revision++;
        }

        FirstPartyShaderPipeline.Prepared prepared;
        try (ShaderPackSource.Session source = ShaderPackSource.openSession(descriptor)) {
            prepared = FirstPartyShaderPipeline.prepare(source, defines);
        } catch (Exception error) {
            synchronized (this) {
                if (generation != compileRequestGeneration
                        || !requestedPackId.equals(selectedPackId)) {
                    return;
                }
                compileError = safeMessage(error);
                lastError = primaryError();
                stage = "prepare-error";
                revision++;
            }
            return;
        }

        synchronized (this) {
            if (generation != compileRequestGeneration
                    || !requestedPackId.equals(selectedPackId)) {
                return;
            }
            stage = "compile-queued";
            revision++;
        }

        FirstPartyShaderPipeline.Prepared targetPrepared = prepared;
        RenderSystem.recordRenderCall(
                () -> compilePreparedOnRenderThread(
                        requestedPackId,
                        generation,
                        targetPrepared
                )
        );
        } finally {
            synchronized (this) {
                if (preparationThread == self) preparationThread = null;
            }
        }
    }

    private void compilePreparedOnRenderThread(
            String requestedPackId,
            long generation,
            FirstPartyShaderPipeline.Prepared prepared
    ) {
        RenderSystem.assertOnRenderThread();

        synchronized (this) {
            if (generation != compileRequestGeneration
                    || !requestedPackId.equals(selectedPackId)) {
                return;
            }
            stage = "compiling";
            revision++;
        }

        FirstPartyShaderPipeline candidate = null;
        try {
            candidate = FirstPartyShaderPipeline.compile(prepared);

            synchronized (this) {
                if (generation != compileRequestGeneration
                        || !requestedPackId.equals(selectedPackId)) {
                    candidate.close();
                    stage = pipeline == null ? "selected" : "active";
                    revision++;
                    return;
                }

                FirstPartyShaderPipeline previous = pipeline;
                boolean previousTerrainIntegrated = terrainIntegrated;
                boolean terrainChanged = previous == null
                        || !previous.terrainSourceFingerprint()
                        .equals(candidate.terrainSourceFingerprint());
                boolean requiresTerrainReload = terrainChanged || !previousTerrainIntegrated;

                if (requiresTerrainReload) {
                    clearTerrainRollbackLocked();

                    terrainRollbackPipeline = previous;
                    terrainRollbackPackId = activePackId;
                    terrainRollbackIntegrated = previousTerrainIntegrated;

                    pipeline = candidate;
                    candidate = null;
                    pendingTerrainPackId = requestedPackId;
                    releaseUnusedAuxiliariesLocked(pipeline);
                    lastFrameApplied = false;
                    terrainVertexCompiled = false;
                    terrainFragmentCompiled = false;
                    terrainIntegrated = false;
                    terrainGeneration++;
                    terrainReloadPending = true;
                    stage = "compiled";
                } else {
                    pipeline = candidate;
                    candidate = null;
                    releaseUnusedAuxiliariesLocked(pipeline);
                    activePackId = requestedPackId;
                    pendingTerrainPackId = "";
                    persisted = persisted.withSelectedPack(requestedPackId).withEnabled(true);
                    configStore.save(persisted);
                    lastFrameApplied = false;
                    terrainReloadPending = false;
                    terrainError = "";
                    stage = "terrain-active";
                    if (previous != null) previous.close();
                }

                compileError = "";
                controlError = "";
                lastError = primaryError();
                revision++;
            }
        } catch (Exception error) {
            if (candidate != null) candidate.close();
            synchronized (this) {
                if (generation != compileRequestGeneration
                        || !requestedPackId.equals(selectedPackId)) {
                    return;
                }
                compileError = safeMessage(error);
                lastError = primaryError();
                stage = "compile-error";
                revision++;
            }
        }
    }

    public void activateConfiguredSelection() {
        ShaderRuntimePreferences preferences = persisted;
        if (preferences.enabled() && !preferences.selectedPackId().isBlank()) {
            compileSelected();
        }
    }

    public void recompileIfEnabled() {
        ShaderRuntimePreferences preferences = persisted;
        if (preferences.enabled() && !preferences.selectedPackId().isBlank()) {
            compileSelected();
        }
    }

    public void applyStagedOptions() {
        boolean shouldCompile;
        synchronized (this) {
            shouldCompile = stagedOptionsRequireCompile
                    || (persisted.enabled() && !persisted.selectedPackId().isBlank());
            stagedOptionsRequireCompile = false;
        }
        if (shouldCompile) compileSelected();
    }

    public synchronized void disable() {
        compileRequestGeneration++;
        cancelPreparationLocked();
        stagedOptionsRequireCompile = false;
        boolean restoreMinecraftTerrain = pipeline != null
                || terrainVertexCompiled
                || terrainFragmentCompiled
                || terrainIntegrated;

        closePipelineLocked();
        activePackId = "";
        persisted = persisted.withEnabled(false);
        configStore.save(persisted);
        lastFrameApplied = false;
        terrainVertexCompiled = false;
        terrainFragmentCompiled = false;
        terrainIntegrated = false;
        if (restoreMinecraftTerrain) terrainGeneration++;
        terrainReloadPending = restoreMinecraftTerrain;
        stage = "disabled";
        catalogError = "";
        compileError = "";
        terrainError = "";
        gbufferError = "";
        shadowError = "";
        postProcessError = "";
        controlError = "";
        previewError = "";
        lastError = "";
        revision++;
    }

    /**
     * Minecraft resource reload replaces Minecraft ShaderProgram identities.
     * The independent LazyBuilder post-process pipeline stays alive; only terrain
     * link proof is reset so the active first-party sources can participate in
     * the same reload.
     */
    public synchronized void invalidateTerrainIntegrationForResourceReload() {
        invalidateTerrainIntegrationForResourceReload(terrainGeneration);
    }

    public synchronized void invalidateTerrainIntegrationForResourceReload(long generation) {
        if (generation != terrainGeneration) return;
        terrainVertexCompiled = false;
        terrainFragmentCompiled = false;
        terrainIntegrated = false;
        if (shadowRenderer != null) shadowRenderer.invalidateCache();
        if (pipeline != null) stage = "terrain-reloading";
        revision++;
    }

    public synchronized long consumeTerrainReloadGeneration() {
        if (!terrainReloadPending) return -1L;
        terrainReloadPending = false;
        terrainReloadActiveGeneration = terrainGeneration;
        return terrainGeneration;
    }

    /** Compatibility helper for focused tests/older callers. */
    public synchronized boolean consumeTerrainReloadRequest() {
        return consumeTerrainReloadGeneration() >= 0L;
    }

    public synchronized long currentTerrainGeneration() {
        return terrainGeneration;
    }

    public synchronized void recordTerrainReloadCompletion(
            boolean success,
            String error
    ) {
        recordTerrainReloadCompletion(terrainGeneration, success, error, "");
    }

    public synchronized void recordTerrainReloadCompletion(
            boolean success,
            String error,
            String sourceStatus
    ) {
        recordTerrainReloadCompletion(terrainGeneration, success, error, sourceStatus);
    }

    public synchronized void recordTerrainReloadCompletion(
            long generation,
            boolean success,
            String error,
            String sourceStatus
    ) {
        if (generation != terrainGeneration) return;
        if (terrainReloadActiveGeneration == generation) {
            terrainReloadActiveGeneration = -1L;
        }

        if (!success) {
            String failure = error == null || error.isBlank()
                    ? "Minecraft shader reload failed while applying the terrain shader."
                    : error;
            rollbackTerrainCandidateLocked(failure);
            return;
        }

        if (pipeline != null && terrainIntegrated) {
            activePackId = pendingTerrainPackId.isBlank()
                    ? activePackId
                    : pendingTerrainPackId;
            pendingTerrainPackId = "";
            persisted = persisted.withSelectedPack(activePackId).withEnabled(true);
            configStore.save(persisted);
            clearTerrainRollbackLocked();
            terrainError = "";
            lastError = primaryError();
            stage = lastError.isBlank()
                    ? (lastFrameApplied ? "terrain+postprocess-active" : "terrain-active")
                    : "degraded";
            revision++;
            return;
        }

        String failure = switch (sourceStatus == null ? "" : sourceStatus) {
            case "external-resource-pack" ->
                    "A Resource Pack overrides Minecraft's terrain shader; LazyBuilder terrain integration stayed disabled.";
            case "renderer-owned" ->
                    "Another renderer owns Minecraft's terrain shader path.";
            case "contract-missing" ->
                    "The active terrain shader does not expose the LazyBuilder transform contract.";
            case "compile-fallback", "first-party-compile-fallback" ->
                    "First-party terrain source fell back to Minecraft after compilation failed.";
            default ->
                    "Minecraft shader reload completed without first-party terrain integration.";
        };
        discardTerrainCandidateAfterSuccessfulFallbackLocked(failure);
    }

    public synchronized TerrainSource terrainSource(boolean vertex) {
        return terrainSource(vertex, terrainGeneration);
    }

    public synchronized TerrainSource terrainSource(boolean vertex, long expectedGeneration) {
        if (expectedGeneration >= 0L && expectedGeneration != terrainGeneration) {
            return TerrainSource.NONE;
        }

        FirstPartyShaderPipeline current = pipeline;
        String sourcePackId = pendingTerrainPackId.isBlank()
                ? activePackId
                : pendingTerrainPackId;
        if (current == null || sourcePackId.isBlank()) return TerrainSource.NONE;

        String source = current.terrainSource(vertex);
        if (source.isBlank()) {
            String message = "Active shader pipeline lost its prepared terrain source.";
            terrainError = message;
            lastError = primaryError();
            stage = "terrain-source-error";
            revision++;
            return new TerrainSource(false, "", sourcePackId, message);
        }

        return new TerrainSource(true, source, sourcePackId, "");
    }

    public synchronized void recordTerrainCompile(boolean vertex, boolean success, String error) {
        recordTerrainCompile(terrainGeneration, vertex, success, error);
    }

    public synchronized void recordTerrainCompile(
            long generation,
            boolean vertex,
            boolean success,
            String error
    ) {
        if (generation != terrainGeneration) return;
        if (success) {
            if (vertex) terrainVertexCompiled = true;
            else terrainFragmentCompiled = true;
            if (terrainVertexCompiled && terrainFragmentCompiled) {
                stage = "terrain-linking";
            }
            if (error == null || error.isBlank()) terrainError = "";
            lastError = primaryError();
        } else {
            if (vertex) terrainVertexCompiled = false;
            else terrainFragmentCompiled = false;
            terrainIntegrated = false;
            terrainError = error == null || error.isBlank()
                    ? "First-party terrain shader fell back to Minecraft."
                    : error;
            lastError = primaryError();
            stage = "terrain-fallback";
        }
        revision++;
    }

    public synchronized void recordTerrainProgramLinked() {
        recordTerrainProgramLinked(terrainGeneration);
    }

    public synchronized void recordTerrainProgramLinked(long generation) {
        if (generation != terrainGeneration) return;
        if (pipeline == null
                || !terrainVertexCompiled
                || !terrainFragmentCompiled
                || terrainIntegrated) {
            return;
        }
        terrainIntegrated = true;
        terrainError = "";
        lastError = primaryError();
        stage = lastError.isBlank()
                ? (lastFrameApplied ? "terrain+postprocess-active" : "terrain-active")
                : "degraded";
        revision++;
    }

    public FirstPartyShadowRenderer.Snapshot renderShadow(
            double cameraX,
            double cameraY,
            double cameraZ,
            long timeOfDay
    ) {
        return renderShadow(cameraX, cameraY, cameraZ, timeOfDay, 1, 1);
    }

    public FirstPartyShadowRenderer.Snapshot renderShadow(
            double cameraX,
            double cameraY,
            double cameraZ,
            long timeOfDay,
            int framebufferWidth,
            int framebufferHeight
    ) {
        FirstPartyShaderPipeline current;
        ShaderMemoryBudget.ShadowPlan shadowPlan;
        synchronized (this) {
            current = terrainIntegrated ? pipeline : null;
            if (current == null || !current.has("shadow")) {
                FirstPartyShadowRenderer previous = shadowRenderer;
                shadowRenderer = null;
                if (previous != null) previous.close();
                return FirstPartyShadowRenderer.emptySnapshot();
            }

            shadowPlan = ShaderMemoryBudget.planShadow(
                    framebufferWidth,
                    framebufferHeight,
                    current.gbufferAttachments(),
                    current.has("composite"),
                    current.has("composite") || current.has("final"),
                    2048
            );
            if (!shadowPlan.allowed()) {
                FirstPartyShadowRenderer previous = shadowRenderer;
                shadowRenderer = null;
                if (previous != null) previous.close();

                String nextShadowError = shadowPlan.status()
                        + ":" + shadowPlan.estimatedBytes()
                        + "/" + shadowPlan.limitBytes();
                boolean changed = !nextShadowError.equals(shadowError)
                        || !"memory-budget".equals(stage);
                shadowError = nextShadowError;
                lastError = primaryError();
                stage = "memory-budget";
                if (changed) revision++;
                return FirstPartyShadowRenderer.emptySnapshot();
            }

            if (shadowRenderer == null) shadowRenderer = new FirstPartyShadowRenderer();
        }

        FirstPartyShadowRenderer.Snapshot result = shadowRenderer.render(
                current,
                cameraX,
                cameraY,
                cameraZ,
                timeOfDay,
                shadowPlan.resolution()
        );

        synchronized (this) {
            if (!result.ready() && !"no-visible-terrain".equals(result.status())) {
                boolean changed = !result.status().equals(shadowError)
                        || !"shadow-error".equals(stage);
                shadowError = result.status();
                lastError = primaryError();
                stage = "shadow-error";
                if (changed) revision++;
            } else if (result.ready()) {
                boolean hadShadowError = !shadowError.isEmpty();
                String previousStage = stage;
                shadowError = "";
                lastError = primaryError();
                stage = lastError.isBlank()
                        ? (terrainIntegrated
                        ? (lastFrameApplied ? "terrain+postprocess-active" : "terrain-active")
                        : (lastFrameApplied ? "postprocess-active" : "compiled"))
                        : "degraded";
                if (hadShadowError || !stage.equals(previousStage)) revision++;
            }
        }
        return result;
    }

    public FirstPartyShadowRenderer.Snapshot shadowSnapshot() {
        FirstPartyShadowRenderer current;
        synchronized (this) {
            current = shadowRenderer;
        }
        return current == null
                ? FirstPartyShadowRenderer.emptySnapshot()
                : current.snapshot();
    }

    private static ShaderMemoryBudget.Estimate frameBudget(
            FirstPartyShaderPipeline pipeline,
            int width,
            int height
    ) {
        if (pipeline == null) {
            return new ShaderMemoryBudget.Estimate(false, 0L, 0L, "missing-pipeline");
        }

        boolean composite = pipeline.has("composite");
        boolean post = composite || pipeline.has("final");
        if (!pipeline.has("shadow")) {
            return ShaderMemoryBudget.estimate(
                    width,
                    height,
                    pipeline.gbufferAttachments(),
                    composite,
                    post,
                    false,
                    0
            );
        }

        ShaderMemoryBudget.ShadowPlan shadow = ShaderMemoryBudget.planShadow(
                width,
                height,
                pipeline.gbufferAttachments(),
                composite,
                post,
                2048
        );
        if (!shadow.allowed()) {
            return new ShaderMemoryBudget.Estimate(
                    false,
                    shadow.estimatedBytes(),
                    shadow.limitBytes(),
                    shadow.status()
            );
        }

        return ShaderMemoryBudget.estimate(
                width,
                height,
                pipeline.gbufferAttachments(),
                composite,
                post,
                true,
                shadow.resolution()
        );
    }

    public boolean beginGBufferFrame(
            int targetFramebuffer,
            int width,
            int height
    ) {
        FirstPartyShaderPipeline current;
        synchronized (this) {
            current = pipeline;
            if (current == null
                    || !terrainIntegrated
                    || current.gbufferAttachments() <= 0
                    || (!current.has("composite") && !current.has("final"))) {
                return false;
            }

            ShaderMemoryBudget.Estimate budget = frameBudget(current, width, height);
            if (!budget.allowed()) {
                FirstPartyShaderGBuffer previous = gbuffer;
                gbuffer = null;
                if (previous != null) previous.close();

                String nextGbufferError = budget.status()
                        + ":" + budget.estimatedBytes()
                        + "/" + budget.limitBytes();
                boolean changed = !nextGbufferError.equals(gbufferError)
                        || !"memory-budget".equals(stage);
                gbufferError = nextGbufferError;
                lastError = primaryError();
                stage = "memory-budget";
                if (changed) revision++;
                return false;
            }

            if (gbuffer == null) gbuffer = new FirstPartyShaderGBuffer();
        }

        try {
            boolean begun = gbuffer.begin(
                    targetFramebuffer,
                    width,
                    height,
                    current.gbufferAttachments()
            );
            if (begun) {
                synchronized (this) {
                    boolean hadError = !gbufferError.isEmpty();
                    String previousError = lastError;
                    gbufferError = "";
                    lastError = primaryError();
                    if (hadError || !lastError.equals(previousError)) revision++;
                }
            }
            return begun;
        } catch (RuntimeException error) {
            synchronized (this) {
                String nextError = safeMessage(error);
                boolean changed = !nextError.equals(gbufferError)
                        || !"gbuffer-error".equals(stage);
                gbufferError = nextError;
                lastError = primaryError();
                stage = "gbuffer-error";
                if (changed) revision++;
            }
            return false;
        }
    }

    public FirstPartyShaderGBuffer.Snapshot endGBufferFrame(int targetFramebuffer) {
        FirstPartyShaderGBuffer current;
        synchronized (this) {
            current = gbuffer;
        }
        if (current == null) {
            return new FirstPartyShaderGBuffer.Snapshot(false, "inactive", 0, 0, 0, 0L);
        }

        try {
            return current.end(targetFramebuffer);
        } catch (RuntimeException error) {
            synchronized (this) {
                gbufferError = safeMessage(error);
                lastError = primaryError();
                stage = "gbuffer-error";
                revision++;
            }
            return new FirstPartyShaderGBuffer.Snapshot(false, "error", 0, 0, 0, 0L);
        }
    }

    public boolean renderPostProcess(
            int targetFramebuffer,
            int sourceDepthTexture,
            int gbufferTexture1,
            int gbufferTexture2,
            Matrix4f inverseViewProjection,
            float cameraX,
            float cameraY,
            float cameraZ,
            int width,
            int height,
            float timeSeconds
    ) {
        FirstPartyShaderPipeline current;
        synchronized (this) {
            current = terrainIntegrated ? pipeline : null;
            if (current == null) {
                lastFrameApplied = false;
                return false;
            }
            if (!current.has("composite") && !current.has("final")) {
                FirstPartyShaderPostProcessor previous = postProcessor;
                postProcessor = null;
                if (previous != null) previous.close();
                lastFrameApplied = false;
                return false;
            }

            int requiredGbuffers = current.gbufferAttachments();
            boolean gbufferMissing = (requiredGbuffers >= 1 && gbufferTexture1 <= 0)
                    || (requiredGbuffers >= 2 && gbufferTexture2 <= 0);
            if (gbufferMissing) {
                String nextError = "Required GBuffer attachment is unavailable for this frame.";
                boolean changed = lastFrameApplied
                        || !nextError.equals(gbufferError)
                        || !"gbuffer-fallback".equals(stage);
                lastFrameApplied = false;
                gbufferError = nextError;
                lastError = primaryError();
                stage = "gbuffer-fallback";
                if (changed) revision++;
                return false;
            }

            ShaderMemoryBudget.Estimate budget = frameBudget(current, width, height);
            if (!budget.allowed()) {
                FirstPartyShaderPostProcessor previous = postProcessor;
                postProcessor = null;
                if (previous != null) previous.close();

                String nextPostError = budget.status()
                        + ":" + budget.estimatedBytes()
                        + "/" + budget.limitBytes();
                boolean changed = lastFrameApplied
                        || !nextPostError.equals(postProcessError)
                        || !"memory-budget".equals(stage);
                lastFrameApplied = false;
                postProcessError = nextPostError;
                lastError = primaryError();
                stage = "memory-budget";
                if (changed) revision++;
                return false;
            }
        }

        try {
            FirstPartyShaderPostProcessor processor;
            synchronized (this) {
                if (postProcessor == null) postProcessor = new FirstPartyShaderPostProcessor();
                processor = postProcessor;
            }

            boolean applied = processor.render(
                    current,
                    targetFramebuffer,
                    sourceDepthTexture,
                    gbufferTexture1,
                    gbufferTexture2,
                    shadowSnapshot(),
                    inverseViewProjection,
                    cameraX,
                    cameraY,
                    cameraZ,
                    width,
                    height,
                    timeSeconds
            );

            synchronized (this) {
                boolean changed = lastFrameApplied != applied;
                lastFrameApplied = applied;

                String nextStage;
                if (applied && terrainIntegrated) {
                    nextStage = "terrain+postprocess-active";
                } else if (applied) {
                    nextStage = "postprocess-active";
                } else if (terrainIntegrated) {
                    nextStage = "terrain-active";
                } else {
                    nextStage = ("postprocess-active".equals(stage) ? "compiled" : stage);
                }
                if (!nextStage.equals(stage)) {
                    stage = nextStage;
                    changed = true;
                }
                if (applied && !postProcessError.isEmpty()) {
                    postProcessError = "";
                    changed = true;
                }
                String nextError = primaryError();
                if (!nextError.equals(lastError)) {
                    lastError = nextError;
                    changed = true;
                }
                if (!lastError.isBlank()) nextStage = "degraded";
                if (!nextStage.equals(stage)) {
                    stage = nextStage;
                    changed = true;
                }
                if (changed) revision++;
            }
            return applied;
        } catch (RuntimeException error) {
            synchronized (this) {
                String nextError = safeMessage(error);
                boolean changed = lastFrameApplied
                        || !"render-error".equals(stage)
                        || !nextError.equals(lastError);
                lastFrameApplied = false;
                postProcessError = nextError;
                lastError = primaryError();
                stage = "render-error";
                if (changed) revision++;
            }
            return false;
        }
    }

    public boolean updateOption(String optionId, String rawValue) {
        return updateOption(optionId, rawValue, true);
    }

    public boolean updateOption(String optionId, String rawValue, boolean allowRecompile) {
        return updateOptions(Map.of(optionId, rawValue == null ? "" : rawValue), allowRecompile);
    }

    public boolean updateOptions(
            Map<String, String> updates,
            boolean allowRecompile
    ) {
        if (updates == null || updates.isEmpty()) return true;

        boolean recompile;
        synchronized (this) {
            ShaderPackDescriptor selected = selectedPack();
            if (selected == null) {
                controlError = "No shader pack selected.";
                lastError = primaryError();
                stage = "selection-error";
                revision++;
                return false;
            }

            Map<String, ShaderPackManifest.Option> declared = new LinkedHashMap<>();
            for (ShaderPackManifest.Option option : selected.manifest().options()) {
                declared.put(option.id(), option);
            }

            ShaderRuntimePreferences next = persisted;
            boolean changed = false;
            for (Map.Entry<String, String> update : updates.entrySet()) {
                ShaderPackManifest.Option option = declared.get(update.getKey());
                if (option == null) {
                    controlError = "Shader option is no longer available: " + update.getKey();
                    lastError = primaryError();
                    stage = "option-error";
                    revision++;
                    return false;
                }

                String sanitized = option.sanitize(update.getValue());
                String previous = option.sanitize(next.optionValue(
                        selected.id(),
                        option.id(),
                        option.defaultValue()
                ));
                if (!previous.equals(sanitized)) {
                    next = next.withOption(selected.id(), option.id(), sanitized);
                    changed = true;
                }
            }

            if (!changed) {
                controlError = "";
                lastError = primaryError();
                return true;
            }

            boolean compilePending = preparationThread != null
                    || "prepare-queued".equals(stage)
                    || "preparing".equals(stage)
                    || "compile-queued".equals(stage)
                    || "compiling".equals(stage);
            boolean activeEnabled = persisted.enabled()
                    && pipeline != null
                    && !activePackId.isBlank();

            compileRequestGeneration++;
            cancelPreparationLocked();
            stagedOptionsRequireCompile = compilePending || activeEnabled;

            persisted = next;
            configStore.save(persisted);
            controlError = "";
            lastError = primaryError();
            recompile = allowRecompile && stagedOptionsRequireCompile;
            if (recompile) stagedOptionsRequireCompile = false;
            stage = recompile
                    ? "option-recompile-queued"
                    : stagedOptionsRequireCompile ? "option-recompile-pending" : "selected";
            revision++;
        }

        if (recompile) compileSelected();
        return true;
    }

    private synchronized Map<String, String> optionDefines(ShaderPackDescriptor descriptor) {
        if (descriptor == null) return Map.of();
        return descriptor.manifest().defines(descriptor.id(), persisted);
    }

    public synchronized SourcePreview preprocess(String relativePath) {
        ShaderPackDescriptor selected = selectedPack();
        if (selected == null) {
            return new SourcePreview(false, "", List.of(), "No shader pack selected.");
        }

        try {
            ShaderSourcePreprocessor.Result result;
            try (ShaderPackSource.Session source = ShaderPackSource.openSession(selected)) {
                result = ShaderSourcePreprocessor.preprocess(
                        source,
                        relativePath,
                        optionDefines(selected)
                );
            }
            List<String> dependencies = new ArrayList<>(result.dependencies());
            dependencies.sort(String::compareTo);
            previewError = "";
            lastError = primaryError();
            return new SourcePreview(true, result.source(), List.copyOf(dependencies), "");
        } catch (Exception error) {
            previewError = safeMessage(error);
            lastError = primaryError();
            return new SourcePreview(false, "", List.of(), previewError);
        } finally {
            revision++;
        }
    }

    public synchronized Snapshot snapshot() {
        ShaderPackDescriptor selected = selectedPack();
        ShaderPackDescriptor active = packById(activePackId);
        FirstPartyShadowRenderer.Snapshot shadow = shadowSnapshot();
        return new Snapshot(
                revision,
                "lazybuilder",
                stage,
                !"catalog-error".equals(stage),
                pipeline != null,
                pipeline != null && (pipeline.has("composite") || pipeline.has("final")),
                pipeline == null ? 0 : pipeline.gbufferAttachments(),
                shadow.ready(),
                shadow.status(),
                shadow.resolution(),
                shadow.drawnBuffers(),
                lastFrameApplied || terrainIntegrated,
                terrainIntegrated,
                selected == null ? "" : selected.id(),
                selected == null ? "" : selected.displayName(),
                active == null ? "" : active.id(),
                active == null ? "" : active.displayName(),
                packs.stream().map(ShaderPackDescriptor::id).toList(),
                packs.stream().map(ShaderPackDescriptor::displayName).toList(),
                catalog.directory(),
                lastError
        );
    }

    public synchronized Map<String, Object> diagnosticsMap() {
        FirstPartyShadowRenderer.Snapshot shadow = shadowSnapshot();
        FirstPartyShaderGBuffer.Snapshot gbufferSnapshot = gbuffer == null
                ? new FirstPartyShaderGBuffer.Snapshot(false, "inactive", 0, 0, 0, 0L)
                : gbuffer.snapshot();

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("shadowStatus", shadow.status());
        values.put("shadowResolution", shadow.resolution());
        values.put("shadowDrawnBuffers", shadow.drawnBuffers());
        values.put("shadowSkippedBuffers", shadow.skippedBuffers());
        values.put("shadowReusedFrames", shadow.reusedFrames());
        values.put("gbufferStatus", gbufferSnapshot.status());
        values.put("gbufferActive", gbufferSnapshot.active());
        values.put("gbufferAttachments", gbufferSnapshot.attachmentCount());
        values.put("gbufferStaleFrameRecoveries", gbufferSnapshot.staleFrameRecoveries());
        values.put("compileGeneration", compileRequestGeneration);
        values.put("terrainReloadPending", terrainReloadPending);
        values.put("terrainGeneration", terrainGeneration);
        values.put("terrainReloadActiveGeneration", terrainReloadActiveGeneration);
        values.put("terrainCandidatePackId", pendingTerrainPackId);
        values.put("terrainRollbackAvailable", terrainRollbackPipeline != null);
        values.put("invalidPackCount", catalog.invalidEntries().size());
        values.put("catalogHealthy", catalogError.isBlank());
        return Map.copyOf(values);
    }

    public synchronized Map<String, Object> snapshotMap() {
        long currentRevision = revision;
        if (cachedSnapshotMapRevision == currentRevision) {
            return cachedSnapshotMap;
        }

        Snapshot snapshot = snapshot();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("revision", snapshot.revision());
        values.put("owner", snapshot.owner());
        values.put("stage", snapshot.stage());
        values.put("sourceReady", snapshot.sourceReady());
        values.put("compiledReady", snapshot.compiledReady());
        values.put("postProcessReady", snapshot.postProcessReady());
        values.put("gbufferAttachments", snapshot.gbufferAttachments());
        values.put("shadowReady", snapshot.shadowReady());
        values.put("shadowStatus", snapshot.shadowStatus());
        values.put("shadowResolution", snapshot.shadowResolution());
        values.put("shadowDrawnBuffers", snapshot.shadowDrawnBuffers());
        values.put("renderingReady", snapshot.renderingReady());
        values.put("terrainIntegrated", snapshot.terrainIntegrated());
        values.put("configuredEnabled", persisted.enabled());
        values.put("selectedPackId", snapshot.selectedPackId());
        values.put("selectedPackName", snapshot.selectedPackName());
        values.put("activePackId", snapshot.activePackId());
        values.put("activePackName", snapshot.activePackName());
        values.put("packIds", snapshot.packIds());
        values.put("packNames", snapshot.packNames());
        values.put("shaderpacksDirectory", snapshot.shaderpacksDirectory().toString());
        values.put("lastError", snapshot.lastError());
        values.put("invalidPacks", catalog.invalidEntries());
        Map<String, String> health = new LinkedHashMap<>();
        health.put("catalog", catalogError);
        health.put("compile", compileError);
        health.put("terrain", terrainError);
        health.put("gbuffer", gbufferError);
        health.put("shadow", shadowError);
        health.put("postProcess", postProcessError);
        health.put("control", controlError);
        health.put("preview", previewError);
        values.put("health", Map.copyOf(health));
        values.put("degraded", health.values().stream().anyMatch(value -> !value.isBlank()));

        ShaderPackDescriptor selected = selectedPack();
        if (selected != null) {
            values.put("selectedPackAuthor", selected.manifest().author());
            values.put("selectedPackDescription", selected.manifest().description());
            List<Map<String, Object>> optionRows = new ArrayList<>();
            for (ShaderPackManifest.Option option : selected.manifest().options()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", option.id());
                row.put("label", option.label());
                row.put("type", option.type().name().toLowerCase(java.util.Locale.ROOT));
                row.put(
                        "value",
                        option.sanitize(persisted.optionValue(
                                selected.id(),
                                option.id(),
                                option.defaultValue()
                        ))
                );
                row.put("default", option.defaultValue());
                row.put("min", option.min());
                row.put("max", option.max());
                row.put("step", option.step());
                optionRows.add(Map.copyOf(row));
            }
            values.put("options", List.copyOf(optionRows));
        } else {
            values.put("selectedPackAuthor", "");
            values.put("selectedPackDescription", "");
            values.put("options", List.of());
        }
        Map<String, Object> immutable = Map.copyOf(values);
        cachedSnapshotMap = immutable;
        cachedSnapshotMapRevision = currentRevision;
        return immutable;
    }

    private void migratePersistedPackIdsLocked() {
        ShaderRuntimePreferences migrated = persisted;

        Map<String, Integer> legacyCounts = new LinkedHashMap<>();
        for (ShaderPackDescriptor pack : packs) {
            String legacyId = ShaderPackCatalog.legacyId(pack);
            if (!legacyId.isBlank()) legacyCounts.merge(legacyId, 1, Integer::sum);
        }

        for (ShaderPackDescriptor pack : packs) {
            String legacyId = ShaderPackCatalog.legacyId(pack);
            if (!legacyId.isBlank()
                    && !legacyId.equals(pack.id())
                    && legacyCounts.getOrDefault(legacyId, 0) == 1) {
                migrated = migrated.migratePackId(legacyId, pack.id());
            }

            String sourceDerivedId = ShaderPackCatalog.sourceDerivedId(pack);
            if (!sourceDerivedId.isBlank() && !sourceDerivedId.equals(pack.id())) {
                migrated = migrated.migratePackId(sourceDerivedId, pack.id());
            }
        }

        if (migrated.equals(persisted)) return;
        persisted = migrated;
        selectedPackId = migrated.selectedPackId();
        configStore.save(migrated);
    }

    private ShaderPackDescriptor selectedPack() {
        return packById(selectedPackId);
    }

    private ShaderPackDescriptor packById(String id) {
        if (id == null || id.isBlank()) return null;
        return packs.stream()
                .filter(pack -> pack.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    private void rollbackTerrainCandidateLocked(String failure) {
        FirstPartyShaderPipeline failed = pipeline;
        FirstPartyShaderPipeline restore = terrainRollbackPipeline;
        String restorePackId = terrainRollbackPackId;
        boolean restoreIntegrated = terrainRollbackIntegrated;

        terrainRollbackPipeline = null;
        terrainRollbackPackId = "";
        terrainRollbackIntegrated = false;
        pendingTerrainPackId = "";

        pipeline = restore;
        activePackId = restorePackId;
        terrainIntegrated = restore != null && restoreIntegrated;
        terrainVertexCompiled = terrainIntegrated;
        terrainFragmentCompiled = terrainIntegrated;
        terrainReloadPending = false;

        if (failed != null && failed != restore) failed.close();

        terrainError = failure == null ? "" : failure;
        lastError = primaryError();
        stage = terrainIntegrated
                ? "terrain-rollback-active"
                : "terrain-reload-error";
        revision++;
    }

    private void discardTerrainCandidateAfterSuccessfulFallbackLocked(String failure) {
        FirstPartyShaderPipeline failed = pipeline;
        FirstPartyShaderPipeline previous = terrainRollbackPipeline;

        pipeline = null;
        terrainRollbackPipeline = null;
        terrainRollbackPackId = "";
        terrainRollbackIntegrated = false;
        pendingTerrainPackId = "";
        activePackId = "";
        terrainIntegrated = false;
        terrainVertexCompiled = false;
        terrainFragmentCompiled = false;
        terrainReloadPending = false;

        if (failed != null) failed.close();
        if (previous != null && previous != failed) previous.close();

        // The candidate never became authoritative. Keep the persisted
        // last-known-good configuration unchanged; only this failed candidate is discarded.
        releaseAllAuxiliariesLocked();

        terrainError = failure == null ? "" : failure;
        lastError = primaryError();
        stage = "terrain-reload-incomplete";
        revision++;
    }

    private void clearTerrainRollbackLocked() {
        FirstPartyShaderPipeline rollback = terrainRollbackPipeline;
        terrainRollbackPipeline = null;
        terrainRollbackPackId = "";
        terrainRollbackIntegrated = false;
        if (rollback != null && rollback != pipeline) rollback.close();
    }

    private void releaseAllAuxiliariesLocked() {
        FirstPartyShaderPostProcessor processor = postProcessor;
        postProcessor = null;
        if (processor != null) processor.close();

        FirstPartyShaderGBuffer frameGBuffer = gbuffer;
        gbuffer = null;
        if (frameGBuffer != null) frameGBuffer.close();

        FirstPartyShadowRenderer shadows = shadowRenderer;
        shadowRenderer = null;
        if (shadows != null) shadows.close();
    }

    private void releaseUnusedAuxiliariesLocked(FirstPartyShaderPipeline next) {
        if (next == null) return;

        if (!next.has("shadow")) {
            FirstPartyShadowRenderer shadows = shadowRenderer;
            shadowRenderer = null;
            if (shadows != null) shadows.close();
        }

        if (!next.has("composite") && !next.has("final")) {
            FirstPartyShaderPostProcessor processor = postProcessor;
            postProcessor = null;
            if (processor != null) processor.close();
        }

        if (next.gbufferAttachments() <= 0
                || (!next.has("composite") && !next.has("final"))) {
            FirstPartyShaderGBuffer frameGBuffer = gbuffer;
            gbuffer = null;
            if (frameGBuffer != null) frameGBuffer.close();
        }
    }

    private void closePipelineLocked() {
        FirstPartyShaderPipeline current = pipeline;
        pipeline = null;
        if (current != null) current.close();

        FirstPartyShaderPipeline rollback = terrainRollbackPipeline;
        terrainRollbackPipeline = null;
        terrainRollbackPackId = "";
        terrainRollbackIntegrated = false;
        pendingTerrainPackId = "";
        if (rollback != null && rollback != current) rollback.close();

        FirstPartyShaderPostProcessor processor = postProcessor;
        postProcessor = null;
        if (processor != null) processor.close();

        FirstPartyShaderGBuffer frameGBuffer = gbuffer;
        gbuffer = null;
        if (frameGBuffer != null) frameGBuffer.close();

        FirstPartyShadowRenderer shadows = shadowRenderer;
        shadowRenderer = null;
        if (shadows != null) shadows.close();
    }

    public synchronized void shutdown() {
        compileRequestGeneration++;
        catalogRefreshGeneration++;
        catalogRefreshRequested = false;
        cancelPreparationLocked();
        Thread catalogWorker = catalogRefreshThread;
        catalogRefreshThread = null;
        if (catalogWorker != null && catalogWorker != Thread.currentThread()) {
            catalogWorker.interrupt();
        }
        stagedOptionsRequireCompile = false;
        terrainReloadPending = false;
        terrainReloadActiveGeneration = -1L;
        terrainGeneration++;
        closePipelineLocked();

        activePackId = "";
        terrainVertexCompiled = false;
        terrainFragmentCompiled = false;
        terrainIntegrated = false;
        lastFrameApplied = false;
        stage = "stopped";
        cachedSnapshotMapRevision = Long.MIN_VALUE;
        cachedSnapshotMap = Map.of();
        revision++;
    }

    private void cancelPreparationLocked() {
        Thread worker = preparationThread;
        preparationThread = null;
        if (worker != null && worker != Thread.currentThread()) worker.interrupt();
    }

    private String primaryError() {
        if (!catalogError.isBlank()) return catalogError;
        if (!compileError.isBlank()) return compileError;
        if (!terrainError.isBlank()) return terrainError;
        if (!gbufferError.isBlank()) return gbufferError;
        if (!shadowError.isBlank()) return shadowError;
        if (!postProcessError.isBlank()) return postProcessError;
        if (!controlError.isBlank()) return controlError;
        if (!previewError.isBlank()) return previewError;
        return "";
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        if (message != null && !message.isBlank()) return message;
        return error == null ? "Unknown shader runtime error." : error.getClass().getSimpleName();
    }

    private record CatalogScan(
            List<ShaderPackDescriptor> packs,
            String error
    ) {
        private CatalogScan {
            packs = packs == null ? List.of() : List.copyOf(packs);
            error = error == null ? "" : error;
        }
    }

    public record Snapshot(
            long revision,
            String owner,
            String stage,
            boolean sourceReady,
            boolean compiledReady,
            boolean postProcessReady,
            int gbufferAttachments,
            boolean shadowReady,
            String shadowStatus,
            int shadowResolution,
            int shadowDrawnBuffers,
            boolean renderingReady,
            boolean terrainIntegrated,
            String selectedPackId,
            String selectedPackName,
            String activePackId,
            String activePackName,
            List<String> packIds,
            List<String> packNames,
            Path shaderpacksDirectory,
            String lastError
    ) {
        public Snapshot {
            owner = owner == null ? "" : owner;
            stage = stage == null ? "" : stage;
            selectedPackId = selectedPackId == null ? "" : selectedPackId;
            selectedPackName = selectedPackName == null ? "" : selectedPackName;
            activePackId = activePackId == null ? "" : activePackId;
            activePackName = activePackName == null ? "" : activePackName;
            packIds = packIds == null ? List.of() : List.copyOf(packIds);
            packNames = packNames == null ? List.of() : List.copyOf(packNames);
            shadowStatus = shadowStatus == null ? "" : shadowStatus;
            lastError = lastError == null ? "" : lastError;
        }
    }

    public record TerrainSource(
            boolean available,
            String source,
            String packId,
            String error
    ) {
        private static final TerrainSource NONE = new TerrainSource(false, "", "", "");

        public TerrainSource {
            source = source == null ? "" : source;
            packId = packId == null ? "" : packId;
            error = error == null ? "" : error;
        }
    }

    public record SourcePreview(
            boolean ready,
            String source,
            List<String> dependencies,
            String error
    ) {
        public SourcePreview {
            source = source == null ? "" : source;
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
            error = error == null ? "" : error;
        }
    }
}
