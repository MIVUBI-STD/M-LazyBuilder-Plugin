package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/** File-first import flow; upload and publication remain one visible operation. */
public final class ImportWorldScreen extends Screen {
    private final Screen parent;
    private final ClientWorldController worlds;
    private final ClientTransferController transfers;
    private TextFieldWidget displayName;
    private String validation;
    private boolean choosing;
    private boolean processingImport;
    private long observedTransferRevision;
    private long observedWorldRevision;

    public ImportWorldScreen(Screen parent, ClientWorldController worlds, ClientTransferController transfers) {
        super(Text.literal("Import World"));
        this.parent = parent;
        this.worlds = worlds;
        this.transfers = transfers;
        this.observedTransferRevision = transfers.revision();
        this.observedWorldRevision = worlds.revision();
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(460, width - 48);
        int left = width / 2 - panelWidth / 2;
        boolean busy = choosing || processingImport;

        displayName = new TextFieldWidget(textRenderer, left + 28, 108, panelWidth - 56, 24, Text.literal("World Name"));
        displayName.setPlaceholder(Text.literal("Optional — uses file name"));
        displayName.setMaxLength(96);
        displayName.setDrawsBackground(false);
        displayName.setEditableColor(LbUi.TEXT_PRIMARY);
        displayName.setUneditableColor(LbUi.TEXT_DISABLED);
        displayName.active = !busy;
        addDrawableChild(displayName);

        String primaryLabel = processingImport ? "Finishing Import…"
                : choosing ? "Uploading World…"
                : "Choose World File";
        LbButtonWidget choose = LbUi.button(left + 28, 156, panelWidth - 56, 28,
                primaryLabel, LbButtonWidget.Style.PRIMARY, this::choose);
        choose.active = !busy;
        addDrawableChild(choose);

        addDrawableChild(LbUi.button(width / 2 - 58, 206, 116, 22,
                busy ? "Back to Worlds" : "Cancel", LbButtonWidget.Style.GHOST, this::close));
        if (!busy) setInitialFocus(displayName);
    }

    private void choose() {
        if (choosing || processingImport) return;
        String requestedDisplay = displayName == null ? "" : displayName.getText().strip();
        validation = null;
        choosing = true;
        clearAndInit();

        try {
            transfers.chooseAndUploadImport(artifactName -> {
                String baseName = baseName(artifactName);
                String folder = availableFolderName(baseName);
                String finalDisplay = requestedDisplay.isBlank() ? baseName : requestedDisplay;
                choosing = false;
                processingImport = true;
                worlds.importWorld(artifactName, folder, finalDisplay);
                observedWorldRevision = worlds.revision();
                if (client != null && client.currentScreen == this) clearAndInit();
            }, () -> {
                choosing = false;
                if (client != null && client.currentScreen == this) clearAndInit();
            });
        } catch (RuntimeException exception) {
            choosing = false;
            validation = exception.getMessage();
            clearAndInit();
        }
    }

    @Override
    public void tick() {
        if (observedTransferRevision != transfers.revision()) {
            observedTransferRevision = transfers.revision();
            ClientTransferController.TransferStatus transfer = transfers.status();
            if (choosing && transfer.phase() == ClientTransferController.TransferPhase.FAILED) {
                choosing = false;
                validation = transfer.message();
                clearAndInit();
                return;
            }
        }

        if (!processingImport || observedWorldRevision == worlds.revision()) return;
        observedWorldRevision = worlds.revision();
        if (worlds.lastError() != null) {
            processingImport = false;
            validation = worlds.lastError();
            clearAndInit();
            return;
        }
        if (worlds.activityMessage() == null) {
            processingImport = false;
            if (client != null && client.currentScreen == this) client.setScreen(parent);
        }
    }

    private static String baseName(String artifactName) {
        String name = artifactName == null ? "Imported World" : artifactName.strip();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mcworld")) name = name.substring(0, name.length() - 8);
        else if (lower.endsWith(".zip")) name = name.substring(0, name.length() - 4);
        name = name.strip();
        return name.isEmpty() ? "Imported World" : name;
    }

    private String availableFolderName(String baseName) {
        String base = folderName(baseName);
        String candidate = base;
        int suffix = 2;
        while (folderExists(candidate)) {
            String tail = "_" + suffix++;
            int prefixLength = Math.min(base.length(), Math.max(1, 64 - tail.length()));
            candidate = base.substring(0, prefixLength) + tail;
        }
        return candidate;
    }

    private boolean folderExists(String candidate) {
        return worlds.worlds().stream().anyMatch(world -> world.folderName().equalsIgnoreCase(candidate));
    }

    private static String folderName(String baseName) {
        String normalized = baseName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isEmpty()) normalized = "imported_world";
        if (normalized.length() > 64) normalized = normalized.substring(0, 64);
        return normalized;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        LbUi.background(context, width, height);
        int panelWidth = Math.min(460, width - 48);
        int left = width / 2 - panelWidth / 2;
        LbUi.elevatedPanel(context, left, 26, panelWidth, 248);

        context.drawTextWithShadow(textRenderer, Text.literal("IMPORT WORLD"), left + 24, 44, LbUi.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, Text.literal("Bring an existing world into LazyBuilder"),
                left + 24, 62, LbUi.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer,
                Text.literal("Choose the archive. LazyBuilder handles the setup automatically."),
                left + 24, 80, LbUi.TEXT_SECONDARY);
        context.drawTextWithShadow(textRenderer, Text.literal("World name"), left + 28, 96, LbUi.TEXT_MUTED);
        LbUi.field(context, displayName, validation != null);
        context.drawTextWithShadow(textRenderer, Text.literal("Supported  .zip  •  .mcworld"),
                left + 28, 138, LbUi.TEXT_MUTED);

        String statusLabel = null;
        int percent = -1;
        if (processingImport) {
            statusLabel = worlds.activityMessage() == null ? "Finishing import…" : worlds.activityMessage();
        } else if (choosing) {
            ClientTransferController.TransferStatus transfer = transfers.status();
            if (transfer.phase() != ClientTransferController.TransferPhase.IDLE) {
                statusLabel = transfer.message();
                percent = transfer.percent();
            }
        }

        if (statusLabel != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(statusLabel), width / 2, 238, LbUi.TEXT_SECONDARY);
            if (percent >= 0) LbUi.progress(context, left + 44, 252, panelWidth - 88, percent);
        }
        if (validation != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(validation), width / 2, 256, LbUi.DANGER_BRIGHT);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
