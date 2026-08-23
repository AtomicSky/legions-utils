package com.legions.client.render;

public final class LegionsPlayerOverlayColorContext {
    public static final int STYLE_FULL = 0;
    public static final int STYLE_OUTLINE = 1;
    public static final int STYLE_OUTLINE_FULL = 2;
    public static final int STYLE_PULSE = 3;
    public static final int STYLE_ARMOR_FULL = 4;

    private static int color = -1;
    private static int style = STYLE_FULL;
    private static int armorRenderDepth;
    private static long lastPulseMillis = Long.MIN_VALUE;
    private static int lastPulseInputColor;
    private static int lastPulseOutputColor;

    private LegionsPlayerOverlayColorContext() {
    }

    public static void set(int color, int style) {
        LegionsPlayerOverlayColorContext.color = color;
        LegionsPlayerOverlayColorContext.style = style;
    }

    public static void clear() {
        color = -1;
        style = STYLE_FULL;
        armorRenderDepth = 0;
    }

    public static void pushArmorRender() {
        armorRenderDepth++;
    }

    public static void popArmorRender() {
        armorRenderDepth = Math.max(0, armorRenderDepth - 1);
    }

    public static int apply(int originalColor) {
        int overlayColor = color;
        if (overlayColor == -1 || style == STYLE_OUTLINE) {
            return originalColor;
        }
        if (style == STYLE_ARMOR_FULL && armorRenderDepth <= 0) {
            return originalColor;
        }
        if (style == STYLE_PULSE) {
            overlayColor = pulseColor(overlayColor);
        }

        int alpha = (overlayColor >>> 24) & 255;
        if (alpha <= 0) {
            return originalColor;
        }
        if (alpha >= 255) {
            return overlayColor;
        }

        int baseColor = originalColor == -1 ? 0xFFFFFFFF : originalColor;
        int inverseAlpha = 255 - alpha;
        int baseAlpha = (baseColor >>> 24) & 255;
        int outAlpha = alpha + baseAlpha * inverseAlpha / 255;
        int r = (((overlayColor >>> 16) & 255) * alpha + ((baseColor >>> 16) & 255) * inverseAlpha) / 255;
        int g = (((overlayColor >>> 8) & 255) * alpha + ((baseColor >>> 8) & 255) * inverseAlpha) / 255;
        int b = ((overlayColor & 255) * alpha + (baseColor & 255) * inverseAlpha) / 255;
        return (outAlpha << 24) | (r << 16) | (g << 8) | b;
    }

    private static int pulseColor(int overlayColor) {
        int alpha = (overlayColor >>> 24) & 255;
        if (alpha <= 0) {
            return overlayColor;
        }
        long now = System.currentTimeMillis();
        if (now == lastPulseMillis && overlayColor == lastPulseInputColor) {
            return lastPulseOutputColor;
        }
        double wave = (Math.sin(now / 260.0) + 1.0) * 0.5;
        int pulsedAlpha = Math.max(1, Math.min(255, Math.round((float) (alpha * (0.35 + wave * 0.65)))));
        lastPulseMillis = now;
        lastPulseInputColor = overlayColor;
        lastPulseOutputColor = (overlayColor & 0x00FFFFFF) | (pulsedAlpha << 24);
        return lastPulseOutputColor;
    }
}
