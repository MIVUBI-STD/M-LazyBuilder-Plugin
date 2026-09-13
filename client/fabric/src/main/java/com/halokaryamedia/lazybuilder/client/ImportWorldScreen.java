package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/** File-first import flow; upload and server publication remain one visible operation. */
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
        int center = width / 2;
        int fieldWidth = Math.min(340, width - 60);
        int left = center - fieldWidth / 2;
        boolean busy = choosing || processingImport;

        displayName = new TextFieldWidget(textRenderer, left, 96, fieldWidth, 22, Text.literal("World Name"));
        displayName.setPlaceholder(Text.literal("Optional — uses the file name by default"));
        displayName.setMaxLength(96);
        displayName.active = !busy;
        addDrawableChild(displayName);

        String primaryLabel = processingImport ? "Importing on server…"
                : choosing ? "Uploading world…"
                : "Choose World File";
        ButtonWidget choose = ButtonWidget.builder(Text.literal(primaryLabel), button -> choose())
                .dimensions(center - 125, 140, 250, 24).build();
        choose.active = !busy;
        addDrawableChild(choose);

        addDrawableChild(ButtonWidget.builder(Text.literal(busy ? "Back" : "Cancel"), button -> close())
                .dimensions(center - 50, 178, 100, 20).build());
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
        return worlds.worlds().stream()
                .anyMatch(world -> world.folderName().equalsIgnoreCase(candidate));
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
        renderBackground(context, mouseX, mouseY, delta);
        int panelWidth = Math.min(430, width - 40);
        int left = width / 2 - panelWidth / 2;
        context.fill(left, 14, left + panelWidth, 270, 0xB915191F);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 24, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Choose a world archive. LazyBuilder handles its server folder automatically."),
                width / 2, 48, 0xB8C0CC);
        context.drawTextWithShadow(textRenderer, Text.literal("World Name"), displayName.getX(), 82, 0xAEB7C4);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Supported files: .zip and .mcworld"), width / 2, 168, 0x8F9AA8);

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
            if (percent >= 0) statusLabel += "  " + percent + "%";
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(statusLabel), width / 2, 214, 0xD8DEE9);
            if (percent >= 0) {
                int barWidth = Math.min(300, width - 80);
                int barLeft = width / 2 - barWidth / 2;
                int filled = (int) Math.round(barWidth * (percent / 100.0));
                context.fill(barLeft, 230, barLeft + barWidth, 236, 0xFF303740);
                context.fill(barLeft, 230, barLeft + filled, 236, 0xFFD8DEE9);
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
