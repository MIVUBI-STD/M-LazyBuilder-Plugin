package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.BuilderRuntime;
import com.halokaryamedia.lazybuilder.builder.history.HistoryRequirement;
import com.halokaryamedia.lazybuilder.builder.material.BlockMaterial;
import com.halokaryamedia.lazybuilder.builder.material.BuilderMaterial;
import com.halokaryamedia.lazybuilder.builder.material.ConditionalMaterial;
import com.halokaryamedia.lazybuilder.builder.material.ExistingBlockMaterial;
import com.halokaryamedia.lazybuilder.builder.material.FieldBlendMaterial;
import com.halokaryamedia.lazybuilder.builder.material.MaterialMask;
import com.halokaryamedia.lazybuilder.builder.material.MaterialMasks;
import com.halokaryamedia.lazybuilder.builder.material.MaterialOperation;
import com.halokaryamedia.lazybuilder.builder.material.MaterialOperationPreparer;
import com.halokaryamedia.lazybuilder.builder.material.PreparedMaterialMutation;
import com.halokaryamedia.lazybuilder.builder.material.ScalarField;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationDisposition;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationSource;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationToken;
import com.halokaryamedia.lazybuilder.builder.operation.DefaultOperationPlanner;
import com.halokaryamedia.lazybuilder.builder.operation.ExecutionBudget;
import com.halokaryamedia.lazybuilder.builder.operation.MutationReadMode;
import com.halokaryamedia.lazybuilder.builder.operation.OperationLifecycle;
import com.halokaryamedia.lazybuilder.builder.operation.OperationPlan;
import com.halokaryamedia.lazybuilder.builder.operation.OperationPreflight;
import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.operation.OperationState;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import com.halokaryamedia.lazybuilder.builder.region.BoxRegion;
import com.halokaryamedia.lazybuilder.builder.region.BuilderRegion;
import com.halokaryamedia.lazybuilder.builder.region.DeterministicRegionPlanner;
import com.moulberry.axiomclientapi.CustomTool;
import imgui.moulberry92.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Two-corner procedural texture tool. Fractal noise selects blocks that receive
 * Axiom's active block while the below-threshold branch preserves existing state.
 */
public final class AxiomProceduralTextureTool implements CustomTool {
    private static final String TOOL_NAME = "LazyBuilder Procedural Texture";

    private final AxiomClientServices services;
    private final BuilderRuntime runtime;
    private final AxiomMutationController mutation;
    private final float[] frequency = {0.08f};
    private final int[] octaves = {4};
    private final float[] threshold = {0.58f};
    private final int[] seedValue = {424242};
    private final int[] fieldMode = {0};
    private final float[] flowAngle = {270.0f};
    private final int[] targetMode = {0};
    private final int[] materialMode = {0};

    private String materialAState;
    private String materialBState;

    private BlockPos first;
    private BlockPos second;
    private List<PlacementPoint> previewPoints = List.of();
    private AxiomPointPreviewRegion preview;
    private String idleStatus = "Right-click two block faces to define a texture box";

    public AxiomProceduralTextureTool(AxiomClientServices services, BuilderRuntime runtime) {
        this.services = Objects.requireNonNull(services, "services");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.mutation = new AxiomMutationController(services, runtime);
    }

    @Override public String name() { return TOOL_NAME; }

    @Override
    public boolean callUseTool() {
        if (mutation.isActive()) return false;
        BlockHitResult hit = services.toolService().raycastBlock();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos point = hit.getBlockPos().offset(hit.getSide());

        if (first == null || second != null) {
            first = point;
            second = null;
            previewPoints = List.of();
            if (preview != null) preview.clear();
            idleStatus = "First corner set; choose second corner";
        } else {
            second = point;
            rebuildPreview();
        }
        return true;
    }

    @Override
    public boolean callDelete() {
        if (mutation.isActive()) return false;
        if (second != null) {
            second = null;
            previewPoints = List.of();
            if (preview != null) preview.clear();
            idleStatus = "Second corner cleared";
            return true;
        }
        if (first != null) {
            first = null;
            idleStatus = "Texture box cleared";
            return true;
        }
        return false;
    }

    @Override
    public boolean callConfirm() {
        if (mutation.isActive() || first == null || second == null || previewPoints.isEmpty()) return false;
        try {
            startMutation();
            return true;
        } catch (Exception e) {
            idleStatus = "Apply failed: " + safeMessage(e);
            return false;
        }
    }

