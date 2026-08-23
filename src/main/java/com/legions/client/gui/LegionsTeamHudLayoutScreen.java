package com.legions.client.gui;

import com.legions.client.LegionsClient;
import com.legions.client.LegionsHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class LegionsTeamHudLayoutScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int SCALE_SLIDER_WIDTH = 220;
    private final Screen parent;
    private boolean dragging;

    public LegionsTeamHudLayoutScreen(Screen parent) {
        super(Text.literal("Move Team HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = 86;
        int gap = 8;
        int sliderX = this.width / 2 - SCALE_SLIDER_WIDTH / 2;
        int sliderY = this.height - 56;
        int y = this.height - 28;
        int x = this.width / 2 - buttonWidth - gap / 2;
        addDrawableChild(new LegionsUiScaleSlider(sliderX, sliderY, SCALE_SLIDER_WIDTH, BUTTON_HEIGHT, this::clampTeamText));
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), button -> {
            LegionsClient.CONFIG.teamHudX = 8;
            LegionsClient.CONFIG.teamHudY = 8;
            LegionsClient.saveConfig();
        }).dimensions(x, y, buttonWidth, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions(x + buttonWidth + gap, y, buttonWidth, BUTTON_HEIGHT).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC080B0F);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 18, 0xFFE7F0FF);
        drawTeamText(context);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) {
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && isOverTeamText(click.x(), click.y())) {
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (!dragging) {
            return super.mouseDragged(click, offsetX, offsetY);
        }
        LegionsClient.CONFIG.teamHudX += (int) Math.round(offsetX);
        LegionsClient.CONFIG.teamHudY += (int) Math.round(offsetY);
        clampTeamText();
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging) {
            dragging = false;
            LegionsClient.saveConfig();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void close() {
        clampTeamText();
        LegionsClient.saveConfig();
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void drawTeamText(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        clampTeamText();
        LegionsHud.renderTeamHudPreview(context, client, LegionsClient.CONFIG.teamHudX, LegionsClient.CONFIG.teamHudY);
    }

    private boolean isOverTeamText(double mouseX, double mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        int x = LegionsClient.CONFIG.teamHudX;
        int y = LegionsClient.CONFIG.teamHudY;
        return mouseX >= x && mouseX <= x + LegionsHud.teamHudPreviewWidth(client)
                && mouseY >= y && mouseY <= y + LegionsHud.teamHudPreviewHeight(client);
    }

    private void clampTeamText() {
        MinecraftClient client = MinecraftClient.getInstance();
        LegionsClient.CONFIG.teamHudX = clamp(LegionsClient.CONFIG.teamHudX, 0, Math.max(0, this.width - LegionsHud.teamHudPreviewWidth(client)));
        LegionsClient.CONFIG.teamHudY = clamp(LegionsClient.CONFIG.teamHudY, 0, Math.max(0, this.height - LegionsHud.teamHudPreviewHeight(client)));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
