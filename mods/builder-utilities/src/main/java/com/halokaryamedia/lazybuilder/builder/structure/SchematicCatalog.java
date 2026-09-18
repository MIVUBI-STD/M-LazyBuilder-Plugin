package com.halokaryamedia.lazybuilder.builder.structure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Deterministic directory-backed Sponge schematic catalog. */
public final class SchematicCatalog {
    private final Path directory;

    public SchematicCatalog(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    public Path directory() {
        return directory;
    }

    public List<Entry> list() throws IOException {
        Files.createDirectories(directory);
        try (var paths = Files.list(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".schem"))
                    .sorted((a, b) -> a.getFileName().toString()
                            .compareToIgnoreCase(b.getFileName().toString()))
                    .map(path -> new Entry(path.getFileName().toString(), path))
                    .toList();
        }
    }

    public SpongeSchematicImport load(Entry entry) throws IOException {
        Objects.requireNonNull(entry, "entry");
        if (!entry.path().normalize().getParent().equals(directory.normalize())) {
            throw new IllegalArgumentException("schematic entry is outside catalog directory");
        }
        return SpongeSchematicV3Importer.importCompressed(entry.path());
    }

    public record Entry(String name, Path path) {
        public Entry {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("schematic name must be non-blank");
            }
            Objects.requireNonNull(path, "path");
        }
    }
}
