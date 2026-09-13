package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Whole-world export surface. Server snapshot/package stays authoritative. */
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

    public ExportWorldScreen(
            Screen parent,
            ClientWorldController controller,
            ClientTransferController transfers,
            WorldControlWireProtocol.WorldSummary world
    ) {
        super(Text.literal("Export World"));
        this.parent = parent;
        this.controller = controller;
        this.transfers = transfers;
        this.world = world;
        this.observedWorldRevision = controller.revision();
        this.observedTransferRevision = transfers.revision();
    }

    public ExportWorldScreen(
            Screen parent,
            ClientWorldController controller,
            WorldControlWireProtocol.WorldSummary world
    ) {
        this(parent, controller, LazyBuilderClient.transfers(), world);
    }

    @Override
    protected void init() {
        int center = width / 2;
        int fieldWidth = Math.min(340, width - 60);
        int left = center - fieldWidth / 2;

        artifactName = new TextFieldWidget(textRenderer, left, 102, fieldWidth, 22, Text.literal("File Name"));
        artifactName.setText(world.folderName() + "-" + EXPORT_SUFFIX.format(LocalDateTime.now()));
        artifactName.setMaxLength(96);
        artifactName.active = !submitted;
        addDrawableChild(artifactName);

        String label = submitted ? "Export in progress…" : "Export World";
        ButtonWidget export = ButtonWidget.builder(Text.literal(label), button -> submit())
                .dimensions(center - 125, 144, 250, 24).build();
        export.active = !submitted;
        addDrawableChild(export);

        addDrawableChild(ButtonWidget.builder(Text.literal(submitted ? "Back" : "Cancel"), button -> close())
                .dimensions(center - 50, 182, 100, 20).build());
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
        renderBackground(context, mouseX, mouseY, delta);
        int panelWidth = Math.min(430, width - 40);
        int left = width / 2 - panelWidth / 2;
        context.fill(left, 14, left + panelWidth, 270, 0xB915191F);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 22, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(world.displayName()), width / 2, 48, 0xD8DEE9);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Java 1.21.4 world archive"), width / 2, 64, 0xAEB7C4);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Snapshot → Save As → download. Keep this screen open to follow the operation."),
                width / 2, 80, 0x8F9AA8);
        context.drawTextWithShadow(textRenderer, Text.literal("File Name"), artifactName.getX(), 90, 0xAEB7C4);

        if (submitted) {
            String status = controller.activityMessage();
            int percent = -1;
            ClientTransferController.TransferStatus transfer = transfers.status();
            if (transfer.phase() != ClientTransferController.TransferPhase.IDLE) {
                status = transfer.message();
                percent = transfer.percent();
            }
            if (status == null) status = "Waiting for Save As…";
            if (percent >= 0) status += "  " + percent + "%";
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, 218, 0xD8DEE9);
            if (percent >= 0) {
                int barWidth = Math.min(300, width - 80);
                int barLeft = width / 2 - barWidth / 2;
                int filled = (int) Math.round(barWidth * (percent / 100.0));
                context.fill(barLeft, 234, barLeft + barWidth, 240, 0xFF303740);
                context.fill(barLeft, 234, barLeft + filled, 240, 0xFFD8DEE9);
            }
        }

        if (validation != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(validation), width / 2, 252, 0xFF7777);
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
