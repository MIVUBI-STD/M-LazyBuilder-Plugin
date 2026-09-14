package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native file dialog adapter. Dialog work exists only while the user requested it. */
public final class ClientFileDialogs {
    private static final ExecutorService DIALOG_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "LazyBuilder-File-Dialog");
        thread.setDaemon(true);
        return thread;
    });

    private ClientFileDialogs() {}

    public static CompletableFuture<Optional<Path>> chooseImport() {
        return completeOnClient(CompletableFuture.supplyAsync(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(2);
                filters.put(stack.UTF8("*.zip"));
                filters.put(stack.UTF8("*.mcworld"));
                filters.flip();
                String selected = TinyFileDialogs.tinyfd_openFileDialog(
                        "Import Minecraft World", null, filters, "Minecraft worlds", false);
                return selected == null ? Optional.empty() : Optional.of(Path.of(selected));
            }
        }, DIALOG_EXECUTOR));
    }

    public static CompletableFuture<Optional<Path>> chooseExportDestination(String suggestedName) {
        return completeOnClient(CompletableFuture.supplyAsync(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(2);
                filters.put(stack.UTF8("*.zip"));
                filters.put(stack.UTF8("*.mcworld"));
                filters.flip();
                String selected = TinyFileDialogs.tinyfd_saveFileDialog(
                        "Save LazyBuilder Export", suggestedName, filters, "Minecraft worlds");
                return selected == null ? Optional.empty() : Optional.of(Path.of(selected));
            }
        }, DIALOG_EXECUTOR));
    }

    public static void shutdown() {
        DIALOG_EXECUTOR.shutdown();
    }

    private static CompletableFuture<Optional<Path>> completeOnClient(
            CompletableFuture<Optional<Path>> future
    ) {
        return future.thenApplyAsync(value -> value, MinecraftClient.getInstance()::execute);
    }
}
