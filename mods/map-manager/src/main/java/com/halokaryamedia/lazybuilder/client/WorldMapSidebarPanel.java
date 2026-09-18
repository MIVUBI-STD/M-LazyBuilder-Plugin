package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

/** Presentation-only world navigator sidebar for {@link WorldMapScreen}. */
final class WorldMapSidebarPanel {
    private static final int COLLAPSED_WIDTH = 34;
    private static final int MIN_WIDTH = 152;
    private static final int MAX_WIDTH = 176;
    private static final int ROW_HEIGHT = 27;

    enum ActionType {
        NONE,
        EXPAND,
        COLLAPSE,
        SELECT_CURRENT,
        TOGGLE_CURRENT_PIN,
        SHOW_FAVORITES,
        SHOW_ALL,
        TOGGLE_ROW_PIN,
        SELECT_ROW,
        TELEPORT_SELECTED,
        MANAGE
    }

    record Action(ActionType type, UUID worldId) {
        static Action none() {
            return new Action(ActionType.NONE, null);
        }

        static Action of(ActionType type) {
            return new Action(type, null);
        }

        static Action of(ActionType type, UUID worldId) {
            return new Action(type, worldId);
        }
    }

    record View(
            boolean collapsed,
            boolean showAllWorlds,
            UUID selectedWorldId,
            UUID currentWorldId,
            String currentWorldName,
            String dimensionPath,
            List<WorldControlWireProtocol.WorldSummary> rows,
            boolean canTeleport,
            boolean teleportBusy
    ) {
        View {
            rows = List.copyOf(rows);
        }
    }

