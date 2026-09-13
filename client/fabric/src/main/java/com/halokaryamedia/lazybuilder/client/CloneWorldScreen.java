package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/** Clone flow keeps heavy copy server-owned while presenting one continuous operation. */
public final class CloneWorldScreen extends Screen {
    private final Screen parent;
    private final ClientWorldController controller;
    private final WorldControlWireProtocol.WorldSummary source;
    private TextFieldWidget displayName;
    private String validation;
    private boolean submitting;
    private long observedRevision;

    public CloneWorldScreen(Screen parent, ClientWorldController controller, WorldControlWireProtocol.WorldSummary source) {
        super(Text.literal("Clone World"));
        this.parent = parent;
        this.controller = controller;
        this.source = source;
        this.observedRevision = controller.revision();
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(440, width - 48);
        int left = width / 2 - panelWidth / 2;

        displayName = new TextFieldWidget(textRenderer, left + 28, 118, panelWidth - 56, 24, Text.literal("Clone Name"));
        displayName.setPlaceholder(Text.literal(source.displayName() + " Copy"));
        displayName.setMaxLength(96);
        displayName.setDrawsBackground(false);
        displayName.setEditableColor(LbUi.TEXT_PRIMARY);
        displayName.setUneditableColor(LbUi.TEXT_DISABLED);
        displayName.active = !submitting;
        addDrawableChild(displayName);

        LbButtonWidget clone = LbUi.button(left + 28, 162, panelWidth - 56, 28,
                submitting ? "Cloning…" : "Clone World", LbButtonWidget.Style.PRIMARY, this::submit);
        clone.active = !submitting;
        addDrawableChild(clone);

        addDrawableChild(LbUi.button(width / 2 - 50, 208, 100, 22,
                submitting ? "Back" : "Cancel", LbButtonWidget.Style.GHOST, this::close));
        if (!submitting) setInitialFocus(displayName);
    }

    private void submit() {
        if (submitting) return;
        String display = displayName.getText().strip();
        if (display.isEmpty()) display = source.displayName() + " Copy";
        String folder = availableFolderName(display);
        validation = null;
        submitting = true;
        try {
            controller.cloneWorld(source.worldId(), folder, display);
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
        LbUi.elevatedPanel(context, left, 30, panelWidth, 248);

        context.drawTextWithShadow(textRenderer, Text.literal("CLONE WORLD"), left + 24, 48, LbUi.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, Text.literal(source.displayName()), left + 24, 66, LbUi.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer,
                Text.literal("Create an independent copy for another build iteration."),
                left + 24, 84, LbUi.TEXT_SECONDARY);
        context.drawTextWithShadow(textRenderer, Text.literal("Clone name"), left + 28, 106, LbUi.TEXT_MUTED);
        LbUi.field(context, displayName, validation != null);

        if (submitting && controller.activityMessage() != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(controller.activityMessage()),
                    width / 2, 248, LbUi.TEXT_SECONDARY);
        } else if (validation != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(validation),
                    width / 2, 248, LbUi.DANGER_BRIGHT);
        } else {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("LazyBuilder generates a unique internal folder automatically."),
                    width / 2, 248, LbUi.TEXT_MUTED);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
