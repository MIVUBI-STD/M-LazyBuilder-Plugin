package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Compact General settings surface backed by server-authoritative snapshots. */
public final class WorldSettingsScreen extends Screen {
    private static final String[] GAME_MODES = {"SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR"};
    private static final String[] DIFFICULTIES = {"PEACEFUL", "EASY", "NORMAL", "HARD"};

    private final Screen parent;
    private final ClientWorldController controller;
    private final WorldControlWireProtocol.WorldSummary world;
    private long observedRevision;

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
            controller.requestSettings(world.worldId());
            return;
        }

        int center = width / 2;
        int buttonWidth = Math.min(240, width - 60);
        int left = center - buttonWidth / 2;
        int y = 54;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Auto Load: " + onOff(settings.autoLoad())),
                button -> controller.setAutoLoad(world.worldId(), !settings.autoLoad()))
                .dimensions(left, y, buttonWidth, 20).build());
        y += 26;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Default Game Mode: " + settings.defaultGameMode()),
                button -> controller.setDefaultMode(world.worldId(), next(GAME_MODES, settings.defaultGameMode())))
                .dimensions(left, y, buttonWidth, 20).build());
        y += 26;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Difficulty: " + settings.difficulty()),
                button -> controller.setDifficulty(world.worldId(), next(DIFFICULTIES, settings.difficulty())))
                .dimensions(left, y, buttonWidth, 20).build());
        y += 26;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("PVP: " + onOff(settings.pvpEnabled())),
                button -> controller.setPvp(world.worldId(), !settings.pvpEnabled()))
                .dimensions(left, y, buttonWidth, 20).build());
        y += 30;
        addDrawableChild(ButtonWidget.builder(Text.literal("Set Current Position as Spawn"),
                button -> controller.setSpawnHere(world.worldId()))
                .dimensions(left, y, buttonWidth, 20).build());
        y += 26;
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset to BUILD_READY"), button -> confirmReset())
                .dimensions(left, y, buttonWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close())
                .dimensions(center - 50, height - 28, 100, 20).build());
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
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Settings — " + world.displayName()), width / 2, 16, 0xFFFFFF);

        WorldControlWireProtocol.SettingsSnapshot settings = controller.settings(world.worldId());
        if (settings == null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Loading server settings…"),
                    width / 2, 72, 0xAAAAAA);
            return;
        }
        String runtime = "Weather " + settings.weather()
                + " • Time " + settings.timeOfDayTicks()
                + " • Spawn " + round(settings.spawnX()) + ", " + round(settings.spawnY()) + ", " + round(settings.spawnZ());
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(runtime), width / 2, height - 52, 0x888888);
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
