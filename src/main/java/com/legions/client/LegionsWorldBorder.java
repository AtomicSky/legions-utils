package com.legions.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LegionsWorldBorder {
    private static final int MAX_SAMPLES = 384;
    private static final int MIN_SAMPLES = 3;
    private static final int RECOMPUTE_INTERVAL_TICKS = 4;
    private static final long SAMPLE_TTL_MILLIS = 1_500L;
    private static final long BORDER_TTL_MILLIS = 10_000L;
    private static final double MIN_SAMPLE_SEPARATION_SQUARED = 0.35D * 0.35D;
    private static final double MIN_SAMPLE_UPDATE_DISTANCE_SQUARED = 0.05D * 0.05D;
    private static final double SAMPLE_POSITION_SMOOTHING = 0.35D;
    private static final double SAME_HEIGHT_BAND = 1.0D;
    private static final int MIN_CIRCLE_SAMPLES = 6;
    private static final double MIN_CIRCLE_RADIUS = 2.0D;
    private static final double MAX_CIRCLE_RADIUS = 512.0D;
    private static final double MIN_OBSERVED_ARC_RADIANS = Math.toRadians(90.0D);
    private static final double MIN_CIRCLE_RESIDUAL = 0.75D;
    private static final double MAX_CIRCLE_RELATIVE_RESIDUAL = 0.04D;
    private static final double MAX_OBSERVED_GAP_MULTIPLIER = 2.75D;
    private static final double MIN_OBSERVED_GAP_DISTANCE = 3.0D;
    private static final double PREDICTED_SEGMENT_LENGTH = 3.0D;
    private static final int MIN_PREDICTED_SEGMENTS = 48;
    private static final int MAX_PREDICTED_SEGMENTS = 160;
    private static final int OBSERVED_CURVE_SEGMENTS = 4;
    private static final double OBSERVED_CURVE_STRENGTH = 0.06D;
    private static final double MAX_OBSERVED_CURVE_OFFSET = 1.25D;
    private static final double CIRCLE_SMOOTHING_FACTOR = 0.15D;
    private static final double MAX_CENTER_STEP_PER_TICK = 1.25D;
    private static final double MAX_RADIUS_STEP_PER_TICK = 0.75D;
    private static final double SMOOTHING_SETTLED_DISTANCE = 0.01D;
    private static final long BORDER_FADE_IN_MILLIS = 650L;
    private static final long BORDER_FADE_OUT_MILLIS = 750L;
    private static final double CONNECTOR_BAND_SPACING = 16.0D;
    private static final double CONNECTOR_VISIBLE_RANGE = 32.0D;
    private static final double REDUCED_CONNECTOR_VISIBLE_RANGE = 16.0D;
    private static final float BORDER_STROKE_WIDTH = 1.25F;
    private static final Set<Identifier> GLITTER_TEXTURES = Set.of(
            Identifier.ofVanilla("glitter_0"),
            Identifier.ofVanilla("glitter_1"),
            Identifier.ofVanilla("glitter_2"),
            Identifier.ofVanilla("glitter_3"),
            Identifier.ofVanilla("glitter_4"),
            Identifier.ofVanilla("glitter_5"),
            Identifier.ofVanilla("glitter_6"),
            Identifier.ofVanilla("glitter_7")
    );

    private static final ArrayList<Sample> samples = new ArrayList<>();
    private static final ArrayList<BorderSegment> borderSegments = new ArrayList<>();
    private static ClientWorld sampledWorld;
    private static long lastParticleAt;
    private static long borderFadeStartedAt;
    private static int recomputeTicks;
    private static boolean samplesChanged;
    private static CircleFit predictedCircle;
    private static CircleFit displayedCircle;
    private static boolean displayingPredictedCircle;
    private static double selectedHeightY = Double.NaN;

    private LegionsWorldBorder() {
    }

    public static boolean captureGlitterParticle(Sprite sprite, double x, double y, double z) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isGlitterSprite(sprite) || !available(client)) {
            return false;
        }

        ensureWorld(client.world);
        long now = System.currentTimeMillis();
        lastParticleAt = now;
        rememberSample(x, y, z, now);
        return true;
    }

    public static boolean shouldHideGlitterParticle(Sprite sprite) {
        MinecraftClient client = MinecraftClient.getInstance();
        return isGlitterSprite(sprite)
                && available(client)
                && LegionsClient.CONFIG.customWorldBorderHideGlitterParticles;
    }

    public static void tick(MinecraftClient client) {
        if (!available(client)) {
            reset();
            return;
        }

        ensureWorld(client.world);
        long now = System.currentTimeMillis();
        boolean removed = samples.removeIf(sample -> now - sample.seenAt > SAMPLE_TTL_MILLIS);
        samplesChanged |= removed;

        if (!borderSegments.isEmpty() && now - lastParticleAt > BORDER_TTL_MILLIS) {
            borderSegments.clear();
            borderFadeStartedAt = 0L;
            predictedCircle = null;
            displayedCircle = null;
            displayingPredictedCircle = false;
            selectedHeightY = Double.NaN;
        }
        if (samplesChanged && ++recomputeTicks >= RECOMPUTE_INTERVAL_TICKS) {
            recomputeTicks = 0;
            samplesChanged = false;
            rebuildBorderSegments();
            if (borderSegments.isEmpty()) {
                borderFadeStartedAt = 0L;
            }
        }
        advanceCircleSmoothing();
    }

    public static void render() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!available(client) || borderSegments.isEmpty()
                || System.currentTimeMillis() - lastParticleAt > BORDER_TTL_MILLIS) {
            return;
        }

        double minY = client.world.getBottomY();
        double maxY = client.world.getTopYInclusive() + 1.0D;
        long now = System.currentTimeMillis();
        if (borderFadeStartedAt == 0L) {
            borderFadeStartedAt = now;
        }
        float fadeIn = Math.max(0.0F, Math.min(1.0F,
                (now - borderFadeStartedAt) / (float) BORDER_FADE_IN_MILLIS));
        float fadeOut = Math.max(0.0F, Math.min(1.0F,
                (BORDER_TTL_MILLIS - (now - lastParticleAt)) / (float) BORDER_FADE_OUT_MILLIS));
        float visibility = Math.min(fadeIn, fadeOut);
        int borderRgb = configuredBorderRgb();
        int opacity = LegionsClient.CONFIG.customWorldBorderOpacity;
        int strokeColor = withAlpha(borderRgb, Math.round(255.0F * opacity / 100.0F * visibility));
        int fillColor = withAlpha(borderRgb, Math.round(255.0F * opacity / 100.0F * 0.23F * visibility));
        DrawStyle wallStyle = DrawStyle.filled(fillColor);
        Entity camera = client.getCameraEntity() == null ? client.player : client.getCameraEntity();
        double cameraY = camera == null ? (minY + maxY) * 0.5D : camera.getY();
        double connectorRange = LegionsAdaptivePerformance.isActivelyReducing()
                ? REDUCED_CONNECTOR_VISIBLE_RANGE
                : CONNECTOR_VISIBLE_RANGE;
        double firstConnectorY = Math.max(minY,
                Math.ceil((cameraY - connectorRange) / CONNECTOR_BAND_SPACING) * CONNECTOR_BAND_SPACING);
        double lastConnectorY = Math.min(maxY, cameraY + connectorRange);

        for (BorderSegment segment : borderSegments) {
            Vec3d bottomFirst = new Vec3d(segment.firstX, minY, segment.firstZ);
            Vec3d bottomSecond = new Vec3d(segment.secondX, minY, segment.secondZ);
            Vec3d topSecond = new Vec3d(segment.secondX, maxY, segment.secondZ);
            Vec3d topFirst = new Vec3d(segment.firstX, maxY, segment.firstZ);
            GizmoDrawing.quad(bottomFirst, bottomSecond, topSecond, topFirst, wallStyle);

            for (double y = firstConnectorY; y <= lastConnectorY; y += CONNECTOR_BAND_SPACING) {
                GizmoDrawing.line(new Vec3d(segment.firstX, y, segment.firstZ),
                        new Vec3d(segment.secondX, y, segment.secondZ),
                        strokeColor, BORDER_STROKE_WIDTH);
            }
        }
    }

    public static void reset() {
        samples.clear();
        borderSegments.clear();
        sampledWorld = null;
        lastParticleAt = 0L;
        borderFadeStartedAt = 0L;
        recomputeTicks = 0;
        samplesChanged = false;
        predictedCircle = null;
        displayedCircle = null;
        displayingPredictedCircle = false;
        selectedHeightY = Double.NaN;
    }

    private static boolean available(MinecraftClient client) {
        return client != null
                && client.world != null
                && LegionsClient.CONFIG != null
                && LegionsClient.CONFIG.customWorldBorderEnabled
                && LegionsClient.enabled(client);
    }

    private static void ensureWorld(ClientWorld world) {
        if (sampledWorld == world) {
            return;
        }
        reset();
        sampledWorld = world;
    }

    private static boolean isGlitterSprite(Sprite sprite) {
        return sprite != null
                && sprite.getContents() != null
                && GLITTER_TEXTURES.contains(sprite.getContents().getId());
    }

    private static int configuredBorderRgb() {
        String color = LegionsClient.CONFIG == null ? null : LegionsClient.CONFIG.customWorldBorderColor;
        if (color == null) {
            return 0xFF5555;
        }
        String hex = color.startsWith("#") ? color.substring(1) : color;
        try {
            return Integer.parseInt(hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            return 0xFF5555;
        }
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    private static void rememberSample(double x, double y, double z, long now) {
        for (int index = samples.size() - 1; index >= 0; index--) {
            Sample sample = samples.get(index);
            double horizontalDistanceSquared = distanceSquared(sample.x, sample.z, x, z);
            double yDifference = y - sample.y;
            if (Math.abs(yDifference) <= SAME_HEIGHT_BAND * 0.5D
                    && horizontalDistanceSquared <= MIN_SAMPLE_SEPARATION_SQUARED) {
                sample.seenAt = now;
                if (horizontalDistanceSquared > MIN_SAMPLE_UPDATE_DISTANCE_SQUARED
                        || Math.abs(yDifference) > 0.05D) {
                    sample.x += (x - sample.x) * SAMPLE_POSITION_SMOOTHING;
                    sample.y += yDifference * SAMPLE_POSITION_SMOOTHING;
                    sample.z += (z - sample.z) * SAMPLE_POSITION_SMOOTHING;
                    samplesChanged = true;
                }
                return;
            }
        }

        if (samples.size() >= MAX_SAMPLES) {
            samples.remove(0);
        }
        samples.add(new Sample(x, y, z, now));
        samplesChanged = true;
    }

    private static void rebuildBorderSegments() {
        borderSegments.clear();
        List<HorizontalPoint> levelPoints = findBestHeightLevel();
        List<HorizontalPoint> perimeter = convexHull(levelPoints);

        CircleFit currentFit = fitCircle(perimeter);
        if (currentFit != null) {
            predictedCircle = currentFit;
            if (displayedCircle == null) {
                displayedCircle = currentFit;
            }
            displayingPredictedCircle = true;
            addPredictedCircle(displayedCircle);
            return;
        }

        // Once a circle has been acquired, keep that render state through temporary
        // sample gaps. The normal border TTL clears it when the signal really stops.
        if (predictedCircle != null) {
            displayingPredictedCircle = true;
            if (displayedCircle == null) {
                displayedCircle = predictedCircle;
            }
            addPredictedCircle(displayedCircle);
            return;
        }

        predictedCircle = null;
        displayedCircle = null;
        displayingPredictedCircle = false;
        addObservedPerimeter(perimeter);
    }

    private static void advanceCircleSmoothing() {
        if (!displayingPredictedCircle || predictedCircle == null || displayedCircle == null) {
            return;
        }

        double deltaX = predictedCircle.centerX - displayedCircle.centerX;
        double deltaZ = predictedCircle.centerZ - displayedCircle.centerZ;
        double centerDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double radiusDelta = predictedCircle.radius - displayedCircle.radius;
        if (centerDistance <= SMOOTHING_SETTLED_DISTANCE
                && Math.abs(radiusDelta) <= SMOOTHING_SETTLED_DISTANCE) {
            if (!displayedCircle.equals(predictedCircle)) {
                displayedCircle = predictedCircle;
                rebuildDisplayedCircle();
            }
            return;
        }

        double centerScale = CIRCLE_SMOOTHING_FACTOR;
        if (centerDistance * centerScale > MAX_CENTER_STEP_PER_TICK) {
            centerScale = MAX_CENTER_STEP_PER_TICK / centerDistance;
        }
        double radiusStep = Math.max(-MAX_RADIUS_STEP_PER_TICK,
                Math.min(MAX_RADIUS_STEP_PER_TICK, radiusDelta * CIRCLE_SMOOTHING_FACTOR));
        displayedCircle = new CircleFit(
                displayedCircle.centerX + deltaX * centerScale,
                displayedCircle.centerZ + deltaZ * centerScale,
                displayedCircle.radius + radiusStep);
        rebuildDisplayedCircle();
    }

    private static void rebuildDisplayedCircle() {
        borderSegments.clear();
        addPredictedCircle(displayedCircle);
    }

    private static void addPredictedCircle(CircleFit circle) {
        int segmentCount = Math.max(MIN_PREDICTED_SEGMENTS, Math.min(MAX_PREDICTED_SEGMENTS,
                (int) Math.ceil(Math.PI * 2.0D * circle.radius / PREDICTED_SEGMENT_LENGTH)));
        for (int index = 0; index < segmentCount; index++) {
            double firstAngle = Math.PI * 2.0D * index / segmentCount;
            double secondAngle = Math.PI * 2.0D * (index + 1) / segmentCount;
            borderSegments.add(new BorderSegment(
                    circle.centerX + Math.cos(firstAngle) * circle.radius,
                    circle.centerZ + Math.sin(firstAngle) * circle.radius,
                    circle.centerX + Math.cos(secondAngle) * circle.radius,
                    circle.centerZ + Math.sin(secondAngle) * circle.radius));
        }
    }

    private static void addObservedPerimeter(List<HorizontalPoint> perimeter) {
        if (perimeter.size() < MIN_SAMPLES) {
            return;
        }

        double centerX = 0.0D;
        double centerZ = 0.0D;
        for (HorizontalPoint point : perimeter) {
            centerX += point.x;
            centerZ += point.z;
        }
        centerX /= perimeter.size();
        centerZ /= perimeter.size();

        double[] edgeLengths = new double[perimeter.size()];
        for (int index = 0; index < perimeter.size(); index++) {
            HorizontalPoint first = perimeter.get(index);
            HorizontalPoint second = perimeter.get((index + 1) % perimeter.size());
            edgeLengths[index] = Math.sqrt(distanceSquared(first.x, first.z, second.x, second.z));
        }
        double[] sortedLengths = edgeLengths.clone();
        java.util.Arrays.sort(sortedLengths);
        double typicalLength = sortedLengths[sortedLengths.length / 2];
        double maximumObservedGap = Math.max(MIN_OBSERVED_GAP_DISTANCE,
                typicalLength * MAX_OBSERVED_GAP_MULTIPLIER);

        for (int index = 0; index < perimeter.size(); index++) {
            if (edgeLengths[index] > maximumObservedGap) {
                continue;
            }
            HorizontalPoint first = perimeter.get(index);
            HorizontalPoint second = perimeter.get((index + 1) % perimeter.size());
            addRoundedObservedConnection(first, second, centerX, centerZ, edgeLengths[index]);
        }
    }

    private static void addRoundedObservedConnection(HorizontalPoint first, HorizontalPoint second,
                                                      double centerX, double centerZ, double edgeLength) {
        double midpointX = (first.x + second.x) * 0.5D;
        double midpointZ = (first.z + second.z) * 0.5D;
        double outwardX = midpointX - centerX;
        double outwardZ = midpointZ - centerZ;
        double outwardLength = Math.sqrt(outwardX * outwardX + outwardZ * outwardZ);
        if (outwardLength < 1.0E-6D) {
            borderSegments.add(new BorderSegment(first.x, first.z, second.x, second.z));
            return;
        }

        double curveOffset = Math.min(MAX_OBSERVED_CURVE_OFFSET, edgeLength * OBSERVED_CURVE_STRENGTH);
        double controlX = midpointX + outwardX / outwardLength * curveOffset;
        double controlZ = midpointZ + outwardZ / outwardLength * curveOffset;
        double previousX = first.x;
        double previousZ = first.z;
        for (int step = 1; step <= OBSERVED_CURVE_SEGMENTS; step++) {
            double progress = (double) step / OBSERVED_CURVE_SEGMENTS;
            double inverse = 1.0D - progress;
            double nextX = inverse * inverse * first.x
                    + 2.0D * inverse * progress * controlX
                    + progress * progress * second.x;
            double nextZ = inverse * inverse * first.z
                    + 2.0D * inverse * progress * controlZ
                    + progress * progress * second.z;
            borderSegments.add(new BorderSegment(previousX, previousZ, nextX, nextZ));
            previousX = nextX;
            previousZ = nextZ;
        }
    }

    private static CircleFit fitCircle(List<HorizontalPoint> points) {
        if (points.size() < MIN_CIRCLE_SAMPLES) {
            return null;
        }

        CircleFit initial = leastSquaresCircle(points);
        if (initial == null) {
            return null;
        }

        double[] residuals = radialResiduals(points, initial);
        double[] sortedResiduals = residuals.clone();
        java.util.Arrays.sort(sortedResiduals);
        double medianResidual = sortedResiduals[sortedResiduals.length / 2];
        double inlierThreshold = Math.max(MIN_CIRCLE_RESIDUAL, medianResidual * 3.0D);
        ArrayList<HorizontalPoint> inliers = new ArrayList<>();
        for (int index = 0; index < points.size(); index++) {
            if (residuals[index] <= inlierThreshold) {
                inliers.add(points.get(index));
            }
        }
        if (inliers.size() < MIN_CIRCLE_SAMPLES) {
            return null;
        }

        CircleFit refined = leastSquaresCircle(inliers);
        if (refined == null
                || refined.radius < MIN_CIRCLE_RADIUS
                || refined.radius > MAX_CIRCLE_RADIUS
                || observedArc(inliers, refined) < MIN_OBSERVED_ARC_RADIANS) {
            return null;
        }

        double squaredResidualSum = 0.0D;
        for (double residual : radialResiduals(inliers, refined)) {
            squaredResidualSum += residual * residual;
        }
        double rootMeanSquareResidual = Math.sqrt(squaredResidualSum / inliers.size());
        double allowedResidual = Math.max(MIN_CIRCLE_RESIDUAL,
                refined.radius * MAX_CIRCLE_RELATIVE_RESIDUAL);
        return rootMeanSquareResidual <= allowedResidual ? refined : null;
    }

    private static CircleFit leastSquaresCircle(List<HorizontalPoint> points) {
        double meanX = 0.0D;
        double meanZ = 0.0D;
        for (HorizontalPoint point : points) {
            meanX += point.x;
            meanZ += point.z;
        }
        meanX /= points.size();
        meanZ /= points.size();

        double sumXX = 0.0D;
        double sumXZ = 0.0D;
        double sumZZ = 0.0D;
        double sumX = 0.0D;
        double sumZ = 0.0D;
        double sumQ = 0.0D;
        double sumXQ = 0.0D;
        double sumZQ = 0.0D;
        for (HorizontalPoint point : points) {
            double x = point.x - meanX;
            double z = point.z - meanZ;
            double q = x * x + z * z;
            sumXX += x * x;
            sumXZ += x * z;
            sumZZ += z * z;
            sumX += x;
            sumZ += z;
            sumQ += q;
            sumXQ += x * q;
            sumZQ += z * q;
        }

        double[] solution = solveThreeByThree(new double[][]{
                {sumXX, sumXZ, sumX, sumXQ},
                {sumXZ, sumZZ, sumZ, sumZQ},
                {sumX, sumZ, points.size(), sumQ}
        });
        if (solution == null) {
            return null;
        }

        double relativeCenterX = solution[0] * 0.5D;
        double relativeCenterZ = solution[1] * 0.5D;
        double radiusSquared = solution[2]
                + relativeCenterX * relativeCenterX
                + relativeCenterZ * relativeCenterZ;
        if (!Double.isFinite(radiusSquared) || radiusSquared <= 0.0D) {
            return null;
        }
        return new CircleFit(meanX + relativeCenterX, meanZ + relativeCenterZ, Math.sqrt(radiusSquared));
    }

    private static double[] solveThreeByThree(double[][] matrix) {
        for (int column = 0; column < 3; column++) {
            int pivot = column;
            for (int row = column + 1; row < 3; row++) {
                if (Math.abs(matrix[row][column]) > Math.abs(matrix[pivot][column])) {
                    pivot = row;
                }
            }
            if (Math.abs(matrix[pivot][column]) < 1.0E-8D) {
                return null;
            }
            double[] swap = matrix[column];
            matrix[column] = matrix[pivot];
            matrix[pivot] = swap;

            double divisor = matrix[column][column];
            for (int entry = column; entry < 4; entry++) {
                matrix[column][entry] /= divisor;
            }
            for (int row = 0; row < 3; row++) {
                if (row == column) {
                    continue;
                }
                double factor = matrix[row][column];
                for (int entry = column; entry < 4; entry++) {
                    matrix[row][entry] -= factor * matrix[column][entry];
                }
            }
        }
        return new double[]{matrix[0][3], matrix[1][3], matrix[2][3]};
    }

    private static double[] radialResiduals(List<HorizontalPoint> points, CircleFit circle) {
        double[] residuals = new double[points.size()];
        for (int index = 0; index < points.size(); index++) {
            HorizontalPoint point = points.get(index);
            residuals[index] = Math.abs(Math.sqrt(distanceSquared(point.x, point.z,
                    circle.centerX, circle.centerZ)) - circle.radius);
        }
        return residuals;
    }

    private static double observedArc(List<HorizontalPoint> points, CircleFit circle) {
        double[] angles = new double[points.size()];
        for (int index = 0; index < points.size(); index++) {
            HorizontalPoint point = points.get(index);
            double angle = Math.atan2(point.z - circle.centerZ, point.x - circle.centerX);
            angles[index] = angle < 0.0D ? angle + Math.PI * 2.0D : angle;
        }
        java.util.Arrays.sort(angles);
        double largestGap = angles[0] + Math.PI * 2.0D - angles[angles.length - 1];
        for (int index = 1; index < angles.length; index++) {
            largestGap = Math.max(largestGap, angles[index] - angles[index - 1]);
        }
        return Math.PI * 2.0D - largestGap;
    }

    private static List<HorizontalPoint> findBestHeightLevel() {
        List<HorizontalPoint> best = List.of();
        double bestHeightY = Double.NaN;
        for (double offset : new double[]{0.0D, SAME_HEIGHT_BAND * 0.5D}) {
            Map<Long, ArrayList<HorizontalPoint>> levels = new HashMap<>();
            for (Sample sample : samples) {
                long level = (long) Math.floor((sample.y + offset) / SAME_HEIGHT_BAND);
                ArrayList<HorizontalPoint> points = levels.computeIfAbsent(level, ignored -> new ArrayList<>());
                rememberHorizontalPoint(points, sample.x, sample.z);
            }
            for (Map.Entry<Long, ArrayList<HorizontalPoint>> entry : levels.entrySet()) {
                ArrayList<HorizontalPoint> points = entry.getValue();
                double heightY = entry.getKey() * SAME_HEIGHT_BAND - offset + SAME_HEIGHT_BAND * 0.5D;
                boolean closerToPrevious = points.size() == best.size()
                        && Double.isFinite(selectedHeightY)
                        && (!Double.isFinite(bestHeightY)
                        || Math.abs(heightY - selectedHeightY) < Math.abs(bestHeightY - selectedHeightY));
                if (points.size() > best.size() || closerToPrevious) {
                    best = points;
                    bestHeightY = heightY;
                }
            }
        }
        if (!best.isEmpty()) {
            selectedHeightY = bestHeightY;
        }
        return best;
    }

    private static void rememberHorizontalPoint(List<HorizontalPoint> points, double x, double z) {
        for (HorizontalPoint point : points) {
            if (distanceSquared(point.x, point.z, x, z) <= MIN_SAMPLE_SEPARATION_SQUARED) {
                return;
            }
        }
        points.add(new HorizontalPoint(x, z));
    }

    private static List<HorizontalPoint> convexHull(List<HorizontalPoint> input) {
        if (input.size() < MIN_SAMPLES) {
            return List.of();
        }

        ArrayList<HorizontalPoint> sorted = new ArrayList<>(input);
        sorted.sort(Comparator.comparingDouble(HorizontalPoint::x).thenComparingDouble(HorizontalPoint::z));
        ArrayList<HorizontalPoint> hull = new ArrayList<>();

        for (HorizontalPoint point : sorted) {
            while (hull.size() >= 2 && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), point) <= 0.0D) {
                hull.remove(hull.size() - 1);
            }
            hull.add(point);
        }

        int lowerSize = hull.size();
        for (int index = sorted.size() - 2; index >= 0; index--) {
            HorizontalPoint point = sorted.get(index);
            while (hull.size() > lowerSize
                    && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), point) <= 0.0D) {
                hull.remove(hull.size() - 1);
            }
            hull.add(point);
        }
        hull.remove(hull.size() - 1);
        return hull;
    }

    private static double cross(HorizontalPoint first, HorizontalPoint second, HorizontalPoint third) {
        return (second.x - first.x) * (third.z - first.z)
                - (second.z - first.z) * (third.x - first.x);
    }

    private static double distanceSquared(double firstX, double firstZ, double secondX, double secondZ) {
        double deltaX = firstX - secondX;
        double deltaZ = firstZ - secondZ;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private static final class Sample {
        private double x;
        private double y;
        private double z;
        private long seenAt;

        private Sample(double x, double y, double z, long seenAt) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.seenAt = seenAt;
        }
    }

    private record HorizontalPoint(double x, double z) {
    }

    private record CircleFit(double centerX, double centerZ, double radius) {
    }

    private record BorderSegment(double firstX, double firstZ, double secondX, double secondZ) {
    }
}
