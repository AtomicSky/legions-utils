package com.legions.client;

import com.legions.client.config.LegionsConfig;
import com.legions.client.config.LegionsConfig.PingRow;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegionsPingController {
    private static final double RANGE = 100.0;
    private static final double PLAYER_PING_RAY_RADIUS = 3.0;
    private static final double PLAYER_PING_RAY_RADIUS_SQUARED =
            PLAYER_PING_RAY_RADIUS * PLAYER_PING_RAY_RADIUS;
    private static final long PRESS_WINDOW_MILLIS = 350L;
    private static final float BLOCK_PING_EXPAND = 0.02f;
    private static final float BLOCK_PING_STROKE_WIDTH = 2.25f;
    private static final float BLOCK_PING_FAR_MARKER_SIZE = 7.0f;
    private static final double BLOCK_PING_FAR_MARKER_DISTANCE_SQUARED = 40.0 * 40.0;
    private static final int DEFAULT_PING_COLOR = 0xFFFFD84A;
    private static final float BLOCK_PING_LABEL_SCALE = 0.65f;
    private static final float FIGHT_MARKER_SIZE = 8.0f;
    private static final int ARROW_SCREEN_MARGIN = 18;
    private static final int ARROW_STACK_DISTANCE = 22;
    private static final int ARROW_STACK_OFFSET = 10;
    private static final int FIGHT_DETECTION_RADIUS = 24;
    private static final int FIGHT_MARKER_SURFACE_SEARCH_RADIUS = 5;
    private static final double FIGHT_MARKER_SURFACE_OFFSET = 0.04;
    private static final int FIGHT_MIN_PLAYERS = 3;
    private static final int FIGHT_MIN_TEAMS = 2;
    private static final int FIGHT_REFRESH_TICKS = 5;
    private static final long FIGHT_MARKER_MIN_MOVE_MILLIS = 250L;
    private static final long FIGHT_MARKER_MAX_MOVE_MILLIS = 1200L;
    private static final int PING_LABEL_MAX_LENGTH = 14;
    private static final double BLOCK_MARKER_OCCLUSION_TOLERANCE_SQUARED = 1.0;
    private static final double PLAYER_MARKER_OCCLUSION_TOLERANCE_SQUARED = 0.04;
    private static final Pattern MACHINE_PAYLOAD_PATTERN = Pattern.compile("\\[LC:([PB]):([^\\]]+)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static final Map<BlockPos, PingMark> markedBlocks = new HashMap<>();
    private static final Map<String, PingMark> markedPlayers = new HashMap<>();
    private static final Map<PingKey, Boolean> keyDownStates = new HashMap<>();
    private static final Set<PingKey> configuredKeyScratch = new HashSet<>();
    private static final ArrayList<FightMark> fightMarkers = new ArrayList<>();
    private static final ArrayList<PlayerFightNode> fightPlayerScratch = new ArrayList<>();
    private static final ArrayList<PlayerFightNode> fightPlayerPool = new ArrayList<>();
    private static final ArrayList<FightCandidate> fightCandidateScratch = new ArrayList<>();
    private static final ArrayList<FightCandidate> fightCandidatePool = new ArrayList<>();
    private static final ArrayList<int[]> fightClusterSignatures = new ArrayList<>();
    private static final Set<String> fightTeamScratch = new HashSet<>();
    private static final ArrayList<ArrowPlacement> arrowPlacementScratch = new ArrayList<>();
    private static final ScreenProjection screenProjectionScratch = new ScreenProjection();
    private static final MarkerProjectionContext markerProjectionContextScratch = new MarkerProjectionContext();
    private static final PingKey[] keyboardPingKeys = new PingKey[349];
    private static final PingKey[] mousePingKeys = new PingKey[8];
    private static final double[] triangleIntersectionScratch = new double[3];
    private static final LongOpenHashSet fightSurfaceVisitedScratch = new LongOpenHashSet();
    private static final FightSurfaceBoxConsumer fightSurfaceBoxConsumer = new FightSurfaceBoxConsumer();
    private static final BlockOutlineBoxConsumer blockOutlineBoxConsumer = new BlockOutlineBoxConsumer();
    private static final BlockPos.Mutable fightBlockPosScratch = new BlockPos.Mutable();
    private static int[] fightClusterScratch = new int[0];
    private static int arrowPlacementCount;
    private static int markedPlayersVersion;
    private static int fightClusterSignatureCount;
    private static int cachedMarkedPlayerVersion = -1;
    private static PlayerEntity cachedMarkedPlayer;
    private static PingMark cachedMarkedPlayerMark;
    private static boolean cachedMarkedPlayerPingsEnabled;
    private static PendingPress pendingPress;
    private static long lastFightDetectionAt;
    private static UUID lastAttackedPlayerUuid;
    private static String lastAttackedPlayerName;
    private static long lastAttackedPlayerAt;
    private static UUID lastAttackerPlayerUuid;
    private static String lastAttackerPlayerName;
    private static long lastAttackerPlayerAt;

    private LegionsPingController() {
    }

    public static void recordAttackedEntity(MinecraftClient client, Entity target) {
        if (!(target instanceof PlayerEntity player) || client == null || client.player == null
                || player.getUuid().equals(client.player.getUuid())) {
            return;
        }

        lastAttackedPlayerUuid = player.getUuid();
        lastAttackedPlayerName = LegionsFeatures.realUsername(player);
        lastAttackedPlayerAt = System.currentTimeMillis();
    }

    public static void recordAttacker(MinecraftClient client, Entity attacker) {
        if (!(attacker instanceof PlayerEntity player) || client == null || client.player == null
                || player.getUuid().equals(client.player.getUuid())) {
            return;
        }

        lastAttackerPlayerUuid = player.getUuid();
        lastAttackerPlayerName = LegionsFeatures.realUsername(player);
        lastAttackerPlayerAt = System.currentTimeMillis();
    }

    public static void tick(MinecraftClient client) {
        if (client.world == null) {
            markedBlocks.clear();
            clearMarkedPlayers();
            fightMarkers.clear();
            clearInputState();
            clearRecentTargets();
            lastFightDetectionAt = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (!teamPingEnabled(client)) {
            clearInputState();
            clearPingMarks();
        } else if (!canUsePing(client)) {
            clearInputState();
        } else {
            List<PingRow> rows = configuredRows();
            flushPendingPress(client, rows, now);
            pollConfiguredKeys(client, rows, now);
            flushPendingPress(client, rows, now);
        }
        updateTeamFightMarkers(client, now);

        long ttl = pingTtlMillis();
        markedBlocks.entrySet().removeIf(entry -> now - entry.getValue().markedAt() > ttl);
        if (markedPlayers.entrySet().removeIf(entry -> now - entry.getValue().markedAt() > ttl)) {
            markedPlayersVersion++;
        }
        fightMarkers.removeIf(mark -> fightMarkerOpacity(mark, now) <= 0);
    }

    public static void receiveChatPing(Text message, GameProfile senderProfile) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!LegionsClient.enabled(client) || !teamPingEnabled(client) || message == null) {
            return;
        }

        String raw = message.getString();
        Matcher matcher = MACHINE_PAYLOAD_PATTERN.matcher(raw);
        while (matcher.find()) {
            ReceivedPing ping = parseReceivedPing(matcher.group(1), matcher.group(2));
            if (ping != null && canAcceptFrom(client, senderProfile, ping.sender(), ping.audience())) {
                if (ping.playerTarget() != null) {
                    markPlayer(ping.playerTarget(), ping.color(), ping.icon(), ping.label());
                } else if (ping.blockPos() != null) {
                    markBlock(ping.blockPos(), ping.color(), ping.icon(), ping.label());
                }
            }
        }
    }

    public static boolean shouldBlockIncomingPingText(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        return LegionsClient.enabled(client)
                && !teamPingEnabled(client)
                && hasMachinePayload(message);
    }

    public static boolean shouldCleanReceivedPingText(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        return LegionsClient.enabled(client)
                && teamPingEnabled(client)
                && hasMachinePayload(message);
    }

    public static boolean hasMachinePayload(Text message) {
        return message != null && MACHINE_PAYLOAD_PATTERN.matcher(message.getString()).find();
    }

    public static Text cleanReceivedPingText(Text message) {
        if (message == null) {
            return Text.empty();
        }
        return stripMachinePayload(message);
    }

    public static boolean isMarkedPlayer(PlayerEntity player) {
        return enabledMarkedPlayer(player) != null;
    }

    public static int markedPlayerColor(PlayerEntity player) {
        if (player == null) {
            return DEFAULT_PING_COLOR;
        }
        PingMark mark = markedPlayers.get(LegionsFeatures.normalizedPlayerName(LegionsFeatures.realUsername(player)));
        return mark == null ? DEFAULT_PING_COLOR : mark.color();
    }

    public static int enabledMarkedPlayerColor(PlayerEntity player) {
        PingMark mark = enabledMarkedPlayer(player);
        return mark == null ? 0 : mark.color();
    }

    private static PingMark enabledMarkedPlayer(PlayerEntity player) {
        if (player == null) {
            return null;
        }
        boolean pingsEnabled = teamPingEnabled(MinecraftClient.getInstance());
        if (player == cachedMarkedPlayer
                && markedPlayersVersion == cachedMarkedPlayerVersion
                && pingsEnabled == cachedMarkedPlayerPingsEnabled) {
            return cachedMarkedPlayerMark;
        }

        cachedMarkedPlayer = player;
        cachedMarkedPlayerVersion = markedPlayersVersion;
        cachedMarkedPlayerPingsEnabled = pingsEnabled;
        cachedMarkedPlayerMark = pingsEnabled
                ? markedPlayers.get(LegionsFeatures.normalizedPlayerName(LegionsFeatures.realUsername(player)))
                : null;
        return cachedMarkedPlayerMark;
    }

    public static void renderBlockHighlights() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!LegionsClient.enabled(client) || !LegionsClient.hudVisible(client) || client.world == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long ttl = pingTtlMillis();
        if (teamPingEnabled(client)) {
            for (Map.Entry<BlockPos, PingMark> entry : markedBlocks.entrySet()) {
                if (now - entry.getValue().markedAt() > ttl) {
                    continue;
                }
                renderBlockPingOutline(client, entry.getKey(), entry.getValue().color());
            }
        }

        if (!fightMarkers.isEmpty()) {
            int fightColor = parseColor(LegionsClient.CONFIG.teamFightMarkerColor);
            for (FightMark mark : fightMarkers) {
                int opacity = fightMarkerOpacity(mark, now);
                if (opacity > 0) {
                    renderFightMarker(client, mark, fightColor, opacity, now);
                }
            }
        }
    }

    public static void renderHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!LegionsClient.enabled(client)
                || !LegionsClient.hudVisible(client)
                || !LegionsClient.CONFIG.offscreenPingArrowsEnabled
                || client.world == null
                || client.player == null) {
            return;
        }

        Entity camera = client.getCameraEntity() == null ? client.player : client.getCameraEntity();
        if (camera == null) {
            return;
        }
        Vec3d cameraPos = camera.getCameraPosVec(1.0f);
        renderOffscreenArrows(context, client, System.currentTimeMillis(), camera, cameraPos);
    }

    private static void renderOffscreenArrows(DrawContext context, MinecraftClient client, long now,
                                              Entity camera, Vec3d cameraPos) {
        boolean pingsAvailable = teamPingEnabled(client) && (!markedBlocks.isEmpty() || !markedPlayers.isEmpty());
        if (!pingsAvailable && fightMarkers.isEmpty()) {
            return;
        }

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        if (screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        float scale = arrowScale();
        MarkerProjectionContext projectionContext = markerProjectionContext(client, camera, cameraPos,
                screenWidth, screenHeight, scale);
        arrowPlacementCount = 0;

        long pingTtl = pingTtlMillis();
        if (pingsAvailable) {
            for (Map.Entry<BlockPos, PingMark> entry : markedBlocks.entrySet()) {
                PingMark mark = entry.getValue();
                if (now - mark.markedAt() <= pingTtl) {
                    Vec3d pos = Vec3d.ofCenter(entry.getKey());
                    if (hasClearPathToPoint(client, cameraPos, pos,
                            BLOCK_MARKER_OCCLUSION_TOLERANCE_SQUARED, camera)) {
                        drawMarkerArrow(context, client, projectionContext, cameraPos, scale,
                                pos, mark.color(), mark.icon(), 100);
                    }
                }
            }
            for (Map.Entry<String, PingMark> entry : markedPlayers.entrySet()) {
                PingMark mark = entry.getValue();
                if (now - mark.markedAt() > pingTtl) {
                    continue;
                }
                PlayerEntity player = findPlayer(client, entry.getKey());
                if (player == null || player.getUuid().equals(client.player.getUuid())
                        || !hasPlayerLineOfSight(client, cameraPos, player, camera)) {
                    continue;
                }
                drawMarkerArrow(context, client, projectionContext, cameraPos, scale,
                        player.getBoundingBox().getCenter(), mark.color(), mark.icon(), 100);
            }
        }

        if (!fightMarkers.isEmpty()) {
            int fightColor = parseColor(LegionsClient.CONFIG.teamFightMarkerColor);
            for (FightMark mark : fightMarkers) {
                int opacity = fightMarkerOpacity(mark, now);
                if (opacity > 0) {
                    Vec3d pos = fightMarkerPosition(client, mark, now);
                    if (hasClearPathToPoint(client, cameraPos, pos,
                            BLOCK_MARKER_OCCLUSION_TOLERANCE_SQUARED, camera)) {
                        drawMarkerArrow(context, client, projectionContext, cameraPos, scale,
                                pos, fightColor, PingRow.ICON_FIRE, opacity);
                    }
                }
            }
        }
    }

    private static void drawMarkerArrow(DrawContext context, MinecraftClient client,
                                        MarkerProjectionContext projectionContext, Vec3d cameraPos, float scale,
                                        Vec3d pos, int color, int icon, int markerOpacity) {
        ScreenProjection projection = projectMarkerToScreenEdge(projectionContext, pos);
        if (projection.visible()) {
            return;
        }

        ArrowPlacement placement = stackedArrowPlacement(projection, arrowPlacementScratch, arrowPlacementCount,
                projectionContext, scale);
        arrowPlacementCount++;
        drawArrow(context, client, pos, color, icon, markerOpacity, placement, cameraPos, scale);
    }

    private static MarkerProjectionContext markerProjectionContext(MinecraftClient client, Entity camera,
                                                                   Vec3d cameraPos, int screenWidth,
                                                                   int screenHeight, float scale) {
        Vec3d look = camera.getRotationVec(1.0f).normalize();
        double yawRadians = Math.toRadians(camera.getYaw(1.0f));
        Vec3d right = new Vec3d(-Math.cos(yawRadians), 0.0, -Math.sin(yawRadians)).normalize();
        Vec3d up = right.crossProduct(look).normalize();
        double aspect = screenWidth / (double) screenHeight;
        double fov = client.options == null ? 70.0 : client.options.getFov().getValue();
        double verticalTan = Math.tan(Math.toRadians(clampDouble(fov, 30.0, 120.0) * 0.5));
        return markerProjectionContextScratch.set(cameraPos, look, right, up, verticalTan * aspect, verticalTan,
                screenWidth, screenHeight, Math.round(ARROW_SCREEN_MARGIN * scale));
    }

    private static ScreenProjection projectMarkerToScreenEdge(MarkerProjectionContext context, Vec3d pos) {
        int screenWidth = context.screenWidth();
        int screenHeight = context.screenHeight();
        Vec3d offset = pos.subtract(context.cameraPos());
        if (offset.lengthSquared() < 0.0001) {
            return screenProjectionScratch.set(true, screenWidth / 2.0, screenHeight / 2.0, 0.0, -1.0);
        }

        double horizontal = offset.dotProduct(context.right());
        double vertical = offset.dotProduct(context.up());
        double forward = offset.dotProduct(context.look());
        double screenX;
        double screenY;
        if (forward > 0.01) {
            screenX = horizontal / (forward * context.horizontalTan());
            screenY = -vertical / (forward * context.verticalTan());
        } else {
            double divisor = Math.max(1.0, Math.abs(forward));
            screenX = horizontal / (divisor * context.horizontalTan());
            screenY = -vertical / (divisor * context.verticalTan());
        }

        boolean visible = forward > 0.01 && Math.abs(screenX) <= 1.0 && Math.abs(screenY) <= 1.0;
        if (visible) {
            return screenProjectionScratch.set(true, screenWidth / 2.0 + screenX * screenWidth / 2.0,
                    screenHeight / 2.0 + screenY * screenHeight / 2.0, screenX, screenY);
        }

        if (Math.abs(screenX) < 0.001 && Math.abs(screenY) < 0.001) {
            screenY = 1.0;
        }
        double scale = 1.0 / Math.max(Math.abs(screenX), Math.abs(screenY));
        double edgeX = screenX * scale;
        double edgeY = screenY * scale;
        int margin = context.margin();
        double x = screenWidth / 2.0 + edgeX * Math.max(0.0, screenWidth / 2.0 - margin);
        double y = screenHeight / 2.0 + edgeY * Math.max(0.0, screenHeight / 2.0 - margin);
        double dirX = x - screenWidth / 2.0;
        double dirY = y - screenHeight / 2.0;
        double length = Math.sqrt(dirX * dirX + dirY * dirY);
        if (length < 0.001) {
            dirX = 0.0;
            dirY = 1.0;
        } else {
            dirX /= length;
            dirY /= length;
        }
        return screenProjectionScratch.set(false, x, y, dirX, dirY);
    }

    private static ArrowPlacement stackedArrowPlacement(ScreenProjection projection, List<ArrowPlacement> placements,
                                                        int placementCount, MarkerProjectionContext context,
                                                        float arrowScale) {
        double x = projection.x();
        double y = projection.y();
        int overlap = 0;
        double stackDistanceSquared = Math.pow(ARROW_STACK_DISTANCE * arrowScale, 2.0);
        for (int i = 0; i < placementCount; i++) {
            ArrowPlacement placement = placements.get(i);
            double dx = placement.x() - x;
            double dy = placement.y() - y;
            if (dx * dx + dy * dy < stackDistanceSquared) {
                overlap++;
            }
        }

        if (overlap > 0) {
            double side = overlap % 2 == 0 ? -1.0 : 1.0;
            double offset = side * (ARROW_STACK_OFFSET * arrowScale) * ((overlap + 1) / 2.0);
            x += -projection.dirY() * offset;
            y += projection.dirX() * offset;
        }

        int margin = context.margin();
        x = clampDouble(x, margin, Math.max(margin, context.screenWidth() - margin));
        y = clampDouble(y, margin, Math.max(margin, context.screenHeight() - margin));
        if (placementCount < placements.size()) {
            return placements.get(placementCount).set(x, y, projection.dirX(), projection.dirY());
        }
        ArrowPlacement placement = new ArrowPlacement().set(x, y, projection.dirX(), projection.dirY());
        placements.add(placement);
        return placement;
    }

    private static void drawArrow(DrawContext context, MinecraftClient client, Vec3d markerPos,
                                  int markerColor, int markerIcon, int markerOpacity,
                                  ArrowPlacement placement, Vec3d cameraPos, float scale) {
        int opacity = arrowOpacity(client, cameraPos, markerPos, markerOpacity);
        int color = applyOpacity(markerColor, opacity);
        int fadeColor = applyOpacity(markerColor, Math.max(10, opacity / 2));
        int shadowColor = applyOpacity(0xFF000000, Math.max(18, opacity / 2));
        double headLength = Math.max(10.0, 14.0 * scale);
        double headWidth = Math.max(8.0, 12.0 * scale);
        int tail = Math.max(2, Math.round(3.0f * scale));
        int x = (int) Math.round(placement.x());
        int y = (int) Math.round(placement.y());
        double perpX = -placement.dirY();
        double perpY = placement.dirX();
        double baseX = x - placement.dirX() * headLength;
        double baseY = y - placement.dirY() * headLength;

        drawFilledTriangle(context,
                x + 1.0, y + 1.0,
                baseX + perpX * headWidth * 0.5 + 1.0, baseY + perpY * headWidth * 0.5 + 1.0,
                baseX - perpX * headWidth * 0.5 + 1.0, baseY - perpY * headWidth * 0.5 + 1.0,
                shadowColor);
        drawFilledTriangle(context,
                x, y,
                baseX + perpX * headWidth * 0.5, baseY + perpY * headWidth * 0.5,
                baseX - perpX * headWidth * 0.5, baseY - perpY * headWidth * 0.5,
                color);
        for (int i = 1; i <= 3; i++) {
            int tailX = (int) Math.round(baseX - placement.dirX() * tail * 3.0 * i);
            int tailY = (int) Math.round(baseY - placement.dirY() * tail * 3.0 * i);
            context.fill(tailX - tail / 2, tailY - tail / 2, tailX + tail / 2 + 1, tailY + tail / 2 + 1, fadeColor);
        }

        drawArrowIcon(context, client.textRenderer, iconSymbol(markerIcon),
                (int) Math.round(x - placement.dirX() * headLength * 2.25),
                (int) Math.round(y - placement.dirY() * headLength * 2.25),
                color,
                Math.max(1.0f, 1.35f * scale));
    }

    private static void drawArrowIcon(DrawContext context, TextRenderer renderer, String icon, int centerX, int centerY,
                                      int color, float iconScale) {
        int width = renderer.getWidth(icon);
        int height = renderer.fontHeight;
        int x = (int) Math.round(centerX - width * iconScale / 2.0f);
        int y = (int) Math.round(centerY - height * iconScale / 2.0f);
        x = clamp(x, 2, Math.max(2, context.getScaledWindowWidth() - (int) Math.ceil(width * iconScale) - 2));
        y = clamp(y, 2, Math.max(2, context.getScaledWindowHeight() - (int) Math.ceil(height * iconScale) - 2));

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(iconScale, iconScale);
        context.drawTextWithShadow(renderer, icon, 0, 0, color);
        context.getMatrices().popMatrix();
    }

    private static int arrowOpacity(MinecraftClient client, Vec3d cameraPos, Vec3d markerPos, int markerOpacity) {
        int minOpacity = Math.max(10, Math.min(100, LegionsClient.CONFIG.offscreenPingArrowMinOpacity));
        int maxOpacity = Math.max(minOpacity, Math.min(100, LegionsClient.CONFIG.offscreenPingArrowMaxOpacity));
        int opacity = maxOpacity;
        if (LegionsClient.CONFIG.offscreenPingArrowDistanceFadeEnabled) {
            double distance = cameraPos.distanceTo(markerPos);
            double fadeDistance = Math.max(96.0, client.options == null ? 160.0 : client.options.getViewDistance().getValue() * 16.0);
            double progress = clampDouble(distance / fadeDistance, 0.0, 1.0);
            opacity = (int) Math.round(maxOpacity - (maxOpacity - minOpacity) * progress);
        }
        return Math.max(0, Math.min(100, opacity * markerOpacity / 100));
    }

    private static void drawFilledTriangle(DrawContext context,
                                           double x1, double y1,
                                           double x2, double y2,
                                           double x3, double y3,
                                           int color) {
        int minY = (int) Math.floor(Math.min(y1, Math.min(y2, y3)));
        int maxY = (int) Math.ceil(Math.max(y1, Math.max(y2, y3)));
        double[] intersections = triangleIntersectionScratch;
        for (int y = minY; y <= maxY; y++) {
            double scanY = y + 0.5;
            int count = 0;
            count = addTriangleIntersection(intersections, count, x1, y1, x2, y2, scanY);
            count = addTriangleIntersection(intersections, count, x2, y2, x3, y3, scanY);
            count = addTriangleIntersection(intersections, count, x3, y3, x1, y1, scanY);
            if (count < 2) {
                continue;
            }
            sort(intersections, count);
            int left = (int) Math.floor(intersections[0]);
            int right = (int) Math.ceil(intersections[count - 1]);
            if (right >= left) {
                context.fill(left, y, right + 1, y + 1, color);
            }
        }
    }

    private static int addTriangleIntersection(double[] intersections, int count,
                                               double x1, double y1,
                                               double x2, double y2,
                                               double scanY) {
        double minY = Math.min(y1, y2);
        double maxY = Math.max(y1, y2);
        if (scanY < minY || scanY >= maxY || y1 == y2) {
            return count;
        }
        double progress = (scanY - y1) / (y2 - y1);
        intersections[count] = x1 + (x2 - x1) * progress;
        return count + 1;
    }

    private static void sort(double[] values, int count) {
        for (int i = 1; i < count; i++) {
            double value = values[i];
            int j = i - 1;
            while (j >= 0 && values[j] > value) {
                values[j + 1] = values[j];
                j--;
            }
            values[j + 1] = value;
        }
    }

    private static float arrowScale() {
        return Math.max(50, Math.min(200, LegionsClient.CONFIG.offscreenPingArrowScale)) / 100.0f
                * LegionsClient.uiScaleFactor();
    }

    private static int applyOpacity(int color, int opacityPercent) {
        int alpha = Math.max(0, Math.min(100, opacityPercent)) * 255 / 100;
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void updateTeamFightMarkers(MinecraftClient client, long now) {
        if (!canUseTeamFightDetector(client)) {
            fightMarkers.clear();
            recycleFightScratch();
            lastFightDetectionAt = 0L;
            return;
        }

        long refreshMillis = FIGHT_REFRESH_TICKS * 50L;
        if (lastFightDetectionAt > 0L && now - lastFightDetectionAt < refreshMillis) {
            return;
        }
        lastFightDetectionAt = now;

        collectFightCandidates(client);
        refreshFightMarkers(client, now);
    }

    private static boolean canUseTeamFightDetector(MinecraftClient client) {
        return LegionsClient.enabled(client)
                && LegionsClient.CONFIG.teamFightDetectorEnabled
                && client.world != null
                && client.player != null
                && (!LegionsClient.CONFIG.teamFightDetectorSpectatorOnly || LegionsFeatures.isSpectatorTeam(client.player));
    }

    private static void collectFightCandidates(MinecraftClient client) {
        recycleFightScratch();
        Entity camera = client.getCameraEntity() == null ? client.player : client.getCameraEntity();
        Vec3d cameraPos = camera.getCameraPosVec(1.0f);

        for (PlayerEntity player : client.world.getPlayers()) {
            PlayerFightNode node = fightNode(client, player, camera, cameraPos);
            if (node == null) {
                continue;
            }
            fightPlayerScratch.add(node);
        }

        int count = fightPlayerScratch.size();
        if (count < FIGHT_MIN_PLAYERS) {
            return;
        }

        ensureFightClusterCapacity(count);
        fightClusterSignatureCount = 0;
        double radiusSquared = (double) FIGHT_DETECTION_RADIUS * FIGHT_DETECTION_RADIUS;
        for (int i = 0; i < count; i++) {
            int clusterSize = 0;
            Vec3d focus = fightPlayerScratch.get(i).pos();
            for (int otherIndex = 0; otherIndex < count; otherIndex++) {
                if (focus.squaredDistanceTo(fightPlayerScratch.get(otherIndex).pos()) <= radiusSquared) {
                    fightClusterScratch[clusterSize++] = otherIndex;
                }
            }
            if (clusterSize >= FIGHT_MIN_PLAYERS && rememberUniqueFightCluster(fightClusterScratch, clusterSize)) {
                maybeAddFightCandidate(client, fightClusterScratch, clusterSize, cameraPos);
            }
        }

        fightCandidateScratch.sort((first, second) -> {
            int players = Integer.compare(second.players(), first.players());
            if (players != 0) {
                return players;
            }
            int spread = Double.compare(first.spreadSquared(), second.spreadSquared());
            if (spread != 0) {
                return spread;
            }
            int teams = Integer.compare(second.teams(), first.teams());
            if (teams != 0) {
                return teams;
            }
            return Double.compare(first.distanceSquared(), second.distanceSquared());
        });
    }

    private static void recycleFightScratch() {
        fightPlayerPool.addAll(fightPlayerScratch);
        fightPlayerScratch.clear();
        fightCandidatePool.addAll(fightCandidateScratch);
        fightCandidateScratch.clear();
    }

    private static void ensureFightClusterCapacity(int capacity) {
        if (fightClusterScratch.length < capacity) {
            fightClusterScratch = new int[capacity];
        }
    }

    private static boolean rememberUniqueFightCluster(int[] cluster, int clusterSize) {
        for (int signatureIndex = 0; signatureIndex < fightClusterSignatureCount; signatureIndex++) {
            int[] signature = fightClusterSignatures.get(signatureIndex);
            if (signature.length != clusterSize) {
                continue;
            }
            int clusterIndex = 0;
            while (clusterIndex < clusterSize && signature[clusterIndex] == cluster[clusterIndex]) {
                clusterIndex++;
            }
            if (clusterIndex == clusterSize) {
                return false;
            }
        }

        int[] signature;
        if (fightClusterSignatureCount < fightClusterSignatures.size()
                && fightClusterSignatures.get(fightClusterSignatureCount).length == clusterSize) {
            signature = fightClusterSignatures.get(fightClusterSignatureCount);
        } else {
            signature = new int[clusterSize];
            if (fightClusterSignatureCount < fightClusterSignatures.size()) {
                fightClusterSignatures.set(fightClusterSignatureCount, signature);
            } else {
                fightClusterSignatures.add(signature);
            }
        }
        System.arraycopy(cluster, 0, signature, 0, clusterSize);
        fightClusterSignatureCount++;
        return true;
    }

    private static PlayerFightNode fightNode(MinecraftClient client, PlayerEntity player, Entity camera, Vec3d cameraPos) {
        if (player == null
                || !player.isAlive()
                || player.isSpectator()
                || LegionsFeatures.isSpectatorTeam(player)) {
            return null;
        }
        if (!hasPlayerLineOfSight(client, cameraPos, player, camera)) {
            return null;
        }

        Team team = player.getScoreboardTeam();
        if (team == null || team.getName() == null || team.getName().isBlank()) {
            return null;
        }

        Vec3d pos = player.getBoundingBox().getCenter();
        PlayerFightNode node = fightPlayerPool.isEmpty() ? new PlayerFightNode() : fightPlayerPool.removeLast();
        return node.set(team.getName(), pos);
    }

    private static void maybeAddFightCandidate(MinecraftClient client, int[] cluster, int clusterSize,
                                               Vec3d cameraPos) {
        if (clusterSize < FIGHT_MIN_PLAYERS) {
            return;
        }

        fightTeamScratch.clear();
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int clusterIndex = 0; clusterIndex < clusterSize; clusterIndex++) {
            PlayerFightNode node = fightPlayerScratch.get(cluster[clusterIndex]);
            fightTeamScratch.add(node.team());
            x += node.pos().x;
            y += node.pos().y;
            z += node.pos().z;
            minY = Math.min(minY, node.pos().y);
            maxY = Math.max(maxY, node.pos().y);
        }

        if (fightTeamScratch.size() < FIGHT_MIN_TEAMS) {
            return;
        }

        double averageY = y / clusterSize;
        Vec3d rawCenter = new Vec3d(x / clusterSize, averageY, z / clusterSize);
        Vec3d center = groundedFightPosition(client,
                rawCenter,
                minY,
                maxY,
                cluster,
                clusterSize);
        if (center == null) {
            return;
        }
        double spreadSquared = 0.0;
        for (int clusterIndex = 0; clusterIndex < clusterSize; clusterIndex++) {
            spreadSquared += fightPlayerScratch.get(cluster[clusterIndex]).pos().squaredDistanceTo(rawCenter);
        }
        spreadSquared /= clusterSize;
        double distanceSquared = cameraPos.squaredDistanceTo(center);
        FightCandidate candidate = fightCandidatePool.isEmpty()
                ? new FightCandidate()
                : fightCandidatePool.removeLast();
        fightCandidateScratch.add(candidate.set(center, averageY, clusterSize, fightTeamScratch.size(),
                spreadSquared, distanceSquared));
    }

    private static void refreshFightMarkers(MinecraftClient client, long now) {
        FightMark next = null;
        if (!fightCandidateScratch.isEmpty()) {
            FightCandidate candidate = fightCandidateScratch.getFirst();
            Vec3d target = candidate.center();
            next = fightMarkers.isEmpty()
                    ? new FightMark(target, target, candidate.averageY(), now, 0L, now, 0L, candidate.players(), candidate.teams())
                    : updatedFightMarker(client, fightMarkers.getFirst(), candidate, now);
        } else if (!fightMarkers.isEmpty()) {
            FightMark old = fightMarkers.getFirst();
            long fadeStartedAt = old.fadeStartedAt() > 0L ? old.fadeStartedAt() : now;
            Vec3d current = fightMarkerPosition(client, old, now);
            FightMark fading = new FightMark(current, current, old.averageY(),
                    old.markedAt(), fadeStartedAt, now, 0L, old.players(), old.teams());
            if (fightMarkerOpacity(fading, now) > 0) {
                next = fading;
            }
        }

        fightMarkers.clear();
        if (next != null) {
            fightMarkers.add(next);
        }
    }

    private static FightMark updatedFightMarker(MinecraftClient client, FightMark old, FightCandidate candidate, long now) {
        Vec3d target = candidate.center();
        if (!LegionsClient.CONFIG.teamFightSmoothingEnabled) {
            return new FightMark(target, target, candidate.averageY(),
                    now, 0L, now, 0L, candidate.players(), candidate.teams());
        }

        if (old.targetPos().squaredDistanceTo(target) < 0.04) {
            return new FightMark(old.fromPos(), old.targetPos(), candidate.averageY(),
                    now, 0L, old.moveStartedAt(), old.moveDurationMillis(), candidate.players(), candidate.teams());
        }

        Vec3d current = fightMarkerPosition(client, old, now);
        return new FightMark(current, target, candidate.averageY(),
                now, 0L, now, fightMarkerMoveMillis(), candidate.players(), candidate.teams());
    }

    private static Vec3d fightMarkerPosition(MinecraftClient client, FightMark mark, long now) {
        Vec3d raw = mark.rawPos(now);
        Vec3d grounded = groundedFightPosition(client, raw,
                mark.averageY() - FIGHT_MARKER_SURFACE_SEARCH_RADIUS,
                mark.averageY() + 0.5);
        return grounded == null ? mark.targetPos() : grounded;
    }

    private static long fightMarkerMoveMillis() {
        if (!LegionsClient.CONFIG.teamFightSmoothingEnabled) {
            return 0L;
        }
        int strength = Math.max(5, Math.min(100, LegionsClient.CONFIG.teamFightSmoothingStrength));
        double speed = strength / 100.0;
        return Math.round(FIGHT_MARKER_MAX_MOVE_MILLIS
                - (FIGHT_MARKER_MAX_MOVE_MILLIS - FIGHT_MARKER_MIN_MOVE_MILLIS) * speed);
    }

    private static Vec3d groundedFightPosition(MinecraftClient client, Vec3d pos) {
        return groundedFightPosition(client, pos, pos.y - FIGHT_MARKER_SURFACE_SEARCH_RADIUS,
                pos.y + 0.5);
    }

    private static Vec3d groundedFightPosition(MinecraftClient client, Vec3d pos, double minY, double maxY,
                                               int[] cluster, int clusterSize) {
        Vec3d centerSurface = groundedFightPosition(client, pos, minY - FIGHT_MARKER_SURFACE_SEARCH_RADIUS, maxY + 0.5);
        if (centerSurface != null) {
            return centerSurface;
        }

        Vec3d nearbySurface = nearestNearbyFightSurface(client, pos, minY - FIGHT_MARKER_SURFACE_SEARCH_RADIUS,
                maxY + 0.5, cluster, clusterSize);
        if (nearbySurface != null) {
            return nearbySurface;
        }
        return null;
    }

    private static Vec3d groundedFightPosition(MinecraftClient client, Vec3d pos, double minY, double maxY) {
        if (client.world == null || client.player == null) {
            return null;
        }

        double startY = maxY;
        double endY = minY;
        BlockHitResult hit = client.world.raycast(new RaycastContext(
                new Vec3d(pos.x, startY, pos.z),
                new Vec3d(pos.x, endY, pos.z),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                client.player
        ));
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos blockPos = hit.getBlockPos();
            if (isFightSupportBlock(client, blockPos)) {
                return new Vec3d(hit.getPos().x, hit.getPos().y + FIGHT_MARKER_SURFACE_OFFSET, hit.getPos().z);
            }
        }
        return null;
    }

    private static Vec3d nearestNearbyFightSurface(MinecraftClient client, Vec3d center, double minSurfaceY,
                                                   double maxSurfaceY, int[] cluster, int clusterSize) {
        if (client.world == null) {
            return null;
        }

        Vec3d bestSurface = null;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;
        int radius = FIGHT_MARKER_SURFACE_SEARCH_RADIUS;
        int bottomY = client.world.getBottomY();
        int topY = client.world.getTopYInclusive();
        int minY = Math.max(bottomY, (int) Math.floor(minSurfaceY));
        int maxY = Math.min(topY, (int) Math.floor(maxSurfaceY));
        int radiusSquared = radius * radius;
        fightSurfaceVisitedScratch.clear();

        for (int clusterIndex = 0; clusterIndex < clusterSize; clusterIndex++) {
            Vec3d playerPos = fightPlayerScratch.get(cluster[clusterIndex]).pos();
            int minX = (int) Math.floor(playerPos.x - radius);
            int maxX = (int) Math.floor(playerPos.x + radius);
            int minZ = (int) Math.floor(playerPos.z - radius);
            int maxZ = (int) Math.floor(playerPos.z + radius);

            for (int y = maxY; y >= minY; y--) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (blockHorizontalDistanceSquared(playerPos, x, z) > radiusSquared) {
                            continue;
                        }
                        if (!fightSurfaceVisitedScratch.add(BlockPos.asLong(x, y, z))) {
                            continue;
                        }

                        fightBlockPosScratch.set(x, y, z);
                        BlockState state = client.world.getBlockState(fightBlockPosScratch);
                        Vec3d surface = fightSurfacePosition(client, fightBlockPosScratch, state, center);
                        if (surface == null
                                || surface.y < minSurfaceY - 1.0e-4
                                || surface.y > maxSurfaceY + 1.0e-4) {
                            continue;
                        }

                        double distanceSquared = horizontalDistanceSquared(center, surface);
                        if (distanceSquared < bestDistanceSquared) {
                            bestSurface = surface;
                            bestDistanceSquared = distanceSquared;
                        }
                    }
                }
            }
        }

        return bestSurface;
    }

    private static boolean isFightSupportBlock(MinecraftClient client, BlockPos pos) {
        return fightSurfacePosition(client, pos, client.world.getBlockState(pos), Vec3d.ofCenter(pos)) != null;
    }

    private static Vec3d fightSurfacePosition(MinecraftClient client, BlockPos pos, BlockState state, Vec3d target) {
        if (state.isAir()) {
            return null;
        }

        VoxelShape shape = state.getCollisionShape(client.world, pos, ShapeContext.absent());
        if (shape.isEmpty()) {
            return null;
        }

        fightSurfaceBoxConsumer.reset(target.x - pos.getX(), target.z - pos.getZ());
        shape.forEachBox(fightSurfaceBoxConsumer);
        if (!Double.isFinite(fightSurfaceBoxConsumer.bestY)) {
            return null;
        }
        return new Vec3d(pos.getX() + fightSurfaceBoxConsumer.bestX,
                pos.getY() + fightSurfaceBoxConsumer.bestY + FIGHT_MARKER_SURFACE_OFFSET,
                pos.getZ() + fightSurfaceBoxConsumer.bestZ);
    }

    private static double blockHorizontalDistanceSquared(Vec3d pos, int blockX, int blockZ) {
        double x = clampDouble(pos.x, blockX, blockX + 1.0);
        double z = clampDouble(pos.z, blockZ, blockZ + 1.0);
        double dx = pos.x - x;
        double dz = pos.z - z;
        return dx * dx + dz * dz;
    }

    private static double horizontalDistanceSquared(Vec3d first, Vec3d second) {
        return square(first.x - second.x) + square(first.z - second.z);
    }

    private static double square(double value) {
        return value * value;
    }

    private static Vec3d lerp(Vec3d from, Vec3d to, double progress) {
        return new Vec3d(
                from.x + (to.x - from.x) * progress,
                from.y + (to.y - from.y) * progress,
                from.z + (to.z - from.z) * progress
        );
    }

    private static void pollConfiguredKeys(MinecraftClient client, List<PingRow> rows, long now) {
        configuredKeyScratch.clear();
        for (PingRow row : rows) {
            PingKey key = cachedPingKey(row.keyType, row.keyCode);
            configuredKeyScratch.add(key);
            boolean down = isKeyDown(client, key);
            boolean wasDown = keyDownStates.getOrDefault(key, false);
            if (down && !wasDown) {
                recordPress(client, rows, key, now);
            }
            keyDownStates.put(key, down);
        }
        keyDownStates.keySet().removeIf(key -> !configuredKeyScratch.contains(key));
        configuredKeyScratch.clear();
    }

    private static PingKey cachedPingKey(int keyType, int keyCode) {
        if (keyType != PingRow.KEY_TYPE_KEYBOARD && keyType != PingRow.KEY_TYPE_MOUSE) {
            return new PingKey(keyType, keyCode);
        }
        PingKey[] keys = keyType == PingRow.KEY_TYPE_MOUSE ? mousePingKeys : keyboardPingKeys;
        if (keyCode < 0 || keyCode >= keys.length) {
            return new PingKey(keyType, keyCode);
        }
        PingKey key = keys[keyCode];
        if (key == null) {
            key = new PingKey(keyType, keyCode);
            keys[keyCode] = key;
        }
        return key;
    }

    private static void recordPress(MinecraftClient client, List<PingRow> rows, PingKey key, long now) {
        boolean continuingPress = pendingPress != null
                && pendingPress.key().equals(key)
                && now - pendingPress.pressedAt() <= PRESS_WINDOW_MILLIS;
        int presses = continuingPress ? pendingPress.presses() + 1 : 1;
        AimSnapshot aim = continuingPress ? pendingPress.aim() : captureAim(client);
        pendingPress = new PendingPress(key, presses, now, aim);

        PingRow row = findRow(rows, key, presses);
        if (row == null && !hasHigherPressRow(rows, key, presses)) {
            pendingPress = null;
            return;
        }

        if (row != null && !hasHigherPressRow(rows, key, presses)) {
            PendingPress press = pendingPress;
            pendingPress = null;
            dispatchRow(client, row, press.aim());
        }
    }

    private static void flushPendingPress(MinecraftClient client, List<PingRow> rows, long now) {
        if (pendingPress == null || now - pendingPress.pressedAt() < PRESS_WINDOW_MILLIS) {
            return;
        }

        PendingPress press = pendingPress;
        pendingPress = null;
        PingRow row = findRow(rows, press.key(), press.presses());
        if (row != null) {
            dispatchRow(client, row, press.aim());
        }
    }

    private static void dispatchRow(MinecraftClient client, PingRow row, AimSnapshot aim) {
        PingTarget target = resolveTarget(client, row, aim);
        if (target == null) {
            return;
        }

        int color = parseColor(row.color);
        if (target.blockPos() != null) {
            pingBlock(client, row, target.blockPos(), color);
        } else if (target.player() != null) {
            pingPlayer(client, row, target.player(), color);
        }
    }

    private static PingTarget resolveTarget(MinecraftClient client, PingRow row, AimSnapshot aim) {
        if (row.targetType == PingRow.TARGET_TYPE_BLOCKS_ONLY) {
            BlockPos pos = aim == null ? null : aim.blockPos();
            if (pos == null) {
                showFeedback(client, "No block targeted");
                return null;
            }
            return PingTarget.block(pos);
        }

        PlayerEntity target = switch (row.targetSource) {
            case PingRow.TARGET_SOURCE_LAST_ATTACKER -> recentPlayer(client, lastAttackerPlayerUuid, lastAttackerPlayerName, lastAttackerPlayerAt);
            case PingRow.TARGET_SOURCE_LAST_ATTACKED -> recentPlayer(client, lastAttackedPlayerUuid, lastAttackedPlayerName, lastAttackedPlayerAt);
            case PingRow.TARGET_SOURCE_SELF -> client.player;
            default -> raycastPlayer(client, row, aim);
        };
        if (target == null || !isAllowedPlayerTarget(client, row, target)) {
            showFeedback(client, "No matching player target");
            return null;
        }
        return PingTarget.player(target);
    }

    private static void pingPlayer(MinecraftClient client, PingRow row, PlayerEntity target, int color) {
        String sender = LegionsFeatures.realUsername(client.player);
        String targetName = LegionsFeatures.realUsername(target);
        String visibleMessage = formatPlayerMessage(client, row, target, sender, targetName);
        int icon = rowIcon(client, row, target);
        String label = shortRowLabel(client, row, target, icon);
        sendChat(client, visibleMessage + " [LC:P:" + sender + ":" + targetName
                + ":color=" + hexColor(color) + ":audience=" + audienceName(row.visualAudience)
                + ":icon=" + iconName(icon) + ":label=" + payloadLabel(label) + "]");
        markPlayer(targetName, color, icon, label);
    }

    private static void pingBlock(MinecraftClient client, PingRow row, BlockPos pos, int color) {
        String sender = LegionsFeatures.realUsername(client.player);
        String visibleMessage = formatBlockMessage(row.message, sender, pos);
        int icon = rowIcon(client, row, null);
        String label = shortRowLabel(client, row, null, icon);
        sendChat(client, visibleMessage + " [LC:B:" + sender + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ()
                + ":color=" + hexColor(color) + ":audience=" + audienceName(row.visualAudience)
                + ":icon=" + iconName(icon) + ":label=" + payloadLabel(label) + "]");
        markBlock(pos, color, icon, label);
    }

    private static String formatPlayerMessage(MinecraftClient client, PingRow row, PlayerEntity target, String sender, String targetName) {
        String template;
        if (row.targetType == PingRow.TARGET_TYPE_ALL_PLAYERS_DIFFERENT_MESSAGE) {
            template = LegionsFeatures.isTeammate(client.player, target) ? row.teammateMessage : row.enemyMessage;
        } else {
            template = row.message;
        }
        return formatPlayerMessage(template, sender, targetName);
    }

    private static String formatPlayerMessage(String template, String sender, String targetName) {
        return cleanVisibleMessage(template)
                .replace("{sender}", sender)
                .replace("{SENDER}", sender)
                .replace("{player}", targetName)
                .replace("{PLAYER}", targetName);
    }

    private static String formatBlockMessage(String template, String sender, BlockPos pos) {
        return cleanVisibleMessage(template)
                .replace("{sender}", sender)
                .replace("{SENDER}", sender)
                .replace("{x}", Integer.toString(pos.getX()))
                .replace("{X}", Integer.toString(pos.getX()))
                .replace("{y}", Integer.toString(pos.getY()))
                .replace("{Y}", Integer.toString(pos.getY()))
                .replace("{z}", Integer.toString(pos.getZ()))
                .replace("{Z}", Integer.toString(pos.getZ()));
    }

    private static int rowIcon(MinecraftClient client, PingRow row, PlayerEntity target) {
        if (row.targetType == PingRow.TARGET_TYPE_ALL_PLAYERS_DIFFERENT_MESSAGE) {
            return target != null && LegionsFeatures.isTeammate(client.player, target)
                    ? row.teammateMessageIcon
                    : row.enemyMessageIcon;
        }
        return row.messageIcon;
    }

    private static String shortRowLabel(MinecraftClient client, PingRow row, PlayerEntity target, int icon) {
        if (row.targetType == PingRow.TARGET_TYPE_BLOCKS_ONLY) {
            return "Go";
        }
        if (row.targetType == PingRow.TARGET_TYPE_ALL_PLAYERS_DIFFERENT_MESSAGE
                && target != null
                && LegionsFeatures.isTeammate(client.player, target)) {
            return "Help";
        }
        return defaultIconLabel(icon);
    }

    private static String cleanVisibleMessage(String template) {
        String message = template == null || template.isBlank() ? "Ping" : template.trim();
        return message.length() > 190 ? message.substring(0, 190) : message;
    }

    private static String payloadLabel(String label) {
        return cleanPayloadLabel(label).replace(' ', '_');
    }

    private static String cleanPayloadLabel(String label) {
        return cleanMarkerLabel(label == null ? "" : label.replace('_', ' '), "");
    }

    private static String cleanMarkerLabel(String label, String fallback) {
        String cleaned = label == null ? "" : label.trim().replace('[', ' ').replace(']', ' ').replace(':', ' ');
        cleaned = WHITESPACE_PATTERN.matcher(cleaned).replaceAll(" ").trim();
        if (cleaned.isBlank()) {
            cleaned = fallback == null || fallback.isBlank() ? "Ping" : fallback;
        }
        return cleaned.length() <= PING_LABEL_MAX_LENGTH ? cleaned : cleaned.substring(0, PING_LABEL_MAX_LENGTH);
    }

    private static ReceivedPing parseReceivedPing(String type, String body) {
        String[] parts = body.split(":");
        if ("P".equalsIgnoreCase(type)) {
            if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
                return null;
            }
            PayloadOptions options = parsePayloadOptions(parts, 2, PingRow.ICON_STAR);
            return ReceivedPing.player(parts[0], parts[1], options.color(), options.audience(), options.icon(), options.label());
        }

        if (parts.length < 4 || parts[0].isBlank()) {
            return null;
        }
        try {
            PayloadOptions options = parsePayloadOptions(parts, 4, PingRow.ICON_PICKAXE);
            return ReceivedPing.block(parts[0], new BlockPos(
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])
            ), options.color(), options.audience(), options.icon(), options.label());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static PayloadOptions parsePayloadOptions(String[] parts, int start, int defaultIcon) {
        int color = DEFAULT_PING_COLOR;
        int audience = PingRow.VISUAL_AUDIENCE_TEAMMATES;
        int icon = defaultIcon;
        String label = "";
        for (int i = start; i < parts.length; i++) {
            String part = parts[i];
            int equals = part.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = part.substring(0, equals).toLowerCase(Locale.ROOT);
            String value = part.substring(equals + 1);
            if ("color".equals(key)) {
                color = parseColor(value);
            } else if ("audience".equals(key)) {
                audience = parseAudience(value);
            } else if ("icon".equals(key)) {
                icon = parseIcon(value, icon);
            } else if ("label".equals(key)) {
                label = cleanPayloadLabel(value);
            }
        }
        return new PayloadOptions(color, audience, icon, label);
    }

    private static boolean canAcceptFrom(MinecraftClient client, GameProfile signedSender, String embeddedSender, int audience) {
        if (client.player == null) {
            return false;
        }
        String senderName = signedSender != null ? signedSender.name() : embeddedSender;
        if (senderName == null || senderName.equalsIgnoreCase(LegionsFeatures.realUsername(client.player))) {
            return true;
        }
        if (LegionsFeatures.isSpectatorTeam(client.player)) {
            return true;
        }
        if (audience == PingRow.VISUAL_AUDIENCE_EVERYONE) {
            return true;
        }

        PlayerEntity sender = findPlayer(client, senderName);
        if (sender == null || LegionsFeatures.isSpectatorTeam(sender)) {
            return false;
        }
        return audience == PingRow.VISUAL_AUDIENCE_OPPONENTS
                ? LegionsFeatures.isOpponent(sender, client.player)
                : LegionsFeatures.isTeammate(sender, client.player);
    }

    private static Text stripMachinePayload(Text message) {
        String raw = message.getString();
        List<Range> ranges = machinePayloadRanges(raw);
        if (ranges.isEmpty()) {
            return message.copy();
        }

        MutableText clean = Text.empty();
        int[] cursor = new int[]{0};
        message.visit((style, text) -> {
            appendCleanSegment(clean, text, style, cursor[0], ranges);
            cursor[0] += text.length();
            return Optional.empty();
        }, Style.EMPTY);
        return clean;
    }

    private static void appendCleanSegment(MutableText clean, String text, Style style, int segmentStart, List<Range> ranges) {
        int segmentEnd = segmentStart + text.length();
        int cursor = 0;
        for (Range range : ranges) {
            if (range.end <= segmentStart) {
                continue;
            }
            if (range.start >= segmentEnd) {
                break;
            }

            int localStart = Math.max(0, range.start - segmentStart);
            int localEnd = Math.min(text.length(), range.end - segmentStart);
            if (localStart > cursor) {
                clean.append(Text.literal(text.substring(cursor, localStart)).setStyle(style));
            }
            cursor = Math.max(cursor, localEnd);
        }
        if (cursor < text.length()) {
            clean.append(Text.literal(text.substring(cursor)).setStyle(style));
        }
    }

    private static List<Range> machinePayloadRanges(String raw) {
        ArrayList<Range> ranges = new ArrayList<>();
        Matcher matcher = MACHINE_PAYLOAD_PATTERN.matcher(raw);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            if (start > 0 && Character.isWhitespace(raw.charAt(start - 1))) {
                start--;
            }
            while (end < raw.length() && Character.isWhitespace(raw.charAt(end))) {
                end++;
            }
            ranges.add(new Range(start, end));
        }
        return mergeRanges(ranges);
    }

    private static List<Range> mergeRanges(List<Range> ranges) {
        if (ranges.size() < 2) {
            return ranges;
        }

        Range current = ranges.getFirst();
        int writeIndex = 0;
        for (int i = 1; i < ranges.size(); i++) {
            Range next = ranges.get(i);
            if (next.start <= current.end) {
                current = new Range(current.start, Math.max(current.end, next.end));
            } else {
                ranges.set(writeIndex++, current);
                current = next;
            }
        }
        ranges.set(writeIndex++, current);
        ranges.subList(writeIndex, ranges.size()).clear();
        return ranges;
    }

    private static void renderBlockPingOutline(MinecraftClient client, BlockPos pos, int color) {
        Vec3d center = Vec3d.ofCenter(pos);
        renderBlockShapeOutline(client, pos, color);
        float scale = LegionsClient.uiScaleFactor();
        if (LegionsClient.CONFIG.blockPingDistanceLabelEnabled) {
            GizmoDrawing.blockLabel(bracketDistanceLabel(client, center), pos, 0, color, BLOCK_PING_LABEL_SCALE * scale);
        }

        Entity camera = client.getCameraEntity();
        if (camera != null && camera.squaredDistanceTo(center) > BLOCK_PING_FAR_MARKER_DISTANCE_SQUARED) {
            GizmoDrawing.point(center, color, BLOCK_PING_FAR_MARKER_SIZE * scale);
        }
    }

    private static void renderFightMarker(MinecraftClient client, FightMark mark, int color, int opacity, long now) {
        Vec3d center = fightMarkerPosition(client, mark, now);
        if (!isBlockMarkerVisibleToCamera(client, center)) {
            return;
        }
        int fadedColor = applyOpacity(color, opacity);
        float scale = LegionsClient.uiScaleFactor();
        GizmoDrawing.point(center, fadedColor, FIGHT_MARKER_SIZE * scale);
        GizmoDrawing.circle(center, Math.max(1.0f, Math.max(2.0f, FIGHT_DETECTION_RADIUS / 6.0f) * scale),
                blockPingDrawStyle(fadedColor));
        if (LegionsClient.CONFIG.blockPingDistanceLabelEnabled) {
            String label = "Team fight [" + distanceLabel(client, center) + "]";
            GizmoDrawing.blockLabel(label, BlockPos.ofFloored(center), 0, fadedColor, BLOCK_PING_LABEL_SCALE * scale);
        }
    }

    private static void renderBlockShapeOutline(MinecraftClient client, BlockPos pos, int color) {
        if (client.world == null) {
            return;
        }

        BlockState state = client.world.getBlockState(pos);
        VoxelShape shape = state.getOutlineShape(client.world, pos, ShapeContext.absent());
        if (shape.isEmpty()) {
            renderBox(pos, color);
            return;
        }

        blockOutlineBoxConsumer.reset(pos, blockPingDrawStyle(color));
        shape.forEachBox(blockOutlineBoxConsumer);
    }

    private static void renderBox(BlockPos pos, int color) {
        GizmoDrawing.box(pos, BLOCK_PING_EXPAND, blockPingDrawStyle(color));
    }

    private static void renderBox(Box box, DrawStyle style) {
        GizmoDrawing.box(box.expand(BLOCK_PING_EXPAND), style);
    }

    private static DrawStyle blockPingDrawStyle(int color) {
        return DrawStyle.filledAndStroked(color, Math.max(0.75f, BLOCK_PING_STROKE_WIDTH * LegionsClient.uiScaleFactor()),
                (color & 0x00FFFFFF) | 0x35000000);
    }

    private static String bracketDistanceLabel(MinecraftClient client, Vec3d pos) {
        return "[" + distanceLabel(client, pos) + "]";
    }

    private static String distanceLabel(MinecraftClient client, Vec3d pos) {
        Entity camera = client.getCameraEntity() == null ? client.player : client.getCameraEntity();
        if (camera == null || pos == null) {
            return "?m";
        }
        return Math.round(camera.getCameraPosVec(1.0f).distanceTo(pos)) + "m";
    }

    private static void markBlock(BlockPos pos, int color, int icon, String label) {
        markedBlocks.put(pos.toImmutable(), new PingMark(System.currentTimeMillis(), opaque(color), normalizeIcon(icon), cleanMarkerLabel(label, "Go")));
    }

    private static void markPlayer(String name, int color, int icon, String label) {
        if (name != null && !name.isBlank()) {
            markedPlayers.put(name.toLowerCase(Locale.ROOT), new PingMark(System.currentTimeMillis(), opaque(color), normalizeIcon(icon), cleanMarkerLabel(label, defaultIconLabel(icon))));
            markedPlayersVersion++;
        }
    }

    private static List<PingRow> configuredRows() {
        if (LegionsClient.CONFIG == null) {
            return LegionsConfig.defaultPingRows();
        }
        List<PingRow> rows = LegionsClient.CONFIG.pingRows;
        return rows == null || rows.isEmpty() ? LegionsConfig.defaultPingRows() : rows;
    }

    private static PingRow findRow(List<PingRow> rows, PingKey key, int presses) {
        for (PingRow row : rows) {
            if (matches(row, key) && row.presses == presses) {
                return row;
            }
        }
        return null;
    }

    private static boolean hasHigherPressRow(List<PingRow> rows, PingKey key, int presses) {
        for (PingRow row : rows) {
            if (matches(row, key) && row.presses > presses) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(PingRow row, PingKey key) {
        return row.keyType == key.keyType() && row.keyCode == key.keyCode();
    }

    private static boolean isKeyDown(MinecraftClient client, PingKey key) {
        if (client == null || client.getWindow() == null) {
            return false;
        }
        if (key.keyType() == PingRow.KEY_TYPE_MOUSE) {
            return GLFW.glfwGetMouseButton(client.getWindow().getHandle(), key.keyCode()) == GLFW.GLFW_PRESS;
        }
        return InputUtil.isKeyPressed(client.getWindow(), key.keyCode());
    }

    private static void clearInputState() {
        keyDownStates.clear();
        pendingPress = null;
    }

    private static void clearPingMarks() {
        markedBlocks.clear();
        clearMarkedPlayers();
    }

    private static void clearMarkedPlayers() {
        if (!markedPlayers.isEmpty()) {
            markedPlayers.clear();
            markedPlayersVersion++;
        }
    }

    private static long pingTtlMillis() {
        return Math.max(1, Math.min(25, LegionsClient.CONFIG.pingDurationSeconds)) * 1000L;
    }

    private static int fightMarkerOpacity(FightMark mark, long now) {
        if (mark.fadeStartedAt() <= 0L) {
            return 100;
        }
        long fadeMillis = Math.max(1, LegionsClient.CONFIG.teamFightFadeOutSeconds) * 1000L;
        double progress = clampDouble((now - mark.fadeStartedAt()) / (double) fadeMillis, 0.0, 1.0);
        return (int) Math.round(100.0 * (1.0 - progress));
    }

    private static long recentTargetTtlMillis() {
        return Math.max(1, Math.min(60, LegionsClient.CONFIG.pingRecentTargetTimeoutSeconds)) * 1000L;
    }

    private static boolean canUsePing(MinecraftClient client) {
        return LegionsClient.enabled(client)
                && teamPingEnabled(client)
                && client.currentScreen == null
                && client.player != null
                && client.world != null;
    }

    private static boolean teamPingEnabled(MinecraftClient client) {
        return client != null && LegionsClient.CONFIG != null && LegionsClient.CONFIG.teamPingEnabled;
    }

    private static PlayerEntity recentPlayer(MinecraftClient client, UUID uuid, String name, long markedAt) {
        if (client.world == null || name == null || System.currentTimeMillis() - markedAt > recentTargetTtlMillis()) {
            return null;
        }
        for (PlayerEntity player : client.world.getPlayers()) {
            if (uuid != null && uuid.equals(player.getUuid()) && isRecentTargetCandidate(client, player)) {
                return player;
            }
        }

        PlayerEntity player = findPlayer(client, name);
        return isRecentTargetCandidate(client, player) ? player : null;
    }

    private static boolean isRecentTargetCandidate(MinecraftClient client, PlayerEntity player) {
        return player != null
                && client.player != null
                && !player.getUuid().equals(client.player.getUuid())
                && player.isAlive()
                && !player.isSpectator();
    }

    private static void clearRecentTargets() {
        lastAttackedPlayerUuid = null;
        lastAttackedPlayerName = null;
        lastAttackedPlayerAt = 0L;
        lastAttackerPlayerUuid = null;
        lastAttackerPlayerName = null;
        lastAttackerPlayerAt = 0L;
    }

    private static PlayerEntity findPlayer(MinecraftClient client, String name) {
        if (client.world == null || name == null) {
            return null;
        }
        for (PlayerEntity player : client.world.getPlayers()) {
            if (LegionsFeatures.realUsername(player).equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    private static PlayerEntity findPlayer(MinecraftClient client, UUID uuid, String name) {
        if (client.world == null) {
            return null;
        }
        for (PlayerEntity player : client.world.getPlayers()) {
            if (uuid != null && uuid.equals(player.getUuid())) {
                return player;
            }
        }
        return findPlayer(client, name);
    }

    private static BlockHitResult raycastBlock(MinecraftClient client) {
        Entity camera = client.getCameraEntity();
        if (camera == null || client.world == null) {
            return null;
        }
        Vec3d start = camera.getCameraPosVec(1.0f);
        Vec3d end = start.add(camera.getRotationVec(1.0f).multiply(RANGE));
        return client.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, camera));
    }

    private static BlockPos raycastBlockPos(MinecraftClient client) {
        BlockHitResult blockHit = raycastBlock(client);
        if (blockHit == null || blockHit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return blockHit.getBlockPos().toImmutable();
    }

    private static AimSnapshot captureAim(MinecraftClient client) {
        return new AimSnapshot(raycastBlockPos(client), raycastPlayerHits(client));
    }

    private static List<PlayerRayHit> raycastPlayerHits(MinecraftClient client) {
        Entity camera = client.getCameraEntity();
        if (camera == null || client.world == null || client.player == null) {
            return List.of();
        }
        Vec3d start = camera.getCameraPosVec(1.0f);
        Vec3d look = camera.getRotationVec(1.0f).normalize();
        ArrayList<PlayerRayHit> hits = new ArrayList<>();

        for (PlayerEntity candidate : client.world.getPlayers()) {
            if (candidate.getUuid().equals(client.player.getUuid()) || !isFirstPressRaycastCandidate(candidate)) {
                continue;
            }

            RayDistance distance = closestDistanceToRay(start, look, candidate);
            if (distance.rayDistance() < 0.0 || distance.rayDistance() > RANGE
                    || distance.distanceSquared() > PLAYER_PING_RAY_RADIUS_SQUARED) {
                continue;
            }
            if (!hasPlayerLineOfSight(client, start, candidate)) {
                continue;
            }

            hits.add(new PlayerRayHit(
                    candidate.getUuid(),
                    LegionsFeatures.realUsername(candidate),
                    distance.distanceSquared(),
                    distance.rayDistance()
            ));
        }

        return hits;
    }

    private static PlayerEntity raycastPlayer(MinecraftClient client, PingRow row, AimSnapshot aim) {
        if (aim == null) {
            return null;
        }

        PlayerEntity best = null;
        double bestDistanceSq = PLAYER_PING_RAY_RADIUS_SQUARED;
        double bestRayDistance = Double.POSITIVE_INFINITY;

        for (PlayerRayHit hit : aim.playerHits()) {
            PlayerEntity candidate = findPlayer(client, hit.uuid(), hit.name());
            if (!isRaycastTargetCandidate(client, row, candidate)) {
                continue;
            }

            if (hit.distanceSquared() < bestDistanceSq
                    || hit.distanceSquared() == bestDistanceSq && hit.rayDistance() < bestRayDistance) {
                best = candidate;
                bestDistanceSq = hit.distanceSquared();
                bestRayDistance = hit.rayDistance();
            }
        }

        return best;
    }

    private static boolean isFirstPressRaycastCandidate(PlayerEntity player) {
        return player != null
                && player.isAlive()
                && !player.isSpectator();
    }

    private static boolean isRaycastTargetCandidate(MinecraftClient client, PingRow row, PlayerEntity player) {
        return player != null
                && player.isAlive()
                && !player.isSpectator()
                && isAllowedPlayerTarget(client, row, player);
    }

    private static boolean isAllowedPlayerTarget(MinecraftClient client, PingRow row, PlayerEntity player) {
        if (client.player == null || player == null || LegionsFeatures.isSpectatorTeam(player)) {
            return false;
        }
        return switch (row.targetType) {
            case PingRow.TARGET_TYPE_TEAMMATES_ONLY -> player == client.player || LegionsFeatures.isTeammate(client.player, player);
            case PingRow.TARGET_TYPE_ENEMIES_ONLY -> LegionsFeatures.isOpponent(client.player, player);
            case PingRow.TARGET_TYPE_ALL_PLAYERS_SAME_MESSAGE, PingRow.TARGET_TYPE_ALL_PLAYERS_DIFFERENT_MESSAGE -> true;
            default -> false;
        };
    }

    private static RayDistance closestDistanceToRay(Vec3d start, Vec3d direction, PlayerEntity player) {
        RayDistance eyeDistance = closestDistanceToRay(start, direction, player.getEyePos());
        RayDistance centerDistance = closestDistanceToRay(start, direction, player.getBoundingBox().getCenter());
        return eyeDistance.distanceSquared() <= centerDistance.distanceSquared() ? eyeDistance : centerDistance;
    }

    private static RayDistance closestDistanceToRay(Vec3d start, Vec3d direction, Vec3d point) {
        Vec3d offset = point.subtract(start);
        double rayDistance = offset.dotProduct(direction);
        Vec3d closest = start.add(direction.multiply(Math.max(0.0, Math.min(RANGE, rayDistance))));
        return new RayDistance(point.squaredDistanceTo(closest), rayDistance);
    }

    private static boolean isBlockMarkerVisibleToCamera(MinecraftClient client, Vec3d pos) {
        Entity camera = client.getCameraEntity() == null ? client.player : client.getCameraEntity();
        return camera != null && hasClearPathToPoint(client, camera.getCameraPosVec(1.0f), pos, BLOCK_MARKER_OCCLUSION_TOLERANCE_SQUARED);
    }

    private static boolean hasPlayerLineOfSight(MinecraftClient client, Vec3d start, PlayerEntity player) {
        Entity camera = client.getCameraEntity() == null ? client.player : client.getCameraEntity();
        return hasPlayerLineOfSight(client, start, player, camera);
    }

    private static boolean hasPlayerLineOfSight(MinecraftClient client, Vec3d start, PlayerEntity player,
                                                Entity camera) {
        if (player == null) {
            return false;
        }
        return hasClearPathToPoint(client, start, player.getEyePos(), PLAYER_MARKER_OCCLUSION_TOLERANCE_SQUARED, camera)
                || hasClearPathToPoint(client, start, player.getBoundingBox().getCenter(),
                PLAYER_MARKER_OCCLUSION_TOLERANCE_SQUARED, camera);
    }

    private static boolean hasClearPathToPoint(MinecraftClient client, Vec3d start, Vec3d end, double targetToleranceSquared) {
        Entity camera = client.getCameraEntity() == null ? client.player : client.getCameraEntity();
        return hasClearPathToPoint(client, start, end, targetToleranceSquared, camera);
    }

    private static boolean hasClearPathToPoint(MinecraftClient client, Vec3d start, Vec3d end,
                                               double targetToleranceSquared, Entity camera) {
        if (client == null || client.world == null || start == null || end == null) {
            return false;
        }
        HitResult hit = client.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                camera
        ));
        return hit == null
                || hit.getType() != HitResult.Type.BLOCK
                || hit.getPos().squaredDistanceTo(end) <= targetToleranceSquared;
    }

    private static void sendChat(MinecraftClient client, String message) {
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatMessage(message);
        }
    }

    private static void showFeedback(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), true);
        }
    }

    private static int parseAudience(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if ("opponents".equals(normalized) || "opponent".equals(normalized) || "enemies".equals(normalized)) {
            return PingRow.VISUAL_AUDIENCE_OPPONENTS;
        }
        if ("all".equals(normalized) || "everyone".equals(normalized)) {
            return PingRow.VISUAL_AUDIENCE_EVERYONE;
        }
        return PingRow.VISUAL_AUDIENCE_TEAMMATES;
    }

    private static int parseIcon(String value, int fallback) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "default", "\u2666" -> PingRow.ICON_DEFAULT;
            case "axe", "\uD83E\uDE93" -> PingRow.ICON_AXE;
            case "pickaxe", "block", "\u26CF" -> PingRow.ICON_PICKAXE;
            case "sword", "focus", "enemy" -> PingRow.ICON_SWORD;
            case "bow" -> PingRow.ICON_BOW;
            case "star", "player" -> PingRow.ICON_STAR;
            case "fire", "fight", "\uD83D\uDD25" -> PingRow.ICON_FIRE;
            case "lightning", "warning", "danger", "\u26A1" -> PingRow.ICON_LIGHTNING;
            case "galaxy" -> PingRow.ICON_GALAXY;
            case "diamond", "\u25C6" -> PingRow.ICON_DIAMOND;
            case "dot" -> PingRow.ICON_DOT;
            case "heart", "shield", "help", "defend", "\u2764" -> PingRow.ICON_HEART;
            case "hourglass" -> PingRow.ICON_HOURGLASS;
            case "home", "flag", "go", "location" -> PingRow.ICON_HOME;
            case "comet" -> PingRow.ICON_COMET;
            default -> normalizeIcon(fallback);
        };
    }

    private static String iconName(int icon) {
        return switch (normalizeIcon(icon)) {
            case PingRow.ICON_AXE -> "axe";
            case PingRow.ICON_PICKAXE -> "pickaxe";
            case PingRow.ICON_SWORD -> "sword";
            case PingRow.ICON_BOW -> "bow";
            case PingRow.ICON_STAR -> "star";
            case PingRow.ICON_FIRE -> "fire";
            case PingRow.ICON_LIGHTNING -> "lightning";
            case PingRow.ICON_GALAXY -> "galaxy";
            case PingRow.ICON_DIAMOND -> "diamond";
            case PingRow.ICON_DOT -> "dot";
            case PingRow.ICON_HEART -> "heart";
            case PingRow.ICON_HOURGLASS -> "hourglass";
            case PingRow.ICON_HOME -> "home";
            case PingRow.ICON_COMET -> "comet";
            default -> "default";
        };
    }

    private static String iconSymbol(int icon) {
        return switch (normalizeIcon(icon)) {
            case PingRow.ICON_AXE -> "\uD83E\uDE93";
            case PingRow.ICON_PICKAXE -> "\u26CF";
            case PingRow.ICON_SWORD -> "\uD83D\uDDE1";
            case PingRow.ICON_BOW -> "\uD83C\uDFF9";
            case PingRow.ICON_STAR -> "\u2B50";
            case PingRow.ICON_FIRE -> "\uD83D\uDD25";
            case PingRow.ICON_LIGHTNING -> "\u26A1";
            case PingRow.ICON_GALAXY -> "\uD83C\uDF0C";
            case PingRow.ICON_DIAMOND -> "\u25C6";
            case PingRow.ICON_DOT -> "\u23FA";
            case PingRow.ICON_HEART -> "\u2764";
            case PingRow.ICON_HOURGLASS -> "\u23F3";
            case PingRow.ICON_HOME -> "\u2302";
            case PingRow.ICON_COMET -> "\u2604";
            default -> "\u2666";
        };
    }

    private static String defaultIconLabel(int icon) {
        return switch (normalizeIcon(icon)) {
            case PingRow.ICON_AXE -> "Attack";
            case PingRow.ICON_PICKAXE -> "Block";
            case PingRow.ICON_BOW -> "Bow";
            case PingRow.ICON_STAR -> "Team Fight";
            case PingRow.ICON_FIRE -> "Fight";
            case PingRow.ICON_LIGHTNING -> "Danger";
            case PingRow.ICON_GALAXY -> "Portal";
            case PingRow.ICON_DIAMOND -> "Point";
            case PingRow.ICON_DOT -> "Point";
            case PingRow.ICON_HEART -> "Help";
            case PingRow.ICON_HOURGLASS -> "Wait";
            case PingRow.ICON_HOME -> "Go";
            case PingRow.ICON_COMET -> "Move";
            default -> "Focus";
        };
    }

    private static int normalizeIcon(int icon) {
        return Math.max(PingRow.ICON_DEFAULT, Math.min(PingRow.ICON_COMET, icon));
    }

    private static String audienceName(int audience) {
        return switch (audience) {
            case PingRow.VISUAL_AUDIENCE_OPPONENTS -> "opponents";
            case PingRow.VISUAL_AUDIENCE_EVERYONE -> "all";
            default -> "team";
        };
    }

    private static int parseColor(String value) {
        if (value == null) {
            return DEFAULT_PING_COLOR;
        }
        String cleaned = value.trim();
        if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        }
        if (!isSixDigitHex(cleaned)) {
            return DEFAULT_PING_COLOR;
        }
        return 0xFF000000 | Integer.parseInt(cleaned, 16);
    }

    private static String hexColor(int color) {
        String hex = Integer.toHexString(color & 0x00FFFFFF);
        return "000000".substring(hex.length()) + hex;
    }

    private static boolean isSixDigitHex(String value) {
        if (value.length() != 6) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (!(character >= '0' && character <= '9')
                    && !(character >= 'a' && character <= 'f')
                    && !(character >= 'A' && character <= 'F')) {
                return false;
            }
        }
        return true;
    }

    private static final class FightSurfaceBoxConsumer implements VoxelShapes.BoxConsumer {
        private double targetX;
        private double targetZ;
        private double bestX;
        private double bestY;
        private double bestZ;
        private double bestDistanceSquared;

        private void reset(double targetX, double targetZ) {
            this.targetX = targetX;
            this.targetZ = targetZ;
            bestX = 0.5;
            bestY = Double.NEGATIVE_INFINITY;
            bestZ = 0.5;
            bestDistanceSquared = Double.POSITIVE_INFINITY;
        }

        @Override
        public void consume(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            if (maxY <= 0.0) {
                return;
            }

            double localX = clampDouble(targetX, minX, maxX);
            double localZ = clampDouble(targetZ, minZ, maxZ);
            double distanceSquared = square(targetX - localX) + square(targetZ - localZ);
            if (distanceSquared < bestDistanceSquared
                    || Math.abs(distanceSquared - bestDistanceSquared) <= 1.0e-4 && maxY > bestY) {
                bestX = localX;
                bestY = maxY;
                bestZ = localZ;
                bestDistanceSquared = distanceSquared;
            }
        }
    }

    private static final class BlockOutlineBoxConsumer implements VoxelShapes.BoxConsumer {
        private BlockPos pos;
        private DrawStyle style;

        private void reset(BlockPos pos, DrawStyle style) {
            this.pos = pos;
            this.style = style;
        }

        @Override
        public void consume(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            renderBox(new Box(minX + pos.getX(), minY + pos.getY(), minZ + pos.getZ(),
                    maxX + pos.getX(), maxY + pos.getY(), maxZ + pos.getZ()), style);
        }
    }

    private static int opaque(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    private record PingKey(int keyType, int keyCode) {
    }

    private static final class ScreenProjection {
        private boolean visible;
        private double x;
        private double y;
        private double dirX;
        private double dirY;

        private ScreenProjection set(boolean visible, double x, double y, double dirX, double dirY) {
            this.visible = visible;
            this.x = x;
            this.y = y;
            this.dirX = dirX;
            this.dirY = dirY;
            return this;
        }

        private boolean visible() { return visible; }
        private double x() { return x; }
        private double y() { return y; }
        private double dirX() { return dirX; }
        private double dirY() { return dirY; }
    }

    private static final class MarkerProjectionContext {
        private Vec3d cameraPos;
        private Vec3d look;
        private Vec3d right;
        private Vec3d up;
        private double horizontalTan;
        private double verticalTan;
        private int screenWidth;
        private int screenHeight;
        private int margin;

        private MarkerProjectionContext set(Vec3d cameraPos, Vec3d look, Vec3d right, Vec3d up,
                                            double horizontalTan, double verticalTan,
                                            int screenWidth, int screenHeight, int margin) {
            this.cameraPos = cameraPos;
            this.look = look;
            this.right = right;
            this.up = up;
            this.horizontalTan = horizontalTan;
            this.verticalTan = verticalTan;
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
            this.margin = margin;
            return this;
        }

        private Vec3d cameraPos() { return cameraPos; }
        private Vec3d look() { return look; }
        private Vec3d right() { return right; }
        private Vec3d up() { return up; }
        private double horizontalTan() { return horizontalTan; }
        private double verticalTan() { return verticalTan; }
        private int screenWidth() { return screenWidth; }
        private int screenHeight() { return screenHeight; }
        private int margin() { return margin; }
    }

    private static final class ArrowPlacement {
        private double x;
        private double y;
        private double dirX;
        private double dirY;

        private ArrowPlacement set(double x, double y, double dirX, double dirY) {
            this.x = x;
            this.y = y;
            this.dirX = dirX;
            this.dirY = dirY;
            return this;
        }

        private double x() { return x; }
        private double y() { return y; }
        private double dirX() { return dirX; }
        private double dirY() { return dirY; }
    }

    private static final class PlayerFightNode {
        private String team;
        private Vec3d pos;

        private PlayerFightNode set(String team, Vec3d pos) {
            this.team = team;
            this.pos = pos;
            return this;
        }

        private String team() { return team; }
        private Vec3d pos() { return pos; }
    }

    private static final class FightCandidate {
        private Vec3d center;
        private double averageY;
        private int players;
        private int teams;
        private double spreadSquared;
        private double distanceSquared;

        private FightCandidate set(Vec3d center, double averageY, int players, int teams,
                                   double spreadSquared, double distanceSquared) {
            this.center = center;
            this.averageY = averageY;
            this.players = players;
            this.teams = teams;
            this.spreadSquared = spreadSquared;
            this.distanceSquared = distanceSquared;
            return this;
        }

        private Vec3d center() { return center; }
        private double averageY() { return averageY; }
        private int players() { return players; }
        private int teams() { return teams; }
        private double spreadSquared() { return spreadSquared; }
        private double distanceSquared() { return distanceSquared; }
    }

    private record FightMark(Vec3d fromPos, Vec3d targetPos, double averageY,
                             long markedAt, long fadeStartedAt,
                             long moveStartedAt, long moveDurationMillis,
                             int players, int teams) {
        private Vec3d rawPos(long now) {
            if (moveDurationMillis <= 0L) {
                return targetPos;
            }
            double progress = clampDouble((now - moveStartedAt) / (double) moveDurationMillis, 0.0, 1.0);
            double eased = progress * progress * (3.0 - 2.0 * progress);
            return lerp(fromPos, targetPos, eased);
        }
    }

    private record PendingPress(PingKey key, int presses, long pressedAt, AimSnapshot aim) {
    }

    private record AimSnapshot(BlockPos blockPos, List<PlayerRayHit> playerHits) {
    }

    private record PlayerRayHit(UUID uuid, String name, double distanceSquared, double rayDistance) {
    }

    private record PingMark(long markedAt, int color, int icon, String label) {
    }

    private record PingTarget(PlayerEntity player, BlockPos blockPos) {
        private static PingTarget player(PlayerEntity player) {
            return new PingTarget(player, null);
        }

        private static PingTarget block(BlockPos pos) {
            return new PingTarget(null, pos);
        }
    }

    private record ReceivedPing(String sender, String playerTarget, BlockPos blockPos, int color, int audience, int icon, String label) {
        private static ReceivedPing player(String sender, String target, int color, int audience, int icon, String label) {
            return new ReceivedPing(sender, target, null, color, audience, icon, label);
        }

        private static ReceivedPing block(String sender, BlockPos pos, int color, int audience, int icon, String label) {
            return new ReceivedPing(sender, null, pos, color, audience, icon, label);
        }
    }

    private record PayloadOptions(int color, int audience, int icon, String label) {
    }

    private record Range(int start, int end) {
    }

    private record RayDistance(double distanceSquared, double rayDistance) {
    }
}