    @Override
    public void displayImguiOptions() {
        ImGui.textWrapped("Set two corners. Choose Fractal, Slope, Curvature, Flow, Light, Ridged, or Cellular as a normalized field. Axiom's active block is applied where the field is above Threshold.");
        ImGui.separator();

        if (mutation.isActive()) {
            OperationLifecycle lifecycle = mutation.lifecycle();
            ImGui.textWrapped("Operation: " + lifecycle.state() + " | " + mutation.status() + " | "
                    + String.format("%.1f%%", lifecycle.progressFraction() * 100.0));
            if (!lifecycle.state().isTerminal() && ImGui.button("Cancel and Roll Back")) {
                mutation.requestRollbackCancellation();
            }
            return;
        }

        ImGui.textWrapped(idleStatus);
        boolean changed = false;
        changed |= ImGui.sliderInt("Field Mode (0 Fractal / 1 Slope / 2 Curvature / 3 Flow / 4 Light / 5 Ridged / 6 Cellular)", fieldMode, 0, 6);
        if (fieldMode[0] == 0 || fieldMode[0] == 5 || fieldMode[0] == 6) {
            changed |= ImGui.sliderFloat("Frequency", frequency, 0.01f, 0.5f);
        }
        if (fieldMode[0] == 0 || fieldMode[0] == 5) {
            changed |= ImGui.sliderInt("Octaves", octaves, 1, 8);
        }
        if (fieldMode[0] == 3) {
            changed |= ImGui.sliderFloat("Flow Angle", flowAngle, 0.0f, 360.0f);
        }
        changed |= ImGui.sliderInt(
                "Material Mode (0 Active/Existing / 1 A-B Threshold / 2 A-B Field Blend)",
                materialMode, 0, 2);
        if (materialMode[0] != 2) {
            changed |= ImGui.sliderFloat("Threshold", threshold, 0.0f, 1.0f);
        }
        changed |= ImGui.sliderInt("Target (0 All / 1 Non-Air / 2 Air Only)", targetMode, 0, 2);
        changed |= ImGui.sliderInt("Seed", seedValue, 0, 999_999);

        if (ImGui.button("Capture Material A")) {
            materialAState = captureActiveState();
            changed = true;
        }
        if (ImGui.button("Capture Material B")) {
            materialBState = captureActiveState();
            changed = true;
        }
        if (materialAState != null) ImGui.textWrapped("Material A: " + materialAState);
        if (materialBState != null) ImGui.textWrapped("Material B: " + materialBState);

        if (ImGui.button("Clear Texture Box")) {
            clearGeometry();
            return;
        }

        if (changed && first != null && second != null) rebuildPreview();
        if (first != null && second != null) {
            ImGui.textWrapped("Field: " + AxiomTextureFields.name(fieldMode[0])
                    + " | Preview selected blocks: " + previewPoints.size());
        }
    }

    @Override
    public void reset() {
        if (!mutation.isActive()) clearGeometry();
    }

    @Override
    public void render(Camera camera, float tickDelta, long time, MatrixStack poseStack, Matrix4f projection) {
        mutation.pump();
        OperationState outcome = mutation.pollOutcome();
        if (outcome != null) {
            idleStatus = "Last operation: " + outcome;
            if (outcome == OperationState.COMPLETED) clearGeometry();
        }
        if (preview != null && !previewPoints.isEmpty()) {
            preview.render(camera, time, poseStack, projection);
        }
    }

    private void rebuildPreview() {
        try {
            BlockBounds bounds = bounds();
            ClientWorld world = Objects.requireNonNull(
                    MinecraftClient.getInstance().world,
                    "Minecraft client world is unavailable");
            requireWithinBuildHeight(bounds, world);
            ScalarField field = AxiomTextureFields.create(
                    fieldMode[0], world, frequency[0], octaves[0], flowAngle[0]);
            AxiomClientWorldStateSource source = new AxiomClientWorldStateSource(world);
            BuilderMaterial material = buildMaterial(world, field);
            previewPoints = ProceduralTexturePreview.sampleResolved(
                    bounds,
                    new OperationSeed(seedValue[0]),
                    material,
                    source,
                    targetMask()
            );
            ensurePreview().update(previewPoints);
            idleStatus = ProceduralTexturePreview.isDecimated(bounds)
                    ? "Preview sampled for large region; Confirm applies the full deterministic region"
                    : "Preview ready";
        } catch (RuntimeException e) {
            previewPoints = List.of();
            if (preview != null) preview.clear();
            idleStatus = "Preview failed: " + safeMessage(e);
        }
    }

