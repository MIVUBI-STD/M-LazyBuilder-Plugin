package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Small Create World surface: name + Flat/Void only; settings stay separate. */
public final class CreateWorldScreen extends Screen {
    private final Screen parent;
    private final ClientWorldController controller;
    private TextFieldWidget folderName;
    private TextFieldWidget displayName;
    private boolean voidWorld;
    private String validation;

    public CreateWorldScreen(Screen parent, ClientWorldController controller) {
        super(Text.literal("Create World"));
        this.parent = parent;
        this.controller = controller;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int fieldWidth = Math.min(260, width - 60);
        int left = center - fieldWidth / 2;

        folderName = new TextFieldWidget(textRenderer, left, 66, fieldWidth, 20, Text.literal("World Folder"));
        folderName.setPlaceholder(Text.literal("world_folder"));
        folderName.setMaxLength(96);
        addDrawableChild(folderName);

        displayName = new TextFieldWidget(textRenderer, left, 108, fieldWidth, 20, Text.literal("Display Name"));
        displayName.setPlaceholder(Text.literal("Display Name"));
        displayName.setMaxLength(96);
        addDrawableChild(displayName);

        addDrawableChild(ButtonWidget.builder(typeLabel(), button -> {
            voidWorld = !voidWorld;
            button.setMessage(typeLabel());
        }).dimensions(left, 146, fieldWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Create"), button -> submit())
                .dimensions(center - 104, 184, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(center + 4, 184, 100, 20).build());

        setInitialFocus(folderName);
    }

    private Text typeLabel() {
        return Text.literal("Type: " + (voidWorld ? "Void" : "Flat"));
    }

    private void submit() {
        String folder = folderName.getText().strip();
        String display = displayName.getText().strip();
        if (folder.isEmpty()) {
            validation = "World folder is required.";
            return;
        }
        if (display.isEmpty()) display = folder;
        try {
            controller.create(folder, display, voidWorld ? "VOID" : "FLAT");
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
        context.drawTextWithShadow(textRenderer, Text.literal("World Folder"), folderName.getX(), 54, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.literal("Display Name"), displayName.getX(), 96, 0xAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Advanced settings are configured after creation."), width / 2, 218, 0x888888);
        if (validation != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(validation), width / 2, 238, 0xFF7777);
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
