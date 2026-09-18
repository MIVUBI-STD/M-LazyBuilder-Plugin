package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import com.halokaryamedia.lazybuilder.builder.structure.StructureBiomeSample;
import com.halokaryamedia.lazybuilder.builder.structure.StructureBlockEntity;
import com.halokaryamedia.lazybuilder.builder.structure.StructureCapture;
import com.halokaryamedia.lazybuilder.builder.structure.StructureEntity;
import com.halokaryamedia.lazybuilder.builder.structure.StructureSnapshot;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Lossless read-only capture adapter for blocks and optional schematic auxiliary payloads. */
public final class AxiomStructureSnapshotCapture {
    private AxiomStructureSnapshotCapture() {}

    public static StructureSnapshot capture(
            ClientWorld world,
            BlockBounds bounds,
            boolean includeAir,
            boolean includeBiomes,
            boolean includeEntities
    ) throws IOException {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(bounds, "bounds");

        StructureSnapshot blocksOnly = StructureCapture.capture(
                bounds,
                new AxiomClientWorldStateSource(world),
                includeAir
        );

        List<StructureBlockEntity> blockEntities = captureBlockEntities(world, bounds);
        List<StructureBiomeSample> biomes = includeBiomes
                ? captureBiomes(world, bounds)
                : List.of();
        List<StructureEntity> entities = includeEntities
                ? captureEntities(world, bounds)
                : List.of();

        return new StructureSnapshot(
                blocksOnly.blocks(),
                blockEntities,
                biomes,
                entities
        );
    }

    private static List<StructureBlockEntity> captureBlockEntities(
            ClientWorld world,
            BlockBounds bounds
    ) throws IOException {
        List<StructureBlockEntity> result = new ArrayList<>();
        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
            for (long z = bounds.minZ(); z <= (long) bounds.maxZ(); z++) {
                for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
                    BlockPos pos = new BlockPos((int) x, (int) y, (int) z);
                    BlockEntity blockEntity = world.getBlockEntity(pos);
                    if (blockEntity == null) continue;

                    NbtCompound payload = blockEntity.createNbt(world.getRegistryManager());
                    payload.remove("id");
                    payload.remove("x");
                    payload.remove("y");
                    payload.remove("z");
                    payload.remove("Pos");
                    var typeId = Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType());
                    if (typeId == null) {
                        throw new IOException("Block entity type is not registered at " + pos);
                    }
                    payload.putString("Id", typeId.toString());

                    result.add(new StructureBlockEntity(
                            Math.toIntExact(x - bounds.minX()),
                            Math.toIntExact(y - bounds.minY()),
                            Math.toIntExact(z - bounds.minZ()),
                            serialize(payload)
                    ));
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<StructureBiomeSample> captureBiomes(
            ClientWorld world,
            BlockBounds bounds
    ) {
        List<StructureBiomeSample> result =
                new ArrayList<>(Math.toIntExact(bounds.blockCount()));
        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
            for (long z = bounds.minZ(); z <= (long) bounds.maxZ(); z++) {
                for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
                    String biome = world.getBiome(new BlockPos((int) x, (int) y, (int) z))
                            .getKey()
                            .map(key -> key.getValue().toString())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Biome has no registry key at " + x + "," + y + "," + z));
                    result.add(new StructureBiomeSample(
                            Math.toIntExact(x - bounds.minX()),
                            Math.toIntExact(y - bounds.minY()),
                            Math.toIntExact(z - bounds.minZ()),
                            biome.getBytes(StandardCharsets.UTF_8)
                    ));
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<StructureEntity> captureEntities(
            ClientWorld world,
            BlockBounds bounds
    ) throws IOException {
        Box box = new Box(
                bounds.minX(),
                bounds.minY(),
                bounds.minZ(),
                (double) bounds.maxX() + 1.0,
                (double) bounds.maxY() + 1.0,
                (double) bounds.maxZ() + 1.0
        );

        List<StructureEntity> result = new ArrayList<>();
        for (var entity : world.getOtherEntities(
                null,
                box,
                entity -> !(entity instanceof PlayerEntity))) {
            NbtCompound payload = new NbtCompound();
            if (!entity.saveNbt(payload)) continue;

            payload.remove("id");
            payload.remove("Pos");
            payload.remove("UUID");
            payload.remove("UUIDMost");
            payload.remove("UUIDLeast");
            var typeId = Registries.ENTITY_TYPE.getId(entity.getType());
            if (typeId == null) {
                throw new IOException("Entity type is not registered: " + entity.getType());
            }
            payload.putString("Id", typeId.toString());

            result.add(new StructureEntity(
                    entity.getX() - bounds.minX(),
                    entity.getY() - bounds.minY(),
                    entity.getZ() - bounds.minZ(),
                    serialize(payload)
            ));
        }
        return List.copyOf(result);
    }

    private static byte[] serialize(NbtCompound compound) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.writeCompound(compound, output);
        }
        return bytes.toByteArray();
    }
}
