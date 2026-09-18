package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.BuilderRuntime;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationSource;
import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.operation.OperationPreflight;
import com.halokaryamedia.lazybuilder.builder.operation.OperationState;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPlanEntry;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementTransform;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementVariation;
import com.halokaryamedia.lazybuilder.builder.spline.BuilderVec3;
import com.halokaryamedia.lazybuilder.builder.spline.CatmullRomSpline;
import com.halokaryamedia.lazybuilder.builder.spline.SplineControlPoint;
import com.halokaryamedia.lazybuilder.builder.spline.SplineParameterization;
import com.halokaryamedia.lazybuilder.builder.spline.SplinePlacementPlanEntry;
import com.halokaryamedia.lazybuilder.builder.spline.SplineModifierPipeline;
import com.halokaryamedia.lazybuilder.builder.spline.SplineModifiers;
import com.halokaryamedia.lazybuilder.builder.spline.SplinePlacementPlanner;
import com.halokaryamedia.lazybuilder.builder.spline.SplineSample;
import com.halokaryamedia.lazybuilder.builder.spline.SplineSampler;
import com.halokaryamedia.lazybuilder.builder.structure.PlacementStructureMutationPreparer;
import com.halokaryamedia.lazybuilder.builder.structure.PreparedStructureMutation;
import com.halokaryamedia.lazybuilder.builder.structure.SchematicCatalog;
import com.halokaryamedia.lazybuilder.builder.structure.SchematicPlacements;
import com.halokaryamedia.lazybuilder.builder.structure.SpongeSchematicImport;
import com.halokaryamedia.lazybuilder.builder.structure.StructurePlacement;
import com.halokaryamedia.lazybuilder.builder.structure.StructurePlacementAdapter;
import com.halokaryamedia.lazybuilder.builder.structure.StructureSnapshot;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Places one block-only catalog schematic repeatedly along a spline. */
public final class AxiomSplineSchematicTool implements CustomTool {
    private static final String TOOL_NAME = "LazyBuilder Schematic Spline";
    private static final int MAX_INSTANCES = 2048;
    private static final int MAX_PREVIEW_BLOCKS = 250_000;
    private static final int MAX_COLLISION_BLOCKS = 2_000_000;

    private final AxiomClientServices services;
    private final BuilderRuntime runtime;
    private final AxiomMutationController mutation;
    private final SchematicCatalog catalog;

    private final List<SplineControlPoint> controlPoints = new ArrayList<>();
    private final float[] spacing = {8.0f};
    private final int[] quality = {20};
    private final int[] rotationOffset = {0};
    private final int[] mirrorX = {0};
    private final int[] mirrorZ = {0};
    private final int[] seedValue = {424242};
    private final int[] parameterization = {1};
    private final float[] normalOffset = {0.0f};
    private final float[] binormalOffset = {0.0f};
    private final float[] jitter = {0.0f};

    private List<SchematicCatalog.Entry> entries = List.of();
    private int selectedIndex;
    private SpongeSchematicImport selected;
    private List<PlacementPlanEntry> placements = List.of();
    private List<PlacementPoint> previewPoints = List.of();
    private AxiomPointPreviewRegion preview;
    private String idleStatus = "Reload schematics and add spline points";

    public AxiomSplineSchematicTool(AxiomClientServices services, BuilderRuntime runtime) {
        this.services = Objects.requireNonNull(services, "services");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.mutation = new AxiomMutationController(services, runtime);
        this.catalog = new SchematicCatalog(runtime.schematicDirectory());
        reload();
    }

    @Override public String name() { return TOOL_NAME; }

