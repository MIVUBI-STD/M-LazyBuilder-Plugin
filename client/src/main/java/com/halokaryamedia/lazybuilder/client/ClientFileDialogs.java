package com.halokaryamedia.lazybuilder.client;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Native file dialog adapter. Dialog work exists only while the user requested it. */
public final class ClientFileDialogs {
    private ClientFileDialogs() {}

    public static CompletableFuture<Optional<Path>> chooseImport() {
        return CompletableFuture.supplyAsync(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(2);
                filters.put(stack.UTF8("*.zip"));
                filters.put(stack.UTF8("*.mcworld"));
                filters.flip();
                String selected = TinyFileDialogs.tinyfd_openFileDialog(
                        "Import Minecraft World", null, filters, "Minecraft worlds", false);
                return selected == null ? Optional.empty() : Optional.of(Path.of(selected));
            }
        });
    }

    public static CompletableFuture<Optional<Path>> chooseExportDestination(String suggestedName) {
        return CompletableFuture.supplyAsync(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(2);
                filters.put(stack.UTF8("*.zip"));
                filters.put(stack.UTF8("*.mcworld"));
                filters.flip();
                String selected = TinyFileDialogs.tinyfd_saveFileDialog(
                        "Save LazyBuilder Export", suggestedName, filters, "Minecraft worlds");
                return selected == null ? Optional.empty() : Optional.of(Path.of(selected));
            }
        });
    }
}
