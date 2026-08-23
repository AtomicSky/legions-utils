package com.legions.client.gui;

import com.legions.client.LegionsClient;
import com.legions.client.LegionsHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class LegionsTeamCountOverlayLayoutScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int SCALE_SLIDER_WIDTH = 220;
    private final Screen parent;
    private int overlayX;
    private int overlayY;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    public LegionsTeamCountOverlayLayoutScreen(Screen parent) {
        super(Component.literal("Move Team Count Overlay"));
        this.parent = parent;
        this.overlayX = LegionsClient.CONFIG.teamCountOverlayX;
        this.overlayY = LegionsClient.CONFIG.teamCountOverlayY;
    }

    @Override
    protected void init() {
        if (overlayX < 0 || overlayY < 0) {
            resetOverlay();
        } else {
            clampOverlay();
        }

        int buttonWidth = 86;
        int gap = 8;
        int sliderX = this.width / 2 - SCALE_SLIDER_WIDTH / 2;
        int sliderY = this.height - 56;
        int y = this.height - 28;
        int x = this.width / 2 - buttonWidth - gap / 2;
        addRenderableWidget(new LegionsUiScaleSlider(sliderX, sliderY, SCALE_SLIDER_WIDTH, BUTTON_HEIGHT, this::clampAndApply));
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> resetOverlay())
                .bounds(x, y, buttonWidth, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(x + buttonWidth + gap, y, buttonWidth, BUTTON_HEIGHT).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC080B0F);
        context.centeredText(this.font, this.title, this.width / 2, 18, 0xFFE7F0FF);
        renderOverlayPreview(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) {
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && isInsideOverlay(click.x(), click.y())) {
            dragging = true;
            dragOffsetX = (int) Math.round(click.x()) - overlayX;
            dragOffsetY = (int) Math.round(click.y()) - overlayY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (!dragging) {
            return super.mouseDragged(click, offsetX, offsetY);
        }
        overlayX = (int) Math.round(click.x()) - dragOffsetX;
        overlayY = (int) Math.round(click.y()) - dragOffsetY;
        clampOverlay();
        apply();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (dragging) {
            dragging = false;
            apply();
            LegionsClient.saveConfig();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void onClose() {
        apply();
        LegionsClient.saveConfig();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderOverlayPreview(GuiGraphicsExtractor context) {
        clampOverlay();
        Minecraft client = Minecraft.getInstance();
        LegionsHud.renderTeamCountOverlayPreview(context, client, overlayX, overlayY);

        int left = overlayX - 4;
        int top = overlayY - 4;
        int right = overlayX + overlayWidth() + 4;
        int bottom = overlayY + overlayHeight() + 4;
        int border = dragging ? 0xFF55E6FF : 0xAA55E6FF;
        context.fill(left, top, right, top + 1, border);
        context.fill(left, bottom - 1, right, bottom, border);
        context.fill(left, top, left + 1, bottom, border);
        context.fill(right - 1, top, right, bottom, border);
    }

    private boolean isInsideOverlay(double mouseX, double mouseY) {
        return mouseX >= overlayX - 4 && mouseX <= overlayX + overlayWidth() + 4
                && mouseY >= overlayY - 4 && mouseY <= overlayY + overlayHeight() + 4;
    }

    private int overlayWidth() {
        return LegionsHud.teamCountOverlayPreviewWidth(Minecraft.getInstance());
    }

    private int overlayHeight() {
        return LegionsHud.teamCountOverlayPreviewHeight(Minecraft.getInstance());
    }

    private void resetOverlay() {
        Minecraft client = Minecraft.getInstance();
        overlayX = LegionsHud.defaultTeamCountOverlayX(client);
        overlayY = LegionsHud.defaultTeamCountOverlayY();
        clampOverlay();
        apply();
        LegionsClient.saveConfig();
    }

    private void clampOverlay() {
        overlayX = clamp(overlayX, 0, Math.max(0, this.width - overlayWidth()));
        overlayY = clamp(overlayY, 0, Math.max(0, this.height - overlayHeight()));
    }

    private void apply() {
        LegionsClient.CONFIG.teamCountOverlayX = overlayX;
        LegionsClient.CONFIG.teamCountOverlayY = overlayY;
    }

    private void clampAndApply() {
        clampOverlay();
        apply();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
