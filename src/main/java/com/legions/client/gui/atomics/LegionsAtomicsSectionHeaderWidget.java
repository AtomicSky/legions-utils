package com.legions.client.gui.atomics;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

public final class LegionsAtomicsSectionHeaderWidget extends ClickableWidget {
    private final TextRenderer textRenderer;
    private final String title;
    private final boolean collapsed;
    private final Runnable action;

    public LegionsAtomicsSectionHeaderWidget(TextRenderer textRenderer, int x, int y, int width, int height, String title, boolean collapsed, Runnable action) {
        super(x, y, width, height, Text.literal(title));
        this.textRenderer = textRenderer;
        this.title = title;
        this.collapsed = collapsed;
        this.action = action;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int bg = isHovered() ? LegionsAtomicsUi.ROW_HOVER : LegionsAtomicsUi.ROW;
        context.fill(x, y, x + width, y + height, bg);
        context.fill(x, y, x + width, y + 1, LegionsAtomicsUi.PANEL_BORDER);
        context.fill(x, y + height - 1, x + width, y + height, LegionsAtomicsUi.PANEL_BORDER);
        context.fill(x, y, x + 3, y + height, collapsed ? LegionsAtomicsUi.TEXT_DIM : LegionsAtomicsUi.ACCENT);

        String marker = collapsed ? ">" : "v";
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(marker), x + 15, y + 7, collapsed ? LegionsAtomicsUi.TEXT_MUTED : LegionsAtomicsUi.TEXT_MAIN);
        context.drawTextWithShadow(textRenderer, Text.literal(title), x + 28, y + 7, LegionsAtomicsUi.TEXT_MAIN);
        String state = collapsed ? "show" : "hide";
        context.drawTextWithShadow(textRenderer, Text.literal(state), x + width - textRenderer.getWidth(state) - 10, y + 7, LegionsAtomicsUi.TEXT_MUTED);
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
