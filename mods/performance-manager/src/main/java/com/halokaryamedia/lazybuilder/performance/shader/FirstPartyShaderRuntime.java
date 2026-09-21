package com.halokaryamedia.lazybuilder.performance.shader;

import com.mojang.blaze3d.systems.RenderSystem;

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
    private volatile List<ShaderPackDescriptor> packs = List.of();
    private volatile String selectedPackId = "";
    private volatile String activePackId = "";
    private volatile String stage = "source-ready";
    private volatile String lastError = "";
    private volatile long revision;
    private FirstPartyShaderPipeline pipeline;

    public FirstPartyShaderRuntime(Path shaderpacksDirectory) {
        this.catalog = new ShaderPackCatalog(shaderpacksDirectory);
        refresh();
    }

    public synchronized void refresh() {
        try {
            List<ShaderPackDescriptor> scanned = catalog.scan();
            packs = List.copyOf(scanned);
            if (!selectedPackId.isBlank()
                    && packs.stream().noneMatch(pack -> pack.id().equals(selectedPackId))) {
                selectedPackId = "";
            }
            if (!activePackId.isBlank()
                    && packs.stream().noneMatch(pack -> pack.id().equals(activePackId))) {
                closePipelineLocked();
                activePackId = "";
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
            selectedPackId = "";
            lastError = "";
            stage = pipeline == null ? "source-ready" : "active";
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
        lastError = "";
        stage = requested.equals(activePackId) && pipeline != null ? "active" : "selected";
        revision++;
        return true;
    }

    public void compileSelected() {
        String requested;
        synchronized (this) {
            requested = selectedPackId;
            if (requested == null || requested.isBlank()) {
                lastError = "No shader pack selected.";
                stage = "selection-error";
                revision++;
                return;
            }
            stage = "compile-queued";
            lastError = "";
            revision++;
        }

        if (!RenderSystem.isOnRenderThread()) {
            String target = requested;
            RenderSystem.recordRenderCall(() -> compileSelectedOnRenderThread(target));
            return;
        }
        compileSelectedOnRenderThread(requested);
    }

    private void compileSelectedOnRenderThread(String requestedPackId) {
        RenderSystem.assertOnRenderThread();

        ShaderPackDescriptor descriptor;
        synchronized (this) {
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
            candidate = FirstPartyShaderPipeline.compile(ShaderPackSource.open(descriptor));

            synchronized (this) {
                if (!requestedPackId.equals(selectedPackId)) {
                    candidate.close();
                    stage = pipeline == null ? "selected" : "active";
                    revision++;
                    return;
                }

                FirstPartyShaderPipeline previous = pipeline;
                pipeline = candidate;
                candidate = null;
                activePackId = requestedPackId;
                lastError = "";
                stage = "active";
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

    public synchronized void disable() {
        closePipelineLocked();
        activePackId = "";
        stage = "disabled";
        lastError = "";
        revision++;
    }

    /**
     * Minecraft resource reload invalidates GL program identity. Selection is kept,
     * but the compiled pipeline is released and must be compiled again explicitly.
     */
    public synchronized void invalidateForResourceReload() {
        closePipelineLocked();
        activePackId = "";
        stage = selectedPackId.isBlank() ? "source-ready" : "selected";
        lastError = "";
        revision++;
    }

    public synchronized SourcePreview preprocess(String relativePath) {
        ShaderPackDescriptor selected = selectedPack();
        if (selected == null) {
            return new SourcePreview(false, "", List.of(), "No shader pack selected.");
        }

        try {
            ShaderSourcePreprocessor.Result result = ShaderSourcePreprocessor.preprocess(
                    ShaderPackSource.open(selected),
                    relativePath
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
        return new Snapshot(
                revision,
                "lazybuilder",
                stage,
                true,
                pipeline != null,
                false,
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
        values.put("renderingReady", snapshot.renderingReady());
        values.put("selectedPackId", snapshot.selectedPackId());
        values.put("selectedPackName", snapshot.selectedPackName());
        values.put("activePackId", snapshot.activePackId());
        values.put("activePackName", snapshot.activePackName());
        values.put("packIds", snapshot.packIds());
        values.put("packNames", snapshot.packNames());
        values.put("shaderpacksDirectory", snapshot.shaderpacksDirectory().toString());
        values.put("lastError", snapshot.lastError());
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
            boolean renderingReady,
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
            lastError = lastError == null ? "" : lastError;
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
