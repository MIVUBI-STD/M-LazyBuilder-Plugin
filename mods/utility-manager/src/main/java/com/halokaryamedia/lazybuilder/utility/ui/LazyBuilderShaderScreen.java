package com.halokaryamedia.lazybuilder.utility.ui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.lang.reflect.Method;
import java.nio.file.Path;

/**
 * LazyBuilder presentation shell for Iris shader management.
 *
 * Iris remains the shader/config/runtime authority. This screen only exposes the
 * common player actions with LazyBuilder's settings visual language.
 */
public final class LazyBuilderShaderScreen extends Screen {
    private static final int BACKGROUND = 0xF20B0E12;
    private static final int TOP_BAR = 0xE813171C;
    private static final int PANEL = 0xA81A1F25;
    private static final int DIVIDER = 0x44545C66;
    private static final int FOOTER_HEIGHT = 40;

    private enum FailedAction {
        NONE,
        TOGGLE,
        OPEN_MANAGER,
        OPEN_FOLDER
    }

    private final Screen parent;
    private String failure;
    private FailedAction failedAction = FailedAction.NONE;

    public LazyBuilderShaderScreen(Screen parent) {
        super(Text.literal("Shaders"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int shell = Math.min(720, Math.max(280, width - 24));
        int left = (width - shell) / 2;
        int right = left + shell;
        int rowWidth = Math.min(190, Math.max(120, shell / 3));
        int actionX = right - rowWidth;
        int y = 82;

        if (!isIrisAvailable()) {
            addDrawableChild(new LazyBuilderSettingsControlWidget(
                    actionX,
                    y,
                    rowWidth,
                    22,
                    Text.literal("Unavailable"),
                    false,
                    LazyBuilderSettingsControlWidget.Kind.STATUS,
                    () -> {}
            ));
        } else {
            addDrawableChild(new LazyBuilderSettingsControlWidget(
                    actionX,
                    y,
                    rowWidth,
                    22,
                    Text.literal(shadersEnabled() ? "Turn Off" : "Turn On"),
                    true,
                    LazyBuilderSettingsControlWidget.Kind.ACTION,
                    this::toggleShaders
            ));

            y += 42;
            addDrawableChild(new LazyBuilderSettingsControlWidget(
                    actionX,
                    y,
                    rowWidth,
                    22,
                    Text.literal("Manage Shader Packs"),
                    true,
                    LazyBuilderSettingsControlWidget.Kind.ACTION,
                    this::openIrisManager
            ));

            y += 34;
            addDrawableChild(new LazyBuilderSettingsControlWidget(
                    actionX,
                    y,
                    rowWidth,
                    22,
                    Text.literal("Shader Settings"),
                    shadersEnabled(),
                    shadersEnabled()
                            ? LazyBuilderSettingsControlWidget.Kind.ACTION
                            : LazyBuilderSettingsControlWidget.Kind.STATUS,
                    this::openIrisManager
            ));

            y += 34;
            addDrawableChild(new LazyBuilderSettingsControlWidget(
                    actionX,
                    y,
                    rowWidth,
                    22,
                    Text.literal("Open Shader Folder"),
                    true,
                    LazyBuilderSettingsControlWidget.Kind.ACTION,
                    this::openShaderFolder
            ));

            if (failure != null) {
                y += 46;
                addDrawableChild(new LazyBuilderSettingsControlWidget(
                        actionX,
                        y,
                        rowWidth,
                        22,
                        Text.literal("Retry"),
                        true,
                        LazyBuilderSettingsControlWidget.Kind.ACTION,
                        this::retry
                ));
            }
        }

        addDrawableChild(new LazyBuilderSettingsControlWidget(
                right - 92,
                height - 30,
                92,
                22,
                Text.literal("Back"),
                true,
                LazyBuilderSettingsControlWidget.Kind.FOOTER,
                this::close
        ));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKGROUND);
        context.fill(0, 0, width, 36, TOP_BAR);
        context.fill(0, height - FOOTER_HEIGHT, width, height, TOP_BAR);

        int shell = Math.min(720, Math.max(280, width - 24));
        int left = (width - shell) / 2;
        int right = left + shell;

        context.drawTextWithShadow(
                textRenderer,
                Text.literal("SHADERS"),
                left,
                15,
                LazyBuilderSettingsScreen.TEXT_PRIMARY
        );

        int panelTop = 64;
        int panelBottom = Math.min(height - FOOTER_HEIGHT - 12, 284);
        context.fill(left, panelTop, right, panelBottom, DIVIDER);
        context.fill(left + 1, panelTop + 1, right - 1, panelBottom - 1, PANEL);

        int textX = left + 14;
        int textY = 82;
        context.drawTextWithShadow(
                textRenderer,
                Text.literal(isIrisAvailable() ? shaderName() : "Shader Support"),
                textX,
                textY,
                LazyBuilderSettingsScreen.TEXT_PRIMARY
        );
        textY += 16;

        String status = !isIrisAvailable()
                ? "A compatible Iris installation is required."
                : shadersEnabled() ? "Shader enabled" : "Shader disabled";
        context.drawTextWithShadow(
                textRenderer,
                Text.literal(status),
                textX,
                textY,
                LazyBuilderSettingsScreen.TEXT_SECONDARY
        );

        textY += 24;
        String description = !isIrisAvailable()
                ? "Install Iris to use shader packs. LazyBuilder does not provide its own shader renderer."
                : "Iris owns shader packs and shader-specific options. LazyBuilder provides the common controls here.";
        for (var line : textRenderer.wrapLines(Text.literal(description), Math.max(100, shell - 230))) {
            context.drawTextWithShadow(
                    textRenderer,
                    line,
                    textX,
                    textY,
                    LazyBuilderSettingsScreen.TEXT_MUTED
            );
            textY += 11;
        }

        if (failure != null) {
            int errorY = Math.min(panelBottom - 38, textY + 18);
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal("Could not apply shader action"),
                    textX,
                    errorY,
                    0xFFFFA7A7
            );
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(textRenderer.trimToWidth(failure, Math.max(100, shell - 230))),
                    textX,
                    errorY + 13,
                    LazyBuilderSettingsScreen.TEXT_MUTED
            );
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private boolean isIrisAvailable() {
        return FabricLoader.getInstance().isModLoaded("iris");
    }

    private boolean shadersEnabled() {
        if (!isIrisAvailable()) return false;
        try {
            Object config = irisConfig();
            return (boolean) config.getClass().getMethod("areShadersEnabled").invoke(config);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return false;
        }
    }

    private String shaderName() {
        if (!isIrisAvailable()) return "Unavailable";
        if (!shadersEnabled()) return "No Shader";

        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Object name = iris.getMethod("getCurrentPackName").invoke(null);
            if (name instanceof String value && !value.isBlank()) return value;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return "Active Shader";
    }

    private Class<?> irisApiClass() throws ClassNotFoundException {
        return Class.forName("net.irisshaders.iris.api.v0.IrisApi");
    }

    private Object irisApi() throws ReflectiveOperationException {
        Class<?> apiClass = irisApiClass();
        return apiClass.getMethod("getInstance").invoke(null);
    }

    private Object irisConfig() throws ReflectiveOperationException {
        Class<?> apiClass = irisApiClass();
        Object api = apiClass.getMethod("getInstance").invoke(null);
        return apiClass.getMethod("getConfig").invoke(api);
    }

    private void toggleShaders() {
        failure = null;
        failedAction = FailedAction.NONE;
        try {
            Object config = irisConfig();
            Method currentMethod = config.getClass().getMethod("areShadersEnabled");
            Method applyMethod = config.getClass().getMethod("setShadersEnabledAndApply", boolean.class);
            boolean current = (boolean) currentMethod.invoke(config);
            applyMethod.invoke(config, !current);
            clearAndInit();
        } catch (ReflectiveOperationException | RuntimeException error) {
            fail(error, FailedAction.TOGGLE);
        }
    }

    private void openIrisManager() {
        failure = null;
        failedAction = FailedAction.NONE;
        try {
            Class<?> apiClass = irisApiClass();
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object screen = apiClass.getMethod("openMainIrisScreenObj", Object.class).invoke(api, this);
            if (screen instanceof Screen irisScreen && client != null) {
                client.setScreen(irisScreen);
                return;
            }
            failure = "Iris did not provide a shader management screen.";
            failedAction = FailedAction.OPEN_MANAGER;
            clearAndInit();
        } catch (ReflectiveOperationException | RuntimeException error) {
            fail(error, FailedAction.OPEN_MANAGER);
        }
    }

    private void openShaderFolder() {
        failure = null;
        failedAction = FailedAction.NONE;
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Object path = iris.getMethod("getShaderpacksDirectory").invoke(null);
            if (path instanceof Path shaderPath) {
                Util.getOperatingSystem().open(shaderPath);
                return;
            }
            failure = "Shader folder is unavailable.";
            failedAction = FailedAction.OPEN_FOLDER;
            clearAndInit();
        } catch (ReflectiveOperationException | RuntimeException error) {
            fail(error, FailedAction.OPEN_FOLDER);
        }
    }

    private void retry() {
        FailedAction action = failedAction;
        failure = null;
        failedAction = FailedAction.NONE;
        switch (action) {
            case TOGGLE -> toggleShaders();
            case OPEN_MANAGER -> openIrisManager();
            case OPEN_FOLDER -> openShaderFolder();
            case NONE -> clearAndInit();
        }
    }

    private void fail(Throwable error, FailedAction action) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        String message = cause.getMessage();
        failure = message == null || message.isBlank()
                ? cause.getClass().getSimpleName()
                : message;
        failedAction = action;
        clearAndInit();
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return true;
    }
}
