package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
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
        int panelWidth = Math.min(440, width - 48);
        int left = width / 2 - panelWidth / 2;
        int fieldLeft = left + 28;
        int fieldWidth = panelWidth - 56;

        displayName = new TextFieldWidget(textRenderer, fieldLeft, 108, fieldWidth, 24, Text.literal("World Name"));
        displayName.setPlaceholder(Text.literal("New build world"));
        displayName.setMaxLength(96);
        displayName.setDrawsBackground(false);
        displayName.setEditableColor(LbUi.TEXT_PRIMARY);
        displayName.setUneditableColor(LbUi.TEXT_DISABLED);
        displayName.active = !submitting;
        addDrawableChild(displayName);

        LbButtonWidget type = LbUi.button(fieldLeft, 152, fieldWidth, 26,
                typeLabel().getString(), LbButtonWidget.Style.SECONDARY,
                () -> {
                    if (submitting) return;
                    voidWorld = !voidWorld;
                    clearAndInit();
                });
        type.active = !submitting;
        addDrawableChild(type);

        LbButtonWidget create = LbUi.button(fieldLeft, 194, fieldWidth, 28,
                submitting ? "Creating…" : "Create World", LbButtonWidget.Style.PRIMARY, this::submit);
        create.active = !submitting;
        addDrawableChild(create);

        addDrawableChild(LbUi.button(width / 2 - 58, 238, 116, 22,
                submitting ? "Back to Worlds" : "Cancel", LbButtonWidget.Style.GHOST, this::close));

        if (!submitting) setInitialFocus(displayName);
    }

    private Text typeLabel() {
        return Text.literal("World Type   " + (voidWorld ? "Void" : "Flat"));
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
        if (controller.activityMessage() == null && client != null) client.setScreen(parent);
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
        LbUi.background(context, width, height);
        int panelWidth = Math.min(440, width - 48);
        int left = width / 2 - panelWidth / 2;
        LbUi.elevatedPanel(context, left, 28, panelWidth, 270);

        context.drawTextWithShadow(textRenderer, Text.literal("CREATE WORLD"), left + 24, 46, LbUi.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, Text.literal("Start a clean build workspace"), left + 24, 64, LbUi.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer,
                Text.literal("Choose a name and starting world type. LazyBuilder handles the rest."),
                left + 24, 82, LbUi.TEXT_SECONDARY);
        context.drawTextWithShadow(textRenderer, Text.literal("World name"), left + 28, 96, LbUi.TEXT_MUTED);
        LbUi.field(context, displayName, validation != null);

        context.drawTextWithShadow(textRenderer,
                Text.literal(voidWorld ? "Void is best for freeform builds." : "Flat is best for normal build maps."),
                left + 28, 184, LbUi.TEXT_MUTED);

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