    int sidebarWidth(int screenWidth, boolean collapsed) {
        if (collapsed) return COLLAPSED_WIDTH;
        if (screenWidth < 620) return MIN_WIDTH;
        return Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, screenWidth / 6));
    }

    int visibleRows(int screenHeight, boolean collapsed) {
        if (collapsed) return 0;
        int available = screenHeight - 108 - 76;
        return Math.max(0, available / ROW_HEIGHT);
    }

    boolean contains(double mouseX, int screenWidth, boolean collapsed) {
        return mouseX < sidebarWidth(screenWidth, collapsed);
    }

    Action actionAt(View view, int screenWidth, int screenHeight, double mouseX, double mouseY) {
        int sidebar = sidebarWidth(screenWidth, view.collapsed());
        if (mouseX >= sidebar) return Action.none();

        if (view.collapsed()) return Action.of(ActionType.EXPAND);

        if (mouseY < 31 && mouseX > sidebar - 32) {
            return Action.of(ActionType.COLLAPSE);
        }
        if (currentWorldRect(sidebar).contains(mouseX, mouseY)) {
            if (view.currentWorldId() != null && mouseX >= sidebar - 32) {
                return Action.of(ActionType.TOGGLE_CURRENT_PIN, view.currentWorldId());
            }
            return Action.of(ActionType.SELECT_CURRENT, view.currentWorldId());
        }
        if (favoritesTabRect(sidebar).contains(mouseX, mouseY)) {
            return Action.of(ActionType.SHOW_FAVORITES);
        }
        if (allWorldsTabRect(sidebar).contains(mouseX, mouseY)) {
            return Action.of(ActionType.SHOW_ALL);
        }

        int rowY = 108;
        int visible = visibleRows(screenHeight, false);
        for (int i = 0; i < view.rows().size() && i < visible; i++) {
            WorldControlWireProtocol.WorldSummary world = view.rows().get(i);
            Rect rect = rowRect(sidebar, rowY);
            if (rect.contains(mouseX, mouseY)) {
                return mouseX < 27
                        ? Action.of(ActionType.TOGGLE_ROW_PIN, world.worldId())
                        : Action.of(ActionType.SELECT_ROW, world.worldId());
            }
            rowY += ROW_HEIGHT;
        }

        if (selectedActionRect(sidebar, screenHeight).contains(mouseX, mouseY)) {
            return Action.of(ActionType.TELEPORT_SELECTED, view.selectedWorldId());
        }
        if (manageWorldsRect(sidebar, screenHeight).contains(mouseX, mouseY)) {
            return Action.of(ActionType.MANAGE);
        }
        return Action.none();
    }

    void render(
            DrawContext context,
            TextRenderer textRenderer,
            WorldNavigationPreferences navigation,
            View view,
            int screenWidth,
            int screenHeight,
            int mouseX,
            int mouseY
    ) {
        int sidebar = sidebarWidth(screenWidth, view.collapsed());
        context.fill(0, 0, sidebar, screenHeight, 0xF214181E);
        context.fill(sidebar - 1, 0, sidebar, screenHeight, LbUi.BORDER);

        if (view.collapsed()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("›"), sidebar / 2, 13, LbUi.TEXT_PRIMARY);
            return;
        }

        context.drawTextWithShadow(textRenderer, Text.literal("Worlds"), 12, 12, LbUi.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, Text.literal("‹"), sidebar - 18, 12, LbUi.TEXT_MUTED);
        LbUi.divider(context, 10, 29, sidebar - 10);

        context.drawTextWithShadow(textRenderer, Text.literal("Current world"), 12, 39, LbUi.TEXT_MUTED);
        Rect currentRect = currentWorldRect(sidebar);
        if (currentRect.contains(mouseX, mouseY)) {
            context.fill(currentRect.left, currentRect.top, currentRect.right, currentRect.bottom, LbUi.SURFACE_2);
        }
        context.drawTextWithShadow(
                textRenderer,
                Text.literal("●"),
                12,
                currentRect.top + 8,
                view.currentWorldId() == null ? LbUi.TEXT_MUTED : LbUi.SUCCESS);
        context.drawTextWithShadow(
                textRenderer,
                Text.literal(trim(textRenderer, view.currentWorldName(), sidebar - 54)),
                27,
                currentRect.top + 6,
                LbUi.TEXT_PRIMARY);
        if (view.dimensionPath() != null && !view.dimensionPath().isBlank()) {
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(friendlyDimension(view.dimensionPath())),
                    27,
                    currentRect.top + 17,
                    LbUi.TEXT_MUTED);
        }
        if (view.currentWorldId() != null) {
            boolean pinned = navigation.isPinned(view.currentWorldId());
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(pinned ? "★" : "☆"),
                    sidebar - 20,
                    currentRect.top + 8,
                    pinned ? LbUi.ACCENT_BRIGHT : LbUi.TEXT_MUTED);
        }

        Rect favoritesTab = favoritesTabRect(sidebar);
        Rect allWorldsTab = allWorldsTabRect(sidebar);
        if (favoritesTab.contains(mouseX, mouseY) && view.showAllWorlds()) {
            context.fill(favoritesTab.left, favoritesTab.top, favoritesTab.right, favoritesTab.bottom, LbUi.SURFACE_2);
        }
        if (allWorldsTab.contains(mouseX, mouseY) && !view.showAllWorlds()) {
            context.fill(allWorldsTab.left, allWorldsTab.top, allWorldsTab.right, allWorldsTab.bottom, LbUi.SURFACE_2);
        }
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("Favorites"),
                (favoritesTab.left + favoritesTab.right) / 2,
                favoritesTab.top + 5,
                view.showAllWorlds() ? LbUi.TEXT_MUTED : LbUi.TEXT_PRIMARY);
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("All worlds"),
                (allWorldsTab.left + allWorldsTab.right) / 2,
                allWorldsTab.top + 5,
                view.showAllWorlds() ? LbUi.TEXT_PRIMARY : LbUi.TEXT_MUTED);
        Rect activeTab = view.showAllWorlds() ? allWorldsTab : favoritesTab;
        context.fill(activeTab.left + 6, activeTab.bottom - 2, activeTab.right - 6, activeTab.bottom - 1, LbUi.ACCENT_BRIGHT);

        int rowY = 108;
        if (!view.showAllWorlds() && view.rows().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("No favorites yet"), sidebar / 2, rowY + 7, LbUi.TEXT_MUTED);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Pin a world with ☆"), sidebar / 2, rowY + 20, LbUi.TEXT_DISABLED);
        }
        int visible = visibleRows(screenHeight, false);
        for (int i = 0; i < view.rows().size() && i < visible; i++) {
            WorldControlWireProtocol.WorldSummary world = view.rows().get(i);
            Rect rect = rowRect(sidebar, rowY);
            boolean selected = world.worldId().equals(view.selectedWorldId());
            boolean hover = rect.contains(mouseX, mouseY);
            if (selected) context.fill(rect.left, rect.top, rect.right, rect.bottom, LbUi.ACCENT_FILL);
            else if (hover) context.fill(rect.left, rect.top, rect.right, rect.bottom, LbUi.SURFACE_2);

            boolean pinned = navigation.isPinned(world.worldId());
            context.drawTextWithShadow(textRenderer, Text.literal(pinned ? "★" : "☆"), 12, rowY + 8,
                    pinned ? LbUi.ACCENT_BRIGHT : LbUi.TEXT_MUTED);
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(trim(textRenderer, world.displayName(), sidebar - 49)),
                    28,
                    rowY + 8,
                    LbUi.TEXT_PRIMARY);
            if (world.worldId().equals(view.currentWorldId())) {
                context.drawTextWithShadow(textRenderer, Text.literal("●"), sidebar - 20, rowY + 8, LbUi.SUCCESS);
            }
            rowY += ROW_HEIGHT;
        }

        renderSelectedAction(context, textRenderer, view, sidebar, screenHeight, mouseX, mouseY);

        Rect manage = manageWorldsRect(sidebar, screenHeight);
        if (manage.contains(mouseX, mouseY)) {
            context.fill(manage.left, manage.top, manage.right, manage.bottom, LbUi.SURFACE_2);
        }
        context.fill(10, manage.top - 6, sidebar - 10, manage.top - 5, LbUi.BORDER);
        context.drawTextWithShadow(textRenderer, Text.literal("Manage worlds"), 12, manage.top + 8, LbUi.TEXT_SECONDARY);
        context.drawTextWithShadow(textRenderer, Text.literal("›"), sidebar - 18, manage.top + 8, LbUi.TEXT_MUTED);
    }

    private static void renderSelectedAction(
            DrawContext context,
            TextRenderer textRenderer,
            View view,
            int sidebar,
            int screenHeight,
            int mouseX,
            int mouseY
    ) {
        if (view.selectedWorldId() == null || view.selectedWorldId().equals(view.currentWorldId())) return;
        Rect action = selectedActionRect(sidebar, screenHeight);
        if (action.top < 110) return;
        boolean enabled = view.canTeleport() && !view.teleportBusy();
        int color = enabled ? LbUi.ACCENT_FILL : LbUi.SURFACE_1;
        if (action.contains(mouseX, mouseY) && enabled) color = LbUi.ACCENT_HOVER;
        context.fill(action.left, action.top, action.right, action.bottom, color);
        String label = view.teleportBusy() ? "Teleporting…" : view.canTeleport() ? "Teleport →" : "Teleport unavailable";
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(label),
                (action.left + action.right) / 2,
                action.top + 7,
                enabled ? LbUi.TEXT_PRIMARY : LbUi.TEXT_MUTED);
    }

    private static Rect currentWorldRect(int sidebar) {
        return new Rect(8, 50, sidebar - 8, 82);
    }

    private static Rect favoritesTabRect(int sidebar) {
        int middle = sidebar / 2;
        return new Rect(8, 87, middle - 2, 104);
    }

    private static Rect allWorldsTabRect(int sidebar) {
        int middle = sidebar / 2;
        return new Rect(middle + 2, 87, sidebar - 8, 104);
    }

    private static Rect rowRect(int sidebar, int rowY) {
        return new Rect(8, rowY, sidebar - 8, rowY + ROW_HEIGHT - 2);
    }

    private static Rect manageWorldsRect(int sidebar, int screenHeight) {
        return new Rect(8, screenHeight - 34, sidebar - 8, screenHeight - 7);
    }

    private static Rect selectedActionRect(int sidebar, int screenHeight) {
        return new Rect(8, screenHeight - 67, sidebar - 8, screenHeight - 41);
    }

    private static String trim(TextRenderer textRenderer, String value, int maxWidth) {
        if (value == null || maxWidth <= 0) return "";
        if (textRenderer.getWidth(value) <= maxWidth) return value;
        String base = value;
        while (base.length() > 1 && textRenderer.getWidth(base + "…") > maxWidth) {
            base = base.substring(0, base.length() - 1);
        }
        return textRenderer.getWidth(base + "…") <= maxWidth ? base + "…" : "";
    }

    private static String friendlyDimension(String raw) {
        return switch (raw) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end" -> "The End";
            default -> raw.replace('_', ' ');
        };
    }

    private record Rect(int left, int top, int right, int bottom) {
        boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }
}
