package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/** File-first import flow; internal destination naming is derived automatically. */
public final class ImportWorldScreen extends Screen {
    private final Screen parent;
    private final ClientWorldController worlds;
    private final ClientTransferController transfers;
    private TextFieldWidget displayName;
    private String validation;
    private String status;
    private boolean choosing;

    public ImportWorldScreen(Screen parent, ClientWorldController worlds, ClientTransferController transfers) {
        super(Text.literal("Import World"));
        this.parent = parent;
        this.worlds = worlds;
        this.transfers = transfers;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int fieldWidth = Math.min(320, width - 60);
        int left = center - fieldWidth / 2;

        displayName = new TextFieldWidget(textRenderer, left, 96, fieldWidth, 22, Text.literal("World Name"));
        displayName.setPlaceholder(Text.literal("Optional — uses the file name by default"));
        displayName.setMaxLength(96);
        addDrawableChild(displayName);

        ButtonWidget choose = ButtonWidget.builder(Text.literal("Choose World File"), button -> choose())
                .dimensions(center - 120, 140, 240, 24).build();
        choose.active = !choosing;
        addDrawableChild(choose);

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(center - 50, 178, 100, 20).build());
        setInitialFocus(displayName);
    }

    private void choose() {
        if (choosing) return;
        String requestedDisplay = displayName == null ? "" : displayName.getText().strip();
        validation = null;
        status = "Choose a .zip or .mcworld file in the system dialog.";
        choosing = true;
        clearAndInit();

        try {
            transfers.chooseAndUploadImport(artifactName -> {
                String baseName = baseName(artifactName);
                String folder = folderName(baseName);
                String finalDisplay = requestedDisplay.isBlank() ? baseName : requestedDisplay;
                worlds.importWorld(artifactName, folder, finalDisplay);
                if (client != null) client.setScreen(parent);
            });
            status = "Preparing and uploading the selected world…";
        } catch (RuntimeException exception) {
            choosing = false;
            status = null;
            validation = exception.getMessage();
            clearAndInit();
        }
    }

    private static String baseName(String artifactName) {
        String name = artifactName == null ? "Imported World" : artifactName.strip();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mcworld")) name = name.substring(0, name.length() - 8);
        else if (lower.endsWith(".zip")) name = name.substring(0, name.length() - 4);
        name = name.strip();
        return name.isEmpty() ? "Imported World" : name;
    }

    private static String folderName(String baseName) {
        String normalized = baseName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isEmpty()) normalized = "imported_world";
        if (normalized.length() > 64) normalized = normalized.substring(0, 64);
        return normalized;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 22, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Select the world first. LazyBuilder handles the server destination automatically."),
                width / 2, 48, 0xB8C0CC);
        context.drawTextWithShadow(textRenderer, Text.literal("World Name"), displayName.getX(), 82, 0xAEB7C4);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Supported: .zip and .mcworld"), width / 2, 168, 0x8F9AA8);
        if (status != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, 214, 0xD8DEE9);
        }
        if (validation != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(validation), width / 2, 234, 0xFF7777);
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
