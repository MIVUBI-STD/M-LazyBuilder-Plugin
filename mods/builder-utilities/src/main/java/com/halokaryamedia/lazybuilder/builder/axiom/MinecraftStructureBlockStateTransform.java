package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.structure.BlockStateTransform;
import com.halokaryamedia.lazybuilder.builder.structure.StructurePlacement;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;

import java.util.Objects;

/**
 * Minecraft-aware structure state transform for facing/axis-sensitive blocks.
 * Coordinate transforms and BlockState transforms use the same mirror/quarter-turn contract.
 */
public final class MinecraftStructureBlockStateTransform implements BlockStateTransform {
    private final AxiomBlockStateCodec codec;

    public MinecraftStructureBlockStateTransform(ClientWorld world) {
        this.codec = new AxiomBlockStateCodec(Objects.requireNonNull(world, "world"));
    }

    @Override
    public String transform(String blockState, StructurePlacement placement) {
        Objects.requireNonNull(placement, "placement");
        BlockState state = codec.decode(blockState);

        if (placement.mirrorX()) {
            state = state.mirror(BlockMirror.FRONT_BACK);
        }
        if (placement.mirrorZ()) {
            state = state.mirror(BlockMirror.LEFT_RIGHT);
        }

        state = state.rotate(rotation(placement.quarterTurnsY()));
        return codec.encode(state);
    }

    static BlockRotation rotation(int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> BlockRotation.NONE;
            case 1 -> BlockRotation.CLOCKWISE_90;
            case 2 -> BlockRotation.CLOCKWISE_180;
            case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> throw new AssertionError();
        };
    }
}
