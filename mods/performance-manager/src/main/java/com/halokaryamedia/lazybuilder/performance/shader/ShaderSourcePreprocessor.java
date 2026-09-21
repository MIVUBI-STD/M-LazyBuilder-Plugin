package com.halokaryamedia.lazybuilder.performance.shader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal deterministic #include expansion for first-party shader compilation. */
public final class ShaderSourcePreprocessor {
    private static final Pattern INCLUDE = Pattern.compile(
            "^\\s*#include\\s+[\"<]([^\">]+)[\">]\\s*$"
    );
    private static final int MAX_DEPTH = 32;
    private static final int MAX_INCLUDE_COUNT = 256;
    private static final int MAX_EXPANDED_CHARS = 2 * 1024 * 1024;

    private ShaderSourcePreprocessor() {
    }

    public static Result preprocess(ShaderPackSource source, String entryPath) throws IOException {
        return preprocess(source, entryPath, Map.of());
    }

    public static Result preprocess(
            ShaderPackSource source,
            String entryPath,
            Map<String, String> defines
    ) throws IOException {
        Deque<String> stack = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        StringBuilder output = new StringBuilder();
        ExpansionBudget budget = new ExpansionBudget();
        expand(
                source,
                ShaderPackSource.normalizeRelativePath(entryPath),
                stack,
                visited,
                output,
                budget,
                0
        );
        return new Result(injectDefines(output.toString(), defines), Set.copyOf(visited));
    }

    private static void expand(
            ShaderPackSource source,
            String path,
            Deque<String> stack,
            Set<String> visited,
            StringBuilder output,
            ExpansionBudget budget,
            int depth
    ) throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("Shader preparation cancelled.");
        }
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
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Shader preparation cancelled.");
            }
            Matcher matcher = INCLUDE.matcher(line);
            if (!matcher.matches()) {
                append(output, line);
                append(output, "\n");
                budget.checkSize(output.length());
                continue;
            }

            String include = matcher.group(1).trim();
            String resolved = include.startsWith("/")
                    ? ShaderPackSource.normalizeRelativePath(include)
                    : ShaderPackSource.normalizeRelativePath(
                            parent.isEmpty() ? include : parent + "/" + include
                    );

            budget.include();
            append(output, "// lazybuilder include: ");
            append(output, resolved);
            append(output, "\n");
            budget.checkSize(output.length());
            expand(source, resolved, stack, visited, output, budget, depth + 1);
            append(output, "// lazybuilder end include: ");
            append(output, resolved);
            append(output, "\n");
            budget.checkSize(output.length());
        }

        stack.removeLast();
    }

    private static String injectDefines(String source, Map<String, String> defines) {
        if (source == null || source.isEmpty() || defines == null || defines.isEmpty()) return source;

        int lineEnd = source.indexOf('\n');
        if (lineEnd < 0 || !source.substring(0, lineEnd).trim().startsWith("#version")) {
            return source;
        }

        TreeMap<String, String> ordered = new TreeMap<>(defines);
        StringBuilder preamble = new StringBuilder();
        for (Map.Entry<String, String> define : ordered.entrySet()) {
            String key = define.getKey();
            String value = define.getValue();
            if (key == null || !key.matches("[A-Z_][A-Z0-9_]*")) continue;
            if (value == null || value.isBlank()) continue;
            if (!containsToken(source, key)) continue;
            preamble.append("#define ")
                    .append(key)
                    .append(' ')
                    .append(value.trim())
                    .append('\n');
        }
        if (preamble.isEmpty()) return source;

        return source.substring(0, lineEnd + 1)
                + preamble
                + source.substring(lineEnd + 1);
    }

    private static void append(StringBuilder output, String value) throws IOException {
        if (value == null || value.isEmpty()) return;
        if ((long) output.length() + value.length() > MAX_EXPANDED_CHARS) {
            throw new IOException(
                    "Expanded shader source exceeds "
                            + MAX_EXPANDED_CHARS
                            + " characters"
            );
        }
        output.append(value);
    }

    private static final class ExpansionBudget {
        private int includes;

        void include() throws IOException {
            includes++;
            if (includes > MAX_INCLUDE_COUNT) {
                throw new IOException(
                        "Shader include count exceeds " + MAX_INCLUDE_COUNT
                );
            }
        }

        void checkSize(int chars) throws IOException {
            if (chars > MAX_EXPANDED_CHARS) {
                throw new IOException(
                        "Expanded shader source exceeds "
                                + MAX_EXPANDED_CHARS
                                + " characters"
                );
            }
        }
    }

    private static boolean containsToken(String source, String token) {
        int index = source.indexOf(token);
        while (index >= 0) {
            boolean left = index == 0
                    || !(Character.isLetterOrDigit(source.charAt(index - 1))
                    || source.charAt(index - 1) == '_');
            int end = index + token.length();
            boolean right = end >= source.length()
                    || !(Character.isLetterOrDigit(source.charAt(end))
                    || source.charAt(end) == '_');
            if (left && right) return true;
            index = source.indexOf(token, index + 1);
        }
        return false;
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