    private void startMutation() throws IOException {
        ClientWorld world = Objects.requireNonNull(
                MinecraftClient.getInstance().world,
                "Minecraft client world is unavailable"
        );
        BlockBounds bounds = bounds();
        requireWithinBuildHeight(bounds, world);
        ScalarField field = AxiomTextureFields.create(
                fieldMode[0], world, frequency[0], octaves[0], flowAngle[0]);
        BuilderMaterial material = buildMaterial(world, field);

        CancellationSource cancellation = new CancellationSource();
        TextureOperation operation = new TextureOperation(
                UUID.randomUUID(),
                new BoxRegion(bounds),
                new OperationSeed(seedValue[0]),
                runtime.dispatchBudget(),
                cancellation.token(),
                material,
                targetMask()
        );

        OperationPlan plan =
                new DefaultOperationPlanner(new DeterministicRegionPlanner()).plan(operation);
        long estimateBytes = OperationPreflight.estimateBytes(
                bounds.blockCount(), 96L, "procedural texture history estimate");
        Optional<PreparedMaterialMutation> prepared = MaterialOperationPreparer.prepare(
                plan,
                new AxiomClientWorldStateSource(world),
                runtime.history(),
                estimateBytes
        );
        if (prepared.isEmpty()) throw new IllegalStateException("Texture preparation was cancelled");

        mutation.start(world, prepared.get(), cancellation, estimateBytes);
        idleStatus = "Mutation started";
    }

    private BuilderMaterial buildMaterial(ClientWorld world, ScalarField field) {
        return switch (materialMode[0]) {
            case 0 -> new ConditionalMaterial(
                    field,
                    threshold[0],
                    new BlockMaterial(captureActiveState(world)),
                    ExistingBlockMaterial.INSTANCE
            );
            case 1 -> new ConditionalMaterial(
                    field,
                    threshold[0],
                    capturedMaterial(materialBState, "B"),
                    capturedMaterial(materialAState, "A")
            );
            case 2 -> new FieldBlendMaterial(
                    field,
                    capturedMaterial(materialAState, "A"),
                    capturedMaterial(materialBState, "B"),
                    0x4d4154424c454e44L
            );
            default -> throw new IllegalArgumentException(
                    "Unknown material mode: " + materialMode[0]);
        };
    }

    private BuilderMaterial capturedMaterial(String state, String slot) {
        if (state == null || state.isBlank()) {
            throw new IllegalStateException("Capture Material " + slot + " first");
        }
        return new BlockMaterial(state);
    }

    private String captureActiveState() {
        ClientWorld world = Objects.requireNonNull(
                MinecraftClient.getInstance().world,
                "Minecraft client world is unavailable");
        return captureActiveState(world);
    }

    private String captureActiveState(ClientWorld world) {
        return new AxiomBlockStateCodec(world)
                .encode(services.toolService().getActiveBlock());
    }

    private MaterialMask targetMask() {
        return switch (targetMode[0]) {
            case 0 -> MaterialMasks.all();
            case 1 -> MaterialMasks.not(MaterialMasks.existingState("minecraft:air"));
            case 2 -> MaterialMasks.existingState("minecraft:air");
            default -> throw new IllegalArgumentException("Unknown target mode: " + targetMode[0]);
        };
    }

    private static void requireWithinBuildHeight(
            BlockBounds bounds,
            ClientWorld world
    ) {
        if (bounds.minY() < world.getBottomY()
                || bounds.maxY() > world.getTopYInclusive()) {
            throw new IllegalArgumentException(
                    "texture region exceeds current world build height");
        }
    }

    private BlockBounds bounds() {
        if (first == null || second == null) throw new IllegalStateException("Both corners are required");
        return new BlockBounds(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ())
        );
    }

    private AxiomPointPreviewRegion ensurePreview() {
        if (preview == null) {
            preview = new AxiomPointPreviewRegion(services.regionProvider().createBoolean());
        }
        return preview;
    }

    private void clearGeometry() {
        first = null;
        second = null;
        previewPoints = List.of();
        if (preview != null) preview.clear();
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private record TextureOperation(
            UUID id,
            BuilderRegion region,
            OperationSeed seed,
            ExecutionBudget executionBudget,
            CancellationToken cancellationToken,
            BuilderMaterial material,
            MaterialMask mask
    ) implements MaterialOperation {
        @Override public String type() { return "lazybuilder:procedural_texture"; }
        @Override public MutationReadMode readMode() { return MutationReadMode.SNAPSHOT_READ; }
        @Override public HistoryRequirement historyRequirement() { return HistoryRequirement.REQUIRED; }
        @Override public CancellationDisposition cancellationDisposition() { return CancellationDisposition.ROLLBACK; }
        @Override public MaterialMask materialMask() { return mask; }
    }
}
