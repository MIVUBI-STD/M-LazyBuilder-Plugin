package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * Presentation-only layout, rendering, and hit-testing for the Map Export sidebar.
 *
 * <p>Domain actions remain in {@link WorldMapScreen}; this class owns only the
 * export workspace's visual geometry and maps pointer locations to presentation intents.</p>
 */
final class MapExportWorkspacePanel {
    private TextFieldWidget nameField;
    private static final int SIDEBAR_MIN = 218;
    private static final int SIDEBAR_MAX = 254;
    private static final int FOOTER = 46;
    private static final int ADVANCED_TOP = 184;
    private static final int ROW_HEIGHT = 26;

    enum Action {
        NONE,
        BACK,
        FULL_SCOPE,
        AREA_SCOPE,
        CYCLE_FORMAT,
        SUBMIT,
        TOGGLE_WORLD_SETTINGS,
        USE_CURRENT_POSITION
    }

    int sidebarWidth(int screenWidth) {
        int responsive = Math.max(SIDEBAR_MIN, screenWidth / 4);
        return Math.min(SIDEBAR_MAX, Math.min(responsive, Math.max(SIDEBAR_MIN, screenWidth - 300)));
    }

    Rect panelRect(int screenWidth, int screenHeight) {
        return new Rect(screenWidth - sidebarWidth(screenWidth), 0, screenWidth, screenHeight);
    }

    Rect nameRect(int screenWidth, int screenHeight) {
        Rect panel = panelRect(screenWidth, screenHeight);
        return new Rect(panel.left + 12, 91, panel.right - 12, 113);
    }

    TextFieldWidget initializeNameField(
            TextRenderer textRenderer,
            MapExportWorkspaceState state,
            int screenWidth,
            int screenHeight
    ) {
        Rect field = nameRect(screenWidth, screenHeight);
        nameField = new TextFieldWidget(
                textRenderer,
                field.left(),
                field.top(),
                field.width(),
                field.height(),
                Text.literal("World Name"));
        nameField.setMaxLength(80);
        nameField.setText(state.artifactName());
        nameField.setChangedListener(state::artifactName);
        return nameField;
    }

    boolean nameFieldFocused() {
        return nameField != null && nameField.isFocused();
    }

    void updateNameField(String value) {
        if (nameField != null && !nameField.getText().equals(value)) {
            nameField.setText(value);
        }
    }

    void clearControls() {
        nameField = null;
    }

    Rect advancedViewport(int screenWidth, int screenHeight) {
        Rect panel = panelRect(screenWidth, screenHeight);
        return new Rect(
                panel.left + 8,
                ADVANCED_TOP,
                panel.right - 8,
                Math.max(ADVANCED_TOP, screenHeight - FOOTER - 16));
    }

    int maxScroll(MapExportWorkspaceState state, int screenWidth, int screenHeight) {
        int rows = state.worldSettingsExpanded() ? 3 : 1;
        int contentHeight = rows * ROW_HEIGHT;
        return Math.max(0, contentHeight - Math.max(1, advancedViewport(screenWidth, screenHeight).height()));
    }

    Action actionAt(
            MapExportWorkspaceState state,
            boolean initialized,
            int screenWidth,
            int screenHeight,
            double mouseX,
            double mouseY
    ) {
        if (backRect(screenWidth, screenHeight).contains(mouseX, mouseY)) return Action.BACK;
        if (!initialized) return Action.NONE;
        if (fullScopeRect(screenWidth, screenHeight).contains(mouseX, mouseY)) return Action.FULL_SCOPE;
        if (areaScopeRect(screenWidth, screenHeight).contains(mouseX, mouseY)) return Action.AREA_SCOPE;
        if (formatRect(screenWidth, screenHeight).contains(mouseX, mouseY)) return Action.CYCLE_FORMAT;
        if (actionRect(screenWidth, screenHeight).contains(mouseX, mouseY)) return Action.SUBMIT;

        int y = ADVANCED_TOP - state.scroll();
        if (advancedRowRect(screenWidth, screenHeight, y).contains(mouseX, mouseY)) {
            return Action.TOGGLE_WORLD_SETTINGS;
        }
        y += ROW_HEIGHT;
        if (state.worldSettingsExpanded()) {
            y += ROW_HEIGHT;
            if (advancedRowRect(screenWidth, screenHeight, y).contains(mouseX, mouseY)) {
                return Action.USE_CURRENT_POSITION;
            }
        }
        return Action.NONE;
    }

