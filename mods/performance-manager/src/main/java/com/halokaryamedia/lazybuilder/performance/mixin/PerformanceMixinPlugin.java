package com.halokaryamedia.lazybuilder.performance.mixin;

import com.halokaryamedia.lazybuilder.performance.compatibility.OptimizationCompatibility;
import com.halokaryamedia.lazybuilder.performance.compatibility.OptimizationCompatibility.OptimizationDomain;
import com.halokaryamedia.lazybuilder.performance.compatibility.RendererCompatibility;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Prevents overlapping first-party and migration-source performance hooks from applying together. */
public final class PerformanceMixinPlugin implements IMixinConfigPlugin {
    private static final String MIXIN_PACKAGE = "com.halokaryamedia.lazybuilder.performance.mixin.";

    private static final Map<String, OptimizationDomain> DOMAIN_BY_MIXIN = Map.ofEntries(
            Map.entry("TextRendererDrawerMixin", OptimizationDomain.TEXT_RENDERING),
            Map.entry("WorldRendererMixin", OptimizationDomain.ENTITY_CULLING),
            Map.entry("BlockEntityRenderDispatcherMixin", OptimizationDomain.ENTITY_CULLING),
            Map.entry("VertexBufferMixin", OptimizationDomain.TERRAIN_BUILD),
            Map.entry("VertexBufferGrowthMixin", OptimizationDomain.DYNAMIC_BUFFER_GROWTH),
            Map.entry("ChunkBuilderUploadMixin", OptimizationDomain.TERRAIN_UPLOAD),
            Map.entry("WorldRendererTerrainSubmissionMixin", OptimizationDomain.TERRAIN_SUBMISSION),
            Map.entry("ShaderLoaderSourceMixin", OptimizationDomain.TERRAIN_SUBMISSION),
            Map.entry("CompiledShaderMixin", OptimizationDomain.TERRAIN_SUBMISSION),
            Map.entry("ShaderProgramMixin", OptimizationDomain.TERRAIN_SUBMISSION),
            Map.entry("ChunkBuilderBuiltChunkMixin", OptimizationDomain.TERRAIN_BUILD),
            Map.entry("ChunkBuilderBackpressureMixin", OptimizationDomain.TERRAIN_BUILD),
            Map.entry("BuiltChunkStorageMixin", OptimizationDomain.TERRAIN_BUILD),
            Map.entry("ChunkDataVisibilityMixin", OptimizationDomain.TERRAIN_BUILD),
            Map.entry("BuiltChunkBufferLookupMixin", OptimizationDomain.TERRAIN_BUILD),
            Map.entry("ChunkDataLayerMembershipMixin", OptimizationDomain.TERRAIN_BUILD),
            Map.entry("BuiltChunkTranslucentSortMixin", OptimizationDomain.TERRAIN_BUILD),
            Map.entry("BlockBufferAllocatorStorageMixin", OptimizationDomain.TERRAIN_BUILD),
            Map.entry("SectionBuilderBufferLookupMixin", OptimizationDomain.TERRAIN_BUILD),
            Map.entry("BlockBufferBuilderPoolMixin", OptimizationDomain.TERRAIN_BUILD),
            Map.entry("BlockColorsMixin", OptimizationDomain.TERRAIN_BUILD),
            Map.entry("BlockSideVisibilityMixin", OptimizationDomain.TERRAIN_BUILD),
            Map.entry("BakedQuadAccessor", OptimizationDomain.BAKED_QUAD_MEMORY),
            Map.entry("BasicBakedModelBuilderMixin", OptimizationDomain.BAKED_QUAD_MEMORY)
    );

    private OptimizationCompatibility.Policy policy;

    @Override
    public void onLoad(String mixinPackage) {
        FabricLoader loader = FabricLoader.getInstance();
        this.policy = OptimizationCompatibility.evaluate(
                RendererCompatibility.detect(),
                loader.isModLoaded("immediatelyfast"),
                loader.isModLoaded("entityculling")
        );
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        OptimizationDomain domain = domainFor(mixinClassName);
        return domain == null || effectivePolicy().owns(domain);
    }

    private OptimizationCompatibility.Policy effectivePolicy() {
        OptimizationCompatibility.Policy current = policy;
        if (current != null) return current;

        FabricLoader loader = FabricLoader.getInstance();
        return OptimizationCompatibility.evaluate(
                RendererCompatibility.detect(),
                loader.isModLoaded("immediatelyfast"),
                loader.isModLoaded("ferritecore"),
                loader.isModLoaded("entityculling")
        );
    }

    private static OptimizationDomain domainFor(String mixinClassName) {
        if (mixinClassName == null || !mixinClassName.startsWith(MIXIN_PACKAGE)) return null;
        return DOMAIN_BY_MIXIN.get(mixinClassName.substring(MIXIN_PACKAGE.length()));
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
