package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Whole-world export surface. Snapshot/package stays server-authoritative. */
public final class ExportWorldScreen extends Screen {
    private static final String NATIVE_FORMAT = "JAVA_1_21_4";
    private static final DateTimeFormatter EXPORT_SUFFIX = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Screen parent;
    private final ClientWorldController controller;
    private final ClientTransferController transfers;
    private final WorldControlWireProtocol.WorldSummary world;
    private TextFieldWidget artifactName;
    private String validation;
    private boolean submitted;
    private boolean transferStarted;
    private long observedWorldRevision;
    private long observedTransferRevision;

    public ExportWorldScreen(Screen parent, ClientWorldController controller, ClientTransferController transfers,
                             WorldControlWireProtocol.WorldSummary world) {
        super(Text.literal("Export World"));
        this.parent = parent;
        this.controller = controller;
        this.transfers = transfers;
        this.world = world;
        this.observedWorldRevision = controller.revision();
        this.observedTransferRevision = transfers.revision();
    }

    public ExportWorldScreen(Screen parent, ClientWorldController controller, WorldControlWireProtocol.WorldSummary world) {
        this(parent, controller, LazyBuilderClient.transfers(), world);
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(460, width - 48);
        int left = width / 2 - panelWidth / 2;

        artifactName = new TextFieldWidget(textRenderer, left + 28, 122, panelWidth - 56, 24, Text.literal("File Name"));
        artifactName.setText(fileStem(world.displayName()) + "-" + EXPORT_SUFFIX.format(LocalDateTime.now()));
        artifactName.setMaxLength(96);
        artifactName.setDrawsBackground(false);
        artifactName.setEditableColor(LbUi.TEXT_PRIMARY);
        artifactName.setUneditableColor(LbUi.TEXT_DISABLED);
        artifactName.active = !submitted;
        addDrawableChild(artifactName);

        LbButtonWidget export = LbUi.button(left + 28, 166, panelWidth - 56, 28,
                submitted ? "Export in Progress…" : "Export World",
                LbButtonWidget.Style.PRIMARY, this::submit);
        export.active = !submitted;
        addDrawableChild(export);

        addDrawableChild(LbUi.button(width / 2 - 58, 210, 116, 22,
                submitted ? "Back to Worlds" : "Cancel", LbButtonWidget.Style.GHOST, this::close));
        if (!submitted) setInitialFocus(artifactName);
    }

    private void submit() {
        if (submitted) return;
        String artifact = artifactName.getText().strip();
        if (artifact.isEmpty()) {
            validation = "File name is required.";
            return;
        }
        try {
            validation = null;
            submitted = true;
            controller.exportWorld(world.worldId(), NATIVE_FORMAT, artifact);
            observedWorldRevision = controller.revision();
            clearAndInit();
        } catch (RuntimeException exception) {
            submitted = false;
            validation = exception.getMessage();
            clearAndInit();
        }
    }

    @Override
    public void tick() {
        if (!submitted) return;

        if (observedWorldRevision != controller.revision()) {
            observedWorldRevision = controller.revision();
            if (controller.lastError() != null) {
                submitted = false;
                validation = controller.lastError();
                clearAndInit();
                return;
            }
            if (controller.activityMessage() == null) {
                ClientTransferController.TransferStatus transfer = transfers.status();
                if (transfer.phase() != ClientTransferController.TransferPhase.IDLE) transferStarted = true;
            }
        }

        if (observedTransferRevision == transfers.revision()) return;
        observedTransferRevision = transfers.revision();
        ClientTransferController.TransferStatus transfer = transfers.status();
        if (transfer.phase() == ClientTransferController.TransferPhase.FAILED) {
            submitted = false;
            transferStarted = false;
            validation = transfer.message();
            clearAndInit();
            return;
        }
        if (transfer.phase() != ClientTransferController.TransferPhase.IDLE) {
            transferStarted = true;
            return;
        }
        if (transferStarted) {
            submitted = false;
            transferStarted = false;
            if (client != null && client.currentScreen == this) client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        LbUi.background(context, width, height);
        int panelWidth = Math.min(460, width - 48);
        int left = width / 2 - panelWidth / 2;
        LbUi.elevatedPanel(context, left, 26, panelWidth, 254);

        context.drawTextWithShadow(textRenderer, Text.literal("EXPORT WORLD"), left + 24, 44, LbUi.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, Text.literal(world.displayName()), left + 24, 62, LbUi.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, Text.literal("Java 1.21.4 world backup"), left + 24, 80, LbUi.TEXT_SECONDARY);
        context.drawTextWithShadow(textRenderer,
                Text.literal("LazyBuilder prepares the file, then opens Save As."), left + 24, 96, LbUi.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, Text.literal("File name"), left + 28, 110, LbUi.TEXT_MUTED);
        LbUi.field(context, artifactName, validation != null);

        if (submitted) {
            String status = controller.activityMessage();
            int percent = -1;
            ClientTransferController.TransferStatus transfer = transfers.status();
            if (transfer.phase() != ClientTransferController.TransferPhase.IDLE) {
                status = transfer.message();
                percent = transfer.percent();
            }
            if (status == null) status = "Choose where to save the file…";
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, 242, LbUi.TEXT_SECONDARY);
            if (percent >= 0) LbUi.progress(context, left + 44, 256, panelWidth - 88, percent);
        }
        if (validation != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(validation), width / 2, 262, LbUi.DANGER_BRIGHT);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private static String fileStem(String displayName) {
        String stem = displayName == null ? "world" : displayName.strip().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-+|-+$", "");
        if (stem.isBlank()) stem = "world";
        return stem.length() > 48 ? stem.substring(0, 48) : stem;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
