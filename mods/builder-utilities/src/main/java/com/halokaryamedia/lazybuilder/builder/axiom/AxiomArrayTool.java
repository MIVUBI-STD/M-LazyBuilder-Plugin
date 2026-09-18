package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.BuilderRuntime;
import com.halokaryamedia.lazybuilder.builder.history.HistoryRequirement;
import com.halokaryamedia.lazybuilder.builder.material.BlockMaterial;
import com.halokaryamedia.lazybuilder.builder.material.BuilderMaterial;
import com.halokaryamedia.lazybuilder.builder.material.MaterialMask;
import com.halokaryamedia.lazybuilder.builder.material.MaterialOperation;
import com.halokaryamedia.lazybuilder.builder.material.MaterialOperationPreparer;
import com.halokaryamedia.lazybuilder.builder.material.PreparedMaterialMutation;
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
import com.halokaryamedia.lazybuilder.builder.placement.ArrayDistribution;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import com.halokaryamedia.lazybuilder.builder.region.BuilderRegion;
import com.halokaryamedia.lazybuilder.builder.region.DeterministicRegionPlanner;
import com.halokaryamedia.lazybuilder.builder.region.PointSetRegion;
import com.halokaryamedia.lazybuilder.builder.spline.BuilderVec3;
import com.halokaryamedia.lazybuilder.builder.symmetry.PointSymmetryPlanner;
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

/** Deterministic line/grid-step array placement through the shared durable mutation pipeline. */
public final class AxiomArrayTool implements CustomTool {
    private static final String TOOL_NAME = "LazyBuilder Array";
    private static final int MAX_COUNT = 4096;

    private final AxiomClientServices services;
    private final BuilderRuntime runtime;
    private final AxiomMutationController mutation;
    private final int[] count = {16};
    private final int[] stepX = {2};
    private final int[] stepZ = {0};
    private final int[] rotationalCopies = {1};
    private final int[] mirrorX = {0};
    private final int[] mirrorZ = {0};
    private final int[] rotationalCopies = {1};
    private final int[] mirrorX = {0};
    private final int[] mirrorZ = {0};

    private BlockPos origin;
    private List<PlacementPoint> points = List.of();
    private AxiomPointPreviewRegion preview;
    private String idleStatus = "Right-click a block face to set the array origin";

    public AxiomArrayTool(AxiomClientServices services, BuilderRuntime runtime) {
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
        origin = hit.getBlockPos().offset(hit.getSide());
        rebuildPreview();
        return true;
    }

    @Override
    public boolean callDelete() {
        if (mutation.isActive() || origin == null) return false;
        clearGeometry();
        idleStatus = "Array origin cleared";
        return true;
    }

    @Override
    public boolean callConfirm() {
        if (mutation.isActive() || points.isEmpty()) return false;
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
        ImGui.textWrapped("Set an origin, then configure Count and X/Z step. Confirm applies Axiom's active block using durable History v2.");
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
        changed |= ImGui.sliderInt("Count", count, 1, MAX_COUNT);
        changed |= ImGui.sliderInt("Step X", stepX, -64, 64);
        changed |= ImGui.sliderInt("Step Z", stepZ, -64, 64);
        changed |= ImGui.sliderInt("Rotational Copies", rotationalCopies, 1, 16);
        changed |= ImGui.sliderInt("Mirror X", mirrorX, 0, 1);
        changed |= ImGui.sliderInt("Mirror Z", mirrorZ, 0, 1);
        changed |= ImGui.sliderInt("Rotational Copies", rotationalCopies, 1, 16);
        changed |= ImGui.sliderInt("Mirror X", mirrorX, 0, 1);
        changed |= ImGui.sliderInt("Mirror Z", mirrorZ, 0, 1);

        if (ImGui.button("Clear Array")) {
            clearGeometry();
            return;
        }
        if (changed && origin != null) rebuildPreview();
        if (origin != null) ImGui.textWrapped("Preview points: " + points.size());
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
        if (preview != null && !points.isEmpty()) preview.render(camera, time, poseStack, projection);
    }

