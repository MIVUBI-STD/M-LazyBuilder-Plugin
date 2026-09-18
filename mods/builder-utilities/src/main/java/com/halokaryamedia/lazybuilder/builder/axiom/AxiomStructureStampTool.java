package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.BuilderRuntime;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationSource;
import com.halokaryamedia.lazybuilder.builder.operation.OperationLifecycle;
import com.halokaryamedia.lazybuilder.builder.operation.OperationPreflight;
import com.halokaryamedia.lazybuilder.builder.operation.OperationState;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import com.halokaryamedia.lazybuilder.builder.structure.PreparedStructureMutation;
import com.halokaryamedia.lazybuilder.builder.structure.SpongeSchematicImport;
import com.halokaryamedia.lazybuilder.builder.structure.SpongeSchematicV3Exporter;
import com.halokaryamedia.lazybuilder.builder.structure.StructureMutationPreparer;
import com.halokaryamedia.lazybuilder.builder.structure.StructurePastePlan;
import com.halokaryamedia.lazybuilder.builder.structure.StructurePastePlanner;
import com.halokaryamedia.lazybuilder.builder.structure.StructurePlacement;
import com.halokaryamedia.lazybuilder.builder.structure.StructureSnapshot;
import com.moulberry.axiomclientapi.CustomTool;
import imgui.moulberry92.ImGui;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Captures a client-world cuboid and stamps it through the same durable Axiom
 * mutation pipeline used by procedural tools.
 */
public final class AxiomStructureStampTool implements CustomTool {
    private static final String TOOL_NAME = "LazyBuilder Structure Stamp";

    private final AxiomClientServices services;
    private final BuilderRuntime runtime;
    private final AxiomMutationController mutation;
    private final int[] quarterTurns = {0};
    private final int[] mirrorX = {0};
    private final int[] mirrorZ = {0};
    private final int[] includeAir = {0};
    private final int[] captureBiomes = {0};
    private final int[] captureEntities = {1};

    private BlockPos firstCorner;
    private BlockPos secondCorner;
    private BlockPos destination;
    private StructureSnapshot snapshot;
    private List<PlacementPoint> previewPoints = List.of();
    private AxiomPointPreviewRegion preview;
    private String idleStatus = "Select two source corners";

    public AxiomStructureStampTool(AxiomClientServices services, BuilderRuntime runtime) {
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

        try {
            if (firstCorner == null) {
                firstCorner = point;
                idleStatus = "First source corner set";
            } else if (secondCorner == null) {
                secondCorner = point;
                captureSnapshot();
                idleStatus = "Source captured; choose destination";
            } else {
                destination = point;
                rebuildPreview();
            }
            return true;
        } catch (RuntimeException e) {
            idleStatus = "Capture/preview failed: " + safeMessage(e);
            return false;
        }
    }

    @Override
    public boolean callDelete() {
        if (mutation.isActive()) return false;
        if (destination != null) {
            destination = null;
            previewPoints = List.of();
            if (preview != null) preview.clear();
            idleStatus = "Destination cleared";
            return true;
        }
        if (secondCorner != null) {
            secondCorner = null;
            snapshot = null;
            idleStatus = "Second source corner cleared";
            return true;
        }
        if (firstCorner != null) {
            firstCorner = null;
            idleStatus = "Source selection cleared";
            return true;
        }
        return false;
    }

    @Override
    public boolean callConfirm() {
        if (mutation.isActive() || snapshot == null || destination == null) return false;
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
        ImGui.textWrapped("Select two source corners, then click a destination anchor. Blocks use Axiom; captured biomes use negotiated server BIOME authority. Block entities/entities remain export-only until their authoritative capability is available.");
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
        changed |= ImGui.sliderInt("Quarter Turns", quarterTurns, 0, 3);
        changed |= ImGui.sliderInt("Mirror X", mirrorX, 0, 1);
        changed |= ImGui.sliderInt("Mirror Z", mirrorZ, 0, 1);
        boolean airChanged = ImGui.sliderInt("Include Air", includeAir, 0, 1);
        boolean biomeChanged = ImGui.sliderInt("Capture Biomes", captureBiomes, 0, 1);
        boolean entityChanged = ImGui.sliderInt("Capture Entities", captureEntities, 0, 1);
        changed |= airChanged || biomeChanged || entityChanged;

        if (ImGui.button("Clear Capture")) {
            clearAll();
            return;
        }

        if ((airChanged || biomeChanged || entityChanged)
                && firstCorner != null && secondCorner != null) {
            try {
                captureSnapshot();
            } catch (RuntimeException e) {
                idleStatus = "Capture failed: " + safeMessage(e);
            }
        }
        if (changed && snapshot != null && destination != null) rebuildPreview();

        if (snapshot != null) {
            ImGui.textWrapped("Captured blocks=" + snapshot.blockCount()
                    + " blockEntities=" + snapshot.blockEntityCount()
                    + " biomes=" + snapshot.biomeCount()
                    + " entities=" + snapshot.entityCount());
            if (ImGui.button("Save Capture to Schematic Catalog")) {
                saveCapture();
            }
        }
    }

