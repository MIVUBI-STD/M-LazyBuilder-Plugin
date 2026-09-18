package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.mutation.ChunkDispatchEntry;
import com.halokaryamedia.lazybuilder.builder.mutation.ChunkDispatchPlan;
import com.halokaryamedia.lazybuilder.builder.mutation.ChunkDispatchPlanner;
import com.moulberry.axiomclientapi.regions.BlockRegion;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;

/**
 * Dispatches a prepared block-only chunk through Axiom's public mutation path.
 * Completion means Axiom accepted the region for mutation; world application is
 * reconciled separately because Axiom owns the actual edit lifecycle.
 */
public final class AxiomChunkMutationDispatcher {
    private final AxiomClientServices services;
    private final ClientWorld world;
    private final AxiomBlockStateCodec codec;

    public AxiomChunkMutationDispatcher(AxiomClientServices services, ClientWorld world) {
        this.services = Objects.requireNonNull(services, "services");
        this.world = Objects.requireNonNull(world, "world");
        this.codec = new AxiomBlockStateCodec(world);
    }

    public AxiomChunkDispatchResult dispatch(ChunkChangeSet chunk) {
        Objects.requireNonNull(chunk, "chunk");
        BlockPos.Mutable readPosition = new BlockPos.Mutable();
        ChunkDispatchPlan plan = ChunkDispatchPlanner.plan(
                chunk,
                (x, y, z) -> codec.encode(world.getBlockState(readPosition.set(x, y, z)))
        );
        return switch (plan.state()) {
            case ALREADY_APPLIED -> AxiomChunkDispatchResult.alreadyApplied();
            case CONFLICT -> AxiomChunkDispatchResult.conflict(
                    plan.conflictX(), plan.conflictY(), plan.conflictZ());
            case READY -> dispatchReady(plan);
        };
    }

    private AxiomChunkDispatchResult dispatchReady(ChunkDispatchPlan plan) {
        BlockRegion region = services.regionProvider().createBlock();
        for (ChunkDispatchEntry entry : plan.entries()) {
            region.addBlock(
                    entry.worldX(),
                    entry.y(),
                    entry.worldZ(),
                    codec.decode(entry.afterState())
            );
        }
        services.toolService().pushBlockRegionChange(region);
        return AxiomChunkDispatchResult.dispatched(plan.entries().size());
    }
}
