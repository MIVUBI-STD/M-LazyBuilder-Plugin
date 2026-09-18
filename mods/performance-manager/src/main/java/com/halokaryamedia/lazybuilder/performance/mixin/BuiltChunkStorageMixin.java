package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.PerformanceManagerClient;
import com.halokaryamedia.lazybuilder.performance.rendering.ChunkPipelineMetrics;
import com.halokaryamedia.lazybuilder.performance.rendering.SectionRingUpdatePolicy;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remaps only toroidal storage strips whose world-section ownership changes as the camera moves. */
@Mixin(BuiltChunkStorage.class)
abstract class BuiltChunkStorageMixin {
    @Shadow protected int sizeX;
    @Shadow protected int sizeY;
    @Shadow protected int sizeZ;
    @Shadow private int viewDistance;
    @Shadow private ChunkSectionPos sectionPos;
    @Shadow public ChunkBuilder.BuiltChunk[] chunks;

    @Shadow @Final protected World world;
    @Shadow @Final protected WorldRenderer worldRenderer;

    @Inject(method = "updateCameraPosition", at = @At("HEAD"), cancellable = true)
    private void lazybuilder$updateChangedStorageStrips(ChunkSectionPos nextSectionPos, CallbackInfo ci) {
        if (!PerformanceManagerClient.preferences().renderingOptimizations()
                || this.sectionPos == null
                || nextSectionPos == null) {
            return;
        }

        int oldX = this.sectionPos.getSectionX();
        int oldZ = this.sectionPos.getSectionZ();
        int newX = nextSectionPos.getSectionX();
        int newZ = nextSectionPos.getSectionZ();

        int changedX = SectionRingUpdatePolicy.changedIndexCount(oldX, newX, this.viewDistance, this.sizeX);
        int changedZ = SectionRingUpdatePolicy.changedIndexCount(oldZ, newZ, this.viewDistance, this.sizeZ);
        int changedColumns = SectionRingUpdatePolicy.uniqueChangedColumns(changedX, changedZ, this.sizeX, this.sizeZ);

        if (changedColumns >= this.sizeX * this.sizeZ) {
            return;
        }

        int remappedSections = 0;
        for (int storageX = 0; storageX < this.sizeX; storageX++) {
            if (!SectionRingUpdatePolicy.mappingChanged(storageX, oldX, newX, this.viewDistance, this.sizeX)) {
                continue;
            }
            remappedSections += lazybuilder$remapColumnX(storageX, newX, newZ);
        }

        for (int storageZ = 0; storageZ < this.sizeZ; storageZ++) {
            if (!SectionRingUpdatePolicy.mappingChanged(storageZ, oldZ, newZ, this.viewDistance, this.sizeZ)) {
                continue;
            }
            remappedSections += lazybuilder$remapColumnZ(storageZ, oldX, newX, oldZ, newZ);
        }

        this.sectionPos = nextSectionPos;
        this.worldRenderer.getChunkRenderingDataPreparer().scheduleTerrainUpdate();
        ChunkPipelineMetrics.recordStorageSectionsRemapped(remappedSections);
        ci.cancel();
    }

    private int lazybuilder$remapColumnX(int storageX, int newCameraX, int newCameraZ) {
        int targetX = SectionRingUpdatePolicy.mappedCoordinate(storageX, newCameraX, this.viewDistance, this.sizeX);
        int remapped = 0;
        for (int storageZ = 0; storageZ < this.sizeZ; storageZ++) {
            int targetZ = SectionRingUpdatePolicy.mappedCoordinate(storageZ, newCameraZ, this.viewDistance, this.sizeZ);
            for (int y = 0; y < this.sizeY; y++) {
                remapped += lazybuilder$setSection(storageX, y, storageZ, targetX, targetZ);
            }
        }
        return remapped;
    }

    private int lazybuilder$remapColumnZ(
            int storageZ,
            int oldCameraX,
            int newCameraX,
            int oldCameraZ,
            int newCameraZ
    ) {
        int targetZ = SectionRingUpdatePolicy.mappedCoordinate(storageZ, newCameraZ, this.viewDistance, this.sizeZ);
        int remapped = 0;
        for (int storageX = 0; storageX < this.sizeX; storageX++) {
            if (SectionRingUpdatePolicy.mappingChanged(storageX, oldCameraX, newCameraX, this.viewDistance, this.sizeX)) {
                continue;
            }
            int targetX = SectionRingUpdatePolicy.mappedCoordinate(storageX, newCameraX, this.viewDistance, this.sizeX);
            for (int y = 0; y < this.sizeY; y++) {
                remapped += lazybuilder$setSection(storageX, y, storageZ, targetX, targetZ);
            }
        }
        return remapped;
    }

    private int lazybuilder$setSection(
            int storageX,
            int y,
            int storageZ,
            int targetX,
            int targetZ
    ) {
        int targetY = this.world.getBottomSectionCoord() + y;
        long target = ChunkSectionPos.asLong(targetX, targetY, targetZ);
        int index = (storageZ * this.sizeY + y) * this.sizeX + storageX;
        ChunkBuilder.BuiltChunk chunk = this.chunks[index];
        if (chunk.getSectionPos() == target) return 0;
        chunk.setSectionPos(target);
        return 1;
    }
}
