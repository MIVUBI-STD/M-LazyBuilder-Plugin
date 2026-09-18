package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.BuilderRuntime;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationSource;
import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.operation.OperationState;
import com.halokaryamedia.lazybuilder.builder.placement.ArrayDistribution;
import com.halokaryamedia.lazybuilder.builder.placement.MinimumSpacingScatterDistribution;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementDistribution;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPlanEntry;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementVariation;
import com.halokaryamedia.lazybuilder.builder.placement.StructureFootprint;
import com.halokaryamedia.lazybuilder.builder.placement.StructurePlacementPlanner;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Distributes one block-only Sponge schematic as an array or minimum-spacing scatter,
 * then commits every accepted instance through one durable History mutation.
 */
public final class AxiomSchematicDistributionTool implements CustomTool {
    private static final String TOOL_NAME = "LazyBuilder Schematic Distribution";
    private static final int MAX_INSTANCES = 1024;
    private static final int MAX_PREVIEW_BLOCKS = 250_000;
    private static final long SCATTER_CHANNEL = 0x534348454d41544cL;

    private final AxiomClientServices services;
    private final BuilderRuntime runtime;
    private final AxiomMutationController mutation;
    private final SchematicCatalog catalog;

    private final int[] mode = {0};
    private final int[] arrayCount = {8};
    private final int[] stepX = {8};
    private final int[] stepZ = {0};
    private final int[] scatterRadius = {32};
    private final int[] scatterCount = {32};
    private final float[] minimumSpacing = {8.0f};
    private final int[] quarterTurns = {0};
    private final int[] mirrorX = {0};
    private final int[] seedValue = {424242};

    private List<SchematicCatalog.Entry> entries = List.of();
    private int selectedIndex;
    private SpongeSchematicImport selected;
    private BlockPos origin;
    private List<PlacementPlanEntry> placements = List.of();
    private List<com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint> previewPoints = List.of();
    private AxiomPointPreviewRegion preview;
    private String idleStatus = "Reload schematics and choose an origin";

    public AxiomSchematicDistributionTool(AxiomClientServices services, BuilderRuntime runtime) {
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
        origin = hit.getBlockPos().offset(hit.getSide());
        rebuildPreview();
        return true;
    }

    @Override
    public boolean callDelete() {
        if (mutation.isActive() || origin == null) return false;
        clearPreview();
        origin = null;
        idleStatus = "Distribution origin cleared";
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
        ImGui.textWrapped("Distribute the selected block-only .schem as Array or Scatter. "
                + "All instances are collision-filtered and committed as one durable operation.");
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
        changed |= ImGui.sliderInt("Mode (0 Array / 1 Scatter)", mode, 0, 1);
        changed |= ImGui.sliderInt("Quarter Turns", quarterTurns, 0, 3);
        changed |= ImGui.sliderInt("Mirror X", mirrorX, 0, 1);
        changed |= ImGui.sliderInt("Seed", seedValue, 0, 999_999);

        if (mode[0] == 0) {
            changed |= ImGui.sliderInt("Array Count", arrayCount, 1, MAX_INSTANCES);
            changed |= ImGui.sliderInt("Step X", stepX, -128, 128);
            changed |= ImGui.sliderInt("Step Z", stepZ, -128, 128);
        } else {
            changed |= ImGui.sliderInt("Scatter Radius", scatterRadius, 2, 256);
            changed |= ImGui.sliderInt("Scatter Count", scatterCount, 1, MAX_INSTANCES);
            changed |= ImGui.sliderFloat("Minimum Spacing", minimumSpacing, 0.0f, 64.0f);
        }

        if (changed && origin != null) rebuildPreview();
        if (origin != null) {
            ImGui.textWrapped("Accepted instances: " + placements.size()
                    + " | preview blocks: " + previewPoints.size());
        }
    }

    @Override
    public void render(Camera camera, float tickDelta, long time, MatrixStack poseStack, Matrix4f projection) {
        mutation.pump();
        OperationState outcome = mutation.pollOutcome();
        if (outcome != null) {
            idleStatus = "Last operation: " + outcome;
            if (outcome == OperationState.COMPLETED) clearPreview();
        }
        if (preview != null && !previewPoints.isEmpty()) {
            preview.render(camera, time, poseStack, projection);
        }
    }

