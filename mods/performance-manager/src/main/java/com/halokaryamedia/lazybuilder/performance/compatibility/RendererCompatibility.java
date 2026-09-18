package com.halokaryamedia.lazybuilder.performance.compatibility;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects renderer ownership using the same metadata signal Fabric Indigo uses to stand down.
 *
 * Compatibility uncertainty disables first-party chunk ownership rather than guessing that the
 * vanilla/Indigo renderer is active.
 */
public final class RendererCompatibility {
    public static final String CUSTOM_RENDERER_MARKER = "fabric-renderer-api-v1:contains_renderer";
    private static volatile Snapshot cached;

    private RendererCompatibility() {
    }

    public static Snapshot detect() {
        Snapshot snapshot = cached;
        if (snapshot != null) return snapshot;

        FabricLoader loader = FabricLoader.getInstance();
        boolean irisPresent = loader.isModLoaded("iris");

        try {
            List<String> rendererOwners = loader.getAllMods().stream()
                    .filter(RendererCompatibility::declaresCustomRenderer)
                    .map(mod -> mod.getMetadata().getId())
                    .toList();
            snapshot = new Snapshot(irisPresent, false, rendererOwners);
        } catch (RuntimeException ignored) {
            snapshot = new Snapshot(irisPresent, true, List.of());
        }
        cached = snapshot;
        return snapshot;
    }

    private static boolean declaresCustomRenderer(ModContainer mod) {
        return mod != null
                && mod.getMetadata() != null
                && mod.getMetadata().containsCustomValue(CUSTOM_RENDERER_MARKER);
    }

    public record Snapshot(
            boolean irisPresent,
            boolean uncertain,
            List<String> rendererOwners
    ) {
        public Snapshot {
            List<String> normalized = rendererOwners == null
                    ? new ArrayList<>()
                    : new ArrayList<>(rendererOwners);
            normalized.removeIf(id -> id == null || id.isBlank());
            normalized.sort(String::compareTo);
            rendererOwners = List.copyOf(normalized);
        }

        public boolean customRendererPresent() {
            return !rendererOwners.isEmpty();
        }

        public boolean firstPartyChunkPipelineSafe() {
            return !uncertain && !customRendererPresent();
        }

        public boolean terrainSubmissionSafe() {
            return firstPartyChunkPipelineSafe() && !irisPresent;
        }

        public String ownerSummary() {
            if (uncertain) {
                return irisPresent ? "iris+compatibility-uncertain" : "compatibility-uncertain";
            }

            String renderer = rendererOwners.isEmpty()
                    ? "fabric-indigo"
                    : String.join("+", rendererOwners);
            return irisPresent ? "iris+" + renderer : renderer;
        }
    }
}
