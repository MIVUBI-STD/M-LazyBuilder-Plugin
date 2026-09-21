package com.halokaryamedia.lazybuilder.performance.shader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal deterministic #include expansion for first-party shader compilation. */
public final class ShaderSourcePreprocessor {
    private static final Pattern INCLUDE = Pattern.compile(
            "^\\s*#include\\s+[\"<]([^\">]+)[\">]\\s*$"
    );
    private static final int MAX_DEPTH = 32;

    private ShaderSourcePreprocessor() {
    }

    public static Result preprocess(ShaderPackSource source, String entryPath) throws IOException {
        Deque<String> stack = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        StringBuilder output = new StringBuilder();
        expand(source, ShaderPackSource.normalizeRelativePath(entryPath), stack, visited, output, 0);
        return new Result(output.toString(), Set.copyOf(visited));
    }

    private static void expand(
            ShaderPackSource source,
            String path,
            Deque<String> stack,
            Set<String> visited,
            StringBuilder output,
            int depth
    ) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("Shader include depth exceeds " + MAX_DEPTH + ": " + path);
        }
        if (stack.contains(path)) {
            throw new IOException("Shader include cycle: " + cycle(stack, path));
        }

        stack.addLast(path);
        visited.add(path);

        String text = source.readText(path);
        String parent = parent(path);
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        for (String line : lines) {
            Matcher matcher = INCLUDE.matcher(line);
            if (!matcher.matches()) {
                output.append(line).append('\n');
                continue;
            }

            String include = matcher.group(1).trim();
            String resolved = include.startsWith("/")
                    ? ShaderPackSource.normalizeRelativePath(include)
                    : ShaderPackSource.normalizeRelativePath(
                            parent.isEmpty() ? include : parent + "/" + include
                    );

            output.append("// lazybuilder include: ").append(resolved).append('\n');
            expand(source, resolved, stack, visited, output, depth + 1);
            output.append("// lazybuilder end include: ").append(resolved).append('\n');
        }

        stack.removeLast();
    }

    private static String parent(String path) {
        Path parent = Path.of(path).getParent();
        return parent == null ? "" : parent.toString().replace('\\', '/');
    }

    private static String cycle(Deque<String> stack, String next) {
        StringBuilder text = new StringBuilder();
        for (String value : stack) {
            if (!text.isEmpty()) text.append(" -> ");
            text.append(value);
        }
        if (!text.isEmpty()) text.append(" -> ");
        text.append(next);
        return text.toString();
    }

    public record Result(String source, Set<String> dependencies) {
    }
}
