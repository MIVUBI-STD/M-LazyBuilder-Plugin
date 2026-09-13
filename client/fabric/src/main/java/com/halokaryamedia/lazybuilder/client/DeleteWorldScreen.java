package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
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

    public DeleteWorldScreen(Screen parent, ClientWorldController controller, WorldControlWireProtocol.WorldSummary world) {
        super(Text.literal("Delete World"));
        this.parent = parent;
        this.controller = controller;
        this.world = world;
        this.observedRevision = controller.revision();
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(440, width - 48);
        int left = width / 2 - panelWidth / 2;

        confirmation = new TextFieldWidget(textRenderer, left + 28, 138, panelWidth - 56, 24, Text.literal("Confirm Folder Name"));
        confirmation.setPlaceholder(Text.literal(world.folderName()));
        confirmation.setMaxLength(128);
        confirmation.setDrawsBackground(false);
        confirmation.setEditableColor(LbUi.TEXT_PRIMARY);
        confirmation.setUneditableColor(LbUi.TEXT_DISABLED);
        confirmation.active = !submitting;
        addDrawableChild(confirmation);

        LbButtonWidget delete = LbUi.button(left + 28, 184, panelWidth - 56, 28,
                submitting ? "Deleting…" : "Delete Permanently", LbButtonWidget.Style.DANGER, this::submit);
        delete.active = !submitting;
        addDrawableChild(delete);

        addDrawableChild(LbUi.button(width / 2 - 50, 228, 100, 22,
                submitting ? "Back" : "Cancel", LbButtonWidget.Style.GHOST, this::close));
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
        LbUi.background(context, width, height);
        int panelWidth = Math.min(440, width - 48);
        int left = width / 2 - panelWidth / 2;
        LbUi.elevatedPanel(context, left, 30, panelWidth, 276);

        context.drawTextWithShadow(textRenderer, Text.literal("DELETE WORLD"), left + 24, 48, LbUi.DANGER_BRIGHT);
        context.drawTextWithShadow(textRenderer, Text.literal(world.displayName()), left + 24, 68, LbUi.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer,
                Text.literal("This permanently removes the managed world and cannot be undone."),
                left + 24, 88, LbUi.TEXT_SECONDARY);
        context.drawTextWithShadow(textRenderer, Text.literal("Type exactly"), left + 28, 112, LbUi.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, Text.literal(world.folderName()), left + 28, 126, LbUi.WARNING);
        LbUi.field(context, confirmation, validation != null);

        if (submitting && controller.activityMessage() != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(controller.activityMessage()),
                    width / 2, 272, LbUi.TEXT_SECONDARY);
        } else if (validation != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(validation),
                    width / 2, 272, LbUi.DANGER_BRIGHT);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
