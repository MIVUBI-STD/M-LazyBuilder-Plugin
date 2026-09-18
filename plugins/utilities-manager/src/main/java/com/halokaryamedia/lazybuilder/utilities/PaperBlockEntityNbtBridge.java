package com.halokaryamedia.lazybuilder.utilities;

import com.halokaryamedia.lazybuilder.builder.wire.BuilderExtensionWireProtocol;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Narrow Paper 1.21.4 bridge for full block-entity NBT compare-and-set.
 *
 * <p>All NMS access is isolated here. Construction performs a mapping/API self-check;
 * callers must not advertise BLOCK_ENTITY authority when construction fails.</p>
 */
final class PaperBlockEntityNbtBridge {
    private final Class<?> blockPosClass;
    private final Class<?> compoundTagClass;
    private final Class<?> blockEntityClass;
    private final Constructor<?> blockPosConstructor;
    private final Method nbtRead;
    private final Method nbtWrite;
    private final Method compoundRemove;
    private final Method compoundGetString;
    private final Method compoundPutString;
    private final Method saveWithFullMetadata;
    private final Method loadWithComponents;
    private final Method setChanged;

    private PaperBlockEntityNbtBridge() throws ReflectiveOperationException {
        blockPosClass = Class.forName("net.minecraft.core.BlockPos");
        compoundTagClass = Class.forName("net.minecraft.nbt.CompoundTag");
        blockEntityClass = Class.forName(
                "net.minecraft.world.level.block.entity.BlockEntity");
        Class<?> nbtIoClass = Class.forName("net.minecraft.nbt.NbtIo");

        blockPosConstructor = blockPosClass.getConstructor(
                int.class, int.class, int.class);
        nbtRead = findStaticRead(nbtIoClass);
        nbtWrite = findStaticWrite(nbtIoClass);
        compoundRemove = compoundTagClass.getMethod("remove", String.class);
        compoundGetString = compoundTagClass.getMethod("getString", String.class);
        compoundPutString = compoundTagClass.getMethod(
                "putString", String.class, String.class);
        saveWithFullMetadata = findNamedMethod(
                blockEntityClass, "saveWithFullMetadata", 1);
        loadWithComponents = findNamedMethod(
                blockEntityClass, "loadWithComponents", 2);
        setChanged = blockEntityClass.getMethod("setChanged");
    }

