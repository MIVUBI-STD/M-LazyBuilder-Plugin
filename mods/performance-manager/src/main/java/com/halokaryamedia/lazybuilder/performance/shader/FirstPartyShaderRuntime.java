package com.halokaryamedia.lazybuilder.performance.shader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime authority for LazyBuilder-owned shader packs.
 *
 * This first stage owns discovery/selection/source preprocessing only. It deliberately
 * reports renderingReady=false until the compiler/framebuffer/pass pipeline is wired.
 */
public final class FirstPartyShaderRuntime {
    private final ShaderPackCatalog catalog;
    private volatile List<ShaderPackDescriptor> packs = List.of();
    private volatile String selectedPackId = "";
    private volatile String lastError = "";
    private volatile long revision;

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
            lastError = "";
        } catch (RuntimeException error) {
            packs = List.of();
            lastError = safeMessage(error);
        }
        revision++;
    }

    public synchronized boolean select(String packId) {
        String requested = packId == null ? "" : packId.trim();
        if (requested.isBlank()) {
            selectedPackId = "";
            lastError = "";
            revision++;
            return true;
        }

        boolean exists = packs.stream().anyMatch(pack -> pack.id().equals(requested));
        if (!exists) {
            lastError = "Shader pack is no longer available.";
            revision++;
            return false;
        }

        selectedPackId = requested;
        lastError = "";
        revision++;
        return true;
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

    public Snapshot snapshot() {
        ShaderPackDescriptor selected = selectedPack();
        return new Snapshot(
                revision,
                "lazybuilder",
                "source-ready",
                false,
                selected == null ? "" : selected.id(),
                selected == null ? "" : selected.displayName(),
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
        values.put("renderingReady", snapshot.renderingReady());
        values.put("selectedPackId", snapshot.selectedPackId());
        values.put("selectedPackName", snapshot.selectedPackName());
        values.put("packIds", snapshot.packIds());
        values.put("packNames", snapshot.packNames());
        values.put("shaderpacksDirectory", snapshot.shaderpacksDirectory().toString());
        values.put("lastError", snapshot.lastError());
        return Map.copyOf(values);
    }

    private ShaderPackDescriptor selectedPack() {
        String selected = selectedPackId;
        if (selected == null || selected.isBlank()) return null;
        return packs.stream()
                .filter(pack -> pack.id().equals(selected))
                .findFirst()
                .orElse(null);
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
            boolean renderingReady,
            String selectedPackId,
            String selectedPackName,
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
