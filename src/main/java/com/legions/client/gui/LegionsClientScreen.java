package com.legions.client.gui;

import com.legions.client.LegionsClient;
import com.legions.client.config.LegionsConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class LegionsClientScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int CONTENT_TOP = 52;
    private static final int PANEL_TOP = 24;
    private static final int PANEL_BOTTOM_MARGIN = 18;
    private static final int ROW_SPACING = 24;
    private static final int SCROLL_STEP = ROW_SPACING;
    private static final int PANEL_SIDE_PADDING = 8;
    private static final int SCROLLBAR_GUTTER = 14;
    private static final int SECTION_HEIGHT = 16;
    private static final int SECTION_GAP = 4;
    private static final int RESET_BUTTON_WIDTH = 28;
    private static final int RESET_GAP = 4;
    private static final int TEXT_FIELD_LABEL_WIDTH = 94;
    private final Screen parent;
    private final List<SectionHeader> sectionHeaders = new ArrayList<>();
    private final List<TextFieldLabel> textFieldLabels = new ArrayList<>();
    private final LegionsConfig defaultConfig = new LegionsConfig().normalize();
    private LegionsConfig savedConfig;
    private ButtonWidget saveButton;
    private int panelBottom = 330;
    private int scrollOffset;
    private int maxScroll;
    private int contentHeight;

    public LegionsClientScreen(Screen parent) {
        super(Text.literal("Legions Utils"));
        this.parent = parent;
        this.savedConfig = LegionsClient.CONFIG == null ? new LegionsConfig().normalize() : LegionsClient.CONFIG.copy();
    }

    @Override
    protected void init() {
        sectionHeaders.clear();
        textFieldLabels.clear();
        saveButton = null;
        int panelWidth = panelWidth();
        int x = (this.width - panelWidth) / 2;
        int controlX = x + PANEL_SIDE_PADDING;
        int controlWidth = controlWidth(panelWidth);
        int y = CONTENT_TOP;

        y = addSectionHeader(controlX, y, controlWidth, "General");
        addToggle(controlX, screenY(y), controlWidth, "Enabled", () -> LegionsClient.CONFIG.enabled, LegionsClient::setEnabled, defaultConfig.enabled);
        y += ROW_SPACING;
        addSlider(controlX, screenY(y), controlWidth, "UI Scale %", 50, 200, () -> LegionsClient.CONFIG.uiScale, value -> LegionsClient.CONFIG.uiScale = value, defaultConfig.uiScale);
        y += ROW_SPACING;
        addButton(controlX, screenY(y), controlWidth, Text.literal("Server IPs"), button -> MinecraftClient.getInstance().setScreen(new LegionsServerListScreen(this)));
        y += ROW_SPACING;

        y = addSectionHeader(controlX, y, controlWidth, "Player Info");
        addToggle(controlX, screenY(y), controlWidth, "Rating Nametags", () -> LegionsClient.CONFIG.ratingNametagsEnabled, value -> LegionsClient.CONFIG.ratingNametagsEnabled = value, defaultConfig.ratingNametagsEnabled);
        y += ROW_SPACING;
        if (LegionsClient.CONFIG.ratingNametagsEnabled) {
            addToggle(controlX, screenY(y), controlWidth, "Nametags Any Server", () -> LegionsClient.CONFIG.ratingNametagsIgnoreServerList, value -> LegionsClient.CONFIG.ratingNametagsIgnoreServerList = value, defaultConfig.ratingNametagsIgnoreServerList);
            y += ROW_SPACING;
        }
        addToggle(controlX, screenY(y), controlWidth, "Enemy Highlights", () -> LegionsClient.CONFIG.enemyHighlightsEnabled, value -> LegionsClient.CONFIG.enemyHighlightsEnabled = value, defaultConfig.enemyHighlightsEnabled);
        y += ROW_SPACING;
        addToggle(controlX, screenY(y), controlWidth, "Dynamic Highlight Opacity", () -> LegionsClient.CONFIG.dynamicHighlightOpacityEnabled, value -> LegionsClient.CONFIG.dynamicHighlightOpacityEnabled = value, defaultConfig.dynamicHighlightOpacityEnabled);
        y += ROW_SPACING;
        addToggle(controlX, screenY(y), controlWidth, "Spectator Glow", () -> LegionsClient.CONFIG.spectatorGlowEnabled, value -> LegionsClient.CONFIG.spectatorGlowEnabled = value, defaultConfig.spectatorGlowEnabled);
        y += ROW_SPACING;

        y = addSectionHeader(controlX, y, controlWidth, "World Border");
        addToggle(controlX, screenY(y), controlWidth, "Custom World Border", () -> LegionsClient.CONFIG.customWorldBorderEnabled, LegionsClient::setCustomWorldBorderEnabled, defaultConfig.customWorldBorderEnabled);
        y += ROW_SPACING;
        if (LegionsClient.CONFIG.customWorldBorderEnabled) {
            addTextField(controlX, screenY(y), controlWidth, "Border Color", LegionsClient.CONFIG.customWorldBorderColor, "#ff5555", value -> LegionsClient.CONFIG.customWorldBorderColor = value, defaultConfig.customWorldBorderColor);
            y += ROW_SPACING;
            addSlider(controlX, screenY(y), controlWidth, "Border Opacity %", 5, 100, () -> LegionsClient.CONFIG.customWorldBorderOpacity, value -> LegionsClient.CONFIG.customWorldBorderOpacity = value, defaultConfig.customWorldBorderOpacity);
            y += ROW_SPACING;
            addToggle(controlX, screenY(y), controlWidth, "Hide Glitter Particles", () -> LegionsClient.CONFIG.customWorldBorderHideGlitterParticles, value -> LegionsClient.CONFIG.customWorldBorderHideGlitterParticles = value, defaultConfig.customWorldBorderHideGlitterParticles);
            y += ROW_SPACING;
        }

        y = addSectionHeader(controlX, y, controlWidth, "Team Ping");
        addToggle(controlX, screenY(y), controlWidth, "Team Ping", () -> LegionsClient.CONFIG.teamPingEnabled, value -> LegionsClient.CONFIG.teamPingEnabled = value, defaultConfig.teamPingEnabled);
        y += ROW_SPACING;
        if (LegionsClient.CONFIG.teamPingEnabled) {
            addButton(controlX, screenY(y), controlWidth, Text.literal("Customize Team Pings"), button -> MinecraftClient.getInstance().setScreen(new LegionsPingConfigScreen(this)));
            y += ROW_SPACING;
            addToggle(controlX, screenY(y), controlWidth, "Block Ping Distance", () -> LegionsClient.CONFIG.blockPingDistanceLabelEnabled, value -> LegionsClient.CONFIG.blockPingDistanceLabelEnabled = value, defaultConfig.blockPingDistanceLabelEnabled);
            y += ROW_SPACING;
            addSlider(controlX, screenY(y), controlWidth, "Ping Seconds", 1, 25, () -> LegionsClient.CONFIG.pingDurationSeconds, value -> LegionsClient.CONFIG.pingDurationSeconds = value, defaultConfig.pingDurationSeconds);
            y += ROW_SPACING;
            addSlider(controlX, screenY(y), controlWidth, "Recent Target Seconds", 1, 60, () -> LegionsClient.CONFIG.pingRecentTargetTimeoutSeconds, value -> LegionsClient.CONFIG.pingRecentTargetTimeoutSeconds = value, defaultConfig.pingRecentTargetTimeoutSeconds);
            y += ROW_SPACING;
        }

        y = addSectionHeader(controlX, y, controlWidth, "Ping Arrows");
        addToggle(controlX, screenY(y), controlWidth, "Off-Screen Arrows", () -> LegionsClient.CONFIG.offscreenPingArrowsEnabled, value -> LegionsClient.CONFIG.offscreenPingArrowsEnabled = value, defaultConfig.offscreenPingArrowsEnabled);
        y += ROW_SPACING;
        if (LegionsClient.CONFIG.offscreenPingArrowsEnabled) {
            addSlider(controlX, screenY(y), controlWidth, "Arrow Scale %", 50, 200, () -> LegionsClient.CONFIG.offscreenPingArrowScale, value -> LegionsClient.CONFIG.offscreenPingArrowScale = value, defaultConfig.offscreenPingArrowScale);
            y += ROW_SPACING;
            addToggle(controlX, screenY(y), controlWidth, "Arrow Distance Fade", () -> LegionsClient.CONFIG.offscreenPingArrowDistanceFadeEnabled, value -> LegionsClient.CONFIG.offscreenPingArrowDistanceFadeEnabled = value, defaultConfig.offscreenPingArrowDistanceFadeEnabled);
            y += ROW_SPACING;
            addSlider(controlX, screenY(y), controlWidth, "Arrow Min Opacity %", 10, 100, () -> LegionsClient.CONFIG.offscreenPingArrowMinOpacity, value -> LegionsClient.CONFIG.offscreenPingArrowMinOpacity = value, defaultConfig.offscreenPingArrowMinOpacity);
            y += ROW_SPACING;
            addSlider(controlX, screenY(y), controlWidth, "Arrow Max Opacity %", 10, 100, () -> LegionsClient.CONFIG.offscreenPingArrowMaxOpacity, value -> LegionsClient.CONFIG.offscreenPingArrowMaxOpacity = value, defaultConfig.offscreenPingArrowMaxOpacity);
            y += ROW_SPACING;
        }

        y = addSectionHeader(controlX, y, controlWidth, "Team Fight Detector");
        addToggle(controlX, screenY(y), controlWidth, "Fight Detector", () -> LegionsClient.CONFIG.teamFightDetectorEnabled, value -> LegionsClient.CONFIG.teamFightDetectorEnabled = value, defaultConfig.teamFightDetectorEnabled);
        y += ROW_SPACING;
        if (LegionsClient.CONFIG.teamFightDetectorEnabled) {
            addToggle(controlX, screenY(y), controlWidth, "Spectator Only", () -> LegionsClient.CONFIG.teamFightDetectorSpectatorOnly, value -> LegionsClient.CONFIG.teamFightDetectorSpectatorOnly = value, defaultConfig.teamFightDetectorSpectatorOnly);
            y += ROW_SPACING;
            addTextField(controlX, screenY(y), controlWidth, "Fight Color", LegionsClient.CONFIG.teamFightMarkerColor, "#ff5555", value -> LegionsClient.CONFIG.teamFightMarkerColor = value, defaultConfig.teamFightMarkerColor);
            y += ROW_SPACING;
            addToggle(controlX, screenY(y), controlWidth, "Fight Smoothing", () -> LegionsClient.CONFIG.teamFightSmoothingEnabled, value -> LegionsClient.CONFIG.teamFightSmoothingEnabled = value, defaultConfig.teamFightSmoothingEnabled);
            y += ROW_SPACING;
            if (LegionsClient.CONFIG.teamFightSmoothingEnabled) {
                addSlider(controlX, screenY(y), controlWidth, "Fight Smoothing %", 5, 100, () -> LegionsClient.CONFIG.teamFightSmoothingStrength, value -> LegionsClient.CONFIG.teamFightSmoothingStrength = value, defaultConfig.teamFightSmoothingStrength);
                y += ROW_SPACING;
            }
            addSlider(controlX, screenY(y), controlWidth, "Fight Fade-Out Seconds", 1, 10, () -> LegionsClient.CONFIG.teamFightFadeOutSeconds, value -> LegionsClient.CONFIG.teamFightFadeOutSeconds = value, defaultConfig.teamFightFadeOutSeconds);
            y += ROW_SPACING;
        }

        y = addSectionHeader(controlX, y, controlWidth, "Overlays");
        addToggle(controlX, screenY(y), controlWidth, "Team Count Overlay", () -> LegionsClient.CONFIG.teamCountOverlayEnabled, value -> LegionsClient.CONFIG.teamCountOverlayEnabled = value, defaultConfig.teamCountOverlayEnabled);
        y += ROW_SPACING;
        if (LegionsClient.CONFIG.teamCountOverlayEnabled) {
            addToggle(controlX, screenY(y), controlWidth, "Team Rating Totals", () -> LegionsClient.CONFIG.teamRatingTotalsEnabled, value -> LegionsClient.CONFIG.teamRatingTotalsEnabled = value, defaultConfig.teamRatingTotalsEnabled);
            y += ROW_SPACING;
            addButton(controlX, screenY(y), controlWidth, Text.literal("Move Team Count Overlay"), button -> MinecraftClient.getInstance().setScreen(new LegionsTeamCountOverlayLayoutScreen(this)));
            y += ROW_SPACING;
        }
        addToggle(controlX, screenY(y), controlWidth, "Team HUD", () -> LegionsClient.CONFIG.teamHudEnabled, value -> LegionsClient.CONFIG.teamHudEnabled = value, defaultConfig.teamHudEnabled);
        y += ROW_SPACING;
        if (LegionsClient.CONFIG.teamHudEnabled) {
            addButton(controlX, screenY(y), controlWidth, Text.literal("Move Team HUD"), button -> MinecraftClient.getInstance().setScreen(new LegionsTeamHudLayoutScreen(this)));
            y += ROW_SPACING;
        }

        y = addSectionHeader(controlX, y, controlWidth, "Player Visibility");
        addToggle(controlX, screenY(y), controlWidth, "Adaptive Performance", () -> LegionsClient.CONFIG.adaptivePerformanceEnabled, value -> LegionsClient.CONFIG.adaptivePerformanceEnabled = value, defaultConfig.adaptivePerformanceEnabled);
        y += ROW_SPACING;
        addToggle(controlX, screenY(y), controlWidth, "Limit Opponents Shown", () -> LegionsClient.CONFIG.opponentLimitEnabled, value -> LegionsClient.CONFIG.opponentLimitEnabled = value, defaultConfig.opponentLimitEnabled);
        y += ROW_SPACING;
        if (LegionsClient.CONFIG.opponentLimitEnabled) {
            addSlider(controlX, screenY(y), controlWidth, "Opponents Shown", 1, 20, () -> LegionsClient.CONFIG.opponentLimit, value -> LegionsClient.CONFIG.opponentLimit = value, defaultConfig.opponentLimit);
            y += ROW_SPACING;
        }
        addToggle(controlX, screenY(y), controlWidth, "Player Render Optimization", () -> LegionsClient.CONFIG.playerRenderOptimizationEnabled, value -> LegionsClient.CONFIG.playerRenderOptimizationEnabled = value, defaultConfig.playerRenderOptimizationEnabled);
        y += ROW_SPACING;
        if (LegionsClient.CONFIG.playerRenderOptimizationEnabled) {
            addSlider(controlX, screenY(y), controlWidth, "Render Distance (Blocks)", 16, 160, () -> LegionsClient.CONFIG.playerRenderDistance, value -> LegionsClient.CONFIG.playerRenderDistance = value, defaultConfig.playerRenderDistance);
            y += ROW_SPACING;
        }
        y += 20;

        int buttonY = screenY(y);
        if (isRowVisible(buttonY)) {
            saveButton = ButtonWidget.builder(Text.literal("Save"), button -> {
                LegionsClient.saveConfig();
                savedConfig = LegionsClient.CONFIG.copy();
                button.setMessage(Text.literal("Saved"));
                button.active = false;
            }).dimensions(controlX, buttonY, controlWidth / 2 - 4, BUTTON_HEIGHT).build();
            saveButton.active = hasUnsavedChanges();
            addDrawableChild(saveButton);
            addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close()).dimensions(controlX + controlWidth / 2 + 4, buttonY, controlWidth / 2 - 4, BUTTON_HEIGHT).build());
        }
        y += BUTTON_HEIGHT + 12;
        contentHeight = y - CONTENT_TOP;
        maxScroll = Math.max(0, contentHeight - visibleContentHeight());
        int clampedScroll = clamp(scrollOffset, 0, maxScroll);
        if (scrollOffset != clampedScroll) {
            scrollOffset = clampedScroll;
            clearAndInit();
            return;
        }
        panelBottom = visibleBottom();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x99000000);
        int panelWidth = panelWidth();
        int x = (this.width - panelWidth) / 2;
        int bottom = panelBottom;
        if (bottom > PANEL_TOP) {
            context.fill(x, PANEL_TOP, x + panelWidth, bottom, 0xD20F1318);
            context.fill(x, PANEL_TOP, x + panelWidth, PANEL_TOP + 1, 0xFF263241);
            context.fill(x, bottom - 1, x + panelWidth, bottom, 0xFF263241);
            renderScrollbar(context, x, panelWidth, bottom);
        }
        for (SectionHeader header : sectionHeaders) {
            renderSectionHeader(context, header);
        }
        for (TextFieldLabel label : textFieldLabels) {
            context.drawTextWithShadow(textRenderer, Text.literal(label.label), label.x, label.y, 0xFFE7F0FF);
        }
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 32, 0xFFE7F0FF);
        if (saveButton != null) {
            saveButton.active = hasUnsavedChanges();
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll > 0 && mouseY >= CONTENT_TOP && mouseY <= visibleBottom()) {
            int oldOffset = scrollOffset;
            int delta = (int) Math.round(verticalAmount * SCROLL_STEP);
            if (delta == 0 && verticalAmount != 0.0) {
                delta = verticalAmount > 0.0 ? SCROLL_STEP : -SCROLL_STEP;
            }
            scrollOffset = clamp(scrollOffset - delta, 0, maxScroll);
            if (scrollOffset != oldOffset) {
                clearAndInit();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        LegionsClient.saveConfig();
        MinecraftClient.getInstance().setScreen(parent);
    }

    private void addToggle(int x, int y, int width, String label, BooleanSupplier getter, Consumer<Boolean> setter, boolean defaultValue) {
        if (!isRowVisible(y)) {
            return;
        }
        int settingWidth = settingControlWidth(width);
        addDrawableChild(ButtonWidget.builder(toggleText(label, getter.getAsBoolean()), button -> {
            boolean value = !getter.getAsBoolean();
            setter.accept(value);
            button.setMessage(toggleText(label, value));
            clearAndInit();
        }).dimensions(x, y, settingWidth, BUTTON_HEIGHT).build());
        addResetButton(x, y, width, getter.getAsBoolean() != defaultValue, () -> {
            setter.accept(defaultValue);
            clearAndInit();
        });
    }

    private void addButton(int x, int y, int width, Text label, ButtonWidget.PressAction action) {
        if (isRowVisible(y)) {
            addDrawableChild(ButtonWidget.builder(label, action).dimensions(x, y, width, BUTTON_HEIGHT).build());
        }
    }

    private void addSlider(int x, int y, int width, String label, int min, int max, IntSupplier getter, IntConsumer setter, int defaultValue) {
        if (isRowVisible(y)) {
            addDrawableChild(new IntSlider(x, y, settingControlWidth(width), label, min, max, getter.getAsInt(), setter));
            addResetButton(x, y, width, getter.getAsInt() != defaultValue, () -> {
                setter.accept(defaultValue);
                LegionsClient.CONFIG.normalize();
                clearAndInit();
            });
        }
    }

    private void addTextField(int x, int y, int width, String label, String value, String placeholder, Consumer<String> setter, String defaultValue) {
        if (!isRowVisible(y)) {
            return;
        }
        int settingWidth = settingControlWidth(width);
        int fieldX = x + TEXT_FIELD_LABEL_WIDTH;
        int fieldWidth = Math.max(80, settingWidth - TEXT_FIELD_LABEL_WIDTH);
        textFieldLabels.add(new TextFieldLabel(x + 4, y + 6, label));
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, fieldX, y, fieldWidth, BUTTON_HEIGHT, Text.literal(label));
        field.setText(value);
        field.setPlaceholder(Text.literal(placeholder));
        field.setChangedListener(setter);
        addDrawableChild(field);
        addResetButton(x, y, width, !value.equals(defaultValue), () -> {
            setter.accept(defaultValue);
            clearAndInit();
        });
    }

    private void addResetButton(int x, int y, int width, boolean visible, Runnable action) {
        if (!visible) {
            return;
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("R"), button -> action.run())
                .dimensions(x + settingControlWidth(width) + RESET_GAP, y, RESET_BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private int addSectionHeader(int x, int contentY, int width, String title) {
        int y = screenY(contentY);
        if (y + SECTION_HEIGHT >= CONTENT_TOP && y <= visibleBottom()) {
            sectionHeaders.add(new SectionHeader(x, y, width, title));
        }
        return contentY + SECTION_HEIGHT + SECTION_GAP;
    }

    private void renderSectionHeader(DrawContext context, SectionHeader header) {
        int lineY = header.y + SECTION_HEIGHT / 2;
        int labelX = header.x + 28;
        int labelY = header.y + 4;
        int textWidth = textRenderer.getWidth(header.title);
        context.fill(header.x, lineY, header.x + 20, lineY + 1, 0x9955E6FF);
        context.drawTextWithShadow(textRenderer, Text.literal(header.title), labelX, labelY, 0xFF55E6FF);
        context.fill(labelX + textWidth + 8, lineY, header.x + header.width, lineY + 1, 0x66354552);
    }

    private void renderScrollbar(DrawContext context, int x, int panelWidth, int bottom) {
        if (maxScroll <= 0 || contentHeight <= 0) {
            return;
        }
        int trackTop = CONTENT_TOP;
        int trackBottom = bottom - 6;
        if (trackBottom <= trackTop) {
            return;
        }
        int trackHeight = trackBottom - trackTop;
        int thumbHeight = clamp(trackHeight * visibleContentHeight() / contentHeight, 18, trackHeight);
        int thumbY = trackTop + (int) Math.round((trackHeight - thumbHeight) * (scrollOffset / (double) maxScroll));
        int trackX = x + panelWidth - SCROLLBAR_GUTTER / 2 - 1;
        context.fill(trackX, trackTop, trackX + 2, trackBottom, 0x55354552);
        context.fill(trackX - 2, thumbY, trackX + 4, thumbY + thumbHeight, 0xAA55E6FF);
    }

    private int screenY(int contentY) {
        return contentY - scrollOffset;
    }

    private boolean isRowVisible(int y) {
        return y + BUTTON_HEIGHT >= CONTENT_TOP && y <= visibleBottom();
    }

    private int visibleBottom() {
        return Math.max(CONTENT_TOP, this.height - PANEL_BOTTOM_MARGIN);
    }

    private int visibleContentHeight() {
        return Math.max(BUTTON_HEIGHT, visibleBottom() - CONTENT_TOP);
    }

    private int panelWidth() {
        return Math.max(300, Math.min(460, this.width - 24));
    }

    private int controlWidth(int panelWidth) {
        return Math.max(220, panelWidth - PANEL_SIDE_PADDING * 2 - SCROLLBAR_GUTTER);
    }

    private int settingControlWidth(int width) {
        return Math.max(120, width - RESET_BUTTON_WIDTH - RESET_GAP);
    }

    private boolean hasUnsavedChanges() {
        return LegionsClient.CONFIG != null && !LegionsClient.CONFIG.copy().sameSettings(savedConfig);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Text toggleText(String label, boolean enabled) {
        return Text.literal(label + "        " + (enabled ? "ON" : "OFF"));
    }

    private record SectionHeader(int x, int y, int width, String title) {
    }

    private record TextFieldLabel(int x, int y, String label) {
    }

    private static class IntSlider extends SliderWidget {
        private final String label;
        private final int min;
        private final int max;
        private final IntConsumer setter;

        private IntSlider(int x, int y, int width, String label, int min, int max, int initial, IntConsumer setter) {
            super(x, y, width, BUTTON_HEIGHT, Text.empty(), 0.0);
            this.label = label;
            this.min = min;
            this.max = max;
            this.setter = setter;
            setActualValue(initial);
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + "        " + getActualValue()));
        }

        @Override
        protected void applyValue() {
            setter.accept(getActualValue());
        }

        private int getActualValue() {
            return Math.max(min, Math.min(max, (int) Math.round(min + value * (max - min))));
        }

        private void setActualValue(int actualValue) {
            value = max <= min ? 0.0 : (double) (Math.max(min, Math.min(max, actualValue)) - min) / (double) (max - min);
            applyValue();
            updateMessage();
        }
    }
}
