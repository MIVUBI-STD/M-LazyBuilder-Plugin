package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Minimal clone form; heavy copy remains server-owned and asynchronous. */
public final class CloneWorldScreen extends Screen {
    private final Screen parent;
    private final ClientWorldController controller;
    private final WorldControlWireProtocol.WorldSummary source;
    private TextFieldWidget folderName;
    private TextFieldWidget displayName;
    private String validation;

    public CloneWorldScreen(
            Screen parent,
            ClientWorldController controller,
            WorldControlWireProtocol.WorldSummary source
    ) {
        super(Text.literal("Clone World"));
        this.parent = parent;
        this.controller = controller;
        this.source = source;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int fieldWidth = Math.min(260, width - 60);
        int left = center - fieldWidth / 2;

        folderName = new TextFieldWidget(textRenderer, left, 76, fieldWidth, 20, Text.literal("Destination Folder"));
        folderName.setPlaceholder(Text.literal(source.folderName() + "_copy"));
        folderName.setMaxLength(96);
        addDrawableChild(folderName);

        displayName = new TextFieldWidget(textRenderer, left, 118, fieldWidth, 20, Text.literal("Display Name"));
        displayName.setPlaceholder(Text.literal(source.displayName() + " Copy"));
        displayName.setMaxLength(96);
        addDrawableChild(displayName);

        addDrawableChild(ButtonWidget.builder(Text.literal("Clone"), button -> submit())
                .dimensions(center - 104, 158, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(center + 4, 158, 100, 20).build());
        setInitialFocus(folderName);
    }

    private void submit() {
        String folder = folderName.getText().strip();
        String display = displayName.getText().strip();
        if (folder.isEmpty()) folder = source.folderName() + "_copy";
        if (display.isEmpty()) display = source.displayName() + " Copy";
        if (folder.equals(source.folderName())) {
            validation = "Destination folder must differ from the source.";
            return;
        }
        try {
            controller.cloneWorld(source.worldId(), folder, display);
            close();
        } catch (RuntimeException exception) {
            validation = exception.getMessage();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Source: " + source.displayName()), width / 2, 44, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.literal("Destination Folder"), folderName.getX(), 64, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.literal("Display Name"), displayName.getX(), 106, 0xAAAAAA);
        if (validation != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(validation), width / 2, 194, 0xFF7777);
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
