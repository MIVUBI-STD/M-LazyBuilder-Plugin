package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/** Clone flow keeps heavy copy server-owned while presenting one continuous operation. */
public final class CloneWorldScreen extends Screen {
    private final Screen parent;
    private final ClientWorldController controller;
    private final WorldControlWireProtocol.WorldSummary source;
    private TextFieldWidget displayName;
    private String validation;
    private boolean submitting;
    private long observedRevision;

    public CloneWorldScreen(
            Screen parent,
            ClientWorldController controller,
            WorldControlWireProtocol.WorldSummary source
    ) {
        super(Text.literal("Clone World"));
        this.parent = parent;
        this.controller = controller;
        this.source = source;
        this.observedRevision = controller.revision();
    }

    @Override
    protected void init() {
        int center = width / 2;
        int panelWidth = Math.min(360, width - 50);
        int left = center - panelWidth / 2;
        int fieldWidth = panelWidth - 32;
        int fieldLeft = left + 16;

        displayName = new TextFieldWidget(textRenderer, fieldLeft, 108, fieldWidth, 22, Text.literal("Clone Name"));
        displayName.setPlaceholder(Text.literal(source.displayName() + " Copy"));
        displayName.setMaxLength(96);
        displayName.active = !submitting;
        addDrawableChild(displayName);

        ButtonWidget clone = ButtonWidget.builder(Text.literal(submitting ? "Cloning…" : "Clone World"), button -> submit())
                .dimensions(fieldLeft, 150, fieldWidth, 24).build();
        clone.active = !submitting;
        addDrawableChild(clone);

        addDrawableChild(ButtonWidget.builder(Text.literal(submitting ? "Back" : "Cancel"), button -> close())
                .dimensions(center - 50, 188, 100, 20).build());
        if (!submitting) setInitialFocus(displayName);
    }

    private void submit() {
        if (submitting) return;
        String display = displayName.getText().strip();
        if (display.isEmpty()) display = source.displayName() + " Copy";
        String folder = availableFolderName(display);
        validation = null;
        submitting = true;
        try {
            controller.cloneWorld(source.worldId(), folder, display);
            observedRevision = controller.revision();
            clearAndInit();
        } catch (RuntimeException exception) {
            submitting = false;
            validation = exception.getMessage();
            clearAndInit();
        }
    }

    @Override
    public void tick() {
        if (!submitting || observedRevision == controller.revision()) return;
        observedRevision = controller.revision();
        if (controller.lastError() != null) {
            submitting = false;
            validation = controller.lastError();
            clearAndInit();
            return;
        }
        if (controller.activityMessage() == null && client != null) client.setScreen(parent);
    }

    private String availableFolderName(String display) {
        String base = folderName(display);
        String candidate = base;
        int suffix = 2;
        while (folderExists(candidate)) {
            String tail = "_" + suffix++;
            int prefixLength = Math.min(base.length(), Math.max(1, 64 - tail.length()));
            candidate = base.substring(0, prefixLength) + tail;
        }
        return candidate;
    }

    private boolean folderExists(String candidate) {
        return controller.worlds().stream().anyMatch(world -> world.folderName().equalsIgnoreCase(candidate));
    }

    private static String folderName(String display) {
        String normalized = display.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isEmpty()) normalized = "world_copy";
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int center = width / 2;
        int panelWidth = Math.min(360, width - 50);
        int left = center - panelWidth / 2;
        context.fill(left, 38, left + panelWidth, 230, 0xB9191E25);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, title, center, 52, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Source: " + source.displayName()), center, 72, 0xAEB7C4);
        context.drawTextWithShadow(textRenderer, Text.literal("Clone Name"), displayName.getX(), 96, 0xAEB7C4);

        if (submitting && controller.activityMessage() != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(controller.activityMessage()), center, 216, 0xD8DEE9);
        } else if (validation != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(validation), center, 216, 0xFF7777);
        } else {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("A unique server folder is generated automatically."), center, 216, 0x7F8996);
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
