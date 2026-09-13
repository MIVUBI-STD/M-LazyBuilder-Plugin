package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * One Import / Export workspace for normal daily use and optional advanced overrides.
 * Import and export keep separate backend owners while sharing one builder-facing surface.
 */
public final class WorldTransferScreen extends Screen {
    public enum Tab { EXPORT, IMPORT }

    private static final String NATIVE_FORMAT = "JAVA_1_21_4";
    private static final DateTimeFormatter EXPORT_SUFFIX = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final WorldTransferPreferences PREFERENCES = new WorldTransferPreferences();

    private final Screen parent;
    private final ClientWorldController worlds;
    private final ClientTransferController transfers;
    private final WorldControlWireProtocol.WorldSummary world;

    private Tab tab;
    private boolean advanced;
    private TextFieldWidget fileName;
    private TextFieldWidget importName;
    private String exportFileName;
    private String importDisplayName = "";
    private String validation;
    private boolean choosing;
    private boolean processingImport;
    private boolean exporting;
    private boolean exportTransferStarted;
    private long observedWorldRevision;
    private long observedTransferRevision;

    public WorldTransferScreen(
            Screen parent,
            ClientWorldController worlds,
            ClientTransferController transfers,
            WorldControlWireProtocol.WorldSummary world,
            Tab initialTab
    ) {
        super(Text.literal("Import / Export"));
        this.parent = parent;
        this.worlds = worlds;
        this.transfers = transfers;
        this.world = world;
        this.tab = world == null ? Tab.IMPORT : initialTab;
        this.observedWorldRevision = worlds.revision();
        this.observedTransferRevision = transfers.revision();
        if (world != null) this.exportFileName = defaultExportFileName(world.displayName());
    }

    @Override
    protected void init() {
        int panelWidth = Math.max(300, Math.min(560, width - 40));
        int left = width / 2 - panelWidth / 2;
        int contentLeft = left + 28;
        int contentWidth = panelWidth - 56;

        int tabWidth = (contentWidth - 8) / 2;
        LbButtonWidget exportTab = LbUi.button(contentLeft, 70, tabWidth, 24,
                "Export", tab == Tab.EXPORT ? LbButtonWidget.Style.PRIMARY : LbButtonWidget.Style.GHOST,
                () -> switchTab(Tab.EXPORT));
        exportTab.active = world != null && !busy();
        addDrawableChild(exportTab);

        LbButtonWidget importTab = LbUi.button(contentLeft + tabWidth + 8, 70, tabWidth, 24,
                "Import", tab == Tab.IMPORT ? LbButtonWidget.Style.PRIMARY : LbButtonWidget.Style.GHOST,
                () -> switchTab(Tab.IMPORT));
        importTab.active = !busy();
        addDrawableChild(importTab);

        if (tab == Tab.EXPORT && world != null) initExport(left, panelWidth, contentLeft, contentWidth);
        else initImport(left, panelWidth, contentLeft, contentWidth);

        addDrawableChild(LbUi.button(width / 2 - 50, height - 34, 100, 22,
                busy() ? "Back" : "Close", LbButtonWidget.Style.GHOST, this::close));
    }

    private void initExport(int left, int panelWidth, int contentLeft, int contentWidth) {
        int y = 150;
        LbButtonWidget export = LbUi.button(contentLeft, y, contentWidth, 30,
                exporting ? "Exporting…" : "Export World", LbButtonWidget.Style.PRIMARY, this::submitExport);
        export.active = !busy();
        addDrawableChild(export);

        y += 42;
        addDrawableChild(LbUi.button(contentLeft, y, contentWidth, 22,
                advanced ? "Advanced options  ▾" : "Advanced options  ▸",
                LbButtonWidget.Style.GHOST, () -> {
                    advanced = !advanced;
                    rememberFields();
                    clearAndInit();
                }));

        if (!advanced) return;
        y += 42;
        if (exportFileName == null || exportFileName.isBlank()) exportFileName = defaultExportFileName(world.displayName());
        fileName = new TextFieldWidget(textRenderer, contentLeft, y + 20, contentWidth, 24, Text.literal("File Name"));
        fileName.setText(exportFileName);
        fileName.setMaxLength(96);
        fileName.setDrawsBackground(false);
        fileName.setEditableColor(LbUi.TEXT_PRIMARY);
        fileName.setUneditableColor(LbUi.TEXT_DISABLED);
        fileName.active = !busy();
        addDrawableChild(fileName);

        y += 58;
        LbButtonWidget reset = LbUi.button(contentLeft, y, contentWidth, 22,
                "Reset Default Export Settings", LbButtonWidget.Style.GHOST, () -> {
                    PREFERENCES.resetExportFormat();
                    validation = null;
                    clearAndInit();
                });
        reset.active = !busy();
        addDrawableChild(reset);
    }