    @Override
    public void reset() {
        if (!mutation.isActive()) clearAll();
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
                idleStatus = "Stamped; choose another destination";
            }
        }
        if (preview != null && !previewPoints.isEmpty()) {
            preview.render(camera, time, poseStack, projection);
        }
    }

    private void captureSnapshot() {
        ClientWorld world = requireWorld();
        BlockBounds bounds = sourceBounds();
        try {
            snapshot = AxiomStructureSnapshotCapture.capture(
                    world,
                    bounds,
                    includeAir[0] != 0,
                    captureBiomes[0] != 0,
                    captureEntities[0] != 0
            );
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to capture auxiliary payloads", e);
        }
        destination = null;
        previewPoints = List.of();
        if (preview != null) preview.clear();
    }

    private void rebuildPreview() {
        try {
            StructurePlacement placement = placement();
            previewPoints = StructurePreviewPoints.create(snapshot, placement);
            ensurePreview().update(previewPoints);
            idleStatus = "Stamp preview ready";
        } catch (RuntimeException e) {
            previewPoints = List.of();
            if (preview != null) preview.clear();
            idleStatus = "Preview failed: " + safeMessage(e);
        }
    }

    private void startMutation() throws IOException {
        ClientWorld world = requireWorld();
        AxiomStructureAuxiliary.requireApplySupported(snapshot);
        StructurePastePlan plan =
                AxiomStructureAuxiliary.planSingle(snapshot, placement(), world);

        CancellationSource cancellation = new CancellationSource();
        long estimateBytes = AxiomStructureAuxiliary.estimateHistoryBytes(plan);
        PreparedStructureMutation prepared = StructureMutationPreparer.prepare(
                UUID.randomUUID().toString(),
                plan,
                runtime.history(),
                estimateBytes,
                cancellation.token()
        ).orElseThrow(() -> new IllegalStateException("Structure preparation was cancelled"));

        mutation.start(world, prepared, cancellation, estimateBytes);
        idleStatus = "Mutation started";
    }

    private void saveCapture() {
        if (snapshot == null) return;
        try {
            Files.createDirectories(runtime.schematicDirectory());
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path path = uniqueCapturePath(stamp);
            int dataVersion = SharedConstants.getGameVersion().getSaveVersion().getId();
            SpongeSchematicV3Exporter.writeCompressed(
                    new SpongeSchematicImport(snapshot, 0, 0, 0, dataVersion),
                    path
            );
            idleStatus = "Saved " + path.getFileName()
                    + " (blocks=" + snapshot.blockCount()
                    + ", blockEntities=" + snapshot.blockEntityCount()
                    + ", biomes=" + snapshot.biomeCount()
                    + ", entities=" + snapshot.entityCount() + ")";
        } catch (Exception e) {
            idleStatus = "Capture export failed: " + safeMessage(e);
        }
    }

    private Path uniqueCapturePath(String stamp) {
        Path base = runtime.schematicDirectory().resolve("capture-" + stamp + ".schem");
        if (!Files.exists(base)) return base;
        for (int i = 2; i <= 9999; i++) {
            Path candidate = runtime.schematicDirectory()
                    .resolve("capture-" + stamp + "-" + i + ".schem");
            if (!Files.exists(candidate)) return candidate;
        }
        throw new IllegalStateException("Could not allocate unique capture filename");
    }

    private StructurePlacement placement() {
        if (snapshot == null || destination == null) {
            throw new IllegalStateException("Captured structure and destination are required");
        }
        return new StructurePlacement(
                destination.getX(),
                destination.getY(),
                destination.getZ(),
                quarterTurns[0],
                mirrorX[0] != 0,
                mirrorZ[0] != 0
        );
    }

    private BlockBounds sourceBounds() {
        if (firstCorner == null || secondCorner == null) {
            throw new IllegalStateException("Both source corners are required");
        }
        return new BlockBounds(
                Math.min(firstCorner.getX(), secondCorner.getX()),
                Math.min(firstCorner.getY(), secondCorner.getY()),
                Math.min(firstCorner.getZ(), secondCorner.getZ()),
                Math.max(firstCorner.getX(), secondCorner.getX()),
                Math.max(firstCorner.getY(), secondCorner.getY()),
                Math.max(firstCorner.getZ(), secondCorner.getZ())
        );
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

    private void clearAll() {
        firstCorner = null;
        secondCorner = null;
        destination = null;
        snapshot = null;
        previewPoints = List.of();
        if (preview != null) preview.clear();
        idleStatus = "Select two source corners";
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
