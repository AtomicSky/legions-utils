package com.legions.client.gui;

import com.legions.client.LegionsClient;
import com.legions.client.config.LegionsConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class LegionsServerListScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int CONTENT_TOP = 66;
    private static final int PANEL_TOP = 0;
    private static final int PANEL_SIDE_PADDING = 18;
    private static final int PANEL_BOTTOM_MARGIN = 0;
    private static final int SCROLLBAR_GUTTER = 14;
    private static final int ROW_HEIGHT = 32;
    private static final int SCROLL_STEP = 28;
    private static final int DELETE_WIDTH = 72;
    private static final int FIELD_GAP = 8;

    private final Screen parent;
    private final List<Label> labels = new ArrayList<>();
    private int scrollOffset;
    private int maxScroll;
    private int contentHeight;
    private int panelBottom = 330;

    public LegionsServerListScreen(Screen parent) {
        super(Component.literal("Server IPs"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        labels.clear();
        if (LegionsClient.CONFIG == null) {
            LegionsClient.CONFIG = new LegionsConfig().normalize();
        } else if (LegionsClient.CONFIG.allowedServerAddresses == null || LegionsClient.CONFIG.allowedServerAddresses.isEmpty()) {
            LegionsClient.CONFIG.allowedServerAddresses = new ArrayList<>(List.of("legions"));
        }

        int panelWidth = panelWidth();
        int x = (this.width - panelWidth) / 2;
        int controlX = x + PANEL_SIDE_PADDING;
        int controlWidth = controlWidth(panelWidth);
        addTopButtons(controlX, controlWidth);

        int y = CONTENT_TOP;
        List<String> addresses = LegionsClient.CONFIG.allowedServerAddresses;
        for (int i = 0; i < addresses.size(); i++) {
            addServerRow(i, controlX, screenY(y), controlWidth);
            y += ROW_HEIGHT;
        }

        contentHeight = y - CONTENT_TOP;
        maxScroll = Math.max(0, contentHeight - visibleContentHeight());
        int clampedScroll = clamp(scrollOffset, 0, maxScroll);
        if (scrollOffset != clampedScroll) {
            scrollOffset = clampedScroll;
            rebuildWidgets();
            return;
        }
        panelBottom = visibleBottom();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
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
        context.centeredText(this.font, this.title, this.width / 2, 12, 0xFFE7F0FF);
        for (Label label : labels) {
            context.text(font, Component.literal(label.text()), label.x(), label.y(), label.color());
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
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
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        LegionsClient.saveConfig();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void addTopButtons(int x, int width) {
        int gap = 8;
        int buttonWidth = (width - gap) / 2;
        int y = 34;
        addRenderableWidget(Button.builder(Component.literal("Add Server"), button -> {
            LegionsClient.CONFIG.allowedServerAddresses.add("");
            rebuildWidgets();
        }).bounds(x, y, buttonWidth, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(x + buttonWidth + gap, y, buttonWidth, BUTTON_HEIGHT).build());
    }

    private void addServerRow(int index, int x, int y, int width) {
        if (!isControlVisible(y)) {
            return;
        }

        labels.add(new Label(x + 4, y + 6, "Server " + (index + 1), 0xFF55E6FF));
        int labelWidth = 78;
        int fieldX = x + labelWidth;
        int fieldWidth = Math.max(120, width - labelWidth - DELETE_WIDTH - FIELD_GAP);
        EditBox field = new EditBox(this.font, fieldX, y, fieldWidth, BUTTON_HEIGHT, Component.literal("Server " + (index + 1)));
        field.setValue(LegionsClient.CONFIG.allowedServerAddresses.get(index));
        field.setHint(Component.literal("legions"));
        field.setResponder(value -> {
            if (index >= 0 && index < LegionsClient.CONFIG.allowedServerAddresses.size()) {
                LegionsClient.CONFIG.allowedServerAddresses.set(index, value);
            }
        });
        addRenderableWidget(field);

        Button delete = Button.builder(Component.literal("Delete"), button -> {
            if (LegionsClient.CONFIG.allowedServerAddresses.size() > 1) {
                LegionsClient.CONFIG.allowedServerAddresses.remove(index);
                rebuildWidgets();
            }
        }).bounds(fieldX + fieldWidth + FIELD_GAP, y, DELETE_WIDTH, BUTTON_HEIGHT).build();
        delete.active = LegionsClient.CONFIG.allowedServerAddresses.size() > 1;
        addRenderableWidget(delete);
    }

    private void renderScrollbar(GuiGraphicsExtractor context, int x, int panelWidth, int bottom) {
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

    private boolean isControlVisible(int y) {
        return y >= CONTENT_TOP && y + BUTTON_HEIGHT <= visibleBottom();
    }

    private int visibleBottom() {
        return Math.max(CONTENT_TOP, this.height - PANEL_BOTTOM_MARGIN);
    }

    private int visibleContentHeight() {
        return Math.max(BUTTON_HEIGHT, visibleBottom() - CONTENT_TOP);
    }

    private int panelWidth() {
        return Math.max(320, this.width);
    }

    private int controlWidth(int panelWidth) {
        return Math.max(260, panelWidth - PANEL_SIDE_PADDING * 2 - SCROLLBAR_GUTTER - 4);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Label(int x, int y, String text, int color) {
    }
}
