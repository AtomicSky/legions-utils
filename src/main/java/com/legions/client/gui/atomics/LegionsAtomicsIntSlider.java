package com.legions.client.gui.atomics;

import com.legions.client.LegionsClient;
import com.legions.client.config.LegionsConfig;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public final class LegionsAtomicsIntSlider extends AbstractSliderButton {
    private final String label;
    private final int min;
    private final int max;
    private final IntConsumer setter;
    private boolean saveOnChange;

    public LegionsAtomicsIntSlider(int x, int y, int width, int height, String label, int min, int max, int initial, IntConsumer setter) {
        super(x, y, width, height, Component.empty(), 0.0);
        this.label = label;
        this.min = min;
        this.max = max;
        this.setter = setter;
        setActualValue(initial);
        saveOnChange = true;
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(label + "        " + getActualValue()));
    }

    @Override
    protected void applyValue() {
        setter.accept(getActualValue());
        LegionsConfig config = LegionsClient.CONFIG;
        if (config != null) {
            config.normalize();
        }
        if (saveOnChange) {
            LegionsClient.saveConfig();
        }
    }

    private int getActualValue() {
        return Math.max(min, Math.min(max, (int) Math.round(min + value * (max - min))));
    }

    private void setActualValue(int actualValue) {
        value = max <= min ? 0.0 : (double) (Math.max(min, Math.min(max, actualValue)) - min) / (double) (max - min);
        applyValue();
        updateMessage();
    }
}