    @Override
    public boolean callUseTool() {
        if (mutation.isActive() || selected == null) return false;
        BlockHitResult hit = services.toolService().raycastBlock();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos p = hit.getBlockPos().offset(hit.getSide());
        controlPoints.add(new SplineControlPoint(
                new BuilderVec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5),
                1.0, 0.0));
        rebuildPreview();
        return true;
    }

    @Override
    public boolean callDelete() {
        if (mutation.isActive() || controlPoints.isEmpty()) return false;
        controlPoints.remove(controlPoints.size() - 1);
        rebuildPreview();
        return true;
    }

    @Override
    public boolean callConfirm() {
        if (mutation.isActive() || selected == null || placements.isEmpty()) return false;
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
        ImGui.textWrapped("Place the selected block-only .schem at arc-length intervals along a spline. "
                + "Spline tangent is snapped to the nearest 90-degree structure rotation.");
        ImGui.separator();

        if (mutation.isActive()) {
            var lifecycle = mutation.lifecycle();
            ImGui.textWrapped("Operation: " + lifecycle.state() + " | " + mutation.status()
                    + " | " + String.format("%.1f%%", lifecycle.progressFraction() * 100.0));
            if (!lifecycle.state().isTerminal() && ImGui.button("Cancel and Roll Back")) {
                mutation.requestRollbackCancellation();
            }
            return;
        }

        ImGui.textWrapped(idleStatus);
        if (ImGui.button("Reload Schematics")) reload();
        if (entries.isEmpty()) return;

        if (ImGui.button("Previous Schematic")) selectRelative(-1);
        if (ImGui.button("Next Schematic")) selectRelative(1);
        ImGui.textWrapped("Selected: " + selectedName());

        boolean changed = false;
        changed |= ImGui.sliderFloat("Spacing", spacing, 1.0f, 64.0f);
        changed |= ImGui.sliderInt("Preview Quality", quality, 4, 64);
        changed |= ImGui.sliderInt("Rotation Offset (quarter turns)", rotationOffset, 0, 3);
        changed |= ImGui.sliderInt("Mirror X", mirrorX, 0, 1);
        changed |= ImGui.sliderInt("Mirror Z", mirrorZ, 0, 1);
        changed |= ImGui.sliderInt("Seed", seedValue, 0, 999_999);
        changed |= ImGui.sliderInt("Curve Mode (0 Uniform / 1 Centripetal)", parameterization, 0, 1);
        changed |= ImGui.sliderFloat("Normal Offset", normalOffset, -16.0f, 16.0f);
        changed |= ImGui.sliderFloat("Binormal Offset", binormalOffset, -16.0f, 16.0f);
        changed |= ImGui.sliderFloat("Jitter", jitter, 0.0f, 4.0f);

        if (ImGui.button("Clear Spline")) {
            clearSpline();
            return;
        }
        if (changed && controlPoints.size() >= 2) rebuildPreview();

        ImGui.textWrapped("Control points: " + controlPoints.size()
                + " | accepted instances: " + placements.size()
                + " | preview blocks: " + previewPoints.size());
    }

    @Override
    public void render(Camera camera, float tickDelta, long time, MatrixStack poseStack, Matrix4f projection) {
        mutation.pump();
        OperationState outcome = mutation.pollOutcome();
        if (outcome != null) {
            idleStatus = "Last operation: " + outcome;
            if (outcome == OperationState.COMPLETED) {
                placements = List.of();
                previewPoints = List.of();
                if (preview != null) preview.clear();
            }
        }
        if (preview != null && !previewPoints.isEmpty()) {
            preview.render(camera, time, poseStack, projection);
        }
    }

    @Override
    public void reset() {
        if (!mutation.isActive()) clearSpline();
    }

    private void reload() {
        try {
            entries = catalog.list();
            if (entries.isEmpty()) {
                selected = null;
                selectedIndex = 0;
                idleStatus = "No .schem files found in " + catalog.directory();
                return;
            }
            selectedIndex = Math.min(selectedIndex, entries.size() - 1);
            loadSelected();
        } catch (Exception e) {
            entries = List.of();
            selected = null;
            idleStatus = "Catalog reload failed: " + safeMessage(e);
        }
    }

    private void selectRelative(int delta) {
        if (entries.isEmpty()) return;
        selectedIndex = Math.floorMod(selectedIndex + delta, entries.size());
        loadSelected();
    }

    private void loadSelected() {
        try {
            selected = catalog.load(entries.get(selectedIndex));
            AxiomStructureAuxiliary.requireApplySupported(selected.snapshot());
            if (controlPoints.size() >= 2) rebuildPreview();
            else idleStatus = "Loaded " + selectedName();
        } catch (Exception e) {
            selected = null;
            placements = List.of();
            previewPoints = List.of();
            if (preview != null) preview.clear();
            idleStatus = "Schematic load failed: " + safeMessage(e);
        }
    }

    private void rebuildPreview() {
        if (selected == null || controlPoints.size() < 2) {
            placements = List.of();
            previewPoints = List.of();
            if (preview != null) preview.clear();
            return;
        }
        try {
            CatmullRomSpline spline = new CatmullRomSpline(
                    controlPoints,
                    parameterization[0] == 0
                            ? SplineParameterization.UNIFORM
                            : SplineParameterization.CENTRIPETAL);
            OperationSeed seed = new OperationSeed(seedValue[0]);
            List<SplineSample> samples = SplineSampler.sample(spline, quality[0]);
            samples = SplineModifierPipeline.apply(
                    samples,
                    SplineModifiers.compose(
                            SplineModifiers.offset(normalOffset[0], binormalOffset[0]),
                            SplineModifiers.jitter(
                                    jitter[0], jitter[0], jitter[0] * 0.25,
                                    0x5343484a49545452L)
                    ),
                    seed
            );
            List<SplinePlacementPlanEntry> splinePlan = SplinePlacementPlanner.plan(
                    samples,
                    spacing[0],
                    (point, ignored) -> selectedName(),
                    new PlacementVariation(0, 0, 1, 1, 0, 0),
                    seed
            );
            if (splinePlan.size() > MAX_INSTANCES) {
                throw new IllegalArgumentException("spline schematic instance limit exceeded");
            }

            placements = collisionFilteredPlacements(splinePlan);
            previewPoints = expandPreview(selected.snapshot(), placements);
            ensurePreview().update(previewPoints);
            long totalPreviewSource = OperationPreflight.multiply(
                    selected.snapshot().blockCount(),
                    placements.size(),
                    "spline schematic preview source size");
            idleStatus = StructurePreviewPoints.isDecimated(totalPreviewSource)
                    ? "Spline schematic preview sampled; Confirm applies all accepted instances"
                    : "Spline schematic preview ready";
        } catch (Exception e) {
            placements = List.of();
            previewPoints = List.of();
            if (preview != null) preview.clear();
            idleStatus = "Preview failed: " + safeMessage(e);
        }
    }

    private List<PlacementPlanEntry> collisionFilteredPlacements(
            List<SplinePlacementPlanEntry> splinePlan
    ) {
        StructureSnapshot snapshot = selected.snapshot();
        Set<WorldKey> occupied = new HashSet<>();
        List<PlacementPlanEntry> accepted = new ArrayList<>();

        for (SplinePlacementPlanEntry entry : splinePlan) {
            int quarterTurns = tangentQuarterTurns(entry.frame().tangent());
            quarterTurns = Math.floorMod(quarterTurns + rotationOffset[0], 4);
            BlockPos pasteBase = new BlockPos(
                    rounded(entry.position().x()),
                    rounded(entry.position().y()),
                    rounded(entry.position().z()));
            StructurePlacement placement = SchematicPlacements.atPasteBase(
                    selected, pasteBase, quarterTurns, mirrorX[0] != 0, mirrorZ[0] != 0);

            List<WorldKey> instance = new ArrayList<>(snapshot.blockCount());
            boolean collision = false;
            for (var block : snapshot.blocks()) {
                var world = placement.transform(block.x(), block.y(), block.z());
                WorldKey key = new WorldKey(world.x(), world.y(), world.z());
                if (occupied.contains(key)) {
                    collision = true;
                    break;
                }
                instance.add(key);
            }
            if (collision) continue;

            OperationPreflight.requireAtMost(
                    (long) occupied.size() + instance.size(),
                    MAX_COLLISION_BLOCKS,
                    "spline schematic collision workspace");
            occupied.addAll(instance);
            accepted.add(new PlacementPlanEntry(
                    new PlacementPoint(
                            placement.anchorX(),
                            placement.anchorY(),
                            placement.anchorZ(),
                            accepted.size()),
                    selectedName(),
                    new PlacementTransform(
                            quarterTurns * 90.0,
                            1.0,
                            mirrorX[0] != 0,
                            mirrorZ[0] != 0)
            ));
        }
        return List.copyOf(accepted);
    }

    private List<PlacementPoint> expandPreview(
            StructureSnapshot snapshot,
            List<PlacementPlanEntry> entries
    ) {
        List<StructurePreviewPoints.Instance> instances = new ArrayList<>(entries.size());
        for (PlacementPlanEntry entry : entries) {
            instances.add(new StructurePreviewPoints.Instance(
                    snapshot,
                    StructurePlacementAdapter.from(entry)
            ));
        }
        return StructurePreviewPoints.createMany(instances, MAX_PREVIEW_BLOCKS);
    }

    private void startMutation() throws IOException {
        ClientWorld world = requireWorld();
        CancellationSource cancellation = new CancellationSource();
        long estimatedBlocks = OperationPreflight.multiply(
                selected.snapshot().blockCount(),
                placements.size(),
                "spline schematic block estimate");
        long estimateBytes = OperationPreflight.estimateBytes(
                estimatedBlocks, 96L, "spline schematic history estimate");

        Optional<PreparedStructureMutation> prepared =
                selected.snapshot().biomeCount() == 0
                        ? PlacementStructureMutationPreparer.prepareBlocks(
                                UUID.randomUUID().toString(),
                                placements,
                                id -> selected.snapshot(),
                                new MinecraftStructureBlockStateTransform(world),
                                new AxiomClientWorldStateSource(world),
                                runtime.history(),
                                estimateBytes,
                                cancellation.token()
                        )
                        : PlacementStructureMutationPreparer.prepareAll(
                                UUID.randomUUID().toString(),
                                placements,
                                id -> selected.snapshot(),
                                new MinecraftStructureBlockStateTransform(world),
                                new AxiomClientWorldStateSource(world),
                                AxiomStructureAuxiliary.contextFor(selected.snapshot(), world),
                                runtime.history(),
                                estimateBytes,
                                cancellation.token()
                        );
        if (prepared.isEmpty()) {
            throw new IllegalStateException("Spline schematic preparation was cancelled");
        }
        mutation.start(world, prepared.get(), cancellation, estimateBytes);
        idleStatus = "Mutation started";
    }

    private int tangentQuarterTurns(BuilderVec3 tangent) {
        double yaw = Math.toDegrees(Math.atan2(tangent.z(), tangent.x()));
        return Math.floorMod((int) Math.round(yaw / 90.0), 4);
    }

    private static int rounded(double value) {
        long rounded = Math.round(value);
        if (rounded < Integer.MIN_VALUE || rounded > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("spline structure coordinate exceeds world integer range");
        }
        return (int) rounded;
    }

    private String selectedName() {
        return entries.isEmpty() ? "<none>" : entries.get(selectedIndex).name();
    }

    private ClientWorld requireWorld() {
        return Objects.requireNonNull(
                MinecraftClient.getInstance().world,
                "Minecraft client world is unavailable");
    }

    private AxiomPointPreviewRegion ensurePreview() {
        if (preview == null) {
            preview = new AxiomPointPreviewRegion(services.regionProvider().createBoolean());
        }
        return preview;
    }

    private void clearSpline() {
        controlPoints.clear();
        placements = List.of();
        previewPoints = List.of();
        if (preview != null) preview.clear();
        idleStatus = selected == null
                ? "Reload schematics and add spline points"
                : "Spline cleared; selected " + selectedName();
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private record WorldKey(int x, int y, int z) {}
}
