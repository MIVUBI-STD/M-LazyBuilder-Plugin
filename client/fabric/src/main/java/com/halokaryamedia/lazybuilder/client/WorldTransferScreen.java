package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import com.halokaryamedia.lazybuilder.world.map.MapActionWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** One builder-facing Import / Export workspace with daily defaults and optional advanced overrides. */
public final class WorldTransferScreen extends Screen {
    public enum Tab { EXPORT, IMPORT }

    private static final String NATIVE_FORMAT = "JAVA_1_21_4";
    private static final DateTimeFormatter EXPORT_SUFFIX = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final WorldTransferPreferences PREFERENCES = new WorldTransferPreferences();

    private final Screen parent;
    private final ClientWorldController worlds;
    private final ClientTransferController transfers;
    private final ClientMapController maps;
    private final WorldControlWireProtocol.WorldSummary world;
    private final AreaSelection area;

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
    private long observedMapRevision;

    public WorldTransferScreen(
            Screen parent,
            ClientWorldController worlds,
            ClientTransferController transfers,
            WorldControlWireProtocol.WorldSummary world,
            Tab initialTab
    ) {
        this(parent, worlds, transfers, null, world, initialTab, null);
    }

    private WorldTransferScreen(
            Screen parent,
            ClientWorldController worlds,
            ClientTransferController transfers,
            ClientMapController maps,
            WorldControlWireProtocol.WorldSummary world,
            Tab initialTab,
            AreaSelection area
    ) {
        super(Text.literal("Import / Export"));
        this.parent = parent;
        this.worlds = worlds;
        this.transfers = transfers;
        this.maps = maps;
        this.world = world;
        this.area = area;
        this.tab = world == null ? Tab.IMPORT : initialTab;
        this.observedWorldRevision = worlds.revision();
        this.observedTransferRevision = transfers.revision();
        this.observedMapRevision = maps == null ? 0L : maps.revision();
        this.selectedExportFormat = PREFERENCES.exportFormat();
        if (world != null) {
            String suffix = area == null ? "" : "-area";
            this.exportFileName = fileStem(world.displayName()) + suffix + "-" + EXPORT_SUFFIX.format(LocalDateTime.now());
        }
    }

    public static WorldTransferScreen forArea(
            Screen parent,
            ClientWorldController worlds,
            ClientTransferController transfers,
            ClientMapController maps,
            MapActionWireProtocol.CurrentWorldResult current,
            int x1,
            int z1,
            int x2,
            int z2
    ) {
        WorldControlWireProtocol.WorldSummary summary = new WorldControlWireProtocol.WorldSummary(
                current.worldId().value(), current.folderName(), current.displayName(),
                "IMPORTED", "ACTIVE", "CREATIVE");
        return new WorldTransferScreen(parent, worlds, transfers, maps, summary, Tab.EXPORT,
                new AreaSelection(x1, z1, x2, z2));
    }

    @Override
    protected void init() {
        if (world != null && !requestedFormats) {
            requestedFormats = true;
            worlds.requestExportFormats();
        }
        reconcileSelectedFormat();

        int panelWidth = Math.max(320, Math.min(580, width - 40));
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
        importTab.active = area == null && !busy();
        addDrawableChild(importTab);

        if (tab == Tab.EXPORT && world != null) initExport(contentLeft, contentWidth);
        else initImport(contentLeft, contentWidth);

        addDrawableChild(LbUi.button(width / 2 - 50, height - 34, 100, 22,
                busy() ? "Back" : "Close", LbButtonWidget.Style.GHOST, this::close));
    }

    private void initExport(int contentLeft, int contentWidth) {
        int y = 158;
        LbButtonWidget export = LbUi.button(contentLeft, y, contentWidth, 30,
                exporting ? "Exporting…" : area == null ? "Export World" : "Export Area",
                LbButtonWidget.Style.PRIMARY, this::submitExport);
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
                LbButtonWidget.Style.SECONDARY, this::cycleExportFormat);
        format.active = !busy() && availableFormats().size() > 1;
        addDrawableChild(format);

        y += 46;
        fileName = new TextFieldWidget(textRenderer, contentLeft, y + 18, contentWidth, 24, Text.literal("File Name"));
        fileName.setText(exportFileName == null ? "" : exportFileName);
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