    void render(
            DrawContext context,
            TextRenderer textRenderer,
            MapExportWorkspaceState state,
            boolean initialized,
            String worldError,
            String mapError,
            boolean busy,
            int screenWidth,
            int screenHeight,
            int mouseX,
            int mouseY
    ) {
        Rect panel = panelRect(screenWidth, screenHeight);
        context.fill(panel.left, panel.top, panel.right, panel.bottom, 0xF514181E);
        context.fill(panel.left, 0, panel.left + 1, screenHeight, LbUi.BORDER);

        Rect back = backRect(screenWidth, screenHeight);
        if (back.contains(mouseX, mouseY)) context.fill(back.left, back.top, back.right, back.bottom, LbUi.SURFACE_2);
        context.drawTextWithShadow(textRenderer, Text.literal("‹ Back to Map"), back.left + 2, back.top + 6, LbUi.TEXT_SECONDARY);
        context.drawTextWithShadow(textRenderer, Text.literal("EXPORT WORLD"), panel.left + 12, 38, LbUi.TEXT_MUTED);

        renderScopeTab(
                context,
                textRenderer,
                fullScopeRect(screenWidth, screenHeight),
                "Full World",
                state.scope() == MapExportWorkspaceState.Scope.FULL_WORLD,
                mouseX,
                mouseY);
        renderScopeTab(
                context,
                textRenderer,
                areaScopeRect(screenWidth, screenHeight),
                "Custom Area",
                state.scope() == MapExportWorkspaceState.Scope.CUSTOM_AREA,
                mouseX,
                mouseY);

        if (!initialized) {
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal("Loading export settings…"),
                    panel.left + panel.width() / 2,
                    104,
                    LbUi.TEXT_SECONDARY);
            if (worldError != null) {
                context.drawCenteredTextWithShadow(
                        textRenderer,
                        Text.literal(trim(textRenderer, worldError, panel.width() - 24)),
                        panel.left + panel.width() / 2,
                        124,
                        LbUi.DANGER_BRIGHT);
            }
            return;
        }

        context.drawTextWithShadow(textRenderer, Text.literal("World Name"), panel.left + 12, 78, LbUi.TEXT_MUTED);
        renderSettingRow(
                context,
                textRenderer,
                formatRect(screenWidth, screenHeight),
                "Version",
                friendlyFormat(state.format()),
                mouseX,
                mouseY);

        context.fill(panel.left + 10, 158, panel.right - 10, 159, LbUi.BORDER);
        context.drawTextWithShadow(textRenderer, Text.literal("Advanced Settings"), panel.left + 12, 168, LbUi.TEXT_MUTED);
        renderAdvanced(context, textRenderer, state, screenWidth, screenHeight, mouseX, mouseY);

