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
    private volatile long revision;
    private volatile long compileRequestGeneration;
    private volatile boolean lastFrameApplied;
    private volatile boolean terrainVertexCompiled;
    private volatile boolean terrainFragmentCompiled;
    private volatile boolean terrainIntegrated;
    private volatile boolean terrainReloadPending;
    private FirstPartyShaderPipeline pipeline;
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

    public synchronized void refresh() {
        try {
            List<ShaderPackDescriptor> scanned = catalog.scan();
            packs = List.copyOf(scanned);
            if (!selectedPackId.isBlank()
                    && packs.stream().noneMatch(pack -> pack.id().equals(selectedPackId))) {
                selectedPackId = "";
                persisted = persisted.withSelectedPack("");
                configStore.save(persisted);
            }
            if (!activePackId.isBlank()
                    && packs.stream().noneMatch(pack -> pack.id().equals(activePackId))) {
                closePipelineLocked();
                activePackId = "";
                terrainVertexCompiled = false;
                terrainFragmentCompiled = false;
                terrainIntegrated = false;
                terrainReloadPending = true;
            }
            lastError = "";
            if (pipeline == null) stage = "source-ready";
        } catch (RuntimeException error) {
            packs = List.of();
            lastError = safeMessage(error);
            stage = "catalog-error";
        }
        revision++;
    }

    public synchronized boolean select(String packId) {
        String requested = packId == null ? "" : packId.trim();
        if (requested.isBlank()) {
            compileRequestGeneration++;
            selectedPackId = "";
            persisted = persisted.withSelectedPack("");
            configStore.save(persisted);
            lastError = "";
            stage = pipeline == null ? "source-ready" : "compiled";
            revision++;
            return true;
        }

        boolean exists = packs.stream().anyMatch(pack -> pack.id().equals(requested));
        if (!exists) {
            lastError = "Shader pack is no longer available.";
            stage = "selection-error";
            revision++;
            return false;
        }

        selectedPackId = requested;
        persisted = persisted.withSelectedPack(requested);
        configStore.save(persisted);
        lastError = "";
        stage = requested.equals(activePackId) && pipeline != null ? "compiled" : "selected";
        revision++;
        return true;
    }

    public void compileSelected() {
        String requested;
        long generation;
        synchronized (this) {
            requested = selectedPackId;
            if (requested == null || requested.isBlank()) {
                lastError = "No shader pack selected.";
                stage = "selection-error";
                revision++;
                return;
            }
            generation = ++compileRequestGeneration;
            stage = "compile-queued";
            lastError = "";
            revision++;
        }

        if (!RenderSystem.isOnRenderThread()) {
            String target = requested;
            long targetGeneration = generation;
            RenderSystem.recordRenderCall(
                    () -> compileSelectedOnRenderThread(target, targetGeneration)
            );
            return;
        }
        compileSelectedOnRenderThread(requested, generation);
    }

    private void compileSelectedOnRenderThread(String requestedPackId, long generation) {
        RenderSystem.assertOnRenderThread();

        ShaderPackDescriptor descriptor;
        synchronized (this) {
            if (generation != compileRequestGeneration
                    || !requestedPackId.equals(selectedPackId)) {
                return;
            }
            descriptor = packById(requestedPackId);
            if (descriptor == null) {
                lastError = "Shader pack is no longer available.";
                stage = "selection-error";
                revision++;
                return;
            }
            stage = "compiling";
            revision++;
        }

        FirstPartyShaderPipeline candidate = null;
        try {
            candidate = FirstPartyShaderPipeline.compile(
                    ShaderPackSource.open(descriptor),
                    optionDefines(descriptor)
            );

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

                pipeline = candidate;
                candidate = null;
                activePackId = requestedPackId;
                persisted = persisted.withSelectedPack(requestedPackId).withEnabled(true);
                configStore.save(persisted);
                lastFrameApplied = false;

                if (requiresTerrainReload) {
                    terrainVertexCompiled = false;
                    terrainFragmentCompiled = false;
                    terrainIntegrated = false;
                    terrainReloadPending = true;
                    stage = "compiled";
                } else {
                    terrainReloadPending = false;
                    stage = "terrain-active";
                }

                lastError = "";
                revision++;

                if (previous != null) previous.close();
            }
        } catch (Exception error) {
            if (candidate != null) candidate.close();
            synchronized (this) {
                lastError = safeMessage(error);
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

    public synchronized void disable() {
        compileRequestGeneration++;
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
        terrainReloadPending = restoreMinecraftTerrain;
        stage = "disabled";
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
        terrainVertexCompiled = false;
        terrainFragmentCompiled = false;
        terrainIntegrated = false;
        if (pipeline != null) stage = "terrain-reloading";
        revision++;
    }

    public synchronized boolean consumeTerrainReloadRequest() {
        if (!terrainReloadPending) return false;
        terrainReloadPending = false;
        return true;
    }

    public synchronized TerrainSource terrainSource(boolean vertex) {
        ShaderPackDescriptor active = packById(activePackId);
        if (pipeline == null || active == null) return TerrainSource.NONE;

        String path = vertex ? "shaders/terrain.vsh" : "shaders/terrain.fsh";
        try {
            ShaderSourcePreprocessor.Result result = ShaderSourcePreprocessor.preprocess(
                    ShaderPackSource.open(active),
                    path,
                    optionDefines(active)
            );
            return new TerrainSource(true, result.source(), activePackId, "");
        } catch (Exception error) {
            String message = safeMessage(error);
            lastError = message;
            stage = "terrain-source-error";
            revision++;
            return new TerrainSource(false, "", activePackId, message);
        }
    }

    public synchronized void recordTerrainCompile(boolean vertex, boolean success, String error) {
        if (success) {
            if (vertex) terrainVertexCompiled = true;
            else terrainFragmentCompiled = true;
            if (terrainVertexCompiled && terrainFragmentCompiled) {
                stage = "terrain-linking";
            }
            if (error == null || error.isBlank()) lastError = "";
        } else {
            if (vertex) terrainVertexCompiled = false;
            else terrainFragmentCompiled = false;
            terrainIntegrated = false;
            lastError = error == null || error.isBlank()
                    ? "First-party terrain shader fell back to Minecraft."
                    : error;
            stage = "terrain-fallback";
        }
        revision++;
    }

    public synchronized void recordTerrainProgramLinked() {
        if (pipeline == null
                || !terrainVertexCompiled
                || !terrainFragmentCompiled
                || terrainIntegrated) {
            return;
        }
        terrainIntegrated = true;
        lastError = "";
        stage = lastFrameApplied ? "terrain+postprocess-active" : "terrain-active";
        revision++;
    }

    public FirstPartyShadowRenderer.Snapshot renderShadow(
            double cameraX,
            double cameraY,
            double cameraZ,
            long timeOfDay
    ) {
        FirstPartyShaderPipeline current;
        synchronized (this) {
            current = pipeline;
            if (current == null || !current.has("shadow")) {
                return FirstPartyShadowRenderer.emptySnapshot();
            }
            if (shadowRenderer == null) shadowRenderer = new FirstPartyShadowRenderer();
        }

        FirstPartyShadowRenderer.Snapshot result = shadowRenderer.render(
                current,
                cameraX,
                cameraY,
                cameraZ,
                timeOfDay
        );

        synchronized (this) {
            if (!result.ready() && !"no-visible-terrain".equals(result.status())) {
                lastError = result.status();
                stage = "shadow-error";
                revision++;
            } else if (result.ready() && "shadow-error".equals(stage)) {
                lastError = "";
                stage = terrainIntegrated
                        ? (lastFrameApplied ? "terrain+postprocess-active" : "terrain-active")
                        : (lastFrameApplied ? "postprocess-active" : "compiled");
                revision++;
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
            if (gbuffer == null) gbuffer = new FirstPartyShaderGBuffer();
        }

        try {
            return gbuffer.begin(
                    targetFramebuffer,
                    width,
                    height,
                    current.gbufferAttachments()
            );
        } catch (RuntimeException error) {
            synchronized (this) {
                lastError = safeMessage(error);
                stage = "gbuffer-error";
                revision++;
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
                lastError = safeMessage(error);
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
            current = pipeline;
            if (current == null) {
                lastFrameApplied = false;
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
                if (applied && !lastError.isEmpty()) {
                    lastError = "";
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
                lastError = nextError;
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
        boolean recompile;
        synchronized (this) {
            ShaderPackDescriptor selected = selectedPack();
            if (selected == null) {
                lastError = "No shader pack selected.";
                stage = "selection-error";
                revision++;
                return false;
            }

            ShaderPackManifest.Option option = selected.manifest().options().stream()
                    .filter(candidate -> candidate.id().equals(optionId))
                    .findFirst()
                    .orElse(null);
            if (option == null) {
                lastError = "Shader option is no longer available: " + optionId;
                stage = "option-error";
                revision++;
                return false;
            }

            String sanitized = option.sanitize(rawValue);
            persisted = persisted.withOption(selected.id(), option.id(), sanitized);
            configStore.save(persisted);
            lastError = "";
            recompile = allowRecompile
                    && persisted.enabled()
                    && selected.id().equals(activePackId);
            stage = recompile ? "option-recompile-queued" : "selected";
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
            ShaderSourcePreprocessor.Result result = ShaderSourcePreprocessor.preprocess(
                    ShaderPackSource.open(selected),
                    relativePath,
                    optionDefines(selected)
            );
            List<String> dependencies = new ArrayList<>(result.dependencies());
            dependencies.sort(String::compareTo);
            lastError = "";
            return new SourcePreview(true, result.source(), List.copyOf(dependencies), "");
        } catch (Exception error) {
            lastError = safeMessage(error);
            return new SourcePreview(false, "", List.of(), lastError);
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

    public Map<String, Object> snapshotMap() {
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
        return Map.copyOf(values);
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

    private void closePipelineLocked() {
        FirstPartyShaderPipeline current = pipeline;
        pipeline = null;
        if (current != null) current.close();

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

    private static String safeMessage(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        if (message != null && !message.isBlank()) return message;
        return error == null ? "Unknown shader runtime error." : error.getClass().getSimpleName();
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
