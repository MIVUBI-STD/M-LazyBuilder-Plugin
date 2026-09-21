package com.halokaryamedia.lazybuilder.performance.shader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Read-only normalized source access for folder and ZIP shader packs. */
public interface ShaderPackSource {
    boolean exists(String relativePath) throws IOException;

    String readText(String relativePath) throws IOException;

    static ShaderPackSource open(ShaderPackDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return descriptor.kind() == ShaderPackDescriptor.Kind.ZIP
                ? new ZipSource(descriptor.path())
                : new DirectorySource(descriptor.path());
    }

    static String normalizeRelativePath(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Shader source path is blank");
        }

        String normalized = value.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);

        Path path = Path.of(normalized).normalize();
        if (path.isAbsolute() || path.startsWith("..")) {
            throw new IllegalArgumentException("Shader source path escapes the pack");
        }

        String result = path.toString().replace('\\', '/');
        if (result.isBlank() || result.equals(".")) {
            throw new IllegalArgumentException("Shader source path is invalid");
        }
        return result;
    }

    final class DirectorySource implements ShaderPackSource {
        private final Path root;

        DirectorySource(Path root) {
            this.root = root.toAbsolutePath().normalize();
        }

        @Override
        public boolean exists(String relativePath) {
            Path resolved = resolve(relativePath);
            return Files.isRegularFile(resolved);
        }

        @Override
        public String readText(String relativePath) throws IOException {
            return Files.readString(resolve(relativePath), StandardCharsets.UTF_8);
        }

        private Path resolve(String relativePath) {
            Path resolved = root.resolve(normalizeRelativePath(relativePath)).normalize();
            if (!resolved.startsWith(root)) {
                throw new IllegalArgumentException("Shader source path escapes the pack");
            }
            return resolved;
        }
    }

    final class ZipSource implements ShaderPackSource {
        private static final int MAX_ENTRY_BYTES = 4 * 1024 * 1024;
        private final Path zip;

        ZipSource(Path zip) {
            this.zip = zip;
        }

        @Override
        public boolean exists(String relativePath) throws IOException {
            return read(relativePath, false) != null;
        }

        @Override
        public String readText(String relativePath) throws IOException {
            byte[] bytes = read(relativePath, true);
            if (bytes == null) throw new IOException("Missing shader source: " + relativePath);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        private byte[] read(String relativePath, boolean capture) throws IOException {
            String target = normalizeRelativePath(relativePath);
            try (ZipFile file = new ZipFile(zip.toFile())) {
                ZipEntry entry = file.getEntry(target);
                if (entry == null || entry.isDirectory()) return null;
                if (!capture) return new byte[0];

                long declaredSize = entry.getSize();
                if (declaredSize > MAX_ENTRY_BYTES) {
                    throw new IOException("Shader source exceeds size limit: " + target);
                }

                try (InputStream input = file.getInputStream(entry)) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream(
                            declaredSize > 0 && declaredSize <= Integer.MAX_VALUE
                                    ? (int) declaredSize
                                    : 8192
                    );
                    byte[] buffer = new byte[8192];
                    int total = 0;
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        total += read;
                        if (total > MAX_ENTRY_BYTES) {
                            throw new IOException("Shader source exceeds size limit: " + target);
                        }
                        output.write(buffer, 0, read);
                    }
                    return output.toByteArray();
                }
            }
        }
    }
}
