package com.legions.client.gui;

import com.legions.client.LegionsClient;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

final class LegionsUiScaleSlider extends AbstractSliderButton {
    private static final int MIN_SCALE = 50;
    private static final int MAX_SCALE = 200;

    private final Runnable onChanged;

    LegionsUiScaleSlider(int x, int y, int width, int height, Runnable onChanged) {
        super(x, y, width, height, Component.empty(), 0.0);
        this.onChanged = onChanged;
        setActualValue(LegionsClient.CONFIG == null ? 100 : LegionsClient.CONFIG.uiScale);
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal("UI Scale %        " + getActualValue()));
    }

    @Override
    protected void applyValue() {
        if (LegionsClient.CONFIG != null) {
            LegionsClient.CONFIG.uiScale = getActualValue();
            LegionsClient.CONFIG.normalize();
        }
        if (onChanged != null) {
            onChanged.run();
        }
    }

    private int getActualValue() {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, (int) Math.round(MIN_SCALE + value * (MAX_SCALE - MIN_SCALE))));
    }

    private void setActualValue(int actualValue) {
        value = (double) (Math.max(MIN_SCALE, Math.min(MAX_SCALE, actualValue)) - MIN_SCALE)
                / (double) (MAX_SCALE - MIN_SCALE);
        applyValue();
        updateMessage();
    }
}