    private void initImport(int left, int panelWidth, int contentLeft, int contentWidth) {
        int y = 146;
        if (advanced) {
            importName = new TextFieldWidget(textRenderer, contentLeft, y, contentWidth, 24, Text.literal("World Name"));
            importName.setText(importDisplayName);
            importName.setPlaceholder(Text.literal("Optional — uses file name"));
            importName.setMaxLength(96);
            importName.setDrawsBackground(false);
            importName.setEditableColor(LbUi.TEXT_PRIMARY);
            importName.setUneditableColor(LbUi.TEXT_DISABLED);
            importName.active = !busy();
            addDrawableChild(importName);
            y += 42;
        }

        String primaryLabel = processingImport ? "Finishing Import…"
                : choosing ? "Uploading World…"
                : "Choose World File";
        LbButtonWidget choose = LbUi.button(contentLeft, y, contentWidth, 30,
                primaryLabel, LbButtonWidget.Style.PRIMARY, this::chooseImport);
        choose.active = !busy();
        addDrawableChild(choose);

        y += 42;
        addDrawableChild(LbUi.button(contentLeft, y, contentWidth, 22,
                advanced ? "Advanced options  ▾" : "Advanced options  ▸",
                LbButtonWidget.Style.GHOST, () -> {
                    advanced = !advanced;
                    rememberFields();
                    clearAndInit();
                }));
    }

    private void switchTab(Tab next) {
        if (busy() || next == tab) return;
        if (next == Tab.EXPORT && world == null) return;
        rememberFields();
        tab = next;
        advanced = false;
        validation = null;
        clearAndInit();
    }

    private void submitExport() {
        if (world == null || busy()) return;
        rememberFields();
        String artifact = exportFileName == null ? "" : exportFileName.strip();
        if (artifact.isEmpty()) {
            validation = "File name is required.";
            advanced = true;
            clearAndInit();
            return;
        }

        String format = validatedDefaultFormat();
        validation = null;
        exporting = true;
        try {
            worlds.exportWorld(world.worldId(), format, artifact);
            observedWorldRevision = worlds.revision();
            clearAndInit();
        } catch (RuntimeException exception) {
            exporting = false;
            validation = exception.getMessage();
            clearAndInit();
        }
    }

