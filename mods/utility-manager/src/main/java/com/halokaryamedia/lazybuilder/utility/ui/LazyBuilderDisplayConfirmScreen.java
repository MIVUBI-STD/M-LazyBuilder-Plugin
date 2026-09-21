package com.halokaryamedia.lazybuilder.utility.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Confirmation/revert countdown for risky live display changes. */
public final class LazyBuilderDisplayConfirmScreen extends Screen {
    private static final int BACKGROUND = 0xF20B0E12;
    private static final int PANEL = 0xE8151A20;
    private static final int BORDER = 0x66545C66;
    private static final int TIMEOUT_TICKS = 15 * 20;

    private final Screen parent;
    private final Runnable keep;
    private final Runnable revert;
    private int ticksRemaining = TIMEOUT_TICKS;
    private boolean resolved;

    public LazyBuilderDisplayConfirmScreen(
            Screen parent,
            Runnable keep,
            Runnable revert
    ) {
        super(Text.literal("Keep Display Changes?"));
        this.parent = parent;
        this.keep = keep;
        this.revert = revert;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(430, Math.max(280, width - 40));
        int left = (width - panelWidth) / 2;
        int y = height / 2 + 36;

        addDrawableChild(new LazyBuilderSettingsControlWidget(
                left + 18,
                y,
                (panelWidth - 44) / 2,
                22,
                Text.literal("Revert"),
                true,
                LazyBuilderSettingsControlWidget.Kind.FOOTER,
                this::revertNow
        ));
        addDrawableChild(new LazyBuilderSettingsControlWidget(
                left + panelWidth / 2 + 4,
                y,
                (panelWidth - 44) / 2,
                22,
                Text.literal("Keep Changes"),
                true,
                LazyBuilderSettingsControlWidget.Kind.FOOTER,
                this::keepNow
        ));
    }

    @Override
    public void tick() {
        if (resolved) return;
        ticksRemaining--;
        if (ticksRemaining <= 0) revertNow();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKGROUND);

        int panelWidth = Math.min(430, Math.max(280, width - 40));
        int left = (width - panelWidth) / 2;
        int top = Math.max(44, height / 2 - 78);
        int bottom = Math.min(height - 44, top + 156);

        context.fill(left, top, left + panelWidth, bottom, BORDER);
        context.fill(left + 1, top + 1, left + panelWidth - 1, bottom - 1, PANEL);

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("KEEP DISPLAY CHANGES?"),
                width / 2,
                top + 22,
                LazyBuilderSettingsScreen.TEXT_PRIMARY
        );
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("If the new display mode is not usable, it will revert automatically."),
                width / 2,
                top + 50,
                LazyBuilderSettingsScreen.TEXT_MUTED
        );

        int seconds = Math.max(0, (ticksRemaining + 19) / 20);
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("Reverting in " + seconds + "s"),
                width / 2,
                top + 72,
                LazyBuilderSettingsScreen.ACCENT
        );

        super.render(context, mouseX, mouseY, delta);
    }

    private void keepNow() {
        if (resolved) return;
        resolved = true;
        keep.run();
        if (client != null) client.setScreen(parent);
    }

    private void revertNow() {
        if (resolved) return;
        resolved = true;
        revert.run();
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void close() {
        revertNow();
    }

    @Override
    public boolean shouldPause() {
        return true;
    }
}
