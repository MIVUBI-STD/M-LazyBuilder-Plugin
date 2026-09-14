package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Builder-first quick navigator and Manage World workspace reached from the fullscreen map. */
public final class WorldManagerScreen extends Screen {
    private static final WorldNavigationPreferences NAVIGATION = new WorldNavigationPreferences();

    private final Screen parent;
    private final ClientWorldController controller;
    private final ClientTransferController transfers;
    private final ClientMapController maps;

    private UUID selectedWorld;
    private String query = "";
    private int scrollOffset;
    private boolean showArchived;
    private boolean compactDetail;
    private boolean searchEditing;
    private boolean requestedInitialRefresh;
    private boolean requestedCurrentWorld;
    private long observedRevision;
    private long observedMapRevision;
    private TextFieldWidget search;
    private List<ListEntry> currentEntries = List.of();

    public WorldManagerScreen(
            Screen parent,
            ClientWorldController controller,
            ClientTransferController transfers,
            ClientMapController maps
    ) {
        super(Text.literal("Worlds"));
        this.parent = parent;
        this.controller = controller;
        this.transfers = transfers;
        this.maps = maps;
        this.observedRevision = controller.revision();
        this.observedMapRevision = maps.revision();
    }

    public WorldManagerScreen(ClientWorldController controller, ClientTransferController transfers, ClientMapController maps) {
        this(null, controller, transfers, maps);
    }