        Rect action = actionRect(screenWidth, screenHeight);
        int actionColor = busy ? LbUi.SURFACE_1
                : action.contains(mouseX, mouseY) ? LbUi.ACCENT_HOVER : LbUi.ACCENT_FILL;
        context.fill(action.left, action.top, action.right, action.bottom, actionColor);
        String label = busy ? "Exporting…"
                : state.scope() == MapExportWorkspaceState.Scope.CUSTOM_AREA ? "Export Area" : "Export World";
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(label),
                action.left + action.width() / 2,
                action.top + 7,
                busy ? LbUi.TEXT_MUTED : LbUi.TEXT_PRIMARY);

        String error = mapError != null ? mapError : worldError;
        if (error != null && !error.isBlank()) {
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal(trim(textRenderer, error, panel.width() - 24)),
                    panel.left + panel.width() / 2,
                    action.top - 15,
                    LbUi.DANGER_BRIGHT);
        }
    }

    private void renderAdvanced(
            DrawContext context,
            TextRenderer textRenderer,
            MapExportWorkspaceState state,
            int screenWidth,
            int screenHeight,
            int mouseX,
            int mouseY
    ) {
        Rect viewport = advancedViewport(screenWidth, screenHeight);
        context.enableScissor(viewport.left, viewport.top, viewport.right, viewport.bottom);
        int y = ADVANCED_TOP - state.scroll();

        Rect worldHeader = advancedRowRect(screenWidth, screenHeight, y);
        renderAccordionRow(context, textRenderer, worldHeader, "World Settings", state.worldSettingsExpanded(), mouseX, mouseY);
        y += ROW_HEIGHT;
        if (state.worldSettingsExpanded()) {
            String spawn = state.spawnX() + ", " + state.spawnY() + ", " + state.spawnZ();
            renderSettingRow(
                    context,
                    textRenderer,
                    advancedRowRect(screenWidth, screenHeight, y),
                    "Spawn Position",
                    spawn,
                    mouseX,
                    mouseY);
            y += ROW_HEIGHT;
            renderSettingRow(
                    context,
                    textRenderer,
                    advancedRowRect(screenWidth, screenHeight, y),
                    "Use Current Position",
                    "Apply",
                    mouseX,
                    mouseY);
        }
        context.disableScissor();
    }

    private static void renderScopeTab(
            DrawContext context,
            TextRenderer textRenderer,
            Rect rect,
            String label,
            boolean selected,
            int mouseX,
            int mouseY
    ) {
        int color = selected ? LbUi.ACCENT_FILL : rect.contains(mouseX, mouseY) ? LbUi.SURFACE_3 : LbUi.SURFACE_2;
        context.fill(rect.left, rect.top, rect.right, rect.bottom, color);
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(label),
                rect.left + rect.width() / 2,
                rect.top + 6,
                selected ? LbUi.TEXT_PRIMARY : LbUi.TEXT_SECONDARY);
    }

    private static void renderSettingRow(
            DrawContext context,
            TextRenderer textRenderer,
            Rect rect,
            String label,
            String value,
            int mouseX,
            int mouseY
    ) {
        context.fill(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                rect.contains(mouseX, mouseY) ? LbUi.SURFACE_3 : LbUi.SURFACE_2);
        context.drawTextWithShadow(textRenderer, Text.literal(label), rect.left + 7, rect.top + 5, LbUi.TEXT_SECONDARY);
        String shown = trim(textRenderer, value, Math.max(20, rect.width() / 2));
        context.drawTextWithShadow(
                textRenderer,
                Text.literal(shown),
                rect.right - 7 - textRenderer.getWidth(shown),
                rect.top + 5,
                LbUi.TEXT_PRIMARY);
    }

    private static void renderAccordionRow(
            DrawContext context,
            TextRenderer textRenderer,
            Rect rect,
            String label,
            boolean expanded,
            int mouseX,
            int mouseY
    ) {
        context.fill(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                rect.contains(mouseX, mouseY) ? LbUi.SURFACE_3 : LbUi.SURFACE_1);
        context.drawTextWithShadow(textRenderer, Text.literal(label), rect.left + 7, rect.top + 6, LbUi.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, Text.literal(expanded ? "⌄" : "›"), rect.right - 14, rect.top + 6, LbUi.TEXT_MUTED);
    }

    private Rect backRect(int screenWidth, int screenHeight) {
        Rect panel = panelRect(screenWidth, screenHeight);
        return new Rect(panel.left + 10, 8, panel.right - 10, 31);
    }

    private Rect fullScopeRect(int screenWidth, int screenHeight) {
        Rect panel = panelRect(screenWidth, screenHeight);
        int gap = 4;
        int available = panel.width() - 24 - gap;
        int half = available / 2;
        return new Rect(panel.left + 12, 54, panel.left + 12 + half, 78);
    }

    private Rect areaScopeRect(int screenWidth, int screenHeight) {
        Rect first = fullScopeRect(screenWidth, screenHeight);
        Rect panel = panelRect(screenWidth, screenHeight);
        return new Rect(first.right + 4, first.top, panel.right - 12, first.bottom);
    }

    private Rect formatRect(int screenWidth, int screenHeight) {
        Rect panel = panelRect(screenWidth, screenHeight);
        return new Rect(panel.left + 12, 122, panel.right - 12, 147);
    }

    private Rect advancedRowRect(int screenWidth, int screenHeight, int y) {
        Rect panel = panelRect(screenWidth, screenHeight);
        return new Rect(panel.left + 12, y, panel.right - 12, y + 23);
    }

    private Rect actionRect(int screenWidth, int screenHeight) {
        Rect panel = panelRect(screenWidth, screenHeight);
        return new Rect(panel.left + 12, screenHeight - 38, panel.right - 12, screenHeight - 10);
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

    private static String friendlyFormat(String raw) {
        String value = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
        if (value.startsWith("JAVA_")) return "Java " + value.substring(5).replace('_', '.');
        if (value.startsWith("BEDROCK_")) return "Bedrock " + value.substring(8).replace('_', '.');
        return MapExportWorkspaceState.titleCase(value);
    }

    record Rect(int left, int top, int right, int bottom) {
        boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }

        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }
    }
}
