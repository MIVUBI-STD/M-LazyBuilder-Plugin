package com.halokaryamedia.lazybuilder.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Clear two-path entry point for adding a managed world. */
public final class AddWorldScreen extends Screen {
    private final Screen parent;
    private final ClientWorldController worlds;
    private final ClientTransferController transfers;

    public AddWorldScreen(Screen parent, ClientWorldController worlds, ClientTransferController transfers) {
        super(Text.literal("Add World"));
        this.parent = parent;
        this.worlds = worlds;
        this.transfers = transfers;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(520, width - 48);
        int left = width / 2 - panelWidth / 2;
        int cardWidth = (panelWidth - 42) / 2;
        int cardY = 100;

        addDrawableChild(LbUi.button(left + 14, cardY + 72, cardWidth - 28, 28,
                "Create New World", LbButtonWidget.Style.PRIMARY,
                () -> { if (client != null) client.setScreen(new CreateWorldScreen(parent, worlds)); }));

        addDrawableChild(LbUi.button(left + cardWidth + 28, cardY + 72, cardWidth - 28, 28,
                "Import Existing World", LbButtonWidget.Style.SECONDARY,
                () -> { if (client != null) client.setScreen(new ImportWorldScreen(parent, worlds, transfers)); }));

        addDrawableChild(LbUi.button(width / 2 - 48, cardY + 126, 96, 22,
                "Back", LbButtonWidget.Style.GHOST, this::close));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        LbUi.background(context, width, height);
        int panelWidth = Math.min(520, width - 48);
        int left = width / 2 - panelWidth / 2;
        int cardWidth = (panelWidth - 42) / 2;
        int cardY = 100;

        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Add World"), width / 2, 28, LbUi.TEXT_PRIMARY);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Choose the quickest path for the world you want to work on."),
                width / 2, 50, LbUi.TEXT_SECONDARY);

        LbUi.elevatedPanel(context, left, cardY, cardWidth, 108);
        LbUi.elevatedPanel(context, left + cardWidth + 14, cardY, cardWidth, 108);

        context.drawTextWithShadow(textRenderer, Text.literal("CREATE"), left + 14, cardY + 14, LbUi.ACCENT_BRIGHT);
        context.drawTextWithShadow(textRenderer, Text.literal("Start clean"), left + 14, cardY + 32, LbUi.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, Text.literal("Flat or void build world"), left + 14, cardY + 50, LbUi.TEXT_MUTED);

        int rightCard = left + cardWidth + 14;
        context.drawTextWithShadow(textRenderer, Text.literal("IMPORT"), rightCard + 14, cardY + 14, LbUi.TEXT_SECONDARY);
        context.drawTextWithShadow(textRenderer, Text.literal("Use an existing world"), rightCard + 14, cardY + 32, LbUi.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, Text.literal(".zip or .mcworld from your PC"), rightCard + 14, cardY + 50, LbUi.TEXT_MUTED);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