    @Override
    public void reset() {
        if (!mutation.isActive()) {
            origin = null;
            clearPreview();
        }
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
            ensureBlockOnly(selected);
            clearPreview();
            idleStatus = "Loaded " + selectedName();
            if (origin != null) rebuildPreview();
        } catch (Exception e) {
            selected = null;
            clearPreview();
            idleStatus = "Schematic load failed: " + safeMessage(e);
        }
    }

    private void rebuildPreview() {
        if (origin == null || selected == null) return;
        try {
            ClientWorld world = requireWorld();
            OperationSeed seed = new OperationSeed(seedValue[0]);
            BlockBounds distributionBounds = distributionBounds(world);
            PlacementDistribution distribution = distribution();
            StructureSnapshot snapshot = selected.snapshot();
            var localBounds = snapshot.localBounds();
            StructureFootprint footprint = new StructureFootprint(
                    (long) localBounds.maxX() - localBounds.minX() + 1L,
                    (long) localBounds.maxZ() - localBounds.minZ() + 1L
            );

            List<PlacementPlanEntry> raw = StructurePlacementPlanner.plan(
                    distributionBounds,
                    mode[0] == 0 ? (x, z) -> origin.getY() : new AxiomWorldSurfaceHeightSource(world, 1),
                    seed,
                    distribution,
                    (point, ignored) -> selectedName(),
                    new PlacementVariation(
                            quarterTurns[0] * 90.0,
                            quarterTurns[0] * 90.0,
                            1.0,
                            1.0,
                            mirrorX[0] != 0 ? 1.0 : 0.0,
                            0x534348454d415452L
                    ),
                    point -> true,
                    ignored -> footprint,
                    true
            );

            placements = raw.stream()
                    .map(this::applySchematicOffset)
                    .toList();
            previewPoints = expandPreview(snapshot, placements);
            ensurePreview().update(previewPoints);
            idleStatus = "Distribution preview ready";
        } catch (Exception e) {
            clearPreview();
            idleStatus = "Preview failed: " + safeMessage(e);
        }
    }

    private PlacementPlanEntry applySchematicOffset(PlacementPlanEntry entry) {
        BlockPos pasteBase = new BlockPos(entry.point().x(), entry.point().y(), entry.point().z());
        StructurePlacement placement = SchematicPlacements.atPasteBase(
                selected,
                pasteBase,
                quarterTurns[0],
                mirrorX[0] != 0,
                false
        );
        return new PlacementPlanEntry(
                new com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint(
                        placement.anchorX(),
                        placement.anchorY(),
                        placement.anchorZ(),
                        entry.point().ordinal()
                ),
                entry.sourceId(),
                entry.transform()
        );
    }

    private List<com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint> expandPreview(
            StructureSnapshot snapshot,
            List<PlacementPlanEntry> entries
    ) {
        LinkedHashSet<com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint> result =
                new LinkedHashSet<>();
        int ordinal = 0;
        for (PlacementPlanEntry entry : entries) {
            StructurePlacement placement = StructurePlacementAdapter.from(entry);
            for (var point : StructurePreviewPoints.create(snapshot, placement)) {
                if (result.size() >= MAX_PREVIEW_BLOCKS) {
                    throw new IllegalArgumentException(
                            "distributed schematic preview exceeds " + MAX_PREVIEW_BLOCKS + " blocks");
                }
                result.add(new com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint(
                        point.x(), point.y(), point.z(), ordinal++));
            }
        }
        return List.copyOf(result);
    }

    private PlacementDistribution distribution() {
        if (mode[0] == 0) {
            return new ArrayDistribution(
                    origin.getX(), origin.getZ(), arrayCount[0], stepX[0], stepZ[0]);
        }
        return new MinimumSpacingScatterDistribution(
                scatterCount[0], minimumSpacing[0], 24, SCATTER_CHANNEL);
    }

    private BlockBounds distributionBounds(ClientWorld world) {
        if (mode[0] == 0) {
            long endX = (long) origin.getX() + (long) (arrayCount[0] - 1) * stepX[0];
            long endZ = (long) origin.getZ() + (long) (arrayCount[0] - 1) * stepZ[0];
            int minX = Math.toIntExact(Math.min(origin.getX(), endX));
            int maxX = Math.toIntExact(Math.max(origin.getX(), endX));
            int minZ = Math.toIntExact(Math.min(origin.getZ(), endZ));
            int maxZ = Math.toIntExact(Math.max(origin.getZ(), endZ));
            return new BlockBounds(minX, origin.getY(), minZ, maxX, origin.getY(), maxZ);
        }
        int r = scatterRadius[0];
        return new BlockBounds(
                Math.subtractExact(origin.getX(), r),
                world.getBottomY(),
                Math.subtractExact(origin.getZ(), r),
                Math.addExact(origin.getX(), r),
                world.getTopYInclusive(),
                Math.addExact(origin.getZ(), r)
        );
    }

    private void startMutation() throws IOException {
        ClientWorld world = requireWorld();
        ensureBlockOnly(selected);
        CancellationSource cancellation = new CancellationSource();
        long estimatedBlocks = Math.multiplyExact(
                (long) selected.snapshot().blockCount(),
                placements.size());
        long estimateBytes = Math.max(1L, Math.multiplyExact(estimatedBlocks, 96L));

        Optional<PreparedStructureMutation> prepared =
                PlacementStructureMutationPreparer.prepareBlocks(
                        UUID.randomUUID().toString(),
                        placements,
                        id -> selected.snapshot(),
                        new MinecraftStructureBlockStateTransform(world),
                        new AxiomClientWorldStateSource(world),
                        runtime.history(),
                        estimateBytes,
                        cancellation.token()
                );
        if (prepared.isEmpty()) {
            throw new IllegalStateException("Distributed schematic preparation was cancelled");
        }
        mutation.start(world, prepared.get(), cancellation, estimateBytes);
        idleStatus = "Mutation started";
    }

    private static void ensureBlockOnly(SpongeSchematicImport imported) {
        var snapshot = imported.snapshot();
        if (snapshot.blockEntityCount() != 0
                || snapshot.biomeCount() != 0
                || snapshot.entityCount() != 0) {
            throw new IllegalArgumentException(
                    "distributed schematic apply currently requires block-only payloads");
        }
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

    private void clearPreview() {
        placements = List.of();
        previewPoints = List.of();
        if (preview != null) preview.clear();
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
