package com.legions.client.gui.atomics;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public final class LegionsAtomicsSubHeaderWidget extends AbstractWidget {
    private final Font textRenderer;
    private final String title;

    public LegionsAtomicsSubHeaderWidget(Font textRenderer, int x, int y, int width, int height, String title) {
        super(x, y, width, height, Component.literal(title));
        this.textRenderer = textRenderer;
        this.title = title;
        active = false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int lineY = y + height / 2;
        int labelX = x + 28;
        int labelY = y + 7;
        int textWidth = textRenderer.width(title);
        context.fill(x, lineY, x + 20, lineY + 1, 0x996FA8FF);
        context.text(textRenderer, Component.literal(title), labelX, labelY, LegionsAtomicsUi.ACCENT);
        context.fill(labelX + textWidth + 8, lineY, x + width, lineY + 1, LegionsAtomicsUi.PANEL_BORDER);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
    }
}
