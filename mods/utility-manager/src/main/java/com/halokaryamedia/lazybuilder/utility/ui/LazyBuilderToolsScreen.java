package com.halokaryamedia.lazybuilder.utility.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.text.Text;

/** Vanilla-style tool settings hub. */
public final class LazyBuilderToolsScreen extends Screen {
    private static final int OPTION_WIDTH = 150;
    private static final int COLUMNS = 2;

    private final Screen parent;
    private final ThreePartsLayoutWidget layout = new ThreePartsLayoutWidget(this);

    public LazyBuilderToolsScreen(Screen parent) {
        super(Text.literal("Tools"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.layout.addHeader(this.title, this.textRenderer);

        GridWidget grid = new GridWidget().setSpacing(4);
        GridWidget.Adder adder = grid.createAdder(COLUMNS);

        ButtonWidget editing = ButtonWidget.builder(Text.literal("Editing Tools: Active"), button -> {})
                .width(OPTION_WIDTH)
                .build();
        editing.active = false;
        adder.add(editing);

        ButtonWidget worlds = ButtonWidget.builder(Text.literal("Map & Worlds: Active"), button -> {})
                .width(OPTION_WIDTH)
                .build();
        worlds.active = false;
        adder.add(worlds);

        this.layout.addBody(grid);
        this.layout.addFooter(
                ButtonWidget.builder(Text.literal("Done"), button -> this.close())
                        .width(200)
                        .build()
        );

        this.layout.forEachChild(this::addDrawableChild);
        this.refreshWidgetPositions();
    }

    @Override
    protected void refreshWidgetPositions() {
        this.layout.refreshPositions();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() {
        return true;
    }
}