    private void chooseImport() {
        if (busy()) return;
        rememberFields();
        String requestedDisplay = importDisplayName.strip();
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
        if (exporting && observedWorldRevision != worlds.revision()) {
            observedWorldRevision = worlds.revision();
            if (worlds.lastError() != null) {
                exporting = false;
                validation = worlds.lastError();
                clearAndInit();
            } else if (worlds.activityMessage() == null
                    && transfers.status().phase() != ClientTransferController.TransferPhase.IDLE) {
                exportTransferStarted = true;
            }
        }

        if (observedTransferRevision != transfers.revision()) {
            observedTransferRevision = transfers.revision();
            ClientTransferController.TransferStatus transfer = transfers.status();
            if ((choosing || exporting) && transfer.phase() == ClientTransferController.TransferPhase.FAILED) {
                choosing = false;
                exporting = false;
                exportTransferStarted = false;
                validation = transfer.message();
                clearAndInit();
                return;
            }
            if (exporting && transfer.phase() != ClientTransferController.TransferPhase.IDLE) {
                exportTransferStarted = true;
            } else if (exporting && exportTransferStarted
                    && transfer.phase() == ClientTransferController.TransferPhase.IDLE) {
                exporting = false;
                exportTransferStarted = false;
                if (client != null && client.currentScreen == this) client.setScreen(parent);
                return;
            }
        }

        if (!processingImport || observedWorldRevision == worlds.revision()) return;
        observedWorldRevision = worlds.revision();
        if (worlds.lastError() != null) {
            processingImport = false;
            validation = worlds.lastError();
            clearAndInit();
        } else if (worlds.activityMessage() == null) {
            processingImport = false;
            if (client != null && client.currentScreen == this) client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        LbUi.background(context, width, height);
        int panelWidth = Math.max(300, Math.min(560, width - 40));
        int left = width / 2 - panelWidth / 2;
        int panelHeight = Math.min(height - 70, advanced ? 330 : 250);
        LbUi.elevatedPanel(context, left, 24, panelWidth, panelHeight);

        context.drawTextWithShadow(textRenderer, Text.literal("IMPORT / EXPORT"), left + 24, 40, LbUi.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer,
                Text.literal(world == null ? "World Transfer" : world.displayName()),
                left + 24, 56, LbUi.TEXT_PRIMARY);

        if (tab == Tab.EXPORT && world != null) renderExport(context, left, panelWidth);
        else renderImport(context, left, panelWidth);

        renderStatus(context, left, panelWidth, panelHeight);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderExport(DrawContext context, int left, int panelWidth) {
        String format = validatedDefaultFormat();
        context.drawTextWithShadow(textRenderer, Text.literal("DEFAULT EXPORT SETTINGS"),
                left + 28, 108, LbUi.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, Text.literal(friendlyFormat(format)),
                left + 28, 124, LbUi.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, Text.literal("Entire world"),
                left + 28, 138, LbUi.TEXT_SECONDARY);

        if (advanced) {
            context.drawTextWithShadow(textRenderer, Text.literal("File name"), left + 28, 218, LbUi.TEXT_MUTED);
            if (fileName != null) LbUi.field(context, fileName, validation != null);
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Additional editions and versions appear only when verified conversion support reports them."),
                    left + 28, 258, LbUi.TEXT_MUTED);
        }
    }

    private void renderImport(DrawContext context, int left, int panelWidth) {
        context.drawTextWithShadow(textRenderer, Text.literal("IMPORT WORLD"),
                left + 28, 108, LbUi.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer,
                Text.literal("Choose a .zip or .mcworld. LazyBuilder detects and prepares it automatically."),
                left + 28, 124, LbUi.TEXT_SECONDARY);
        context.drawTextWithShadow(textRenderer,
                Text.literal("Managed target  •  Java Edition 1.21.4"),
                left + 28, 140, LbUi.TEXT_PRIMARY);
        if (advanced) {
            context.drawTextWithShadow(textRenderer, Text.literal("World name"), left + 28, 132, LbUi.TEXT_MUTED);
            if (importName != null) LbUi.field(context, importName, validation != null);
        }
    }

    private void renderStatus(DrawContext context, int left, int panelWidth, int panelHeight) {
        String status = null;
        int percent = -1;
        ClientTransferController.TransferStatus transfer = transfers.status();
        if (transfer.phase() != ClientTransferController.TransferPhase.IDLE) {
            status = transfer.message();
            percent = transfer.percent();
        } else if (worlds.activityMessage() != null && (exporting || processingImport)) {
            status = worlds.activityMessage();
        }
        if (status != null) {
            int y = 24 + panelHeight - 34;
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, y, LbUi.TEXT_SECONDARY);
            if (percent >= 0) LbUi.progress(context, left + 44, y + 13, panelWidth - 88, percent);
        } else if (validation != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(validation),
                    width / 2, 24 + panelHeight - 24, LbUi.DANGER_BRIGHT);
        }
    }

    private boolean busy() {
        return choosing || processingImport || exporting || transfers.status().active();
    }

    private void rememberFields() {
        if (fileName != null) exportFileName = fileName.getText();
        if (importName != null) importDisplayName = importName.getText();
    }

    private String validatedDefaultFormat() {
        String saved = PREFERENCES.exportFormat();
        // Until a verified format catalog is exposed to the client, never invent unsupported targets.
        if (!NATIVE_FORMAT.equalsIgnoreCase(saved)) {
            PREFERENCES.resetExportFormat();
            return NATIVE_FORMAT;
        }
        return saved;
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
        return worlds.worlds().stream().anyMatch(value -> value.folderName().equalsIgnoreCase(candidate));
    }

    private static String defaultExportFileName(String displayName) {
        return fileStem(displayName) + "-" + EXPORT_SUFFIX.format(LocalDateTime.now());
    }

    private static String fileStem(String displayName) {
        String stem = displayName == null ? "world" : displayName.strip().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-+|-+$", "");
        if (stem.isBlank()) stem = "world";
        return stem.length() > 48 ? stem.substring(0, 48) : stem;
    }

    private static String baseName(String artifactName) {
        String name = artifactName == null ? "Imported World" : artifactName.strip();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mcworld")) name = name.substring(0, name.length() - 8);
        else if (lower.endsWith(".zip")) name = name.substring(0, name.length() - 4);
        name = name.strip();
        return name.isEmpty() ? "Imported World" : name;
    }

    private static String folderName(String baseName) {
        String normalized = baseName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isEmpty()) normalized = "imported_world";
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    private static String friendlyFormat(String format) {
        if ("JAVA_1_21_4".equalsIgnoreCase(format)) return "Java Edition  •  1.21.4";
        if (format != null && format.startsWith("BEDROCK_")) return "Bedrock Edition  •  " + format.substring(8).replace('_', '.');
        if (format != null && format.startsWith("JAVA_")) return "Java Edition  •  " + format.substring(5).replace('_', '.');
        return format == null ? "Java Edition  •  1.21.4" : format;
    }

    @Override
    public void close() {
        rememberFields();
        if (client != null) client.setScreen(parent);
    }
}
