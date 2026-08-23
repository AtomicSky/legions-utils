package com.legions.client.gui.atomics;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

public final class LegionsAtomicsSubHeaderWidget extends ClickableWidget {
    private final TextRenderer textRenderer;
    private final String title;

    public LegionsAtomicsSubHeaderWidget(TextRenderer textRenderer, int x, int y, int width, int height, String title) {
        super(x, y, width, height, Text.literal(title));
        this.textRenderer = textRenderer;
        this.title = title;
        active = false;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int lineY = y + height / 2;
        int labelX = x + 28;
        int labelY = y + 7;
        int textWidth = textRenderer.getWidth(title);
        context.fill(x, lineY, x + 20, lineY + 1, 0x996FA8FF);
        context.drawTextWithShadow(textRenderer, Text.literal(title), labelX, labelY, LegionsAtomicsUi.ACCENT);
        context.fill(labelX + textWidth + 8, lineY, x + width, lineY + 1, LegionsAtomicsUi.PANEL_BORDER);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
    }
}
