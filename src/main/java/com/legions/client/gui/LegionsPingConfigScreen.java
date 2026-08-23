package com.legions.client.gui;

import com.legions.client.LegionsClient;
import com.legions.client.config.LegionsConfig;
import com.legions.client.config.LegionsConfig.PingRow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class LegionsPingConfigScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int CONTENT_TOP = 66;
    private static final int PANEL_TOP = 0;
    private static final int PANEL_SIDE_PADDING = 18;
    private static final int PANEL_BOTTOM_MARGIN = 0;
    private static final int SCROLLBAR_GUTTER = 14;
    private static final int ROW_HEIGHT = 194;
    private static final int ROW_GAP = 18;
    private static final int SCROLL_STEP = 28;
    private static final int FIELD_LABEL_WIDTH = 82;
    private static final String[] SOURCE_LABELS = {"Crosshair", "Last Attacker", "Last Attacked", "Self"};
    private static final String[] TYPE_LABELS = {"Teammates", "Enemies", "All Same", "All Different", "Blocks"};
    private static final String[] AUDIENCE_LABELS = {"Teammates", "Opponents", "Everyone"};
    private static final String[] ICON_LABELS = {
            "Default", "Axe", "Pickaxe", "Sword", "Bow", "Star", "Fire", "Lightning",
            "Galaxy", "Diamond", "Dot", "Heart", "Hourglass", "Home", "Comet"
    };
    private static final String[] ICON_SYMBOLS = {
            "\u2666", "\uD83E\uDE93", "\u26CF", "\uD83D\uDDE1", "\uD83C\uDFF9",
            "\u2B50", "\uD83D\uDD25", "\u26A1", "\uD83C\uDF0C", "\u25C6",
            "\u23FA", "\u2764", "\u23F3", "\u2302", "\u2604"
    };

    private final Screen parent;
    private final List<Label> labels = new ArrayList<>();
    private int scrollOffset;
    private int maxScroll;
    private int contentHeight;
    private int panelBottom = 330;
    private int capturingRow = -1;

    public LegionsPingConfigScreen(Screen parent) {
        super(Text.literal("Team Ping Rows"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        labels.clear();
        if (LegionsClient.CONFIG == null) {
            LegionsClient.CONFIG = new LegionsConfig().normalize();
        } else {
            LegionsClient.CONFIG.normalize();
        }

        int panelWidth = panelWidth();
        int x = (this.width - panelWidth) / 2;
        int controlX = x + PANEL_SIDE_PADDING;
        int controlWidth = controlWidth(panelWidth);
        addTopButtons(controlX, controlWidth);

        int y = CONTENT_TOP;
        List<PingRow> rows = LegionsClient.CONFIG.pingRows;
        for (int i = 0; i < rows.size(); i++) {
            addPingRow(i, rows.get(i), controlX, screenY(y), controlWidth);
            y += ROW_HEIGHT + ROW_GAP;
        }

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
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFE7F0FF);
        for (Label label : labels) {
            context.drawTextWithShadow(textRenderer, Text.literal(label.text()), label.x(), label.y(), label.color());
        }
        if (capturingRow >= 0) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Press a key or click a mouse button"),
                    this.width / 2, visibleBottom() - 12, 0xFF55E6FF);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (capturingRow >= 0) {
            if (keyInput.key() != GLFW.GLFW_KEY_ESCAPE && isValidRow(capturingRow)) {
                PingRow row = LegionsClient.CONFIG.pingRows.get(capturingRow);
                row.keyType = PingRow.KEY_TYPE_KEYBOARD;
                row.keyCode = keyInput.key();
                LegionsClient.CONFIG.normalize();
            }
            capturingRow = -1;
            clearAndInit();
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (capturingRow >= 0) {
            if (isValidRow(capturingRow)) {
                PingRow row = LegionsClient.CONFIG.pingRows.get(capturingRow);
                row.keyType = PingRow.KEY_TYPE_MOUSE;
                row.keyCode = click.button();
                LegionsClient.CONFIG.normalize();
            }
            capturingRow = -1;
            clearAndInit();
            return true;
        }
        return super.mouseClicked(click, doubleClick);
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

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void addTopButtons(int x, int width) {
        int gap = 8;
        int buttonWidth = (width - gap) / 2;
        int y = 34;
        addDrawableChild(ButtonWidget.builder(Text.literal("Add Row"), button -> {
            LegionsClient.CONFIG.pingRows.add(new PingRow().normalize());
            LegionsClient.CONFIG.normalize();
            clearAndInit();
        }).dimensions(x, y, buttonWidth, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions(x + buttonWidth + gap, y, buttonWidth, BUTTON_HEIGHT).build());
    }

    private void addPingRow(int index, PingRow row, int x, int y, int width) {
        if (y + ROW_HEIGHT < CONTENT_TOP || y > visibleBottom()) {
            return;
        }

        addLabel(x + 4, y + 4, "Ping " + (index + 1), 0xFF55E6FF);
        int smallWidth = 86;
        addButtonIfVisible(x + width - smallWidth * 2 - 8, y, smallWidth, Text.literal("Copy"), button -> {
            LegionsClient.CONFIG.pingRows.add(index + 1, row.copy());
            clearAndInit();
        });
        ButtonWidget delete = buttonIfVisible(x + width - smallWidth, y, smallWidth, Text.literal("Delete"), button -> {
            if (LegionsClient.CONFIG.pingRows.size() > 1) {
                LegionsClient.CONFIG.pingRows.remove(index);
                capturingRow = -1;
                clearAndInit();
            }
        });
        if (delete != null) {
            delete.active = LegionsClient.CONFIG.pingRows.size() > 1;
        }

        int lineY = y + 28;
        int gap = 12;
        int half = (width - gap) / 2;
        addButtonIfVisible(x, lineY, half, Text.literal(capturingRow == index ? "Key: listening..." : "Key: " + keyLabel(row)), button -> {
            capturingRow = index;
            button.setMessage(Text.literal("Key: listening..."));
        });
        addButtonIfVisible(x + half + gap, lineY, half, Text.literal("Presses: " + row.presses), button -> {
            row.presses = row.presses >= 5 ? 1 : row.presses + 1;
            LegionsClient.CONFIG.normalize();
            clearAndInit();
        });

        lineY += 28;
        addButtonIfVisible(x, lineY, half, Text.literal("Source: " + SOURCE_LABELS[row.targetSource]), button -> {
            row.targetSource = Math.floorMod(row.targetSource + 1, SOURCE_LABELS.length);
            LegionsClient.CONFIG.normalize();
            clearAndInit();
        });
        addButtonIfVisible(x + half + gap, lineY, half, Text.literal("Type: " + TYPE_LABELS[row.targetType]), button -> {
            row.targetType = Math.floorMod(row.targetType + 1, TYPE_LABELS.length);
            LegionsClient.CONFIG.normalize();
            clearAndInit();
        });

        lineY += 28;
        addButtonIfVisible(x, lineY, half, Text.literal("Audience: " + AUDIENCE_LABELS[row.visualAudience]), button -> {
            row.visualAudience = Math.floorMod(row.visualAudience + 1, AUDIENCE_LABELS.length);
            LegionsClient.CONFIG.normalize();
            clearAndInit();
        });
        addTextField(x + half + gap, lineY, half, "Color", row.color, "#ffa500", value -> row.color = value);

        lineY += 28;
        if (row.targetType == PingRow.TARGET_TYPE_ALL_PLAYERS_DIFFERENT_MESSAGE) {
            addIconButton(x, lineY, half, "Team Icon", row.teammateMessageIcon, value -> row.teammateMessageIcon = value);
            addIconButton(x + half + gap, lineY, half, "Enemy Icon", row.enemyMessageIcon, value -> row.enemyMessageIcon = value);
            lineY += 32;
            addTextField(x, lineY, width, "Team Msg", row.teammateMessage, "{PLAYER} NEEDS HELP!", value -> row.teammateMessage = value);
            lineY += 28;
            addTextField(x, lineY, width, "Enemy Msg", row.enemyMessage, "Focus {PLAYER}", value -> row.enemyMessage = value);
        } else {
            addIconButton(x, lineY, half, "Msg Icon", row.messageIcon, value -> row.messageIcon = value);
            lineY += 32;
            String placeholder = row.targetType == PingRow.TARGET_TYPE_BLOCKS_ONLY ? "Go to {x} {y} {z}" : "Focus {PLAYER}";
            addTextField(x, lineY, width, "Message", row.message, placeholder, value -> row.message = value);
        }
    }

    private void addIconButton(int x, int y, int width, String label, int icon, Consumer<Integer> setter) {
        int normalizedIcon = normalizeIcon(icon);
        addButtonIfVisible(x, y, width, Text.literal(label + ": " + ICON_SYMBOLS[normalizedIcon] + " " + ICON_LABELS[normalizedIcon]), button -> {
            setter.accept(Math.floorMod(normalizedIcon + 1, ICON_LABELS.length));
            LegionsClient.CONFIG.normalize();
            clearAndInit();
        });
    }

    private void addTextField(int x, int y, int width, String label, String value, String placeholder, Consumer<String> setter) {
        if (!isControlVisible(y)) {
            return;
        }
        labels.add(new Label(x + 4, y + 6, label, 0xFFE7F0FF));
        int fieldX = x + FIELD_LABEL_WIDTH;
        int fieldWidth = Math.max(60, width - FIELD_LABEL_WIDTH);
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, fieldX, y, fieldWidth, BUTTON_HEIGHT, Text.literal(label));
        field.setText(value == null ? "" : value);
        field.setPlaceholder(Text.literal(placeholder));
        field.setChangedListener(setter);
        addDrawableChild(field);
    }

    private void addLabel(int x, int y, String text, int color) {
        if (y >= CONTENT_TOP && y + textRenderer.fontHeight <= visibleBottom()) {
            labels.add(new Label(x, y, text, color));
        }
    }

    private void addButtonIfVisible(int x, int y, int width, Text label, ButtonWidget.PressAction action) {
        buttonIfVisible(x, y, width, label, action);
    }

    private ButtonWidget buttonIfVisible(int x, int y, int width, Text label, ButtonWidget.PressAction action) {
        if (!isControlVisible(y)) {
            return null;
        }
        ButtonWidget button = ButtonWidget.builder(label, action).dimensions(x, y, width, BUTTON_HEIGHT).build();
        addDrawableChild(button);
        return button;
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

    private String keyLabel(PingRow row) {
        if (row.keyType == PingRow.KEY_TYPE_MOUSE) {
            return "Mouse " + (row.keyCode + 1);
        }
        String localized = InputUtil.fromKeyCode(new KeyInput(row.keyCode, 0, 0)).getLocalizedText().getString();
        return localized == null || localized.isBlank() ? "Key " + row.keyCode : localized;
    }

    private int screenY(int contentY) {
        return contentY - scrollOffset;
    }

    private boolean isValidRow(int index) {
        return LegionsClient.CONFIG != null && index >= 0 && index < LegionsClient.CONFIG.pingRows.size();
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

    private static int normalizeIcon(int icon) {
        return clamp(icon, PingRow.ICON_DEFAULT, PingRow.ICON_COMET);
    }

    private record Label(int x, int y, String text, int color) {
    }
}
