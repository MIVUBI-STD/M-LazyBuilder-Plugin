package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.structure.EntityExtensionPayload;
import com.halokaryamedia.lazybuilder.builder.structure.StructureEntityStateSource;
import com.halokaryamedia.lazybuilder.builder.structure.StructurePlacement;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

import java.io.IOException;
import java.util.Objects;

/**
 * Conservative placement-only entity source. Any non-player entity occupying the
 * target slot is an explicit conflict; Builder never replaces arbitrary entities.
 */
public final class AxiomEntityPlacementStateSource
        implements StructureEntityStateSource {
    private static final double EPSILON = 0.125;

    private final ClientWorld world;

    public AxiomEntityPlacementStateSource(ClientWorld world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    @Override
    public byte[] read(
            long entityKey,
            StructurePlacement.WorldPositionD position
    ) throws IOException {
        Box box = new Box(
                position.x() - EPSILON,
                position.y() - EPSILON,
                position.z() - EPSILON,
                position.x() + EPSILON,
                position.y() + EPSILON,
                position.z() + EPSILON
        );
        var occupied = world.getOtherEntities(
                null,
                box,
                entity -> !(entity instanceof PlayerEntity));
        if (!occupied.isEmpty()) {
            throw new IllegalArgumentException(
                    "Entity target slot is occupied near "
                            + position.x() + "," + position.y() + "," + position.z());
        }
        return EntityExtensionPayload.absent(
                position.x(), position.y(), position.z()).encode();
    }
}
