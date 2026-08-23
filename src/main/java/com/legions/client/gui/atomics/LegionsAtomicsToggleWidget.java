package com.legions.client.gui.atomics;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

public final class LegionsAtomicsToggleWidget extends ClickableWidget {
    private final TextRenderer textRenderer;
    private final String label;
    private final boolean enabled;
    private final Runnable action;

    public LegionsAtomicsToggleWidget(TextRenderer textRenderer, int x, int y, int width, int height, String label, boolean enabled, Runnable action) {
        super(x, y, width, height, Text.literal(label));
        this.textRenderer = textRenderer;
        this.label = label;
        this.enabled = enabled;
        this.action = action;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        context.fill(x, y, x + width, y + height, isHovered() ? LegionsAtomicsUi.ROW_HOVER : LegionsAtomicsUi.ROW);
        context.fill(x, y, x + width, y + 1, LegionsAtomicsUi.PANEL_BORDER);
        context.fill(x, y + height - 1, x + width, y + height, LegionsAtomicsUi.PANEL_BORDER);
        context.fill(x, y, x + 1, y + height, LegionsAtomicsUi.PANEL_BORDER);
        context.fill(x + width - 1, y, x + width, y + height, LegionsAtomicsUi.PANEL_BORDER);

        String value = enabled ? "ON" : "OFF";
        int valueColor = enabled ? LegionsAtomicsUi.ACCENT : LegionsAtomicsUi.TEXT_MUTED;
        context.drawTextWithShadow(textRenderer, Text.literal(label), x + 8, y + 7, LegionsAtomicsUi.TEXT_MAIN);
        context.drawTextWithShadow(textRenderer, Text.literal(value), x + width - textRenderer.getWidth(value) - 8, y + 7, valueColor);
    }

    @Override
    public void onClick(Click click, boolean doubleClick) {
        action.run();
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
