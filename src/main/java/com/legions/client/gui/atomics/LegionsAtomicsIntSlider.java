package com.legions.client.gui.atomics;

import com.legions.client.LegionsClient;
import com.legions.client.config.LegionsConfig;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;

public final class LegionsAtomicsIntSlider extends SliderWidget {
    private final String label;
    private final int min;
    private final int max;
    private final IntConsumer setter;
    private boolean saveOnChange;

    public LegionsAtomicsIntSlider(int x, int y, int width, int height, String label, int min, int max, int initial, IntConsumer setter) {
        super(x, y, width, height, Text.empty(), 0.0);
        this.label = label;
        this.min = min;
        this.max = max;
        this.setter = setter;
        setActualValue(initial);
        saveOnChange = true;
    }

    @Override
    protected void updateMessage() {
        setMessage(Text.literal(label + "        " + getActualValue()));
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
