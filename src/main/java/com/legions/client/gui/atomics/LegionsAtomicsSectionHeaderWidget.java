package com.legions.client.gui.atomics;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class LegionsAtomicsSectionHeaderWidget extends AbstractWidget {
    private final Font textRenderer;
    private final String title;
    private final boolean collapsed;
    private final Runnable action;

    public LegionsAtomicsSectionHeaderWidget(Font textRenderer, int x, int y, int width, int height, String title, boolean collapsed, Runnable action) {
        super(x, y, width, height, Component.literal(title));
        this.textRenderer = textRenderer;
        this.title = title;
        this.collapsed = collapsed;
        this.action = action;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int bg = isHovered() ? LegionsAtomicsUi.ROW_HOVER : LegionsAtomicsUi.ROW;
        context.fill(x, y, x + width, y + height, bg);
        context.fill(x, y, x + width, y + 1, LegionsAtomicsUi.PANEL_BORDER);
        context.fill(x, y + height - 1, x + width, y + height, LegionsAtomicsUi.PANEL_BORDER);
        context.fill(x, y, x + 3, y + height, collapsed ? LegionsAtomicsUi.TEXT_DIM : LegionsAtomicsUi.ACCENT);

        String marker = collapsed ? ">" : "v";
        context.centeredText(textRenderer, Component.literal(marker), x + 15, y + 7, collapsed ? LegionsAtomicsUi.TEXT_MUTED : LegionsAtomicsUi.TEXT_MAIN);
        context.text(textRenderer, Component.literal(title), x + 28, y + 7, LegionsAtomicsUi.TEXT_MAIN);
        String state = collapsed ? "show" : "hide";
        context.text(textRenderer, Component.literal(state), x + width - textRenderer.width(state) - 10, y + 7, LegionsAtomicsUi.TEXT_MUTED);
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
