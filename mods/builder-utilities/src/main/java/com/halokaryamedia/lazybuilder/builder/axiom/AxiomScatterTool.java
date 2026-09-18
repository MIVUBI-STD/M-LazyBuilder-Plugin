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
import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.operation.OperationState;
import com.halokaryamedia.lazybuilder.builder.placement.MinimumSpacingScatterDistribution;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementConstraint;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementConstraints;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementFootprint;
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

/** Deterministic minimum-spacing scatter backed by the shared durable mutation controller. */
public final class AxiomScatterTool implements CustomTool {
    private static final String TOOL_NAME = "LazyBuilder Scatter";
    private static final int MAX_POINTS = 20_000;
    private static final long SCATTER_CHANNEL = 0x534341545445524cL;

    private final AxiomClientServices services;
    private final BuilderRuntime runtime;
    private final AxiomMutationController mutation;
    private final int[] radius = {24};
    private final int[] requestedCount = {128};
    private final float[] minimumSpacing = {3.0f};
    private final int[] seedValue = {424242};
    private final int[] rotationalCopies = {1};
    private final int[] footprintRadius = {1};
    private final int[] maxHeightDelta = {3};

    private BlockPos center;
    private List<PlacementPoint> points = List.of();
    private AxiomPointPreviewRegion preview;
    private String idleStatus = "Right-click a block face to set the scatter center";

    public AxiomScatterTool(AxiomClientServices services, BuilderRuntime runtime) {
        this.services = Objects.requireNonNull(services, "services");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.mutation = new AxiomMutationController(services, runtime);
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public boolean callUseTool() {
        if (mutation.isActive()) return false;
        BlockHitResult hit = services.toolService().raycastBlock();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;
        center = hit.getBlockPos().offset(hit.getSide());
        rebuildPreview();
        return true;
    }

    @Override
    public boolean callDelete() {
        if (mutation.isActive() || center == null) return false;
        clearGeometry();
        idleStatus = "Scatter center cleared";
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
        ImGui.textWrapped("Right-click a block face to place the scatter center. Confirm applies Axiom's active block on deterministic minimum-spacing points.");
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
        changed |= ImGui.sliderInt("Radius", radius, 2, 128);
        changed |= ImGui.sliderInt("Count", requestedCount, 1, 5000);
        changed |= ImGui.sliderFloat("Minimum Spacing", minimumSpacing, 0.0f, 32.0f);
        changed |= ImGui.sliderInt("Seed", seedValue, 0, 999_999);
        changed |= ImGui.sliderInt("Rotational Copies", rotationalCopies, 1, 16);
        changed |= ImGui.sliderInt("Footprint Radius", footprintRadius, 0, 8);
        changed |= ImGui.sliderInt("Max Height Delta", maxHeightDelta, 0, 16);
        if (ImGui.button("Clear Scatter")) {
            clearGeometry();
            return;
        }
        if (changed && center != null) rebuildPreview();
        if (center != null) ImGui.textWrapped("Preview points: " + points.size());
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
        if (preview != null && !points.isEmpty()) {
            preview.render(camera, time, poseStack, projection);
        }
    }

    private void rebuildPreview() {
        if (center == null) {
            points = List.of();
            if (preview != null) preview.clear();
            return;
        }

        try {
            ClientWorld world = Objects.requireNonNull(
                    MinecraftClient.getInstance().world,
                    "Minecraft client world is unavailable");
            int r = radius[0];
            BlockBounds bounds = new BlockBounds(
                    Math.subtractExact(center.getX(), r),
                    world.getBottomY(),
                    Math.subtractExact(center.getZ(), r),
                    Math.addExact(center.getX(), r),
                    world.getTopYInclusive(),
                    Math.addExact(center.getZ(), r)
            );
            AxiomWorldSurfaceHeightSource surface =
                    new AxiomWorldSurfaceHeightSource(world, 1);

            MinimumSpacingScatterDistribution distribution =
                    new MinimumSpacingScatterDistribution(
                            requestedCount[0],
                            minimumSpacing[0],
                            24,
                            SCATTER_CHANNEL
                    );

            PlacementConstraint slopeConstraint = PlacementConstraints.slope(
                    surface,
                    new PlacementFootprint(footprintRadius[0], footprintRadius[0]),
                    maxHeightDelta[0]
            );
            List<PlacementPoint> basePoints = distribution.generate(
                    bounds,
                    surface,
                    new OperationSeed(seedValue[0])
            ).stream().filter(slopeConstraint::test).toList();
            points = PointSymmetryPlanner.rotational(
                    basePoints,
                    new BuilderVec3(center.getX(), center.getY(), center.getZ()),
                    rotationalCopies[0],
                    MAX_POINTS
            );

            if (points.size() > MAX_POINTS) {
                throw new IllegalStateException("Scatter point limit exceeded");
            }

            ensurePreview().update(points);
            idleStatus = "Preview ready: " + points.size() + " points";
        } catch (RuntimeException e) {
            points = List.of();
            if (preview != null) preview.clear();
            idleStatus = "Preview failed: " + safeMessage(e);
        }
    }

    private void startMutation() throws IOException {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = Objects.requireNonNull(client.world, "Minecraft client world is unavailable");
        if (points.isEmpty()) throw new IllegalStateException("Scatter preview contains no blocks");

        PointSetRegion region = new PointSetRegion(points.stream()
                .map(p -> new PointSetRegion.Point(p.x(), p.y(), p.z()))
                .toList());

        AxiomBlockStateCodec codec = new AxiomBlockStateCodec(world);
        BuilderMaterial material =
                new BlockMaterial(codec.encode(services.toolService().getActiveBlock()));
        CancellationSource cancellation = new CancellationSource();

        ScatterMaterialOperation operation = new ScatterMaterialOperation(
                UUID.randomUUID(),
                region,
                new OperationSeed(seedValue[0]),
                runtime.dispatchBudget(),
                cancellation.token(),
                material
        );

        OperationPlan plan =
                new DefaultOperationPlanner(new DeterministicRegionPlanner()).plan(operation);
        long estimateBytes = Math.max(1L, Math.multiplyExact((long) region.size(), 96L));
        Optional<PreparedMaterialMutation> prepared = MaterialOperationPreparer.prepare(
                plan,
                new AxiomClientWorldStateSource(world),
                runtime.history(),
                estimateBytes
        );

        if (prepared.isEmpty()) {
            throw new IllegalStateException("Scatter preparation was cancelled");
        }

        mutation.start(world, prepared.get(), cancellation, estimateBytes);
        idleStatus = "Mutation started";
    }

    private void clearGeometry() {
        center = null;
        points = List.of();
        if (preview != null) preview.clear();
    }

    private AxiomPointPreviewRegion ensurePreview() {
        if (preview == null) {
            preview = new AxiomPointPreviewRegion(services.regionProvider().createBoolean());
        }
        return preview;
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private record ScatterMaterialOperation(
            UUID id,
            BuilderRegion region,
            OperationSeed seed,
            ExecutionBudget executionBudget,
            CancellationToken cancellationToken,
            BuilderMaterial material
    ) implements MaterialOperation {
        @Override public String type() { return "lazybuilder:scatter"; }
        @Override public MutationReadMode readMode() { return MutationReadMode.SNAPSHOT_READ; }
        @Override public HistoryRequirement historyRequirement() { return HistoryRequirement.REQUIRED; }
        @Override public CancellationDisposition cancellationDisposition() { return CancellationDisposition.ROLLBACK; }
        @Override public MaterialMask materialMask() { return MaterialMask.all(); }
    }
}
