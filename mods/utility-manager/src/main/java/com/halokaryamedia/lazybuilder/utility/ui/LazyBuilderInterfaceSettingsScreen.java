package com.halokaryamedia.lazybuilder.utility.ui;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.UtilityPreferences;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/** Vanilla-style LazyBuilder interface options. */
public final class LazyBuilderInterfaceSettingsScreen extends Screen {
    private static final int OPTION_WIDTH = 150;
    private static final int COLUMNS = 2;

    private final Screen parent;
    private final ThreePartsLayoutWidget layout = new ThreePartsLayoutWidget(this);

    public LazyBuilderInterfaceSettingsScreen(Screen parent) {
        super(Text.literal("Interface Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.layout.addHeader(this.title, this.textRenderer);

        UtilityPreferences prefs = UtilityManagerClient.preferences();
        GridWidget grid = new GridWidget().setSpacing(4);
        GridWidget.Adder adder = grid.createAdder(COLUMNS);

        adder.add(toggleButton("Compact Debug HUD", prefs.compactDebugHud(),
                value -> UtilityManagerClient.updatePreferences(prefs.withCompactDebugHud(value))));
        adder.add(toggleButton("Screenshot Names", prefs.contextualScreenshotNames(),
                value -> UtilityManagerClient.updatePreferences(prefs.withContextualScreenshotNames(value))));
        adder.add(toggleButton("Quick Creative Search", prefs.instantCreativeSearch(),
                value -> UtilityManagerClient.updatePreferences(prefs.withInstantCreativeSearch(value))));

        this.layout.addBody(grid);
        this.layout.addFooter(
                ButtonWidget.builder(Text.literal("Done"), button -> this.close())
                        .width(200)
                        .build()
        );

        this.layout.forEachChild(this::addDrawableChild);
        this.refreshWidgetPositions();
    }

    private ButtonWidget toggleButton(String label, boolean enabled, Consumer<Boolean> setter) {
        return ButtonWidget.builder(
                        Text.literal(label + ": " + (enabled ? "ON" : "OFF")),
                        button -> {
                            setter.accept(!enabled);
                            if (this.client != null) {
                                this.client.setScreen(new LazyBuilderInterfaceSettingsScreen(this.parent));
                            }
                        })
                .width(OPTION_WIDTH)
                .build();
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
