package com.legions.client;

import net.minecraft.client.MinecraftClient;

public final class LegionsAdaptivePerformance {
    private static final int SAMPLE_INTERVAL_TICKS = 40;
    private static final long RECOVERY_INTERVAL_MILLIS = 5_000L;
    private static final double FPS_SMOOTHING = 0.08D;
    private static final int MIN_RENDER_DISTANCE = 24;

    private static int sampleTicks;
    private static int reductionLevel;
    private static double smoothedFps = -1.0D;
    private static long lastLevelChangeAt;

    private LegionsAdaptivePerformance() {
    }

    public static void tick(MinecraftClient client) {
        if (!available(client)) {
            reset();
            return;
        }

        int fps = client.getCurrentFps();
        if (fps <= 0) {
            return;
        }
        smoothedFps = smoothedFps < 0.0D
                ? fps
                : smoothedFps + (fps - smoothedFps) * FPS_SMOOTHING;

        if (++sampleTicks < SAMPLE_INTERVAL_TICKS) {
            return;
        }
        sampleTicks = 0;

        int targetFps = Math.max(30, Math.min(60, client.options.getMaxFps().getValue()));
        int desiredLevel = desiredReductionLevel(smoothedFps / targetFps);
        long now = System.currentTimeMillis();
        if (desiredLevel > reductionLevel) {
            reductionLevel = desiredLevel;
            lastLevelChangeAt = now;
        } else if (desiredLevel < reductionLevel && now - lastLevelChangeAt >= RECOVERY_INTERVAL_MILLIS) {
            reductionLevel--;
            lastLevelChangeAt = now;
        }
    }

    public static boolean isActivelyReducing() {
        return reductionLevel > 0;
    }

    public static int effectiveOpponentLimit(int configuredLimit) {
        int limit = Math.max(1, configuredLimit);
        return switch (reductionLevel) {
            case 1 -> Math.max(1, (int) Math.ceil(limit * 0.8D));
            case 2 -> Math.max(1, (int) Math.ceil(limit * 0.6D));
            case 3 -> Math.max(1, (int) Math.ceil(limit * 0.4D));
            default -> limit;
        };
    }

    public static int effectivePlayerRenderDistance(int configuredDistance) {
        int distance = Math.max(MIN_RENDER_DISTANCE, configuredDistance);
        double multiplier = switch (reductionLevel) {
            case 1 -> 0.75D;
            case 2 -> 0.625D;
            case 3 -> 0.5D;
            default -> 1.0D;
        };
        int reduced = (int) Math.round(distance * multiplier / 8.0D) * 8;
        return Math.max(MIN_RENDER_DISTANCE, Math.min(distance, reduced));
    }

    public static void reset() {
        sampleTicks = 0;
        reductionLevel = 0;
        smoothedFps = -1.0D;
        lastLevelChangeAt = 0L;
    }

    private static boolean available(MinecraftClient client) {
        return client != null
                && client.world != null
                && client.player != null
                && client.options != null
                && client.isWindowFocused()
                && !client.isPaused()
                && LegionsClient.enabled(client)
                && LegionsClient.CONFIG.adaptivePerformanceEnabled;
    }

    private static int desiredReductionLevel(double fpsRatio) {
        if (fpsRatio < 0.5D) {
            return 3;
        }
        if (fpsRatio < 0.65D) {
            return 2;
        }
        if (fpsRatio < 0.8D) {
            return 1;
        }
        return 0;
    }
}
