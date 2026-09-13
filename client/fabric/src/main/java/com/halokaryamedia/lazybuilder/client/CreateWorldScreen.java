package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/** Create flow exposes only real user decisions; internal folder naming is derived. */
public final class CreateWorldScreen extends Screen {
    private final Screen parent;
    private final ClientWorldController controller;
    private TextFieldWidget displayName;
    private boolean voidWorld;
    private boolean submitting;
    private String validation;
    private long observedRevision;

    public CreateWorldScreen(Screen parent, ClientWorldController controller) {
        super(Text.literal("Create World"));
        this.parent = parent;
        this.controller = controller;
        this.observedRevision = controller.revision();
    }

    @Override
    protected void init() {
        int center = width / 2;
        int panelWidth = Math.min(360, width - 50);
        int left = center - panelWidth / 2;
        int fieldWidth = panelWidth - 32;
        int fieldLeft = left + 16;

        displayName = new TextFieldWidget(textRenderer, fieldLeft, 100, fieldWidth, 22, Text.literal("World Name"));
        displayName.setPlaceholder(Text.literal("New World"));
        displayName.setMaxLength(96);
        displayName.active = !submitting;
        addDrawableChild(displayName);

        ButtonWidget type = ButtonWidget.builder(typeLabel(), button -> {
            voidWorld = !voidWorld;
            button.setMessage(typeLabel());
        }).dimensions(fieldLeft, 142, fieldWidth, 22).build();
        type.active = !submitting;
        addDrawableChild(type);

        ButtonWidget create = ButtonWidget.builder(Text.literal(submitting ? "Creating…" : "Create World"), button -> submit())
                .dimensions(fieldLeft, 180, fieldWidth, 24).build();
        create.active = !submitting;
        addDrawableChild(create);

        addDrawableChild(ButtonWidget.builder(Text.literal(submitting ? "Back" : "Cancel"), button -> close())
                .dimensions(center - 50, 216, 100, 20).build());

        if (!submitting) setInitialFocus(displayName);
    }

    private Text typeLabel() {
        return Text.literal("World Type: " + (voidWorld ? "Void" : "Flat"));
    }

    private void submit() {
        if (submitting) return;
        String display = displayName.getText().strip();
        if (display.isEmpty()) {
            validation = "World name is required.";
            return;
        }
        String folder = availableFolderName(display);
        validation = null;
        submitting = true;
        try {
            controller.create(folder, display, voidWorld ? "VOID" : "FLAT");
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
        if (controller.activityMessage() == null) {
            if (client != null) client.setScreen(parent);
        }
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
        if (normalized.isEmpty()) normalized = "world";
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int center = width / 2;
        int panelWidth = Math.min(360, width - 50);
        int left = center - panelWidth / 2;
        context.fill(left, 38, left + panelWidth, 252, 0xB9191E25);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, title, center, 52, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Create a managed build world."), center, 70, 0xAEB7C4);
        context.drawTextWithShadow(textRenderer, Text.literal("World Name"), displayName.getX(), 88, 0xAEB7C4);

        if (submitting && controller.activityMessage() != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(controller.activityMessage()), center, 238, 0xD8DEE9);
        } else if (validation != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(validation), center, 238, 0xFF7777);
        } else {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Server folder is generated automatically from the world name."), center, 238, 0x7F8996);
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
