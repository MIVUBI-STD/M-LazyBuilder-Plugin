package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.BuilderRuntime;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationSource;
import com.halokaryamedia.lazybuilder.builder.operation.OperationLifecycle;
import com.halokaryamedia.lazybuilder.builder.operation.OperationPreflight;
import com.halokaryamedia.lazybuilder.builder.operation.OperationState;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.structure.PreparedStructureMutation;
import com.halokaryamedia.lazybuilder.builder.structure.SchematicCatalog;
import com.halokaryamedia.lazybuilder.builder.structure.SchematicPlacements;
import com.halokaryamedia.lazybuilder.builder.structure.SpongeSchematicImport;
import com.halokaryamedia.lazybuilder.builder.structure.StructureMutationPreparer;
import com.halokaryamedia.lazybuilder.builder.structure.StructurePastePlan;
import com.halokaryamedia.lazybuilder.builder.structure.StructurePastePlanner;
import com.halokaryamedia.lazybuilder.builder.structure.StructurePlacement;
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
import java.util.UUID;

/** Directory-backed Sponge .schem browser and durable block-only stamp tool. */
public final class AxiomSchematicCatalogTool implements CustomTool {
    private static final String TOOL_NAME = "LazyBuilder Schematic Catalog";

    private final AxiomClientServices services;
    private final BuilderRuntime runtime;
    private final AxiomMutationController mutation;
    private final SchematicCatalog catalog;
    private final int[] quarterTurns = {0};
    private final int[] mirrorX = {0};
    private final int[] mirrorZ = {0};

    private List<SchematicCatalog.Entry> entries = List.of();
    private int selectedIndex;
    private SpongeSchematicImport selected;
    private BlockPos destination;
    private List<PlacementPoint> previewPoints = List.of();
    private AxiomPointPreviewRegion preview;
    private String idleStatus = "Reload schematics to scan the catalog folder";

    public AxiomSchematicCatalogTool(AxiomClientServices services, BuilderRuntime runtime) {
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
        destination = hit.getBlockPos().offset(hit.getSide());
        rebuildPreview();
        return true;
    }

    @Override
    public boolean callDelete() {
        if (mutation.isActive() || destination == null) return false;
        destination = null;
        previewPoints = List.of();
        if (preview != null) preview.clear();
        idleStatus = selected == null
                ? "No schematic selected"
                : "Destination cleared; selected " + selectedName();
        return true;
    }

    @Override
    public boolean callConfirm() {
        if (mutation.isActive() || selected == null || destination == null) return false;
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
        ImGui.textWrapped("Reads Sponge v3 .schem files from " + catalog.directory()
                + ". Schematics containing block entities, biomes, or entities are preserved by the core importer but rejected by this Axiom block-only apply path.");
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
        if (ImGui.button("Reload Schematics")) {
            reload();
        }
        if (!entries.isEmpty()) {
            if (ImGui.button("Previous Schematic")) selectRelative(-1);
            if (ImGui.button("Next Schematic")) selectRelative(1);
            ImGui.textWrapped("Selected: " + selectedName());

            boolean changed = false;
            changed |= ImGui.sliderInt("Quarter Turns", quarterTurns, 0, 3);
            changed |= ImGui.sliderInt("Mirror X", mirrorX, 0, 1);
            changed |= ImGui.sliderInt("Mirror Z", mirrorZ, 0, 1);
            if (changed && destination != null) rebuildPreview();
        }
    }

    @Override
    public void reset() {
        if (!mutation.isActive()) {
            destination = null;
            previewPoints = List.of();
            if (preview != null) preview.clear();
        }
    }

    @Override
    public void render(Camera camera, float tickDelta, long time, MatrixStack poseStack, Matrix4f projection) {
        mutation.pump();
        OperationState outcome = mutation.pollOutcome();
        if (outcome != null) {
            idleStatus = "Last operation: " + outcome;
            if (outcome == OperationState.COMPLETED) {
                destination = null;
                previewPoints = List.of();
                if (preview != null) preview.clear();
                idleStatus = "Stamped " + selectedName() + "; choose another destination";
            }
        }
        if (preview != null && !previewPoints.isEmpty()) {
            preview.render(camera, time, poseStack, projection);
        }
    }

    private void reload() {
        try {
            entries = catalog.list();
            if (entries.isEmpty()) {
                selectedIndex = 0;
                selected = null;
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
            destination = null;
            previewPoints = List.of();
            if (preview != null) preview.clear();
            idleStatus = "Loaded " + selectedName() + " ("
                    + selected.snapshot().blockCount() + " blocks)";
        } catch (Exception e) {
            selected = null;
            destination = null;
            previewPoints = List.of();
            if (preview != null) preview.clear();
            idleStatus = "Schematic load failed: " + safeMessage(e);
        }
    }

    private void rebuildPreview() {
        try {
            StructurePlacement placement = placement();
            previewPoints = StructurePreviewPoints.create(selected.snapshot(), placement);
            ensurePreview().update(previewPoints);
            idleStatus = "Preview ready: " + selectedName();
        } catch (RuntimeException e) {
            previewPoints = List.of();
            if (preview != null) preview.clear();
            idleStatus = "Preview failed: " + safeMessage(e);
        }
    }

    private void startMutation() throws IOException {
        ClientWorld world = Objects.requireNonNull(
                MinecraftClient.getInstance().world,
                "Minecraft client world is unavailable");
        ensureBlockOnly(selected);

        StructurePastePlan plan = new StructurePastePlan(
                StructurePastePlanner.plan(
                        selected.snapshot(),
                        placement(),
                        new MinecraftStructureBlockStateTransform(world),
                        new AxiomClientWorldStateSource(world)
                ),
                List.of()
        );

        CancellationSource cancellation = new CancellationSource();
        long estimateBytes = OperationPreflight.estimateBytes(
                plan.blockChanges(), 96L, "structure history estimate");
        PreparedStructureMutation prepared = StructureMutationPreparer.prepare(
                UUID.randomUUID().toString(),
                plan,
                runtime.history(),
                estimateBytes,
                cancellation.token()
        ).orElseThrow(() -> new IllegalStateException("Schematic preparation was cancelled"));

        mutation.start(world, prepared, cancellation, estimateBytes);
        idleStatus = "Mutation started";
    }

    private StructurePlacement placement() {
        if (selected == null || destination == null) {
            throw new IllegalStateException("Selected schematic and destination are required");
        }
        return SchematicPlacements.atPasteBase(
                selected,
                destination,
                quarterTurns[0],
                mirrorX[0] != 0,
                mirrorZ[0] != 0
        );
    }

    private static void ensureBlockOnly(SpongeSchematicImport imported) {
        var snapshot = imported.snapshot();
        if (snapshot.blockEntityCount() != 0
                || snapshot.biomeCount() != 0
                || snapshot.entityCount() != 0) {
            throw new IllegalArgumentException(
                    "schematic contains non-block payloads (blockEntities="
                            + snapshot.blockEntityCount()
                            + ", biomes=" + snapshot.biomeCount()
                            + ", entities=" + snapshot.entityCount()
                            + "); Axiom public block-only apply cannot preserve them"
            );
        }
    }

    private String selectedName() {
        return entries.isEmpty() ? "<none>" : entries.get(selectedIndex).name();
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
}
