package com.halokaryamedia.lazybuilder.performance.shader;

/**
 * Lightweight GLSL lexical helper used by source contracts.
 *
 * It removes line/block comments while preserving newlines. This is intentionally
 * not a GLSL parser; it only prevents commented-out declarations from satisfying
 * required runtime contracts.
 */
final class ShaderSourceSyntax {
    private ShaderSourceSyntax() {
    }

    static String codeOnly(String source) {
        if (source == null || source.isEmpty()) return "";

        StringBuilder output = new StringBuilder(source.length());
        boolean lineComment = false;
        boolean blockComment = false;

        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';

            if (lineComment) {
                if (current == '\n') {
                    lineComment = false;
                    output.append('\n');
                } else {
                    output.append(' ');
                }
                continue;
            }

            if (blockComment) {
                if (current == '*' && next == '/') {
                    output.append("  ");
                    index++;
                    blockComment = false;
                } else if (current == '\n') {
                    output.append('\n');
                } else {
                    output.append(' ');
                }
                continue;
            }

            if (current == '/' && next == '/') {
                output.append("  ");
                index++;
                lineComment = true;
                continue;
            }

            if (current == '/' && next == '*') {
                output.append("  ");
                index++;
                blockComment = true;
                continue;
            }

            output.append(current);
        }

        return output.toString();
    }

    static boolean startsWithVersion(String source) {
        String code = codeOnly(source).stripLeading();
        return code.startsWith("#version");
    }
}
