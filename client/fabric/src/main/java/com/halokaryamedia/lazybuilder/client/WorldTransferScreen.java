package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
    private boolean requestedFormats;
    private TextFieldWidget fileName;
    private TextFieldWidget importName;
    private String exportFileName;
    private String importDisplayName = "";
    private String selectedExportFormat;
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
        this.selectedExportFormat = PREFERENCES.exportFormat();
        if (world != null) this.exportFileName = defaultExportFileName(world.displayName());
    }

    @Override
    protected void init() {
        if (world != null && !requestedFormats) {
            requestedFormats = true;
            worlds.requestExportFormats();
        }
        reconcileSelectedFormat();

        int panelWidth = Math.max(300, Math.min(580, width - 40));
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

        if (tab == Tab.EXPORT && world != null) initExport(contentLeft, contentWidth);
        else initImport(contentLeft, contentWidth);

        addDrawableChild(LbUi.button(width / 2 - 50, height - 34, 100, 22,
                busy() ? "Back" : "Close", LbButtonWidget.Style.GHOST, this::close));
    }

    private void initExport(int contentLeft, int contentWidth) {
        int y = 150;
        LbButtonWidget export = LbUi.button(contentLeft, y, contentWidth, 30,
                exporting ? "Exporting…" : "Export World", LbButtonWidget.Style.PRIMARY, this::submitExport);
        export.active = !busy();
        addDrawableChild(export);

        y += 42;
        addDrawableChild(LbUi.button(contentLeft, y, contentWidth, 22,
                advanced ? "Advanced options  ▾" : "Advanced options  ▸",
                LbButtonWidget.Style.GHOST, () -> {
                    rememberFields();
                    advanced = !advanced;
                    validation = null;
                    clearAndInit();
                }));

        if (!advanced) return;

        y += 36;
        LbButtonWidget format = LbUi.button(contentLeft, y, contentWidth, 26,
                "Export as   " + friendlyFormat(selectedExportFormat),
                LbButtonWidget.Style.SECONDARY,
                this::cycleExportFormat);
        format.active = !busy() && availableFormats().size() > 1;
        addDrawableChild(format);

        y += 46;
        if (exportFileName == null || exportFileName.isBlank()) exportFileName = defaultExportFileName(world.displayName());
        fileName = new TextFieldWidget(textRenderer, contentLeft, y + 18, contentWidth, 24, Text.literal("File Name"));
        fileName.setText(exportFileName);
        fileName.setMaxLength(96);
        fileName.setDrawsBackground(false);
        fileName.setEditableColor(LbUi.TEXT_PRIMARY);
        fileName.setUneditableColor(LbUi.TEXT_DISABLED);
        fileName.active = !busy();
        addDrawableChild(fileName);

        y += 58;
        LbButtonWidget saveDefault = LbUi.button(contentLeft, y, contentWidth, 22,
                isCurrentDefault() ? "Default Export Settings" : "Use These as Default",
                isCurrentDefault() ? LbButtonWidget.Style.GHOST : LbButtonWidget.Style.SECONDARY,
                this::saveCurrentAsDefault);
        saveDefault.active = !busy() && !isCurrentDefault();
        addDrawableChild(saveDefault);
    }

    private void initImport(int contentLeft, int contentWidth) {
        int y = advanced ? 178 : 156;
        if (advanced) {
            importName = new TextFieldWidget(textRenderer, contentLeft, 140, contentWidth, 24, Text.literal("World Name"));
            importName.setText(importDisplayName);
            importName.setPlaceholder(Text.literal("Optional — uses detected file name"));
            importName.setMaxLength(96);
            importName.setDrawsBackground(false);
            importName.setEditableColor(LbUi.TEXT_PRIMARY);
            importName.setUneditableColor(LbUi.TEXT_DISABLED);
            importName.active = !busy();
            addDrawableChild(importName);
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
                    rememberFields();
                    advanced = !advanced;
                    validation = null;
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

    private void cycleExportFormat() {
        if (busy()) return;
        List<String> formats = availableFormats();
        if (formats.size() < 2) return;
        int index = formats.indexOf(selectedExportFormat);
        selectedExportFormat = formats.get((Math.max(0, index) + 1) % formats.size());
        validation = null;
        clearAndInit();
    }

    private void saveCurrentAsDefault() {
        if (busy()) return;
        reconcileSelectedFormat();
        PREFERENCES.setExportFormat(selectedExportFormat);
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

        reconcileSelectedFormat();
        validation = null;
        exporting = true;
        try {
            worlds.exportWorld(world.worldId(), selectedExportFormat, artifact);
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
        if (!busy() && observedWorldRevision != worlds.revision()) {
            observedWorldRevision = worlds.revision();
            reconcileSelectedFormat();
            clearAndInit();
            return;
        }

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
        int panelWidth = Math.max(300, Math.min(580, width - 40));
        int left = width / 2 - panelWidth / 2;
        int panelHeight = Math.min(height - 70, advanced ? 360 : 250);
        LbUi.elevatedPanel(context, left, 24, panelWidth, panelHeight);

        context.drawTextWithShadow(textRenderer, Text.literal("IMPORT / EXPORT"), left + 24, 40, LbUi.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer,
                Text.literal(world == null ? "World Transfer" : world.displayName()),
                left + 24, 56, LbUi.TEXT_PRIMARY);

        if (tab == Tab.EXPORT && world != null) renderExport(context, left);
        else renderImport(context, left);

        renderStatus(context, left, panelWidth, panelHeight);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderExport(DrawContext context, int left) {
        context.drawTextWithShadow(textRenderer, Text.literal("DEFAULT EXPORT SETTINGS"),
                left + 28, 108, LbUi.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, Text.literal(friendlyFormat(defaultFormat())),
                left + 28, 124, LbUi.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, Text.literal("Entire world"),
                left + 28, 138, LbUi.TEXT_SECONDARY);

        if (advanced) {
            context.drawTextWithShadow(textRenderer, Text.literal("Target edition / version"),
                    left + 28, 220, LbUi.TEXT_MUTED);
            context.drawTextWithShadow(textRenderer, Text.literal("File name"), left + 28, 266, LbUi.TEXT_MUTED);
            if (fileName != null) LbUi.field(context, fileName, validation != null);
            String capability = availableFormats().size() > 1
                    ? "Only verified converter targets are listed."
                    : "Only native Java 1.21.4 is currently verified on this server.";
            context.drawTextWithShadow(textRenderer, Text.literal(capability), left + 28, 310, LbUi.TEXT_MUTED);
        }
    }

    private void renderImport(DrawContext context, int left) {
        context.drawTextWithShadow(textRenderer, Text.literal("IMPORT WORLD"),
                left + 28, 108, LbUi.TEXT_MUTED);
        if (advanced) {
            context.drawTextWithShadow(textRenderer, Text.literal("World name"), left + 28, 128, LbUi.TEXT_MUTED);
            if (importName != null) LbUi.field(context, importName, validation != null);
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Source edition and version are detected automatically."),
                    left + 28, 170, LbUi.TEXT_MUTED);
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Managed target  •  Java Edition 1.21.4"),
                    left + 28, 184, LbUi.TEXT_PRIMARY);
        } else {
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Choose a .zip or .mcworld. LazyBuilder handles detection automatically."),
                    left + 28, 126, LbUi.TEXT_SECONDARY);
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Managed target  •  Java Edition 1.21.4"),
                    left + 28, 142, LbUi.TEXT_PRIMARY);
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

    private List<String> availableFormats() {
        List<String> formats = worlds.exportFormats();
        if (formats == null || formats.isEmpty()) return List.of(NATIVE_FORMAT);
        return formats;
    }

    private void reconcileSelectedFormat() {
        List<String> formats = availableFormats();
        if (!formats.contains(selectedExportFormat)) {
            String saved = PREFERENCES.exportFormat();
            selectedExportFormat = formats.contains(saved) ? saved
                    : formats.contains(NATIVE_FORMAT) ? NATIVE_FORMAT : formats.get(0);
        }
    }

    private String defaultFormat() {
        List<String> formats = availableFormats();
        String saved = PREFERENCES.exportFormat();
        if (formats.contains(saved)) return saved;
        return formats.contains(NATIVE_FORMAT) ? NATIVE_FORMAT : formats.get(0);
    }

    private boolean isCurrentDefault() {
        return selectedExportFormat != null && selectedExportFormat.equals(defaultFormat());
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
        if (format != null && format.startsWith("BEDROCK_")) {
            return "Bedrock Edition  •  " + format.substring(8).replace('_', '.');
        }
        if (format != null && format.startsWith("JAVA_")) {
            return "Java Edition  •  " + format.substring(5).replace('_', '.');
        }
        return format == null ? "Java Edition  •  1.21.4" : format;
    }

    @Override
    public void close() {
        rememberFields();
        if (client != null) client.setScreen(parent);
    }
}
