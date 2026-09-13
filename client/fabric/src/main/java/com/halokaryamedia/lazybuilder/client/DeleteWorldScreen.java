package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Exact-name permanent-delete confirmation with visible server completion state. */
public final class DeleteWorldScreen extends Screen {
    private final Screen parent;
    private final ClientWorldController controller;
    private final WorldControlWireProtocol.WorldSummary world;
    private TextFieldWidget confirmation;
    private String validation;
    private boolean submitting;
    private long observedRevision;

    public DeleteWorldScreen(
            Screen parent,
            ClientWorldController controller,
            WorldControlWireProtocol.WorldSummary world
    ) {
        super(Text.literal("Delete World"));
        this.parent = parent;
        this.controller = controller;
        this.world = world;
        this.observedRevision = controller.revision();
    }

    @Override
    protected void init() {
        int center = width / 2;
        int panelWidth = Math.min(380, width - 50);
        int left = center - panelWidth / 2;
        int fieldWidth = panelWidth - 32;
        int fieldLeft = left + 16;

        confirmation = new TextFieldWidget(textRenderer, fieldLeft, 126, fieldWidth, 22, Text.literal("Confirm Folder Name"));
        confirmation.setPlaceholder(Text.literal(world.folderName()));
        confirmation.setMaxLength(128);
        confirmation.active = !submitting;
        addDrawableChild(confirmation);

        ButtonWidget delete = ButtonWidget.builder(Text.literal(submitting ? "Deleting…" : "Delete Permanently"), button -> submit())
                .dimensions(fieldLeft, 168, fieldWidth, 24).build();
        delete.active = !submitting;
        addDrawableChild(delete);

        addDrawableChild(ButtonWidget.builder(Text.literal(submitting ? "Back" : "Cancel"), button -> close())
                .dimensions(center - 50, 206, 100, 20).build());
        if (!submitting) setInitialFocus(confirmation);
    }

    private void submit() {
        if (submitting) return;
        String typed = confirmation.getText();
        if (!typed.equals(world.folderName())) {
            validation = "Type the exact folder name to confirm deletion.";
            return;
        }
        validation = null;
        submitting = true;
        try {
            controller.deleteWorld(world.worldId(), typed);
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

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int center = width / 2;
        int panelWidth = Math.min(380, width - 50);
        int left = center - panelWidth / 2;
        context.fill(left, 36, left + panelWidth, 250, 0xC21D1719);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, title, center, 50, 0xFF7777);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("This permanently removes " + world.displayName() + "."), center, 72, 0xE6E6E6);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("This action cannot be undone."), center, 88, 0xFF9B9B);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Type exactly: " + world.folderName()), center, 106, 0xAEB7C4);
        context.drawTextWithShadow(textRenderer, Text.literal("Folder confirmation"), confirmation.getX(), 114, 0xAEB7C4);

        if (submitting && controller.activityMessage() != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(controller.activityMessage()), center, 236, 0xD8DEE9);
        } else if (validation != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(validation), center, 236, 0xFF7777);
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
