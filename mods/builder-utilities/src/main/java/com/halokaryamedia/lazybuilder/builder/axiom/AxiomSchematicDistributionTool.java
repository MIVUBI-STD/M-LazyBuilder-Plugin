package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.BuilderRuntime;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationSource;
import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.operation.OperationPreflight;
import com.halokaryamedia.lazybuilder.builder.operation.OperationState;
import com.halokaryamedia.lazybuilder.builder.placement.ArrayDistribution3d;
import com.halokaryamedia.lazybuilder.builder.placement.MinimumSpacingScatterDistribution;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementConstraint;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementConstraints;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementDistribution;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPlanEntry;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementVariation;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementSource;
import com.halokaryamedia.lazybuilder.builder.placement.WeightedPlacementSource;
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
import com.halokaryamedia.lazybuilder.builder.structure.StructurePlacementBounds;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Distributes one Sponge schematic as an array or minimum-spacing scatter.
 * Single-source and catalog-palette modes support negotiated BIOME/ENTITY authority.
 * BLOCK_ENTITY payloads use negotiated server authority when available; explicit lossy stripping remains a fallback.
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
    private final int[] stepY = {0};
    private final int[] stepZ = {0};
    private final int[] scatterRadius = {32};
    private final int[] scatterCount = {32};
    private final float[] minimumSpacing = {8.0f};
    private final int[] quarterTurns = {0};
    private final int[] mirrorX = {0};
    private final int[] mirrorZ = {0};
    private final int[] useCatalogPalette = {0};
    private final int[] sameBiome = {0};
    private final int[] seedValue = {424242};
    private final int[] stripBlockEntitiesOnApply = {0};

    private List<SchematicCatalog.Entry> entries = List.of();
    private int selectedIndex;
    private SpongeSchematicImport selected;
    private Map<String, SpongeSchematicImport> supportedCatalog = Map.of();
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
        ImGui.textWrapped("Distribute supported .schem files as Array or Scatter. "
                + "Both single-source and catalog-palette modes can include negotiated "
                + "BLOCK_ENTITY/BIOME/ENTITY payloads when the server advertises authority. "
                + "Lossy block-entity stripping remains an explicit fallback.");
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
        changed |= ImGui.sliderInt("Mirror Z", mirrorZ, 0, 1);
        changed |= ImGui.sliderInt("Use Catalog Palette", useCatalogPalette, 0, 1);
        changed |= ImGui.sliderInt("Match Origin Biome", sameBiome, 0, 1);
        changed |= ImGui.sliderInt("Seed", seedValue, 0, 999_999);
        boolean stripChanged = ImGui.sliderInt(
                "Strip Block Entity Payloads On Apply (Lossy)",
                stripBlockEntitiesOnApply,
                0,
                1);
        changed |= stripChanged;
        if (stripChanged) {
            reload();
            return;
        }
        if (useCatalogPalette[0] != 0) {
            ImGui.textWrapped("Authority-compatible palette sources: " + supportedCatalog.size());
        }

        if (mode[0] == 0) {
            changed |= ImGui.sliderInt("Array Count", arrayCount, 1, MAX_INSTANCES);
            changed |= ImGui.sliderInt("Step X", stepX, -128, 128);
            changed |= ImGui.sliderInt("Step Y", stepY, -128, 128);
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
            supportedCatalog = loadSupportedCatalog(entries);
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
            supportedCatalog = Map.of();
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
            String name = entries.get(selectedIndex).name();
            selected = supportedCatalog.get(name);
            if (selected == null) {
                SpongeSchematicImport imported = catalog.load(entries.get(selectedIndex));
                com.halokaryamedia.lazybuilder.builder.structure.SchematicDataVersionPolicy
                        .requireNotFuture(imported.dataVersion());
                selected = imported;
            }
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
            PlacementSource source = placementSource();
            List<PlacementPlanEntry> raw = StructurePlacementPlanner.plan(
                    distributionBounds,
                    mode[0] == 0 ? (x, z) -> origin.getY() : new AxiomWorldSurfaceHeightSource(world, 1),
                    seed,
                    distribution,
                    source,
                    new PlacementVariation(
                            quarterTurns[0] * 90.0,
                            quarterTurns[0] * 90.0,
                            1.0,
                            1.0,
                            mirrorX[0] != 0 ? 1.0 : 0.0,
                            mirrorZ[0] != 0 ? 1.0 : 0.0,
                            0x534348454d415452L
                    ),
                    placementConstraint(world),
                    sourceId -> footprint(requireSource(sourceId).snapshot()),
                    true
            );

            placements = filterTransformedCollisions(
                    raw.stream().map(this::applySchematicOffset).toList());
            previewPoints = expandPreview(placements);
            boolean decimated =
                    StructurePreviewPoints.isDecimated(estimatedPlacedBlocks(placements));
            ensurePreview().update(previewPoints);
            idleStatus = decimated
                    ? "Distribution preview sampled; Confirm applies every accepted structure block"
                    : "Distribution preview ready";
        } catch (Exception e) {
            clearPreview();
            idleStatus = "Preview failed: " + safeMessage(e);
        }
    }

    private List<PlacementPlanEntry> filterTransformedCollisions(
            List<PlacementPlanEntry> candidates
    ) {
        List<PlacementPlanEntry> accepted = new ArrayList<>(candidates.size());
        List<StructurePlacementBounds> occupied = new ArrayList<>(candidates.size());

        for (PlacementPlanEntry candidate : candidates) {
            StructureSnapshot snapshot =
                    applyImport(requireSource(candidate.sourceId())).snapshot();
            StructurePlacement placement = StructurePlacementAdapter.from(candidate);
            StructurePlacementBounds bounds =
                    StructurePlacementBounds.of(snapshot, placement);

            boolean collision = false;
            for (StructurePlacementBounds existing : occupied) {
                if (bounds.overlaps(existing)) {
                    collision = true;
                    break;
                }
            }
            if (collision) continue;

            accepted.add(candidate);
            occupied.add(bounds);
        }
        return List.copyOf(accepted);
    }

    private PlacementPlanEntry applySchematicOffset(PlacementPlanEntry entry) {
        SpongeSchematicImport imported = requireSource(entry.sourceId());
        BlockPos pasteBase = new BlockPos(entry.point().x(), entry.point().y(), entry.point().z());
        StructurePlacement placement = SchematicPlacements.atPasteBase(
                imported,
                pasteBase,
                quarterTurns[0],
                mirrorX[0] != 0,
                mirrorZ[0] != 0
        );
        return new PlacementPlanEntry(
                new com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint(
                        placement.anchorX(),
                        placement.anchorY(),
                        placement.anchorZ(),
                        entry.point().ordinal()
                ),
                entry.sourceId(),
                new com.halokaryamedia.lazybuilder.builder.placement.PlacementTransform(
                        entry.transform().yawDegrees(),
                        entry.transform().scale(),
                        entry.transform().mirrorX(),
                        mirrorZ[0] != 0)
        );
    }

    private List<com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint> expandPreview(
            List<PlacementPlanEntry> entries
    ) {
        List<StructurePreviewPoints.Instance> instances = new ArrayList<>(entries.size());
        for (PlacementPlanEntry entry : entries) {
            instances.add(new StructurePreviewPoints.Instance(
                    applyImport(requireSource(entry.sourceId())).snapshot(),
                    StructurePlacementAdapter.from(entry)
            ));
        }
        return StructurePreviewPoints.createMany(instances, MAX_PREVIEW_BLOCKS);
    }

    private PlacementDistribution distribution() {
        if (mode[0] == 0) {
            return new ArrayDistribution3d(
                    origin.getX(), origin.getY(), origin.getZ(),
                    arrayCount[0], stepX[0], stepY[0], stepZ[0]);
        }
        return new MinimumSpacingScatterDistribution(
                scatterCount[0], minimumSpacing[0], 24, SCATTER_CHANNEL);
    }

    private BlockBounds distributionBounds(ClientWorld world) {
        if (mode[0] == 0) {
            long endX = (long) origin.getX() + (long) (arrayCount[0] - 1) * stepX[0];
            long endY = (long) origin.getY() + (long) (arrayCount[0] - 1) * stepY[0];
            long endZ = (long) origin.getZ() + (long) (arrayCount[0] - 1) * stepZ[0];
            int minX = Math.toIntExact(Math.min(origin.getX(), endX));
            int maxX = Math.toIntExact(Math.max(origin.getX(), endX));
            int minY = Math.toIntExact(Math.min(origin.getY(), endY));
            int maxY = Math.toIntExact(Math.max(origin.getY(), endY));
            int minZ = Math.toIntExact(Math.min(origin.getZ(), endZ));
            int maxZ = Math.toIntExact(Math.max(origin.getZ(), endZ));
            if (minY < world.getBottomY() || maxY > world.getTopYInclusive()) {
                throw new IllegalArgumentException(
                        "schematic array Y range exceeds current world build height");
            }
            return new BlockBounds(minX, minY, minZ, maxX, maxY, maxZ);
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
        for (PlacementPlanEntry entry : placements) {
            SpongeSchematicImport imported =
                    applyImport(requireSource(entry.sourceId()));
            AxiomSchematicCompatibility.validateForApply(imported, world);
            AxiomStructureAuxiliary.requirePlacementInsideWorld(
                    imported.snapshot(),
                    StructurePlacementAdapter.from(entry),
                    world);
        }
        CancellationSource cancellation = new CancellationSource();
        long estimateBytes = estimatedHistoryBytes(placements);

        String durableOperationId = AxiomDurableOperationIds.random(world);

        Optional<PreparedStructureMutation> prepared;
        if (!requiresAuxiliary(placements)) {
            prepared = PlacementStructureMutationPreparer.prepareBlocks(
                    durableOperationId,
                    placements,
                    id -> applyImport(requireSource(id)).snapshot(),
                    new MinecraftStructureBlockStateTransform(world),
                    new AxiomClientWorldStateSource(world),
                    runtime.history(),
                    estimateBytes,
                    cancellation.token()
            );
        } else {
            prepared = PlacementStructureMutationPreparer.prepareAll(
                    durableOperationId,
                    placements,
                    id -> applyImport(requireSource(id)).snapshot(),
                    new MinecraftStructureBlockStateTransform(world),
                    new AxiomClientWorldStateSource(world),
                    AxiomStructureAuxiliary.contextForAuthorities(world),
                    runtime.history(),
                    estimateBytes,
                    cancellation.token()
            );
        }
        if (prepared.isEmpty()) {
            throw new IllegalStateException("Distributed schematic preparation was cancelled");
        }
        mutation.start(world, prepared.get(), cancellation, estimateBytes);
        idleStatus = "Mutation started";
    }

    private boolean requiresAuxiliary(List<PlacementPlanEntry> entries) {
        for (PlacementPlanEntry entry : entries) {
            StructureSnapshot snapshot = applyImport(requireSource(entry.sourceId())).snapshot();
            if (snapshot.blockEntityCount() != 0
                    || snapshot.biomeCount() != 0
                    || snapshot.entityCount() != 0) {
                return true;
            }
        }
        return false;
    }

    private long estimatedHistoryBytes(List<PlacementPlanEntry> entries) {
        long total = 0L;
        for (PlacementPlanEntry entry : entries) {
            StructureSnapshot snapshot = applyImport(requireSource(entry.sourceId())).snapshot();
            total = Math.addExact(total, Math.multiplyExact(snapshot.blockCount(), 96L));

            for (var blockEntity : snapshot.blockEntities()) {
                total = Math.addExact(
                        total,
                        Math.addExact(128L, Math.multiplyExact(blockEntity.payload().length, 2L)));
            }
            for (var biome : snapshot.biomes()) {
                total = Math.addExact(
                        total,
                        Math.addExact(96L, Math.multiplyExact(biome.payload().length, 2L)));
            }
            for (var entity : snapshot.entities()) {
                total = Math.addExact(
                        total,
                        Math.addExact(160L, Math.multiplyExact(entity.payload().length, 2L)));
            }
        }
        return Math.max(1L, total);
    }

    private PlacementConstraint placementConstraint(ClientWorld world) {
        if (sameBiome[0] == 0) return PlacementConstraints.all();
        AxiomWorldBiomeSource biomes = new AxiomWorldBiomeSource(world);
        return PlacementConstraints.biomeEquals(
                biomes,
                biomes.biomeAt(origin.getX(), origin.getY(), origin.getZ())
        );
    }

    private PlacementSource placementSource() {
        if (useCatalogPalette[0] == 0) {
            return (point, seed) -> selectedName();
        }
        if (supportedCatalog.isEmpty()) {
            throw new IllegalStateException(
                    "catalog palette has no schematics compatible with current authorities");
        }
        List<WeightedPlacementSource.Entry> weighted = supportedCatalog.keySet().stream()
                .map(id -> new WeightedPlacementSource.Entry(id, 1.0))
                .toList();
        return new WeightedPlacementSource(weighted, 0x50414c455454454cL);
    }

    private SpongeSchematicImport applyImport(SpongeSchematicImport source) {
        return AxiomStructureAuxiliary.importForApply(
                source,
                stripBlockEntitiesOnApply[0] != 0);
    }

    private SpongeSchematicImport requireSource(String sourceId) {
        SpongeSchematicImport imported;
        if (useCatalogPalette[0] != 0) {
            imported = supportedCatalog.get(sourceId);
        } else {
            imported = selected;
        }
        if (imported == null) {
            throw new IllegalArgumentException("unknown schematic source: " + sourceId);
        }
        return imported;
    }

    private static StructureFootprint footprint(StructureSnapshot snapshot) {
        var bounds = snapshot.localBounds();
        return new StructureFootprint(
                (long) bounds.maxX() - bounds.minX() + 1L,
                (long) bounds.maxZ() - bounds.minZ() + 1L
        );
    }

    private long estimatedPlacedBlocks(List<PlacementPlanEntry> entries) {
        long total = 0L;
        for (PlacementPlanEntry entry : entries) {
            total = Math.addExact(
                    total,
                    applyImport(requireSource(entry.sourceId())).snapshot().blockCount()
            );
        }
        return total;
    }

    private Map<String, SpongeSchematicImport> loadSupportedCatalog(
            List<SchematicCatalog.Entry> catalogEntries
    ) throws IOException {
        LinkedHashMap<String, SpongeSchematicImport> loaded = new LinkedHashMap<>();
        for (SchematicCatalog.Entry entry : catalogEntries) {
            SpongeSchematicImport imported;
            try {
                imported = catalog.load(entry);
                com.halokaryamedia.lazybuilder.builder.structure.SchematicDataVersionPolicy
                        .requireNotFuture(imported.dataVersion());
                AxiomStructureAuxiliary.snapshotForApply(
                        imported.snapshot(),
                        stripBlockEntitiesOnApply[0] != 0);
            } catch (IOException | IllegalArgumentException unsupported) {
                // Catalog palette discovery is best-effort per source. One corrupt,
                // future-version, or otherwise unsupported schematic must not make
                // every valid palette source disappear from the tool.
                continue;
            }
            loaded.put(entry.name(), imported);
        }
        return Map.copyOf(loaded);
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
