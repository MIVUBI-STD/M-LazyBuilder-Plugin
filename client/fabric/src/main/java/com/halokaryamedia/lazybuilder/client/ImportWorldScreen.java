package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Import form that reuses the canonical transfer channel before server publication. */
public final class ImportWorldScreen extends Screen {
    private final Screen parent;
    private final ClientWorldController worlds;
    private final ClientTransferController transfers;
    private TextFieldWidget folderName;
    private TextFieldWidget displayName;
    private String validation;

    public ImportWorldScreen(Screen parent, ClientWorldController worlds, ClientTransferController transfers) {
        super(Text.literal("Import World"));
        this.parent = parent;
        this.worlds = worlds;
        this.transfers = transfers;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int fieldWidth = Math.min(280, width - 60);
        int left = center - fieldWidth / 2;

        folderName = new TextFieldWidget(textRenderer, left, 76, fieldWidth, 20, Text.literal("Destination Folder"));
        folderName.setPlaceholder(Text.literal("imported_world"));
        folderName.setMaxLength(96);
        addDrawableChild(folderName);

        displayName = new TextFieldWidget(textRenderer, left, 118, fieldWidth, 20, Text.literal("Display Name"));
        displayName.setPlaceholder(Text.literal("Imported World"));
        displayName.setMaxLength(96);
        addDrawableChild(displayName);

        addDrawableChild(ButtonWidget.builder(Text.literal("Choose .zip / .mcworld"), button -> choose())
                .dimensions(center - 112, 158, 224, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(center - 50, 188, 100, 20).build());
        setInitialFocus(folderName);
    }

    private void choose() {
        String folder = folderName.getText().strip();
        String display = displayName.getText().strip();
        if (folder.isEmpty()) {
            validation = "Destination folder is required.";
            return;
        }
        if (display.isEmpty()) display = folder;
        String finalDisplay = display;
        try {
            transfers.chooseAndUploadImport(artifactName ->
                    worlds.importWorld(artifactName, folder, finalDisplay));
            if (client != null) client.setScreen(parent);
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
                Text.literal("Upload first; the server validates/converts before publishing."), width / 2, 44, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.literal("Destination Folder"), folderName.getX(), 64, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.literal("Display Name"), displayName.getX(), 106, 0xAAAAAA);
        if (validation != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(validation), width / 2, 222, 0xFF7777);
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
