package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Exact-name permanent-delete confirmation. */
public final class DeleteWorldScreen extends Screen {
    private final Screen parent;
    private final ClientWorldController controller;
    private final WorldControlWireProtocol.WorldSummary world;
    private TextFieldWidget confirmation;
    private String validation;

    public DeleteWorldScreen(
            Screen parent,
            ClientWorldController controller,
            WorldControlWireProtocol.WorldSummary world
    ) {
        super(Text.literal("Delete World"));
        this.parent = parent;
        this.controller = controller;
        this.world = world;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int fieldWidth = Math.min(300, width - 60);
        int left = center - fieldWidth / 2;

        confirmation = new TextFieldWidget(textRenderer, left, 112, fieldWidth, 20, Text.literal("Confirm Folder Name"));
        confirmation.setPlaceholder(Text.literal(world.folderName()));
        confirmation.setMaxLength(128);
        addDrawableChild(confirmation);

        addDrawableChild(ButtonWidget.builder(Text.literal("Delete Permanently"), button -> submit())
                .dimensions(center - 124, 154, 120, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(center + 4, 154, 120, 20).build());
        setInitialFocus(confirmation);
    }

    private void submit() {
        String typed = confirmation.getText();
        if (!typed.equals(world.folderName())) {
            validation = "Type the exact folder name to confirm deletion.";
            return;
        }
        try {
            controller.deleteWorld(world.worldId(), typed);
            close();
        } catch (RuntimeException exception) {
            validation = exception.getMessage();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFF7777);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("This permanently removes " + world.displayName() + "."), width / 2, 52, 0xDDDDDD);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Type exactly: " + world.folderName()), width / 2, 76, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.literal("Folder confirmation"), confirmation.getX(), 100, 0xAAAAAA);
        if (validation != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(validation), width / 2, 190, 0xFF7777);
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
