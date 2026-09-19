package com.halokaryamedia.lazybuilder.world.files;

import java.util.Locale;
import java.util.Objects;

/** One portable filename policy for transfer/import/export artifact names. */
public final class SafeArtifactName {
    private static final int MAX_FILE_NAME_CHARS = 255;

    private SafeArtifactName() {}

    public static String requirePortable(String value, String label) {
        String safeLabel = Objects.requireNonNull(label, "label");
        String name = Objects.requireNonNull(value, safeLabel);

        if (name.isBlank()
                || !name.equals(name.strip())
                || name.equals(".")
                || name.equals("..")
                || name.length() > MAX_FILE_NAME_CHARS
                || name.indexOf('/') >= 0
                || name.indexOf('\\') >= 0
                || name.indexOf(':') >= 0
                || name.endsWith(".")
                || name.endsWith(" ")
                || name.chars().anyMatch(Character::isISOControl)
                || isWindowsReserved(name)) {
            throw new IllegalArgumentException(safeLabel + " must be one portable safe file name");
        }
        return name;
    }

    private static boolean isWindowsReserved(String value) {
        String upper = value.toUpperCase(Locale.ROOT);
        int dot = upper.indexOf('.');
        String base = dot < 0 ? upper : upper.substring(0, dot);
        if (base.equals("CON")
                || base.equals("PRN")
                || base.equals("AUX")
                || base.equals("NUL")
                || base.equals("CONIN$")
                || base.equals("CONOUT$")) {
            return true;
        }
        if (base.length() == 4 && (base.startsWith("COM") || base.startsWith("LPT"))) {
            char suffix = base.charAt(3);
            return suffix >= '1' && suffix <= '9';
        }
        return false;
    }
}
