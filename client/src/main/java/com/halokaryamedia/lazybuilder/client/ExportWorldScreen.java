package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Minimal whole-world export surface. Native Java fast path is the V1 default. */
public final class ExportWorldScreen extends Screen {
    private static final String NATIVE_FORMAT = "JAVA_1_21_4";

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
        int fieldWidth = Math.min(280, width - 60);
        int left = center - fieldWidth / 2;

        artifactName = new TextFieldWidget(textRenderer, left, 92, fieldWidth, 20, Text.literal("Artifact Name"));
        artifactName.setText(world.folderName());
        artifactName.setMaxLength(96);
        addDrawableChild(artifactName);

        addDrawableChild(ButtonWidget.builder(Text.literal("Export Java 1.21.4"), button -> submit())
                .dimensions(center - 112, 132, 224, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(center - 50, 162, 100, 20).build());
        setInitialFocus(artifactName);
    }

    private void submit() {
        String artifact = artifactName.getText().strip();
        if (artifact.isEmpty()) {
            validation = "Artifact name is required.";
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
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("World: " + world.displayName()), width / 2, 44, 0xAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Native Java export bypasses conversion; Save dialog opens when ready."), width / 2, 62, 0x888888);
        context.drawTextWithShadow(textRenderer, Text.literal("Artifact Name"), artifactName.getX(), 80, 0xAAAAAA);
        if (validation != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(validation), width / 2, 198, 0xFF7777);
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
