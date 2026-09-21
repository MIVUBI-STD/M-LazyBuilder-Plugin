package com.halokaryamedia.lazybuilder.performance.compatibility;

import java.util.ArrayList;
import java.util.List;

import com.halokaryamedia.lazybuilder.performance.compatibility.OptimizationCompatibility.OptimizationDomain;

/** Truthful readiness summary for running the core renderer without migration-source mods. */
public final class FirstPartyRendererReadiness {
    private FirstPartyRendererReadiness() {
    }

    public static Snapshot evaluate(
            RendererCompatibility.Snapshot renderer,
            OptimizationCompatibility.Policy policy
    ) {
        List<String> blockers = new ArrayList<>();

        if (renderer == null || renderer.uncertain()) {
            blockers.add("renderer-ownership-uncertain");
        } else if (renderer.customRendererPresent()) {
            blockers.add("custom-renderer-owner:" + renderer.ownerSummary());
        }

        if (policy == null) {
            blockers.add("optimization-policy-unavailable");
        } else {
            for (OptimizationDomain domain : List.of(
                    OptimizationDomain.TERRAIN_BUILD,
                    OptimizationDomain.TERRAIN_UPLOAD,
                    OptimizationDomain.TERRAIN_SUBMISSION,
                    OptimizationDomain.IMMEDIATE_RENDERING,
                    OptimizationDomain.TEXT_RENDERING,
                    OptimizationDomain.MODEL_MEMORY
            )) {
                if (!policy.owns(domain)) {
                    blockers.add(domain.name().toLowerCase() + ":" + policy.decision(domain).owner());
                }
            }
        }

        return new Snapshot(blockers.isEmpty(), List.copyOf(blockers));
    }

    public record Snapshot(boolean ready, List<String> blockers) {
        public Snapshot {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }

        public String status() {
            return ready ? "first-party-ready" : String.join(",", blockers);
        }
    }
}
