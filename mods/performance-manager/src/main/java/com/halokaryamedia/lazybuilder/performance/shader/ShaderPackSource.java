package com.halokaryamedia.lazybuilder.performance.shader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Read-only normalized source access for folder and ZIP shader packs. */
public interface ShaderPackSource {
    int MAX_SOURCE_BYTES = 4 * 1024 * 1024;

    boolean exists(String relativePath) throws IOException;

    String readText(String relativePath) throws IOException;

    static ShaderPackSource open(ShaderPackDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return descriptor.kind() == ShaderPackDescriptor.Kind.ZIP
                ? new ZipSource(descriptor.path())
                : new DirectorySource(descriptor.path());
    }

    static Session openSession(ShaderPackDescriptor descriptor) throws IOException {
        Objects.requireNonNull(descriptor, "descriptor");
        return descriptor.kind() == ShaderPackDescriptor.Kind.ZIP
                ? new ZipSession(descriptor.path())
                : new DirectorySession(descriptor.path());
    }

    interface Session extends ShaderPackSource, AutoCloseable {
        @Override
        void close() throws IOException;
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
        private final Map<String, String> textCache = new HashMap<>();

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
            String normalized = normalizeRelativePath(relativePath);
            String cached = textCache.get(normalized);
            if (cached != null) return cached;

            Path resolved = resolve(normalized);
            long size = Files.size(resolved);
            if (size > MAX_SOURCE_BYTES) {
                throw new IOException("Shader source exceeds size limit: " + relativePath);
            }
            String text = Files.readString(resolved, StandardCharsets.UTF_8);
            textCache.put(normalized, text);
            return text;
        }

        private Path resolve(String relativePath) {
            Path resolved = root.resolve(normalizeRelativePath(relativePath)).normalize();
            if (!resolved.startsWith(root)) {
                throw new IllegalArgumentException("Shader source path escapes the pack");
            }
            return resolved;
        }
    }

    final class DirectorySession implements Session {
        private final Path root;
        private final Map<String, String> textCache = new HashMap<>();

        DirectorySession(Path root) {
            this.root = root.toAbsolutePath().normalize();
        }

        @Override
        public boolean exists(String relativePath) {
            return Files.isRegularFile(resolve(relativePath));
        }

        @Override
        public String readText(String relativePath) throws IOException {
            String normalized = normalizeRelativePath(relativePath);
            String cached = textCache.get(normalized);
            if (cached != null) return cached;

            Path resolved = resolve(normalized);
            long size = Files.size(resolved);
            if (size > MAX_SOURCE_BYTES) {
                throw new IOException("Shader source exceeds size limit: " + relativePath);
            }
            String text = Files.readString(resolved, StandardCharsets.UTF_8);
            textCache.put(normalized, text);
            return text;
        }

        private Path resolve(String relativePath) {
            Path resolved = root.resolve(normalizeRelativePath(relativePath)).normalize();
            if (!resolved.startsWith(root)) {
                throw new IllegalArgumentException("Shader source path escapes the pack");
            }
            return resolved;
        }

        @Override
        public void close() {
            textCache.clear();
        }
    }

    final class ZipSession implements Session {
        private final ZipFile file;
        private final Map<String, String> textCache = new HashMap<>();

        ZipSession(Path zip) throws IOException {
            this.file = new ZipFile(zip.toFile());
        }

        @Override
        public boolean exists(String relativePath) {
            ZipEntry entry = file.getEntry(normalizeRelativePath(relativePath));
            return entry != null && !entry.isDirectory();
        }

        @Override
        public String readText(String relativePath) throws IOException {
            String target = normalizeRelativePath(relativePath);
            String cached = textCache.get(target);
            if (cached != null) return cached;

            ZipEntry entry = file.getEntry(target);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("Missing shader source: " + relativePath);
            }

            long declaredSize = entry.getSize();
            if (declaredSize > MAX_SOURCE_BYTES) {
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
                    if (total > MAX_SOURCE_BYTES) {
                        throw new IOException("Shader source exceeds size limit: " + target);
                    }
                    output.write(buffer, 0, read);
                }
                String text = output.toString(StandardCharsets.UTF_8);
                textCache.put(target, text);
                return text;
            }
        }

        @Override
        public void close() throws IOException {
            textCache.clear();
            file.close();
        }
    }

    final class ZipSource implements ShaderPackSource {
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
                if (declaredSize > MAX_SOURCE_BYTES) {
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
                        if (total > MAX_SOURCE_BYTES) {
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
