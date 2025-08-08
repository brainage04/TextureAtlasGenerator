package com.github.brainage04.textureatlasgenerator.screen.core;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class FloatSliderWidget extends SliderWidget {
    private final float min;
    private final float max;
    private final float step;
    private final Consumer<Float> onApply;
    private final String label;

    public FloatSliderWidget(int x, int y, int w, int h, String label,
                             float min, float max, float start,
                             float step, Consumer<Float> onApply) {
        super(x, y, w, h, Text.literal(label),
              (start - min) / (double) (max - min));
        this.min  = min;
        this.max  = max;
        this.step = step;
        this.onApply = onApply;
        this.label = label;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Text.literal("%s: %f".formatted(label, getCurrent())));
    }

    @Override
    protected void applyValue() {
        onApply.accept(getCurrent());                          // persist to your config
    }

    private float getCurrent() {
        double scaled = value * (max - min) + min;             // 0-1  → min-max
        return Math.round((float) scaled / step) * step;        // snap to step
    }
}