package com.halokaryamedia.lazybuilder.performance.compatibility;

import java.util.EnumMap;
import java.util.Map;

/**
 * Central capability ownership policy for first-party performance hooks.
 *
 * Decisions are made per optimization domain instead of scattering mod-id checks across individual
 * mixins. This keeps compatibility conservative while allowing unrelated first-party optimizations
 * to remain active.
 */
public final class OptimizationCompatibility {
    private OptimizationCompatibility() {
    }

    public static Policy evaluate(
            RendererCompatibility.Snapshot renderer,
            boolean immediatelyFastPresent,
            boolean ferriteCorePresent,
            boolean entityCullingPresent
    ) {
        RendererCompatibility.Snapshot safeRenderer = renderer == null
                ? new RendererCompatibility.Snapshot(false, true, java.util.List.of())
                : renderer;

        EnumMap<OptimizationDomain, Decision> decisions = new EnumMap<>(OptimizationDomain.class);
        for (OptimizationDomain domain : OptimizationDomain.values()) {
            decisions.put(domain, Decision.lazyBuilder());
        }

        if (immediatelyFastPresent) {
            decisions.put(OptimizationDomain.IMMEDIATE_RENDERING, Decision.external("immediatelyfast"));
            decisions.put(OptimizationDomain.TEXT_RENDERING, Decision.external("immediatelyfast"));
            decisions.put(OptimizationDomain.TERRAIN_UPLOAD, Decision.external("immediatelyfast"));
        }

        if (entityCullingPresent) {
            decisions.put(OptimizationDomain.ENTITY_CULLING, Decision.external("entityculling"));
        }

        if (!safeRenderer.firstPartyChunkPipelineSafe()) {
            String owner = safeRenderer.ownerSummary();
            decisions.put(OptimizationDomain.TERRAIN_BUILD, Decision.external(owner));
            decisions.put(OptimizationDomain.TERRAIN_UPLOAD, Decision.external(owner));
        }

        if (!safeRenderer.terrainSubmissionSafe()) {
            decisions.put(OptimizationDomain.TERRAIN_SUBMISSION, Decision.external(safeRenderer.ownerSummary()));
        }

        if (ferriteCorePresent) {
            decisions.put(OptimizationDomain.MODEL_MEMORY, Decision.external("ferritecore"));
        }

        return new Policy(Map.copyOf(decisions));
    }

    public enum OptimizationDomain {
        TERRAIN_BUILD,
        TERRAIN_UPLOAD,
        TERRAIN_SUBMISSION,
        IMMEDIATE_RENDERING,
        TEXT_RENDERING,
        ENTITY_CULLING,
        MODEL_MEMORY
    }

    public record Decision(boolean lazyBuilderOwned, String owner, String reason) {
        public Decision {
            owner = owner == null || owner.isBlank() ? "unknown" : owner;
            reason = reason == null ? "" : reason;
        }

        static Decision lazyBuilder() {
            return new Decision(true, "lazybuilder", "first-party");
        }

        static Decision external(String owner) {
            return new Decision(false, owner, "external-owner");
        }
    }

    public record Policy(Map<OptimizationDomain, Decision> decisions) {
        public Policy {
            decisions = decisions == null ? Map.of() : Map.copyOf(decisions);
        }

        public Decision decision(OptimizationDomain domain) {
            return decisions.getOrDefault(domain, Decision.lazyBuilder());
        }

        public boolean owns(OptimizationDomain domain) {
            return decision(domain).lazyBuilderOwned();
        }

        public String summary(OptimizationDomain domain) {
            Decision decision = decision(domain);
            return domain.name().toLowerCase() + "=" + decision.owner();
        }
    }
}