    private void rebuildPreview() {
        if (origin == null) return;
        try {
            BlockBounds bounds = bounds();
            ArrayDistribution distribution = new ArrayDistribution(
                    origin.getX(), origin.getZ(), count[0], stepX[0], stepZ[0]);
            List<PlacementPoint> basePoints = distribution.generate(
                    bounds,
                    (x, z) -> origin.getY(),
                    new OperationSeed(0L)
            );
            points = PointSymmetryPlanner.rotationalAndMirrors(
                    basePoints,
                    new BuilderVec3(origin.getX(), origin.getY(), origin.getZ()),
                    rotationalCopies[0],
                    mirrorX[0] != 0,
                    mirrorZ[0] != 0,
                    MAX_COUNT * 64
            );
            ensurePreview().update(points);
            idleStatus = "Preview ready: " + points.size() + " points";
        } catch (RuntimeException e) {
            points = List.of();
            if (preview != null) preview.clear();
            idleStatus = "Preview failed: " + safeMessage(e);
        }
    }

    private void startMutation() throws IOException {
        ClientWorld world = Objects.requireNonNull(
                MinecraftClient.getInstance().world,
                "Minecraft client world is unavailable");
        PointSetRegion region = new PointSetRegion(points.stream()
                .map(p -> new PointSetRegion.Point(p.x(), p.y(), p.z()))
                .toList());

        AxiomBlockStateCodec codec = new AxiomBlockStateCodec(world);
        BuilderMaterial material =
                new BlockMaterial(codec.encode(services.toolService().getActiveBlock()));
        CancellationSource cancellation = new CancellationSource();
        ArrayMaterialOperation operation = new ArrayMaterialOperation(
                UUID.randomUUID(),
                region,
                new OperationSeed(0L),
                runtime.dispatchBudget(),
                cancellation.token(),
                material
        );

        OperationPlan plan =
                new DefaultOperationPlanner(new DeterministicRegionPlanner()).plan(operation);
        long estimateBytes = OperationPreflight.estimateBytes(
                region.size(), 96L, "block mutation history estimate");
        Optional<PreparedMaterialMutation> prepared = MaterialOperationPreparer.prepare(
                plan,
                new AxiomClientWorldStateSource(world),
                runtime.history(),
                estimateBytes
        );
        if (prepared.isEmpty()) throw new IllegalStateException("Array preparation was cancelled");

        mutation.start(world, prepared.get(), cancellation, estimateBytes);
        idleStatus = "Mutation started";
    }

    private BlockBounds bounds() {
        if (origin == null) throw new IllegalStateException("Array origin is required");
        long lastX = (long) origin.getX() + (long) stepX[0] * (count[0] - 1L);
        long lastZ = (long) origin.getZ() + (long) stepZ[0] * (count[0] - 1L);
        if (lastX < Integer.MIN_VALUE || lastX > Integer.MAX_VALUE
                || lastZ < Integer.MIN_VALUE || lastZ > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("array endpoint exceeds integer world range");
        }
        return new BlockBounds(
                Math.min(origin.getX(), (int) lastX), origin.getY(),
                Math.min(origin.getZ(), (int) lastZ),
                Math.max(origin.getX(), (int) lastX), origin.getY(),
                Math.max(origin.getZ(), (int) lastZ)
        );
    }

    private AxiomPointPreviewRegion ensurePreview() {
        if (preview == null) preview = new AxiomPointPreviewRegion(services.regionProvider().createBoolean());
        return preview;
    }

    private void clearGeometry() {
        origin = null;
        points = List.of();
        if (preview != null) preview.clear();
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private record ArrayMaterialOperation(
            UUID id,
            BuilderRegion region,
            OperationSeed seed,
            ExecutionBudget executionBudget,
            CancellationToken cancellationToken,
            BuilderMaterial material
    ) implements MaterialOperation {
        @Override public String type() { return "lazybuilder:array"; }
        @Override public MutationReadMode readMode() { return MutationReadMode.SNAPSHOT_READ; }
        @Override public HistoryRequirement historyRequirement() { return HistoryRequirement.REQUIRED; }
        @Override public CancellationDisposition cancellationDisposition() { return CancellationDisposition.ROLLBACK; }
        @Override public MaterialMask materialMask() { return MaterialMask.all(); }
    }
}
