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
        boolean stacked = width < 520;
        int panelWidth = Math.max(280, Math.min(520, width - 32));
        int left = width / 2 - panelWidth / 2;

        if (stacked) {
            int cardWidth = panelWidth;
            int firstY = 92;
            int secondY = 202;
            addDrawableChild(LbUi.button(left + 16, firstY + 66, cardWidth - 32, 26,
                    "Create New World", LbButtonWidget.Style.PRIMARY,
                    () -> { if (client != null) client.setScreen(new CreateWorldScreen(parent, worlds)); }));
            addDrawableChild(LbUi.button(left + 16, secondY + 66, cardWidth - 32, 26,
                    "Import Existing World", LbButtonWidget.Style.SECONDARY,
                    () -> { if (client != null) client.setScreen(new ImportWorldScreen(parent, worlds, transfers)); }));
            addDrawableChild(LbUi.button(width / 2 - 48, secondY + 112, 96, 22,
                    "Back", LbButtonWidget.Style.GHOST, this::close));
            return;
        }

        int cardWidth = (panelWidth - 14) / 2;
        int cardY = 100;
        addDrawableChild(LbUi.button(left + 14, cardY + 72, cardWidth - 28, 28,
                "Create New World", LbButtonWidget.Style.PRIMARY,
                () -> { if (client != null) client.setScreen(new CreateWorldScreen(parent, worlds)); }));
        addDrawableChild(LbUi.button(left + cardWidth + 14, cardY + 72, cardWidth - 28, 28,
                "Import Existing World", LbButtonWidget.Style.SECONDARY,
                () -> { if (client != null) client.setScreen(new ImportWorldScreen(parent, worlds, transfers)); }));
        addDrawableChild(LbUi.button(width / 2 - 48, cardY + 126, 96, 22,
                "Back", LbButtonWidget.Style.GHOST, this::close));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        LbUi.background(context, width, height);
        boolean stacked = width < 520;
        int panelWidth = Math.max(280, Math.min(520, width - 32));
        int left = width / 2 - panelWidth / 2;

        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Add World"), width / 2, 28, LbUi.TEXT_PRIMARY);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Choose the quickest path for the world you want to work on."),
                width / 2, 50, LbUi.TEXT_SECONDARY);

        if (stacked) {
            int firstY = 92;
            int secondY = 202;
            LbUi.elevatedPanel(context, left, firstY, panelWidth, 102);
            LbUi.elevatedPanel(context, left, secondY, panelWidth, 102);
            renderCreateCard(context, left, firstY);
            renderImportCard(context, left, secondY);
        } else {
            int cardWidth = (panelWidth - 14) / 2;
            int cardY = 100;
            LbUi.elevatedPanel(context, left, cardY, cardWidth, 108);
            LbUi.elevatedPanel(context, left + cardWidth + 14, cardY, cardWidth, 108);
            renderCreateCard(context, left, cardY);
            renderImportCard(context, left + cardWidth + 14, cardY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderCreateCard(DrawContext context, int x, int y) {
        context.drawTextWithShadow(textRenderer, Text.literal("CREATE"), x + 14, y + 14, LbUi.ACCENT_BRIGHT);
        context.drawTextWithShadow(textRenderer, Text.literal("Start clean"), x + 14, y + 32, LbUi.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, Text.literal("Flat or void build world"), x + 14, y + 50, LbUi.TEXT_MUTED);
    }

    private void renderImportCard(DrawContext context, int x, int y) {
        context.drawTextWithShadow(textRenderer, Text.literal("IMPORT"), x + 14, y + 14, LbUi.TEXT_SECONDARY);
        context.drawTextWithShadow(textRenderer, Text.literal("Use an existing world"), x + 14, y + 32, LbUi.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, Text.literal(".zip or .mcworld from your PC"), x + 14, y + 50, LbUi.TEXT_MUTED);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
