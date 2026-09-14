package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/** Builder-facing duplicate flow; filesystem destination naming remains derived. */
public final class DuplicateWorldScreen extends Screen {
    private final Screen parent;
    private final ClientWorldController controller;
    private final WorldControlWireProtocol.WorldSummary source;
    private TextFieldWidget displayName;
    private String enteredName;
    private boolean submitting;
    private String validation;
    private long observedRevision;

    public DuplicateWorldScreen(
            Screen parent,
            ClientWorldController controller,
            WorldControlWireProtocol.WorldSummary source
    ) {
        super(Text.literal("Duplicate World"));
        this.parent = parent;
        this.controller = controller;
        this.source = source;
        this.enteredName = source.displayName() + " Copy";
        this.observedRevision = controller.revision();
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(440, width - 48);
        int left = width / 2 - panelWidth / 2;
        int fieldLeft = left + 28;
        int fieldWidth = panelWidth - 56;

        displayName = new TextFieldWidget(textRenderer, fieldLeft, 118, fieldWidth, 24, Text.literal("New World Name"));
        displayName.setText(enteredName == null ? "" : enteredName);
        displayName.setPlaceholder(Text.literal("Duplicate world name"));
        displayName.setMaxLength(96);
        displayName.setDrawsBackground(false);
        displayName.setEditableColor(LbUi.TEXT_PRIMARY);
        displayName.setUneditableColor(LbUi.TEXT_DISABLED);
        displayName.active = !submitting;
        addDrawableChild(displayName);

        LbButtonWidget duplicate = LbUi.button(fieldLeft, 166, fieldWidth, 28,
                submitting ? "Duplicating…" : "Duplicate World", LbButtonWidget.Style.PRIMARY, this::submit);
        duplicate.active = !submitting;
        addDrawableChild(duplicate);

        addDrawableChild(LbUi.button(width / 2 - 58, 210, 116, 22,
                submitting ? "Back to Worlds" : "Cancel", LbButtonWidget.Style.GHOST, this::close));

        if (!submitting) setInitialFocus(displayName);
    }

    private void submit() {
        if (submitting) return;
        enteredName = displayName.getText().strip();
        if (enteredName.isEmpty()) {
            validation = "New world name is required.";
            return;
        }
        if (enteredName.equals(source.displayName())) {
            validation = "Choose a different name for the duplicate.";
            return;
        }

        String folder = availableFolderName(enteredName);
        validation = null;
        submitting = true;
        try {
            controller.duplicateWorld(source.worldId(), folder, enteredName);
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
        if (normalized.isEmpty()) normalized = "world_copy";
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        LbUi.background(context, width, height);
        int panelWidth = Math.min(440, width - 48);
        int left = width / 2 - panelWidth / 2;
        LbUi.elevatedPanel(context, left, 28, panelWidth, 250);

        context.drawTextWithShadow(textRenderer, Text.literal("DUPLICATE WORLD"), left + 24, 46, LbUi.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, Text.literal(source.displayName()), left + 24, 64, LbUi.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer,
                Text.literal("Create an independent copy for another build workspace."),
                left + 24, 82, LbUi.TEXT_SECONDARY);
        context.drawTextWithShadow(textRenderer, Text.literal("New world name"), left + 28, 104, LbUi.TEXT_MUTED);
        LbUi.field(context, displayName, validation != null);

        context.drawTextWithShadow(textRenderer,
                Text.literal("Player-local data is not copied into the duplicate."),
                left + 28, 150, LbUi.TEXT_MUTED);

        if (submitting && controller.activityMessage() != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(controller.activityMessage()),
                    width / 2, 246, LbUi.TEXT_SECONDARY);
        } else if (validation != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(validation),
                    width / 2, 246, LbUi.DANGER_BRIGHT);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
