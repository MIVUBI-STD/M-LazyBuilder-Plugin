package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Compact server-authoritative settings surface with one consistent visual hierarchy. */
public final class WorldSettingsScreen extends Screen {
    private static final String[] GAME_MODES = {"SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR"};
    private static final String[] DIFFICULTIES = {"PEACEFUL", "EASY", "NORMAL", "HARD"};

    private final Screen parent;
    private final ClientWorldController controller;
    private final WorldControlWireProtocol.WorldSummary world;
    private long observedRevision;
    private boolean requestedInitialSettings;

    public WorldSettingsScreen(
            Screen parent,
            ClientWorldController controller,
            WorldControlWireProtocol.WorldSummary world
    ) {
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
            addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close())
                    .dimensions(width / 2 - 50, height - 30, 100, 20).build());
            return;
        }

        int center = width / 2;
        int panelWidth = Math.min(420, width - 50);
        int left = center - panelWidth / 2;
        int contentLeft = left + 18;
        int contentWidth = panelWidth - 36;
        int half = (contentWidth - 8) / 2;
        int y = 92;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Auto Load: " + onOff(settings.autoLoad())),
                button -> controller.setAutoLoad(world.worldId(), !settings.autoLoad()))
                .dimensions(contentLeft, y, half, 22).build());
        addDrawableChild(ButtonWidget.builder(
                Text.literal("PVP: " + onOff(settings.pvpEnabled())),
                button -> controller.setPvp(world.worldId(), !settings.pvpEnabled()))
                .dimensions(contentLeft + half + 8, y, half, 22).build());

        y += 32;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Game Mode: " + settings.defaultGameMode()),
                button -> controller.setDefaultMode(world.worldId(), next(GAME_MODES, settings.defaultGameMode())))
                .dimensions(contentLeft, y, contentWidth, 22).build());

        y += 30;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Difficulty: " + settings.difficulty()),
                button -> controller.setDifficulty(world.worldId(), next(DIFFICULTIES, settings.difficulty())))
                .dimensions(contentLeft, y, contentWidth, 22).build());

        y += 38;
        addDrawableChild(ButtonWidget.builder(Text.literal("Set Current Position as Spawn"),
                button -> controller.setSpawnHere(world.worldId()))
                .dimensions(contentLeft, y, contentWidth, 22).build());

        y += 30;
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset to BUILD_READY"), button -> confirmReset())
                .dimensions(contentLeft, y, contentWidth, 22).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close())
                .dimensions(center - 50, height - 30, 100, 20).build());
    }

    private void confirmReset() {
        if (client == null) return;
        client.setScreen(new ConfirmWorldActionScreen(
                this,
                Text.literal("Reset to BUILD_READY"),
                Text.literal("Reset builder-safe defaults for " + world.displayName() + "?"),
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
        renderBackground(context, mouseX, mouseY, delta);
        int center = width / 2;
        int panelWidth = Math.min(420, width - 50);
        int left = center - panelWidth / 2;
        context.fill(left, 34, left + panelWidth, height - 42, 0xB9191E25);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Settings — " + world.displayName()), center, 48, 0xFFFFFF);

        WorldControlWireProtocol.SettingsSnapshot settings = controller.settings(world.worldId());
        if (settings == null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Loading server settings…"),
                    center, 92, 0xAEB7C4);
            if (controller.lastError() != null) {
                context.drawCenteredTextWithShadow(textRenderer, Text.literal(controller.lastError()),
                        center, 116, 0xFF7777);
            }
            return;
        }

        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("General"), center, 70, 0xAEB7C4);

        String runtime = "Weather: " + settings.weather()
                + "   •   Time: " + settings.timeOfDayTicks();
        String spawn = "Spawn: " + round(settings.spawnX()) + ", " + round(settings.spawnY()) + ", " + round(settings.spawnZ());
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(runtime), center, height - 72, 0x8F9AA8);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(spawn), center, height - 58, 0x8F9AA8);

        if (controller.lastError() != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(controller.lastError()),
                    center, height - 90, 0xFF7777);
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    private static String onOff(boolean value) { return value ? "ON" : "OFF"; }

    private static String next(String[] values, String current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(current)) return values[(i + 1) % values.length];
        }
        return values[0];
    }

    private static long round(double value) { return Math.round(value); }
}
