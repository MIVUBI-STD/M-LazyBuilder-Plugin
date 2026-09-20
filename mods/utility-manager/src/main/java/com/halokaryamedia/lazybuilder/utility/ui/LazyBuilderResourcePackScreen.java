package com.halokaryamedia.lazybuilder.utility.ui;

import com.halokaryamedia.lazybuilder.utility.mixin.PackListWidgetAccessor;
import com.halokaryamedia.lazybuilder.utility.mixin.PackScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.pack.PackListWidget;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * LazyBuilder presentation shell around Minecraft's native Resource Pack manager.
 *
 * The native PackScreen keeps ownership of pack discovery, compatibility checks,
 * drag/reorder behavior, directory watching, and apply semantics. LazyBuilder only
 * provides clearer terminology and the same visual hierarchy as the Settings shell.
 */
public final class LazyBuilderResourcePackScreen extends PackScreen {
    private static final int BACKGROUND = 0xF20B0E12;
    private static final int TOP_BAR = 0xE813171C;
    private static final int PANEL = 0x991A1F25;
    private static final int PANEL_BORDER = 0x66545C66;
    private static final int ACCENT_BORDER = 0xAA4FD0B0;

    private final Screen parent;
    private final Path packDirectory;

    public LazyBuilderResourcePackScreen(
            Screen parent,
            ResourcePackManager resourcePackManager,
            Consumer<ResourcePackManager> applier,
            Path packDirectory
    ) {
        super(resourcePackManager, applier, packDirectory, Text.literal("Resource Packs"));
        this.parent = parent;
        this.packDirectory = packDirectory;
    }

    @Override
    protected void init() {
        super.init();

        PackScreenAccessor access = (PackScreenAccessor) this;
        PackListWidget available = access.lazybuilder$getAvailablePackList();
        PackListWidget active = access.lazybuilder$getSelectedPackList();

        if (available != null) {
            ((PackListWidgetAccessor) available).lazybuilder$setTitle(Text.literal("AVAILABLE"));
        }
        if (active != null) {
            ((PackListWidgetAccessor) active).lazybuilder$setTitle(Text.literal("ACTIVE"));
        }

        // Replace only the vanilla footer buttons. Pack-list interactions remain native.
        for (var button : Screens.getButtons(this)) {
            button.visible = false;
            button.active = false;
        }

        int footerY = height - 31;
        this.addDrawableChild(new LazyBuilderSettingsControlWidget(
                18,
                footerY,
                132,
                22,
                Text.literal("Open Pack Folder"),
                true,
                LazyBuilderSettingsControlWidget.Kind.FOOTER,
                () -> Util.getOperatingSystem().open(packDirectory)
        ));

        this.addDrawableChild(new LazyBuilderSettingsControlWidget(
                width - 110,
                footerY,
                92,
                22,
                Text.literal("Done"),
                true,
                LazyBuilderSettingsControlWidget.Kind.FOOTER,
                this::close
        ));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKGROUND);
        context.fill(0, 0, width, 38, TOP_BAR);
        context.fill(0, height - 40, width, height, TOP_BAR);

        PackScreenAccessor access = (PackScreenAccessor) this;
        drawListPanel(context, access.lazybuilder$getAvailablePackList(), false);
        drawListPanel(context, access.lazybuilder$getSelectedPackList(), true);
    }

    private void drawListPanel(DrawContext context, PackListWidget list, boolean active) {
        if (list == null) return;
        int left = list.getX() - 4;
        int top = list.getY() - 4;
        int right = list.getX() + list.getWidth() + 4;
        int bottom = list.getY() + list.getHeight() + 4;
        context.fill(left, top, right, bottom, active ? ACCENT_BORDER : PANEL_BORDER);
        context.fill(left + 1, top + 1, right - 1, bottom - 1, PANEL);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawTextWithShadow(
                textRenderer,
                Text.literal("RESOURCE PACKS"),
                18,
                15,
                LazyBuilderSettingsScreen.TEXT_PRIMARY
        );

        PackScreenAccessor access = (PackScreenAccessor) this;
        PackListWidget active = access.lazybuilder$getSelectedPackList();
        if (active != null) {
            int x = active.getX();
            int y = Math.max(42, active.getY() - 16);
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal("Higher packs take priority"),
                    x,
                    y,
                    LazyBuilderSettingsScreen.TEXT_MUTED
            );
        }
    }

    @Override
    public void close() {
        super.close();
        if (client != null && parent != null) {
            client.setScreen(parent);
        }
    }
}
