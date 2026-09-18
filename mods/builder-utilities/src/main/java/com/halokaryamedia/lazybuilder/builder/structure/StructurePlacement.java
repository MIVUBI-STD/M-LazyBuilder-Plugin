package com.halokaryamedia.lazybuilder.builder.structure;

/**
 * Integer-safe structure placement using quarter-turn Y rotations and X/Z mirrors.
 * The anchor is the world position of local (0,0,0).
 */
public record StructurePlacement(
        int anchorX,
        int anchorY,
        int anchorZ,
        int quarterTurnsY,
        boolean mirrorX,
        boolean mirrorZ
) {
    public StructurePlacement {
        quarterTurnsY = Math.floorMod(quarterTurnsY, 4);
    }

    public WorldPosition transform(int localX, int localY, int localZ) {
        int x = mirrorX ? Math.negateExact(localX) : localX;
        int z = mirrorZ ? Math.negateExact(localZ) : localZ;

        int rx;
        int rz;
        switch (quarterTurnsY) {
            case 0 -> { rx = x; rz = z; }
            case 1 -> { rx = Math.negateExact(z); rz = x; }
            case 2 -> { rx = Math.negateExact(x); rz = Math.negateExact(z); }
            case 3 -> { rx = z; rz = Math.negateExact(x); }
            default -> throw new AssertionError("normalized quarterTurnsY");
        }

        return new WorldPosition(
                Math.addExact(anchorX, rx),
                Math.addExact(anchorY, localY),
                Math.addExact(anchorZ, rz)
        );
    }

    public record WorldPosition(int x, int y, int z) {}
}
