package com.halokaryamedia.lazybuilder.utility.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Custom key-binding editor using Minecraft's existing KeyBinding authority and persistence. */
public final class LazyBuilderKeybindSettingsScreen extends Screen {
    private static final int BACKGROUND = 0xF20B0E12;
    private static final int TOP_BAR = 0xE813171C;
    private static final int ROW_FILL = 0xA81A1F25;
    private static final int ROW_HOVER = 0xC521272E;
    private static final int DIVIDER = 0x44545C66;
    private static final int ROW_HEIGHT = 32;
    private static final int ROW_GAP = 2;
    private static final int SECTION_HEIGHT = 18;
    private static final int SECTION_GAP = 8;
    private static final int CONTROL_WIDTH = 150;
    private static final int FOOTER_HEIGHT = 40;

    private final Screen parent;
    private final List<Group> groups = new ArrayList<>();
    private KeyBinding capturing;
    private int scrollOffset;
    private int maxScroll;

    public LazyBuilderKeybindSettingsScreen(Screen parent) {
        super(Text.literal("Key Bindings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        buildGroups();
        layoutRows();

        this.addDrawableChild(new LazyBuilderSettingsControlWidget(
                shellLeft() + 8,
                height - 30,
                110,
                22,
                Text.literal("Reset All"),
                true,
                LazyBuilderSettingsControlWidget.Kind.FOOTER,
                this::confirmResetAll
        ));
        this.addDrawableChild(new LazyBuilderSettingsControlWidget(
                shellLeft() + shellWidth() - 100,
                height - 30,
                92,
                22,
                Text.literal("Back"),
                true,
                LazyBuilderSettingsControlWidget.Kind.FOOTER,
                this::close
        ));
    }

    private void buildGroups() {
        groups.clear();
        if (client == null) return;

        List<KeyBinding> bindings = new ArrayList<>(List.of(client.options.allKeys));
        bindings.sort(Comparator
                .comparing(KeyBinding::getCategory)
                .thenComparing(KeyBinding::getTranslationKey));

        Group current = null;
        String category = null;
        for (KeyBinding binding : bindings) {
            if (!binding.getCategory().equals(category)) {
                category = binding.getCategory();
                current = new Group(Text.translatable(category).getString());
                groups.add(current);
            }
            current.bindings.add(binding);
        }
    }

    private void layoutRows() {
        int left = panelLeft();
        int width = panelWidth();
        int viewportBottom = viewportBottom();
        int contentHeight = totalContentHeight();
        int viewportHeight = Math.max(1, viewportBottom - 76);
        maxScroll = Math.max(0, contentHeight - viewportHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));

        int y = 84 - scrollOffset;
        for (Group group : groups) {
            group.y = y;
            y += SECTION_HEIGHT;

            for (KeyBinding binding : group.bindings) {
                int rowY = y;
                if (rowY + ROW_HEIGHT > 76 && rowY < viewportBottom) {
                    boolean conflict = hasConflict(binding);
                    String label = capturing == binding
                            ? "Press a key..."
                            : binding.getBoundKeyLocalizedText().getString() + (conflict ? "  !" : "");

                    this.addDrawableChild(new LazyBuilderSettingsControlWidget(
                            left + width - CONTROL_WIDTH - 8,
                            rowY + 5,
                            CONTROL_WIDTH,
                            22,
                            Text.literal(label),
                            true,
                            LazyBuilderSettingsControlWidget.Kind.KEY,
                            () -> {
                                capturing = binding;
                                clearAndInit();
                            }
                    ));
                }
                y += ROW_HEIGHT + ROW_GAP;
            }
            y += SECTION_GAP;
        }
    }

    private int totalContentHeight() {
        int total = 0;
        for (Group group : groups) {
            total += SECTION_HEIGHT;
            total += group.bindings.size() * (ROW_HEIGHT + ROW_GAP);
            total += SECTION_GAP;
        }
        return total;
    }

    private void confirmResetAll() {
        if (client == null) return;
        client.setScreen(new LazyBuilderConfirmScreen(
                this,
                "Reset Key Bindings",
                "Restore every vanilla and Fabric key binding to its default?",
                "Reset All",
                this::resetAll
        ));
    }

    private void resetAll() {
        if (client == null) return;
        for (KeyBinding binding : client.options.allKeys) {
            binding.setBoundKey(binding.getDefaultKey());
        }
        KeyBinding.updateKeysByCode();
        client.options.write();
        capturing = null;
        clearAndInit();
    }

    private void bind(InputUtil.Key key) {
        if (client == null || capturing == null) return;
        capturing.setBoundKey(key);
        KeyBinding.updateKeysByCode();
        client.options.write();
        capturing = null;
        clearAndInit();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (capturing != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                capturing = null;
                clearAndInit();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                bind(InputUtil.UNKNOWN_KEY);
                return true;
            }
            bind(InputUtil.fromKeyCode(keyCode, scanCode));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (capturing != null) {
            bind(InputUtil.Type.MOUSE.createFromCode(button));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKGROUND);
        context.fill(0, 0, width, 34, TOP_BAR);
        context.fill(0, height - FOOTER_HEIGHT, width, height, TOP_BAR);

        int left = panelLeft();
        int right = left + panelWidth();

        context.drawTextWithShadow(textRenderer, Text.literal("SETTINGS"), shellLeft() + 8, 15, LazyBuilderSettingsScreen.TEXT_PRIMARY);
        context.drawTextWithShadow(textRenderer, Text.literal("CONTROLS"), left, 49, LazyBuilderSettingsScreen.TEXT_MUTED);
        context.drawTextWithShadow(textRenderer, Text.literal("KEY BINDINGS"), left + 70, 49, LazyBuilderSettingsScreen.TEXT_PRIMARY);
        context.fill(left + 70, 63, left + 142, 65, LazyBuilderSettingsScreen.ACCENT);

        context.enableScissor(left, 76, right, viewportBottom());
        int y = 84 - scrollOffset;
        for (Group group : groups) {
            if (y + SECTION_HEIGHT > 76 && y < viewportBottom()) {
                context.drawTextWithShadow(textRenderer, Text.literal(group.title), left, y + 4, LazyBuilderSettingsScreen.TEXT_SECONDARY);
            }
            y += SECTION_HEIGHT;

            for (KeyBinding binding : group.bindings) {
                int rowY = y;
                if (rowY + ROW_HEIGHT > 76 && rowY < viewportBottom()) {
                    boolean hovered = mouseX >= left && mouseX < right && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
                    context.fill(left, rowY, right, rowY + ROW_HEIGHT - 1, hovered ? ROW_HOVER : ROW_FILL);
                    context.fill(left, rowY + ROW_HEIGHT - 1, right, rowY + ROW_HEIGHT, DIVIDER);

                    int maxText = Math.max(80, panelWidth() - CONTROL_WIDTH - 28);
                    String name = Text.translatable(binding.getTranslationKey()).getString();
                    context.drawTextWithShadow(
                            textRenderer,
                            Text.literal(textRenderer.trimToWidth(name, maxText)),
                            left + 8,
                            rowY + 12,
                            hasConflict(binding) ? 0xFFFFA7A7 : LazyBuilderSettingsScreen.TEXT_PRIMARY
                    );
                }
                y += ROW_HEIGHT + ROW_GAP;
            }
            y += SECTION_GAP;
        }
        context.disableScissor();

        renderScrollBar(context, right + 6);
        if (hasContextPane()) {
            context.fill(right + 14, 76, right + 15, height - FOOTER_HEIGHT - 10, DIVIDER);
        }
        renderHelp(context, right + 30);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderHelp(DrawContext context, int x) {
        if (!hasContextPane()) return;
        int available = shellLeft() + shellWidth() - x - 8;
        if (available < 120) return;
        context.drawTextWithShadow(textRenderer, Text.literal("KEY BINDINGS"), x, 88, LazyBuilderSettingsScreen.TEXT_PRIMARY);
        String copy;
        if (capturing != null) {
            copy = "Listening for input. Press Esc to cancel. Backspace/Delete clears the binding.";
        } else if (hasAnyConflict()) {
            copy = "Some bindings share the same key. Conflicts are marked with ! so they can be resolved before leaving Controls.";
        } else {
            copy = "Select a binding, then press a keyboard or mouse button. Backspace/Delete clears a binding.";
        }
        int y = 106;
        for (var line : textRenderer.wrapLines(Text.literal(copy), available)) {
            context.drawTextWithShadow(textRenderer, line, x, y, LazyBuilderSettingsScreen.TEXT_MUTED);
            y += 11;
        }
    }

    private boolean hasConflict(KeyBinding binding) {
        if (client == null || binding.isUnbound()) return false;
        String key = binding.getBoundKeyTranslationKey();
        for (KeyBinding other : client.options.allKeys) {
            if (other == binding || other.isUnbound()) continue;
            if (key.equals(other.getBoundKeyTranslationKey())) return true;
        }
        return false;
    }

    private boolean hasAnyConflict() {
        if (client == null) return false;
        for (KeyBinding binding : client.options.allKeys) {
            if (hasConflict(binding)) return true;
        }
        return false;
    }

    private void renderScrollBar(DrawContext context, int x) {
        if (maxScroll <= 0) return;
        int top = 76;
        int bottom = viewportBottom();
        int trackHeight = Math.max(1, bottom - top);
        int contentHeight = trackHeight + maxScroll;
        int thumbHeight = Math.max(18, trackHeight * trackHeight / contentHeight);
        int travel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = top + (int) Math.round((scrollOffset / (double) maxScroll) * travel);
        context.fill(x, top, x + 2, bottom, 0x334A525C);
        context.fill(x, thumbY, x + 2, thumbY + thumbHeight, LazyBuilderSettingsScreen.ACCENT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll > 0 && mouseX >= panelLeft() && mouseX <= panelLeft() + panelWidth()) {
            int next = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.round(verticalAmount * 24.0)));
            if (next != scrollOffset) {
                scrollOffset = next;
                clearAndInit();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private int viewportBottom() {
        return Math.max(77, height - FOOTER_HEIGHT - 6);
    }

    private int shellWidth() {
        return Math.min(920, Math.max(280, width - 24));
    }

    private int shellLeft() {
        return (width - shellWidth()) / 2;
    }

    private boolean hasContextPane() {
        return shellWidth() >= 640;
    }

    private int panelWidth() {
        int shell = shellWidth();
        if (!hasContextPane()) return shell - 16;
        return Math.min(520, Math.max(390, (int) (shell * 0.68)));
    }

    private int panelLeft() {
        return shellLeft() + 8;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    private static final class Group {
        private final String title;
        private final List<KeyBinding> bindings = new ArrayList<>();
        private int y;

        private Group(String title) {
            this.title = title;
        }
    }
}
