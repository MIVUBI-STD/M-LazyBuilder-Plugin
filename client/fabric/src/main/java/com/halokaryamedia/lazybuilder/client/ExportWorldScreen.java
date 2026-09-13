package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Whole-world export surface. Server snapshot/package stays authoritative. */
public final class ExportWorldScreen extends Screen {
    private static final String NATIVE_FORMAT = "JAVA_1_21_4";
    private static final DateTimeFormatter EXPORT_SUFFIX = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Screen parent;
    private final ClientWorldController controller;
    private final WorldControlWireProtocol.WorldSummary world;
    private TextFieldWidget artifactName;
    private String validation;

    public ExportWorldScreen(
            Screen parent,
            ClientWorldController controller,
            WorldControlWireProtocol.WorldSummary world
    ) {
        super(Text.literal("Export World"));
        this.parent = parent;
        this.controller = controller;
        this.world = world;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int fieldWidth = Math.min(320, width - 60);
        int left = center - fieldWidth / 2;

        artifactName = new TextFieldWidget(textRenderer, left, 102, fieldWidth, 22, Text.literal("File Name"));
        artifactName.setText(world.folderName() + "-" + EXPORT_SUFFIX.format(LocalDateTime.now()));
        artifactName.setMaxLength(96);
        addDrawableChild(artifactName);

        addDrawableChild(ButtonWidget.builder(Text.literal("Export World"), button -> submit())
                .dimensions(center - 120, 144, 240, 24).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(center - 50, 182, 100, 20).build());
        setInitialFocus(artifactName);
    }

    private void submit() {
        String artifact = artifactName.getText().strip();
        if (artifact.isEmpty()) {
            validation = "File name is required.";
            return;
        }
        try {
            controller.exportWorld(world.worldId(), NATIVE_FORMAT, artifact);
            close();
        } catch (RuntimeException exception) {
            validation = exception.getMessage();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 22, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(world.displayName()), width / 2, 48, 0xD8DEE9);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Java 1.21.4 world archive"), width / 2, 64, 0xAEB7C4);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("LazyBuilder will prepare a safe snapshot, then open Save As when it is ready."),
                width / 2, 80, 0x8F9AA8);
        context.drawTextWithShadow(textRenderer, Text.literal("File Name"), artifactName.getX(), 90, 0xAEB7C4);
        if (validation != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(validation), width / 2, 222, 0xFF7777);
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
