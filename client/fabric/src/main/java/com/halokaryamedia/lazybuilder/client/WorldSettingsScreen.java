package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Compact server-authoritative settings surface with builder-facing terminology. */
public final class WorldSettingsScreen extends Screen {
    private static final String[] GAME_MODES = {"SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR"};
    private static final String[] DIFFICULTIES = {"PEACEFUL", "EASY", "NORMAL", "HARD"};

    private final Screen parent;
    private final ClientWorldController controller;
    private final WorldControlWireProtocol.WorldSummary world;
    private long observedRevision;
    private boolean requestedInitialSettings;

    public WorldSettingsScreen(Screen parent, ClientWorldController controller, WorldControlWireProtocol.WorldSummary world) {
        super(Text.literal("World Settings"));
        this.parent = parent;
        this.controller = controller;
        this.world = world;
        this.observedRevision = controller.revision();
    }

    @Override
    protected void init() {
        observedRevision = controller.revision();
        WorldControlWireProtocol.SettingsSnapshot settings = controller.settings(world.worldId());
        if (settings == null) {
            if (!requestedInitialSettings) {
                requestedInitialSettings = true;
                controller.requestSettings(world.worldId());
            }
            addDrawableChild(LbUi.button(width / 2 - 50, height - 34, 100, 22,
                    "Back", LbButtonWidget.Style.GHOST, this::close));
            return;
        }

        int panelWidth = Math.min(500, width - 48);
        int left = width / 2 - panelWidth / 2;
        int contentLeft = left + 28;
        int contentWidth = panelWidth - 56;
        int half = (contentWidth - 10) / 2;
        int y = 112;

        addDrawableChild(LbUi.button(contentLeft, y, half, 26,
                "Load on Server Start   " + onOff(settings.autoLoad()), LbButtonWidget.Style.SECONDARY,
                () -> controller.setAutoLoad(world.worldId(), !settings.autoLoad())));
        addDrawableChild(LbUi.button(contentLeft + half + 10, y, half, 26,
                "PVP   " + onOff(settings.pvpEnabled()), LbButtonWidget.Style.SECONDARY,
                () -> controller.setPvp(world.worldId(), !settings.pvpEnabled())));

        y += 40;
        addDrawableChild(LbUi.button(contentLeft, y, contentWidth, 26,
                "Default Game Mode   " + titleCase(settings.defaultGameMode()), LbButtonWidget.Style.GHOST,
                () -> controller.setDefaultMode(world.worldId(), next(GAME_MODES, settings.defaultGameMode()))));

        y += 34;
        addDrawableChild(LbUi.button(contentLeft, y, contentWidth, 26,
                "Difficulty   " + titleCase(settings.difficulty()), LbButtonWidget.Style.GHOST,
                () -> controller.setDifficulty(world.worldId(), next(DIFFICULTIES, settings.difficulty()))));

        y += 44;
        addDrawableChild(LbUi.button(contentLeft, y, contentWidth, 26,
                "Set Current Position as World Spawn", LbButtonWidget.Style.SECONDARY,
                () -> controller.setSpawnHere(world.worldId())));

        y += 36;
        addDrawableChild(LbUi.button(contentLeft, y, contentWidth, 24,
                "Reset Builder Defaults", LbButtonWidget.Style.DANGER, this::confirmReset));

        addDrawableChild(LbUi.button(width / 2 - 50, height - 34, 100, 22,
                "Back", LbButtonWidget.Style.GHOST, this::close));
    }

    private void confirmReset() {
        if (client == null) return;
        client.setScreen(new ConfirmWorldActionScreen(
                this,
                Text.literal("Reset Builder Defaults"),
                Text.literal("Restore builder-safe defaults for " + world.displayName() + "?"),
                "Reset",
                () -> controller.resetBuildReady(world.worldId())
        ));
    }

    @Override
    public void tick() {
        if (observedRevision != controller.revision()) {
            observedRevision = controller.revision();
            clearAndInit();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        LbUi.background(context, width, height);
        int panelWidth = Math.min(500, width - 48);
        int left = width / 2 - panelWidth / 2;
        int panelBottom = height - 48;
        LbUi.elevatedPanel(context, left, 28, panelWidth, panelBottom - 28);

        context.drawTextWithShadow(textRenderer, Text.literal("WORLD SETTINGS"), left + 24, 46, LbUi.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, Text.literal(world.displayName()), left + 24, 64, LbUi.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer,
                Text.literal("Common builder settings. Changes are saved by the server."),
                left + 24, 82, LbUi.TEXT_SECONDARY);

        WorldControlWireProtocol.SettingsSnapshot settings = controller.settings(world.worldId());
        if (settings == null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Loading world settings…"),
                    width / 2, 128, LbUi.TEXT_SECONDARY);
            if (controller.lastError() != null) {
                context.drawCenteredTextWithShadow(textRenderer, Text.literal(controller.lastError()),
                        width / 2, 150, LbUi.DANGER_BRIGHT);
            }
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        context.drawTextWithShadow(textRenderer, Text.literal("GENERAL"), left + 28, 100, LbUi.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, Text.literal("BUILD TOOLS"), left + 28, 224, LbUi.TEXT_MUTED);

        String runtime = "Weather  " + titleCase(settings.weather()) + "    •    Time  " + settings.timeOfDayTicks();
        String spawn = "World Spawn  " + round(settings.spawnX()) + ", " + round(settings.spawnY()) + ", " + round(settings.spawnZ());
        context.drawTextWithShadow(textRenderer, Text.literal(runtime), left + 28, panelBottom - 46, LbUi.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, Text.literal(spawn), left + 28, panelBottom - 30, LbUi.TEXT_MUTED);

        if (controller.lastError() != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(controller.lastError()),
                    width / 2, panelBottom - 64, LbUi.DANGER_BRIGHT);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    private static String onOff(boolean value) { return value ? "On" : "Off"; }

    private static String next(String[] values, String current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(current)) return values[(i + 1) % values.length];
        }
        return values[0];
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) return "";
        String lower = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static long round(double value) { return Math.round(value); }
}