        String label = processingImport ? "Finishing Import…" : choosing ? "Uploading World…" : "Choose World File";
        LbButtonWidget choose = LbUi.button(contentLeft, y, contentWidth, 30,
                label, LbButtonWidget.Style.PRIMARY, this::chooseImport);
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
        if (busy() || next == tab || (area != null && next == Tab.IMPORT)) return;
        if (next == Tab.EXPORT && world == null) return;
        rememberFields();
        tab = next;
        advanced = false;
        validation = null;
        clearAndInit();
    }

    private void cycleExportFormat() {
        List<String> formats = availableFormats();
        if (busy() || formats.size() < 2) return;
        int index = formats.indexOf(selectedExportFormat);
        selectedExportFormat = formats.get((Math.max(0, index) + 1) % formats.size());
        validation = null;
        clearAndInit();
    }

    private void saveCurrentAsDefault() {
        if (busy()) return;
        reconcileSelectedFormat();
        PREFERENCES.setExportFormat(selectedExportFormat);
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
            if (area == null) {
                worlds.exportWorld(world.worldId(), selectedExportFormat, artifact);
                observedWorldRevision = worlds.revision();
            } else {
                maps.exportAreaCurrent(area.x1, area.z1, area.x2, area.z2, selectedExportFormat, artifact);
                observedMapRevision = maps.revision();
            }
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

        if (exporting && area == null && observedWorldRevision != worlds.revision()) {
            observedWorldRevision = worlds.revision();
            if (worlds.lastError() != null) failExport(worlds.lastError());
            else if (worlds.activityMessage() == null
                    && transfers.status().phase() != ClientTransferController.TransferPhase.IDLE) exportTransferStarted = true;
        }

        if (exporting && area != null && observedMapRevision != maps.revision()) {
            observedMapRevision = maps.revision();
            if (maps.lastError() != null) failExport(maps.lastError());
            else if (!maps.exportBusy() && transfers.status().phase() != ClientTransferController.TransferPhase.IDLE) {
                exportTransferStarted = true;
            }
        }

        if (observedTransferRevision != transfers.revision()) {
            observedTransferRevision = transfers.revision();
            ClientTransferController.TransferStatus transfer = transfers.status();
            if ((choosing || exporting) && transfer.phase() == ClientTransferController.TransferPhase.FAILED) {
                choosing = false;
                failExport(transfer.message());
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

    private void failExport(String message) {
        exporting = false;
        exportTransferStarted = false;
        validation = message;
        clearAndInit();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        LbUi.background(context, width, height);
        int panelWidth = Math.max(320, Math.min(580, width - 40));
        int left = width / 2 - panelWidth / 2;
        int panelHeight = Math.min(height - 70, advanced ? 370 : 260);
        LbUi.elevatedPanel(context, left, 24, panelWidth, panelHeight);

        context.drawTextWithShadow(textRenderer, Text.literal("IMPORT / EXPORT"), left + 24, 40, LbUi.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, Text.literal(world == null ? "Import World" : world.displayName()),
                left + 24, 56, LbUi.TEXT_PRIMARY);

        if (tab == Tab.EXPORT && world != null) renderExport(context, left);
        else renderImport(context, left);
        renderStatus(context, left, panelWidth, panelHeight);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderExport(DrawContext context, int left) {
        context.drawTextWithShadow(textRenderer, Text.literal("DEFAULT EXPORT SETTINGS"), left + 28, 108, LbUi.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, Text.literal(friendlyFormat(defaultFormat())), left + 28, 124, LbUi.TEXT_PRIMARY);
        if (area == null) {
            context.drawTextWithShadow(textRenderer, Text.literal("Entire world"), left + 28, 140, LbUi.TEXT_SECONDARY);
        } else {
            context.drawTextWithShadow(textRenderer, Text.literal("Selected map area"), left + 28, 140, LbUi.TEXT_SECONDARY);
            context.drawTextWithShadow(textRenderer,
                    Text.literal("X " + area.minX() + " → " + area.maxX() + "    Z " + area.minZ() + " → " + area.maxZ()),
                    left + 28, 152, LbUi.TEXT_MUTED);
        }

        if (advanced) {
            context.drawTextWithShadow(textRenderer, Text.literal("Target edition / version"), left + 28, 222, LbUi.TEXT_MUTED);
            context.drawTextWithShadow(textRenderer, Text.literal("File name"), left + 28, 268, LbUi.TEXT_MUTED);
            if (fileName != null) LbUi.field(context, fileName, validation != null);
            String capability = availableFormats().size() > 1
                    ? "Only verified server-supported targets are listed."
                    : "Only native Java Edition 1.21.4 is currently verified.";
            context.drawTextWithShadow(textRenderer, Text.literal(capability), left + 28, 312, LbUi.TEXT_MUTED);
        }
    }

    private void renderImport(DrawContext context, int left) {
        context.drawTextWithShadow(textRenderer, Text.literal("IMPORT WORLD"), left + 28, 108, LbUi.TEXT_MUTED);
        if (advanced) {
            context.drawTextWithShadow(textRenderer, Text.literal("World name"), left + 28, 128, LbUi.TEXT_MUTED);
            if (importName != null) LbUi.field(context, importName, validation != null);
            context.drawTextWithShadow(textRenderer, Text.literal("Source edition and version are detected automatically."),
                    left + 28, 170, LbUi.TEXT_MUTED);
        } else {
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Choose a .zip or .mcworld. LazyBuilder handles detection automatically."),
                    left + 28, 126, LbUi.TEXT_SECONDARY);
        }
        context.drawTextWithShadow(textRenderer, Text.literal("Managed target  •  Java Edition 1.21.4"),
                left + 28, advanced ? 186 : 144, LbUi.TEXT_PRIMARY);
    }

    private void renderStatus(DrawContext context, int left, int panelWidth, int panelHeight) {
        String status = null;
        int percent = -1;
        ClientTransferController.TransferStatus transfer = transfers.status();
        if (transfer.phase() != ClientTransferController.TransferPhase.IDLE) {
            status = transfer.message();
            percent = transfer.percent();
        } else if (processingImport && worlds.activityMessage() != null) {
            status = worlds.activityMessage();
        } else if (exporting) {
            if (area != null && maps.exportBusy()) status = "Preparing selected area…";
            else if (worlds.activityMessage() != null) status = worlds.activityMessage();
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
        return formats == null || formats.isEmpty() ? List.of(NATIVE_FORMAT) : formats;
    }

    private void reconcileSelectedFormat() {
        List<String> formats = availableFormats();
        if (formats.stream().noneMatch(format -> format.equalsIgnoreCase(selectedExportFormat))) {
            selectedExportFormat = formats.stream().filter(format -> format.equalsIgnoreCase(NATIVE_FORMAT))
                    .findFirst().orElse(formats.get(0));
        }
        String saved = PREFERENCES.exportFormat();
        if (formats.stream().noneMatch(format -> format.equalsIgnoreCase(saved))) {
            PREFERENCES.setExportFormat(selectedExportFormat);
        }
    }

    private String defaultFormat() {
        String saved = PREFERENCES.exportFormat();
        return availableFormats().stream().filter(format -> format.equalsIgnoreCase(saved))
                .findFirst().orElse(NATIVE_FORMAT);
    }

    private boolean isCurrentDefault() {
        return selectedExportFormat.equalsIgnoreCase(defaultFormat());
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

    private static String friendlyFormat(String format) {
        if (format == null) return "Java Edition • 1.21.4";
        String value = format.toUpperCase(Locale.ROOT);
        if (value.equals("JAVA_1_21_4")) return "Java Edition • 1.21.4";
        if (value.startsWith("JAVA_")) return "Java Edition • " + value.substring(5).replace('_', '.');
        if (value.startsWith("BEDROCK_")) return "Bedrock Edition • " + value.substring(8).replace('_', '.');
        return value.replace('_', ' ');
    }

    private static String fileStem(String displayName) {
        String stem = displayName == null ? "world" : displayName.strip().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-").replaceAll("-+", "-").replaceAll("^-+|-+$", "");
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
                .replaceAll("[^a-z0-9_-]+", "_").replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        if (normalized.isEmpty()) normalized = "imported_world";
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    private record AreaSelection(int x1, int z1, int x2, int z2) {
        int minX() { return Math.min(x1, x2); }
        int maxX() { return Math.max(x1, x2); }
        int minZ() { return Math.min(z1, z2); }
        int maxZ() { return Math.max(z1, z2); }
    }
}
