package com.halokaryamedia.lazybuilder.builder.axiom;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;

import java.util.Objects;

/** Canonical Minecraft 1.21.4 block-state string codec used by History v2 and Axiom dispatch. */
public final class AxiomBlockStateCodec {
    private final RegistryWrapper.Impl<Block> blockRegistry;

    public AxiomBlockStateCodec(ClientWorld world) {
        Objects.requireNonNull(world, "world");
        this.blockRegistry = world.getRegistryManager().getOrThrow(RegistryKeys.BLOCK);
    }

    public String encode(BlockState state) {
        return BlockArgumentParser.stringifyBlockState(Objects.requireNonNull(state, "state"));
    }

    public BlockState decode(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            throw new IllegalArgumentException("serialized block state must be non-blank");
        }
        try {
            return BlockArgumentParser.block(blockRegistry, serialized, false).blockState();
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException("Invalid Minecraft block state: " + serialized, e);
        }
    }
}
