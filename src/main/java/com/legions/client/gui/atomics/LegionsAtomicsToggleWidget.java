package com.legions.client.gui.atomics;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class LegionsAtomicsToggleWidget extends AbstractWidget {
    private final Font textRenderer;
    private final String label;
    private final boolean enabled;
    private final Runnable action;

    public LegionsAtomicsToggleWidget(Font textRenderer, int x, int y, int width, int height, String label, boolean enabled, Runnable action) {
        super(x, y, width, height, Component.literal(label));
        this.textRenderer = textRenderer;
        this.label = label;
        this.enabled = enabled;
        this.action = action;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        context.fill(x, y, x + width, y + height, isHovered() ? LegionsAtomicsUi.ROW_HOVER : LegionsAtomicsUi.ROW);
        context.fill(x, y, x + width, y + 1, LegionsAtomicsUi.PANEL_BORDER);
        context.fill(x, y + height - 1, x + width, y + height, LegionsAtomicsUi.PANEL_BORDER);
        context.fill(x, y, x + 1, y + height, LegionsAtomicsUi.PANEL_BORDER);
        context.fill(x + width - 1, y, x + width, y + height, LegionsAtomicsUi.PANEL_BORDER);

        String value = enabled ? "ON" : "OFF";
        int valueColor = enabled ? LegionsAtomicsUi.ACCENT : LegionsAtomicsUi.TEXT_MUTED;
        context.text(textRenderer, Component.literal(label), x + 8, y + 7, LegionsAtomicsUi.TEXT_MAIN);
        context.text(textRenderer, Component.literal(value), x + width - textRenderer.width(value) - 8, y + 7, valueColor);
    }

    @Override
    public void onClick(MouseButtonEvent click, boolean doubleClick) {
        action.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        defaultButtonNarrationText(builder);
    }
}
