package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.BuilderRuntime;
import com.halokaryamedia.lazybuilder.builder.history.HistoryRequirement;
import com.halokaryamedia.lazybuilder.builder.material.BlockMaterial;
import com.halokaryamedia.lazybuilder.builder.material.BuilderMaterial;
import com.halokaryamedia.lazybuilder.builder.material.ConditionalMaterial;
import com.halokaryamedia.lazybuilder.builder.material.ExistingBlockMaterial;
import com.halokaryamedia.lazybuilder.builder.material.MaterialMask;
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
        ImGui.textWrapped("Set two corners. Choose Noise, Slope, Curvature, Flow, or Light as a normalized field. Axiom's active block is applied where the field is above Threshold.");
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
        changed |= ImGui.sliderInt("Field Mode (0 Noise / 1 Slope / 2 Curvature / 3 Flow / 4 Light)", fieldMode, 0, 4);
        if (fieldMode[0] == 0) {
            changed |= ImGui.sliderFloat("Frequency", frequency, 0.01f, 0.5f);
        }
        if (fieldMode[0] == 0) {
            changed |= ImGui.sliderInt("Octaves", octaves, 1, 8);
        }
        if (fieldMode[0] == 3) {
            changed |= ImGui.sliderFloat("Flow Angle", flowAngle, 0.0f, 360.0f);
        }
        changed |= ImGui.sliderFloat("Threshold", threshold, 0.0f, 1.0f);
        changed |= ImGui.sliderInt("Seed", seedValue, 0, 999_999);

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
            ScalarField field = AxiomTextureFields.create(
                    fieldMode[0], world, frequency[0], octaves[0], flowAngle[0]);
            previewPoints = ProceduralTexturePreview.sample(
                    bounds,
                    new OperationSeed(seedValue[0]),
                    field,
                    threshold[0]
            );
            ensurePreview().update(previewPoints);
            idleStatus = "Preview ready";
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
        AxiomBlockStateCodec codec = new AxiomBlockStateCodec(world);
        BuilderMaterial active =
                new BlockMaterial(codec.encode(services.toolService().getActiveBlock()));
        ScalarField field = AxiomTextureFields.create(
                fieldMode[0], world, frequency[0], octaves[0]);
        BuilderMaterial material = new ConditionalMaterial(
                field,
                threshold[0],
                active,
                ExistingBlockMaterial.INSTANCE
        );

        CancellationSource cancellation = new CancellationSource();
        TextureOperation operation = new TextureOperation(
                UUID.randomUUID(),
                new BoxRegion(bounds),
                new OperationSeed(seedValue[0]),
                runtime.dispatchBudget(),
                cancellation.token(),
                material
        );

        OperationPlan plan =
                new DefaultOperationPlanner(new DeterministicRegionPlanner()).plan(operation);
        long estimateBytes = Math.max(1L, Math.multiplyExact((long) previewPoints.size(), 96L));
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
            BuilderMaterial material
    ) implements MaterialOperation {
        @Override public String type() { return "lazybuilder:procedural_texture"; }
        @Override public MutationReadMode readMode() { return MutationReadMode.SNAPSHOT_READ; }
        @Override public HistoryRequirement historyRequirement() { return HistoryRequirement.REQUIRED; }
        @Override public CancellationDisposition cancellationDisposition() { return CancellationDisposition.ROLLBACK; }
        @Override public MaterialMask materialMask() { return MaterialMask.all(); }
    }
}