    static PaperBlockEntityNbtBridge tryCreate(JavaPlugin plugin) {
        try {
            PaperBlockEntityNbtBridge bridge = new PaperBlockEntityNbtBridge();
            plugin.getLogger().info(
                    "Builder BLOCK_ENTITY authority enabled through Paper 1.21.4 NMS bridge.");
            return bridge;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Builder BLOCK_ENTITY authority disabled: NMS bridge self-check failed. "
                            + "BIOME/ENTITY authority remains available.",
                    failure
            );
            return null;
        }
    }

    ApplyResult applyCompareAndSet(
            World world,
            BuilderExtensionWireProtocol.BlockEntityMutation mutation
    ) throws Exception {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(mutation, "mutation");

        Block block = world.getBlockAt(
                mutation.x(), mutation.y(), mutation.z());
        BlockData beforeBlock = Bukkit.createBlockData(
                mutation.beforeBlockState());
        BlockData afterBlock = Bukkit.createBlockData(
                mutation.afterBlockState());

        Snapshot actual = snapshot(world, mutation.x(), mutation.y(), mutation.z());
        Snapshot before = new Snapshot(
                block.getBlockData().equals(beforeBlock),
                decodePayload(mutation.beforeNbt()));
        Snapshot after = new Snapshot(
                block.getBlockData().equals(afterBlock),
                decodePayload(mutation.afterNbt()));

        if (matches(actual, after)) {
            return new ApplyResult(ApplyState.APPLIED, "already applied");
        }
        if (!matches(actual, before)) {
            return new ApplyResult(
                    ApplyState.CONFLICT,
                    "expected block/NBT before-state but actual state differs");
        }

        block.setBlockData(afterBlock, false);
        Object desiredNbt = after.nbt();
        if (desiredNbt != null) {
            Object blockEntity = blockEntity(world, mutation.x(), mutation.y(), mutation.z());
            if (blockEntity == null) {
                return new ApplyResult(
                        ApplyState.CONFLICT,
                        "after block state did not create a block entity");
            }
            String desiredId = id(desiredNbt);
            Object actualNbt = canonicalize(save(blockEntity, world));
            if (!desiredId.equals(id(actualNbt))) {
                return new ApplyResult(
                        ApplyState.CONFLICT,
                        "block entity type mismatch after block placement");
            }
            Object loadPayload = copyForLoad(desiredNbt);
            loadWithComponents.invoke(
                    blockEntity,
                    loadPayload,
                    registryAccess(world)
            );
            setChanged.invoke(blockEntity);
            // Trigger Paper/Bukkit block-state propagation after the NMS payload load.
            block.getState(false).update(true, false);
        }

        Snapshot verified = snapshot(world, mutation.x(), mutation.y(), mutation.z());
        if (!matches(verified, after)) {
            return new ApplyResult(
                    ApplyState.CONFLICT,
                    "block entity verification failed after mutation");
        }
        return new ApplyResult(ApplyState.APPLIED, "applied");
    }

    private Snapshot snapshot(World world, int x, int y, int z) throws Exception {
        Object entity = blockEntity(world, x, y, z);
        Object nbt = entity == null ? null : canonicalize(save(entity, world));
        return new Snapshot(true, nbt);
    }

    private boolean matches(Snapshot actual, Snapshot expected) {
        return expected.blockMatches() && Objects.equals(actual.nbt(), expected.nbt());
    }

    private Object blockEntity(World world, int x, int y, int z) throws Exception {
        Object handle = world.getClass().getMethod("getHandle").invoke(world);
        Method getBlockEntity = handle.getClass().getMethod(
                "getBlockEntity", blockPosClass);
        Object pos = blockPosConstructor.newInstance(x, y, z);
        return getBlockEntity.invoke(handle, pos);
    }

    private Object registryAccess(World world) throws Exception {
        Object handle = world.getClass().getMethod("getHandle").invoke(world);
        return handle.getClass().getMethod("registryAccess").invoke(handle);
    }

    private Object save(Object blockEntity, World world) throws Exception {
        return saveWithFullMetadata.invoke(blockEntity, registryAccess(world));
    }

    private Object decodePayload(byte[] bytes) throws Exception {
        if (bytes.length == 0) return null;
        Object compound;
        try (DataInputStream input =
                     new DataInputStream(new ByteArrayInputStream(bytes))) {
            compound = nbtRead.invoke(null, input);
        }
        return canonicalize(compound);
    }

    private Object canonicalize(Object compound) throws Exception {
        String id = string(compound, "Id");
        if (id.isBlank()) id = string(compound, "id");
        compoundRemove.invoke(compound, "Id");
        compoundRemove.invoke(compound, "id");
        compoundRemove.invoke(compound, "x");
        compoundRemove.invoke(compound, "y");
        compoundRemove.invoke(compound, "z");
        compoundRemove.invoke(compound, "Pos");
        if (!id.isBlank()) compoundPutString.invoke(compound, "Id", id);
        return compound;
    }

    private Object copyForLoad(Object canonical) throws Exception {
        byte[] encoded;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            nbtWrite.invoke(null, canonical, output);
            encoded = bytes.toByteArray();
        }
        Object copy;
        try (DataInputStream input =
                     new DataInputStream(new ByteArrayInputStream(encoded))) {
            copy = nbtRead.invoke(null, input);
        }
        compoundRemove.invoke(copy, "Id");
        return copy;
    }

    private String id(Object canonical) throws Exception {
        return string(canonical, "Id");
    }

    private String string(Object compound, String key) throws Exception {
        Object value = compoundGetString.invoke(compound, key);
        return value == null ? "" : value.toString();
    }

    private static Method findStaticRead(Class<?> nbtIoClass)
            throws ReflectiveOperationException {
        for (Method method : nbtIoClass.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) continue;
            if (!method.getReturnType().getName()
                    .equals("net.minecraft.nbt.CompoundTag")) continue;
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 1
                    && DataInput.class.isAssignableFrom(parameters[0])) {
                return method;
            }
        }
        throw new NoSuchMethodException(
                "No NbtIo CompoundTag read(DataInput) method found");
    }

    private static Method findStaticWrite(Class<?> nbtIoClass)
            throws ReflectiveOperationException {
        for (Method method : nbtIoClass.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) continue;
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 2
                    && parameters[0].getName().equals("net.minecraft.nbt.CompoundTag")
                    && DataOutput.class.isAssignableFrom(parameters[1])) {
                return method;
            }
        }
        throw new NoSuchMethodException(
                "No NbtIo write(CompoundTag, DataOutput) method found");
    }

    private static Method findNamedMethod(
            Class<?> owner,
            String name,
            int parameterCount
    ) throws NoSuchMethodException {
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        throw new NoSuchMethodException(
                owner.getName() + "#" + name + "/" + parameterCount);
    }

    enum ApplyState {
        APPLIED,
        CONFLICT
    }

    record ApplyResult(ApplyState state, String detail) {}

    private record Snapshot(boolean blockMatches, Object nbt) {}
}
