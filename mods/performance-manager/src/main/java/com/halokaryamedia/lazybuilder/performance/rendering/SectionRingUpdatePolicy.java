package com.halokaryamedia.lazybuilder.performance.rendering;

/** Pure toroidal-section mapping used to update only changed BuiltChunkStorage strips. */
public final class SectionRingUpdatePolicy {
    private SectionRingUpdatePolicy() {
    }

    public static int mappedCoordinate(
            int storageIndex,
            int cameraSection,
            int viewDistance,
            int storageSize
    ) {
        int start = cameraSection - viewDistance;
        return start + Math.floorMod(storageIndex - start, storageSize);
    }

    public static boolean mappingChanged(
            int storageIndex,
            int oldCameraSection,
            int newCameraSection,
            int viewDistance,
            int storageSize
    ) {
        return mappedCoordinate(storageIndex, oldCameraSection, viewDistance, storageSize)
                != mappedCoordinate(storageIndex, newCameraSection, viewDistance, storageSize);
    }

    public static int changedIndexCount(
            int oldCameraSection,
            int newCameraSection,
            int viewDistance,
            int storageSize
    ) {
        int changed = 0;
        for (int index = 0; index < storageSize; index++) {
            if (mappingChanged(index, oldCameraSection, newCameraSection, viewDistance, storageSize)) {
                changed++;
            }
        }
        return changed;
    }

    public static int uniqueChangedColumns(int changedX, int changedZ, int sizeX, int sizeZ) {
        return changedX * sizeZ + changedZ * sizeX - changedX * changedZ;
    }
}
