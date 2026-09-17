package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.compatibility.RendererCompatibility;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Prevents overlapping first-party and migration-source performance hooks from applying together. */
public final class PerformanceMixinPlugin implements IMixinConfigPlugin {
    private static final String MIXIN_PACKAGE = "com.halokaryamedia.lazybuilder.performance.mixin.";
    private static final String TEXT_RENDERER_MIXIN = MIXIN_PACKAGE + "TextRendererDrawerMixin";
    private static final String VERTEX_BUFFER_MIXIN = MIXIN_PACKAGE + "VertexBufferMixin";
    private static final String CHUNK_REBUILD_MIXIN = MIXIN_PACKAGE + "ChunkBuilderBuiltChunkMixin";
    private static final String CHUNK_UPLOAD_MIXIN = MIXIN_PACKAGE + "ChunkBuilderUploadMixin";
    private static final String BUILT_CHUNK_STORAGE_MIXIN = MIXIN_PACKAGE + "BuiltChunkStorageMixin";
    private static final String CHUNK_DATA_VISIBILITY_MIXIN = MIXIN_PACKAGE + "ChunkDataVisibilityMixin";
    private static final String BUILT_CHUNK_BUFFER_LOOKUP_MIXIN = MIXIN_PACKAGE + "BuiltChunkBufferLookupMixin";
    private static final String CHUNK_DATA_LAYER_MEMBERSHIP_MIXIN = MIXIN_PACKAGE + "ChunkDataLayerMembershipMixin";
    private static final String BUILT_CHUNK_TRANSLUCENT_SORT_MIXIN = MIXIN_PACKAGE + "BuiltChunkTranslucentSortMixin";
    private static final String WORLD_RENDERER_TERRAIN_SUBMISSION_MIXIN = MIXIN_PACKAGE + "WorldRendererTerrainSubmissionMixin";
    private static final String BLOCK_BUFFER_ALLOCATOR_STORAGE_MIXIN = MIXIN_PACKAGE + "BlockBufferAllocatorStorageMixin";
    private static final String BLOCK_BUFFER_POOL_MIXIN = MIXIN_PACKAGE + "BlockBufferBuilderPoolMixin";
    private static final String BLOCK_COLORS_MIXIN = MIXIN_PACKAGE + "BlockColorsMixin";
    private static final String BLOCK_SIDE_VISIBILITY_MIXIN = MIXIN_PACKAGE + "BlockSideVisibilityMixin";
    private static final String BAKED_QUAD_ACCESSOR = MIXIN_PACKAGE + "BakedQuadAccessor";
    private static final String BAKED_MODEL_BUILDER_MIXIN = MIXIN_PACKAGE + "BasicBakedModelBuilderMixin";

    private RendererCompatibility.Snapshot rendererCompatibility;

    @Override
    public void onLoad(String mixinPackage) {
        this.rendererCompatibility = RendererCompatibility.detect();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean immediatelyFast = FabricLoader.getInstance().isModLoaded("immediatelyfast");
        RendererCompatibility.Snapshot renderer = this.rendererCompatibility == null
                ? RendererCompatibility.detect()
                : this.rendererCompatibility;

        if ((TEXT_RENDERER_MIXIN.equals(mixinClassName) || VERTEX_BUFFER_MIXIN.equals(mixinClassName))
                && immediatelyFast) {
            return false;
        }
        if (CHUNK_UPLOAD_MIXIN.equals(mixinClassName)
                && (immediatelyFast || !renderer.firstPartyChunkPipelineSafe())) {
            return false;
        }
        if (WORLD_RENDERER_TERRAIN_SUBMISSION_MIXIN.equals(mixinClassName)
                && !renderer.terrainSubmissionSafe()) {
            return false;
        }
        if ((CHUNK_REBUILD_MIXIN.equals(mixinClassName)
                || BUILT_CHUNK_STORAGE_MIXIN.equals(mixinClassName)
                || CHUNK_DATA_VISIBILITY_MIXIN.equals(mixinClassName)
                || BUILT_CHUNK_BUFFER_LOOKUP_MIXIN.equals(mixinClassName)
                || CHUNK_DATA_LAYER_MEMBERSHIP_MIXIN.equals(mixinClassName)
                || BUILT_CHUNK_TRANSLUCENT_SORT_MIXIN.equals(mixinClassName)
                || BLOCK_BUFFER_ALLOCATOR_STORAGE_MIXIN.equals(mixinClassName)
                || BLOCK_BUFFER_POOL_MIXIN.equals(mixinClassName)
                || BLOCK_COLORS_MIXIN.equals(mixinClassName)
                || BLOCK_SIDE_VISIBILITY_MIXIN.equals(mixinClassName))
                && !renderer.firstPartyChunkPipelineSafe()) {
            return false;
        }
        if ((BAKED_QUAD_ACCESSOR.equals(mixinClassName) || BAKED_MODEL_BUILDER_MIXIN.equals(mixinClassName))
                && FabricLoader.getInstance().isModLoaded("ferritecore")) {
            return false;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