    @Override
    protected void init() {
        if (!requestedInitialRefresh) {
            requestedInitialRefresh = true;
            controller.refresh();
        }
        if (!requestedCurrentWorld) {
            requestedCurrentWorld = true;
            maps.refreshCurrentWorld();
        }
        observeCurrentWorld();
        observedRevision = controller.revision();
        observedMapRevision = maps.revision();

        List<WorldControlWireProtocol.WorldSummary> worlds = controller.worlds();
        if (selectedWorld != null && worlds.stream().noneMatch(world -> world.worldId().equals(selectedWorld))) {
            selectedWorld = null;
            compactDetail = false;
        }

        Layout l = layout();
        currentEntries = buildEntries(worlds);
        int maxOffset = Math.max(0, currentEntries.size() - maxVisibleEntries(l));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset));

        if (!l.compact || !compactDetail) addListControls(l);
        WorldControlWireProtocol.WorldSummary selected = selected();
        if (selected != null && (!l.compact || compactDetail)) addManageControls(l, selected);

        if (l.compact && compactDetail) {
            addDrawableChild(LbUi.button(l.left + 12, l.bottom + 8, 108, 20, "Back to Worlds",
                    LbButtonWidget.Style.GHOST, () -> {
                        compactDetail = false;
                        clearAndInit();
                    }));
        }
        addDrawableChild(LbUi.button(l.right - 104, l.bottom + 8, 94, 20, "Back to Map",
                LbButtonWidget.Style.GHOST, this::returnToMap));
    }

    private void addListControls(Layout l) {
        int left = l.listLeft();
        int width = l.listPaneWidth();
        boolean busy = operationBusy();
        int trailing = controller.canManage() ? 156 : 28;

        search = new TextFieldWidget(textRenderer, left + 14, 58, Math.max(90, width - trailing), 22,
                Text.literal("Search worlds"));
        search.setPlaceholder(Text.literal("Search worlds…"));
        search.setText(query);
        search.setMaxLength(96);
        search.setDrawsBackground(false);
        search.setEditableColor(LbUi.TEXT_PRIMARY);
        search.setUneditableColor(LbUi.TEXT_DISABLED);
        search.setChangedListener(value -> {
            query = value;
            scrollOffset = 0;
            searchEditing = true;
            clearAndInit();
        });
        addDrawableChild(search);
        if (searchEditing) setInitialFocus(search);

        if (controller.canManage()) {
            LbButtonWidget add = LbUi.button(left + width - 132, 58, 118, 22, "+ Add World",
                    LbButtonWidget.Style.PRIMARY,
                    () -> { if (client != null) client.setScreen(new AddWorldScreen(this, controller, transfers)); });
            add.active = !busy;
            addDrawableChild(add);
        }

        int y = 96;
        int shown = 0;
        for (int i = scrollOffset; i < currentEntries.size() && shown < maxVisibleEntries(l); i++, shown++) {
            ListEntry entry = currentEntries.get(i);
            if (entry.world == null) {
                y += 22;
                continue;
            }
            addWorldRow(l, entry.world, y, busy);
            y += 32;
        }

        if (query.isBlank() && controller.canManage()) {
            int footerY = l.bottom - 28;
            LbButtonWidget archived = LbUi.button(left + 14, footerY, Math.max(110, width - 28), 20,
                    showArchived ? "Hide Archived" : "Archived Worlds  " + archivedCount(),
                    LbButtonWidget.Style.GHOST, () -> {
                        showArchived = !showArchived;
                        scrollOffset = 0;
                        searchEditing = false;
                        clearAndInit();
                    });
            archived.active = !busy;
            addDrawableChild(archived);
        }
    }

    private void addWorldRow(Layout l, WorldControlWireProtocol.WorldSummary world, int y, boolean busy) {
        int left = l.listLeft() + 14;
        int width = l.listPaneWidth() - 28;
        boolean archived = "ARCHIVED".equals(world.lifecycle());
        boolean current = isCurrentWorld(world.worldId());
        boolean pinned = NAVIGATION.isPinned(world.worldId());
        boolean canTeleport = controller.canTeleport() && !archived;
        boolean canManage = controller.canManage();

        LbButtonWidget pin = LbUi.button(left, y, 22, 26, pinned ? "★" : "☆",
                pinned ? LbButtonWidget.Style.SECONDARY : LbButtonWidget.Style.GHOST, () -> {
                    NAVIGATION.togglePinned(world.worldId());
                    searchEditing = false;
                    clearAndInit();
                });
        pin.active = !archived && !busy;
        addDrawableChild(pin);

        int rightActionWidth = (canTeleport ? 70 : 0) + (canManage ? 68 : 0);
        int nameWidth = Math.max(70, width - 28 - rightActionWidth);
        LbButtonWidget name = LbUi.button(left + 28, y, nameWidth, 26,
                world.displayName(), world.worldId().equals(selectedWorld)
                        ? LbButtonWidget.Style.SECONDARY : LbButtonWidget.Style.GHOST,
                () -> openManage(world.worldId(), l.compact));
        name.active = canManage && !busy;
        addDrawableChild(name);

        int actionX = left + 28 + nameWidth + 6;
        if (canTeleport) {
            LbButtonWidget teleport = LbUi.button(actionX, y, 64, 26,
                    current ? "Here" : "Teleport",
                    current ? LbButtonWidget.Style.GHOST : LbButtonWidget.Style.SECONDARY,
                    () -> controller.teleport(world.worldId()));
            teleport.active = !current && !busy;
            addDrawableChild(teleport);
            actionX += 70;
        }

        if (canManage) {
            LbButtonWidget manage = LbUi.button(actionX, y, 62, 26,
                    "Manage", LbButtonWidget.Style.GHOST,
                    () -> openManage(world.worldId(), l.compact));
            manage.active = !busy;
            addDrawableChild(manage);
        }
    }

    private void openManage(UUID worldId, boolean compact) {
        if (!controller.canManage()) return;
        selectedWorld = worldId;
        searchEditing = false;
        if (compact) compactDetail = true;
        clearAndInit();
    }

    private void addManageControls(Layout l, WorldControlWireProtocol.WorldSummary world) {
        boolean busy = operationBusy();
        int x = l.detailPaneLeft() + 24;
        int contentWidth = Math.max(1, l.detailPaneWidth() - 48);
        int y = 124;

        if (!controller.canManage()) {
            if (controller.canTeleport() && !"ARCHIVED".equals(world.lifecycle()) && !isCurrentWorld(world.worldId())) {
                LbButtonWidget teleport = LbUi.button(x, y, contentWidth, 30, "Teleport",
                        LbButtonWidget.Style.PRIMARY, () -> controller.teleport(world.worldId()));
                teleport.active = !busy;
                addDrawableChild(teleport);
            }
            return;
        }

        if ("ARCHIVED".equals(world.lifecycle())) {
            LbButtonWidget restore = LbUi.button(x, y, contentWidth, 30, "Restore World",
                    LbButtonWidget.Style.PRIMARY, () -> controller.restore(world.worldId()));
            restore.active = !busy;
            addDrawableChild(restore);
            y += 48;
            LbButtonWidget delete = LbUi.button(x, y, contentWidth, 26, "Delete Permanently",
                    LbButtonWidget.Style.DANGER,
                    () -> { if (client != null) client.setScreen(new DeleteWorldScreen(this, controller, world)); });
            delete.active = !busy;
            addDrawableChild(delete);
            return;
        }

        if (controller.canTeleport() && !isCurrentWorld(world.worldId())) {
            LbButtonWidget teleport = LbUi.button(x, y, contentWidth, 30, "Teleport",
                    LbButtonWidget.Style.PRIMARY, () -> controller.teleport(world.worldId()));
            teleport.active = !busy;
            addDrawableChild(teleport);
        }
        y += 44;

        LbButtonWidget transfer = LbUi.button(x, y, contentWidth, 28, "Import / Export",
                LbButtonWidget.Style.SECONDARY,
                () -> { if (client != null) client.setScreen(new WorldTransferScreen(
                        this, controller, transfers, world, WorldTransferScreen.Tab.EXPORT)); });
        transfer.active = !busy;
        addDrawableChild(transfer);
        y += 40;

        LbButtonWidget duplicate = LbUi.button(x, y, contentWidth, 26, "Duplicate",
                LbButtonWidget.Style.GHOST,
                () -> { if (client != null) client.setScreen(new DuplicateWorldScreen(this, controller, world)); });
        duplicate.active = !busy;
        addDrawableChild(duplicate);
        y += 34;

        LbButtonWidget settings = LbUi.button(x, y, contentWidth, 26, "World Settings",
                LbButtonWidget.Style.GHOST,
                () -> { if (client != null) client.setScreen(new WorldSettingsScreen(this, controller, world)); });
        settings.active = !busy;
        addDrawableChild(settings);
        y += 46;

        LbButtonWidget archive = LbUi.button(x, y, contentWidth, 24, "Archive",
                LbButtonWidget.Style.GHOST, () -> confirmArchive(world));
        archive.active = !busy && !isCurrentWorld(world.worldId());
        addDrawableChild(archive);
        y += 32;

        LbButtonWidget delete = LbUi.button(x, y, contentWidth, 24, "Delete",
                LbButtonWidget.Style.DANGER,
                () -> { if (client != null) client.setScreen(new DeleteWorldScreen(this, controller, world)); });
        delete.active = !busy;
        addDrawableChild(delete);
    }

    private void confirmArchive(WorldControlWireProtocol.WorldSummary world) {
        if (client == null || !controller.canManage()) return;
        if (isCurrentWorld(world.worldId())) return;
        client.setScreen(new ConfirmWorldActionScreen(
                this,
                Text.literal("Archive World"),
                Text.literal("Archive " + world.displayName() + "? It will leave the daily workspace until restored."),
                "Archive",
                () -> controller.archive(world.worldId())
        ));
    }

    private List<ListEntry> buildEntries(List<WorldControlWireProtocol.WorldSummary> worlds) {
        String normalizedQuery = query.strip().toLowerCase(Locale.ROOT);
        if (!normalizedQuery.isEmpty()) {
            return worlds.stream()
                    .filter(world -> "ACTIVE".equals(world.lifecycle()))
                    .filter(world -> world.displayName().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                    .sorted(java.util.Comparator.comparing(WorldControlWireProtocol.WorldSummary::displayName,
                            String.CASE_INSENSITIVE_ORDER))
                    .map(world -> new ListEntry(null, world))
                    .toList();
        }

        List<WorldControlWireProtocol.WorldSummary> active = worlds.stream()
                .filter(world -> "ACTIVE".equals(world.lifecycle())).toList();
        List<WorldControlWireProtocol.WorldSummary> archived = worlds.stream()
                .filter(world -> "ARCHIVED".equals(world.lifecycle())).toList();

        Set<UUID> used = new HashSet<>();
        List<ListEntry> entries = new ArrayList<>();

        List<WorldControlWireProtocol.WorldSummary> pinned = NAVIGATION.pinned().stream()
                .map(id -> find(active, id)).filter(java.util.Objects::nonNull).toList();
        appendSection(entries, "PINNED", pinned, used);

        List<WorldControlWireProtocol.WorldSummary> recent = NAVIGATION.recent().stream()
                .map(id -> find(active, id)).filter(java.util.Objects::nonNull).toList();
        appendSection(entries, "RECENT", recent, used);

        List<WorldControlWireProtocol.WorldSummary> remaining = active.stream()
                .filter(world -> !used.contains(world.worldId()))
                .sorted(java.util.Comparator.comparing(WorldControlWireProtocol.WorldSummary::displayName,
                        String.CASE_INSENSITIVE_ORDER)).toList();
        appendSection(entries, "ALL WORLDS", remaining, used);

        if (showArchived && controller.canManage()) {
            List<WorldControlWireProtocol.WorldSummary> sortedArchived = archived.stream()
                    .sorted(java.util.Comparator.comparing(WorldControlWireProtocol.WorldSummary::displayName,
                            String.CASE_INSENSITIVE_ORDER)).toList();
            appendSection(entries, "ARCHIVED", sortedArchived, new HashSet<>());
        }
        return List.copyOf(entries);
    }

    private static void appendSection(List<ListEntry> entries, String title,
                                      List<WorldControlWireProtocol.WorldSummary> worlds, Set<UUID> used) {
        List<WorldControlWireProtocol.WorldSummary> available = worlds.stream()
                .filter(world -> !used.contains(world.worldId())).toList();
        if (available.isEmpty()) return;
        entries.add(new ListEntry(title, null));
        for (WorldControlWireProtocol.WorldSummary world : available) {
            entries.add(new ListEntry(null, world));
            used.add(world.worldId());
        }
    }

    private static WorldControlWireProtocol.WorldSummary find(
            List<WorldControlWireProtocol.WorldSummary> worlds, UUID id) {
        return worlds.stream().filter(world -> world.worldId().equals(id)).findFirst().orElse(null);
    }

    @Override
    public void tick() {
        if (observedMapRevision != maps.revision()) {
            observedMapRevision = maps.revision();
            observeCurrentWorld();
            clearAndInit();
            return;
        }
        if (observedRevision != controller.revision()) {
            observedRevision = controller.revision();
            clearAndInit();
        }
    }

    private void observeCurrentWorld() {
        if (maps.currentWorld() != null) {
            NAVIGATION.recordVisited(maps.currentWorld().worldId().value());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Layout l = layout();
        if ((!l.compact || !compactDetail)
                && mouseX >= l.listLeft() && mouseX <= l.listLeft() + l.listPaneWidth()
                && mouseY >= 88 && mouseY <= l.bottom) {
            int maxOffset = Math.max(0, currentEntries.size() - maxVisibleEntries(l));
            int next = Math.max(0, Math.min(maxOffset, scrollOffset - (int) Math.signum(verticalAmount)));
            if (next != scrollOffset) {
                scrollOffset = next;
                searchEditing = search != null && search.isFocused();
                clearAndInit();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        LbUi.background(context, width, height);
        Layout l = layout();
        context.drawTextWithShadow(textRenderer, Text.literal("LAZYBUILDER"), l.left, 14, LbUi.TEXT_MUTED);

        if (!l.compact || !compactDetail) renderWorldListPane(context, l);
        if (!l.compact || compactDetail) renderManagePane(context, l);
        renderOperationStatus(context, l);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderWorldListPane(DrawContext context, Layout l) {
        int left = l.listLeft();
        int width = l.listPaneWidth();
        LbUi.panel(context, left, 34, width, l.bottom - 34);
        context.drawTextWithShadow(textRenderer, Text.literal("WORLDS"), left + 14, 42, LbUi.TEXT_PRIMARY);
        if (!controller.canManage() && controller.canTeleport()) {
            context.drawTextWithShadow(textRenderer, Text.literal("Navigation access"), left + 76, 42, LbUi.TEXT_MUTED);
        }
        if (search != null) LbUi.field(context, search, false);
        LbUi.divider(context, left + 14, 88, left + width - 14);

        int y = 96;
        int shown = 0;
        for (int i = scrollOffset; i < currentEntries.size() && shown < maxVisibleEntries(l); i++, shown++) {
            ListEntry entry = currentEntries.get(i);
            if (entry.world == null) {
                context.drawTextWithShadow(textRenderer, Text.literal(entry.section), left + 16, y + 6, LbUi.TEXT_MUTED);
                y += 22;
                continue;
            }
            if (isCurrentWorld(entry.world.worldId())) {
                context.drawTextWithShadow(textRenderer, Text.literal("You are here"),
                        left + 44, y + 18, LbUi.SUCCESS);
            }
            y += 32;
        }

        if (currentEntries.isEmpty()) {
            String title = query.isBlank() ? "No worlds yet" : "No active worlds match your search";
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(title), left + width / 2, 132, LbUi.TEXT_SECONDARY);
            if (query.isBlank() && controller.canManage()) {
                context.drawCenteredTextWithShadow(textRenderer, Text.literal("Create or import a world to begin"),
                        left + width / 2, 150, LbUi.TEXT_MUTED);
            } else if (controller.canManage() && archivedSearchMatches() > 0) {
                context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal(archivedSearchMatches() + " archived match(es) — clear search and open Archived Worlds"),
                        left + width / 2, 150, LbUi.TEXT_MUTED);
            }
        }
    }

    private void renderManagePane(DrawContext context, Layout l) {
        int left = l.detailPaneLeft();
        int width = l.detailPaneWidth();
        LbUi.elevatedPanel(context, left, 34, width, l.bottom - 34);

        WorldControlWireProtocol.WorldSummary world = selected();
        if (world == null) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(controller.canManage() ? "Select Manage on a world" : "Select a world to navigate"),
                    left + width / 2, 112, LbUi.TEXT_MUTED);
            return;
        }

        int x = left + 24;
        context.drawTextWithShadow(textRenderer,
                Text.literal(controller.canManage() ? "MANAGE WORLD" : "WORLD"), x, 48, LbUi.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, Text.literal(world.displayName()), x, 66, LbUi.TEXT_PRIMARY);
        String subtitle = "ARCHIVED".equals(world.lifecycle()) ? "Archived" : isCurrentWorld(world.worldId())
                ? "You are here" : titleCase(world.kind());
        context.drawTextWithShadow(textRenderer, Text.literal(subtitle), x, 84,
                "ARCHIVED".equals(world.lifecycle()) ? LbUi.TEXT_SECONDARY : LbUi.TEXT_MUTED);
        LbUi.divider(context, x, 106, left + width - 24);

        if (controller.lastError() != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(controller.lastError()),
                    left + width / 2, l.bottom - 18, LbUi.DANGER_BRIGHT);
        }
    }

    private void renderOperationStatus(DrawContext context, Layout l) {
        String label = null;
        int percent = -1;
        if (transfers.status().active()) {
            label = transfers.status().message();
            percent = transfers.status().percent();
        } else if (controller.activityMessage() != null) {
            label = controller.activityMessage();
        }
        if (label == null) return;

        int left = (!l.compact || compactDetail) ? l.detailPaneLeft() : l.listLeft();
        int width = (!l.compact || compactDetail) ? l.detailPaneWidth() : l.listPaneWidth();
        int y = l.bottom - 44;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), left + width / 2, y, LbUi.TEXT_SECONDARY);
        if (percent >= 0) LbUi.progress(context, left + 24, y + 13, width - 48, percent);
    }

    private int archivedCount() {
        return (int) controller.worlds().stream().filter(world -> "ARCHIVED".equals(world.lifecycle())).count();
    }

    private int archivedSearchMatches() {
        if (query.isBlank()) return 0;
        String q = query.strip().toLowerCase(Locale.ROOT);
        return (int) controller.worlds().stream()
                .filter(world -> "ARCHIVED".equals(world.lifecycle()))
                .filter(world -> world.displayName().toLowerCase(Locale.ROOT).contains(q)).count();
    }

    private boolean isCurrentWorld(UUID id) {
        return maps.currentWorld() != null && maps.currentWorld().worldId().value().equals(id);
    }

    private boolean operationBusy() {
        return controller.activityMessage() != null || transfers.status().active();
    }

    private int maxVisibleEntries(Layout l) {
        return Math.max(3, (l.bottom - 140) / 28);
    }

    private Layout layout() {
        boolean compact = width < 650;
        int totalWidth = Math.max(300, Math.min(980, width - 24));
        int left = (width - totalWidth) / 2;
        int right = left + totalWidth;
        int bottom = Math.max(190, height - 42);
        if (compact) return new Layout(left, right, totalWidth, left, totalWidth, bottom, true);

        int listWidth = Math.min(520, Math.max(360, totalWidth * 55 / 100));
        int detailLeft = left + listWidth + 12;
        return new Layout(left, right, listWidth, detailLeft, right - detailLeft, bottom, false);
    }

    private void returnToMap() {
        if (client == null) return;
        if (parent != null) client.setScreen(parent);
        else client.setScreen(new WorldMapScreen(controller, transfers, maps));
    }

    @Override
    public void close() {
        if (layout().compact && compactDetail) {
            compactDetail = false;
            clearAndInit();
            return;
        }
        returnToMap();
    }

    private WorldControlWireProtocol.WorldSummary selected() {
        if (selectedWorld == null) return null;
        return controller.worlds().stream().filter(world -> world.worldId().equals(selectedWorld)).findFirst().orElse(null);
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) return "";
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private record ListEntry(String section, WorldControlWireProtocol.WorldSummary world) {}

    private record Layout(int left, int right, int listWidth, int detailLeft, int detailWidth, int bottom, boolean compact) {
        int listLeft() { return left; }
        int listPaneWidth() { return compact ? right - left : listWidth; }
        int detailPaneLeft() { return compact ? left : detailLeft; }
        int detailPaneWidth() { return compact ? right - left : detailWidth; }
    }
}
