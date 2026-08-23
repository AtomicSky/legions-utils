package com.legions.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;

public final class LegionsHud {
    private static final String TEAM_COUNT_TITLE = "Teams Left";
    private static final int TEAM_COUNT_MIN_WIDTH = 78;
    private static final int TEAM_COUNT_HEADER_HEIGHT = 13;
    private static final int TEAM_COUNT_ROW_HEIGHT = 10;
    private static final int TEAM_COUNT_PADDING_X = 5;
    private static final int TEAM_COUNT_MARGIN = 8;
    private static final int TEAM_COUNT_DEFAULT_Y = 36;
    private static final List<TeamCount> SAMPLE_TEAM_COUNTS = List.of(
            new TeamCount("BLUE", 0xFF5555FF, 8, 12400, 8),
            new TeamCount("RED", 0xFFFF5555, 7, 10900, 7)
    );
    private static final ArrayList<TeamCount> teamCountCache = new ArrayList<>();
    private static final LinkedHashMap<String, MutableTeamCount> teamCountScratch = new LinkedHashMap<>();
    private static final TeamHudState teamHudStateScratch = new TeamHudState();
    private static Object teamCountCacheHandler;
    private static long teamCountCacheTick = Long.MIN_VALUE;
    private static int teamCountCacheSize = -1;
    private static UUID lastTeamHudPlayerUuid;
    private static String lastTeamHudSourceName;
    private static String lastTeamHudTeamName;
    private static int lastTeamHudTeamColor = 0xFFE7F0FF;
    private static String cachedTeamHudTextName;
    private static String cachedTeamHudText;
    private static Component cachedTeamHudTextComponent;

    private LegionsHud() {
    }

    public static void renderHud(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (!LegionsClient.hudVisible(client)) {
            return;
        }
        renderTeamHud(context);
        renderTeamCountOverlay(context);
        LegionsPingController.renderHud(context);
    }

    public static void renderTeamHud(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (!LegionsClient.enabled(client) || !LegionsClient.CONFIG.teamHudEnabled || client.player == null) {
            return;
        }

        renderTeamHudText(context, client, LegionsClient.CONFIG.teamHudX, LegionsClient.CONFIG.teamHudY);
    }

    public static void renderTeamHudPreview(GuiGraphicsExtractor context, Minecraft client, int x, int y) {
        Minecraft renderClient = client == null ? Minecraft.getInstance() : client;
        renderTeamHudText(context, renderClient, x, y);
    }

    public static int teamHudPreviewWidth(Minecraft client) {
        Minecraft renderClient = client == null ? Minecraft.getInstance() : client;
        return scaledDimension(renderClient.font.width(teamHudText(renderClient)));
    }

    public static int teamHudPreviewHeight(Minecraft client) {
        Minecraft renderClient = client == null ? Minecraft.getInstance() : client;
        return scaledDimension(renderClient.font.lineHeight);
    }

    private static void renderTeamHudText(GuiGraphicsExtractor context, Minecraft client, int configuredX, int configuredY) {
        Font renderer = client.font;
        TeamHudState state = teamHudState(client);
        String text = teamHudTextForName(state.name());
        float scale = LegionsClient.uiScaleFactor();
        int width = scaledDimension(renderer.width(text));
        int height = scaledDimension(renderer.lineHeight);
        int x = clamp(configuredX, 0, Math.max(0, context.guiWidth() - width));
        int y = clamp(configuredY, 0, Math.max(0, context.guiHeight() - height));

        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(scale, scale);
        context.text(renderer, cachedTeamHudTextComponent, 0, 0, state.color());
        context.pose().popMatrix();
    }

    public static String teamHudText(Minecraft client) {
        return teamHudTextForName(teamHudState(client).name());
    }

    public static int teamHudColor(Minecraft client) {
        return teamHudState(client).color();
    }

    private static TeamHudState teamHudState(Minecraft client) {
        if (client == null || client.player == null) {
            return teamHudStateScratch.set("?", 0xFFE7F0FF);
        }

        UUID playerUuid = client.player.getUUID();
        if (!playerUuid.equals(lastTeamHudPlayerUuid)) {
            lastTeamHudPlayerUuid = playerUuid;
            lastTeamHudSourceName = null;
            lastTeamHudTeamName = null;
            lastTeamHudTeamColor = 0xFFE7F0FF;
        }

        PlayerTeam team = client.player.getTeam();
        if (team == null) {
            if (client.player.isSpectator() && lastTeamHudTeamName != null) {
                return teamHudStateScratch.set(lastTeamHudTeamName, lastTeamHudTeamColor);
            }
            return teamHudStateScratch.set("None", 0xFFE7F0FF);
        }

        String teamName = team.getName();
        boolean spectatorTeam = isSpectatorTeamName(teamName);
        if ((client.player.isSpectator() || spectatorTeam) && lastTeamHudTeamName != null) {
            return teamHudStateScratch.set(lastTeamHudTeamName, lastTeamHudTeamColor);
        }

        String name = team.getDisplayName().getString();
        if (name == null || name.isBlank()) {
            name = teamName;
        }
        String readableName = name.equals(lastTeamHudSourceName) && lastTeamHudTeamName != null
                ? lastTeamHudTeamName
                : readableTeamName(name);
        int color = rawTeamHudColor(team);
        if (!spectatorTeam) {
            lastTeamHudSourceName = name;
            lastTeamHudTeamName = readableName;
            lastTeamHudTeamColor = color;
        }
        return teamHudStateScratch.set(readableName, color);
    }

    private static int rawTeamHudColor(PlayerTeam team) {
        Integer rgb = team.getColor().map(color -> color.rgb()).orElse(null);
        if (rgb != null) {
            return 0xFF000000 | rgb;
        }
        return fallbackTeamColor(team.getName());
    }

    private static String teamHudTextForName(String name) {
        if (!name.equals(cachedTeamHudTextName)) {
            cachedTeamHudTextName = name;
            cachedTeamHudText = "Team: " + name;
            cachedTeamHudTextComponent = Component.literal(cachedTeamHudText);
        }
        return cachedTeamHudText;
    }

    public static void renderTeamCountOverlay(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (!LegionsClient.enabled(client) || !LegionsClient.CONFIG.teamCountOverlayEnabled || client.player == null) {
            return;
        }

        renderTeamCountOverlay(context, client, LegionsClient.CONFIG.teamCountOverlayX, LegionsClient.CONFIG.teamCountOverlayY, false);
    }

    public static int teamCountOverlayPreviewWidth(Minecraft client) {
        Minecraft renderClient = client == null ? Minecraft.getInstance() : client;
        return scaledDimension(teamCountOverlayWidth(renderClient, displayTeamCounts(client, true)));
    }

    public static int teamCountOverlayPreviewHeight(Minecraft client) {
        return scaledDimension(teamCountOverlayHeight(displayTeamCounts(client, true)));
    }

    public static int defaultTeamCountOverlayX(Minecraft client) {
        Minecraft renderClient = client == null ? Minecraft.getInstance() : client;
        int screenWidth = renderClient.getWindow().getGuiScaledWidth();
        return Math.max(0, screenWidth - teamCountOverlayPreviewWidth(renderClient) - TEAM_COUNT_MARGIN);
    }

    public static int defaultTeamCountOverlayY() {
        return TEAM_COUNT_DEFAULT_Y;
    }

    public static void renderTeamCountOverlayPreview(GuiGraphicsExtractor context, Minecraft client, int x, int y) {
        Minecraft renderClient = client == null ? Minecraft.getInstance() : client;
        renderTeamCountOverlay(context, renderClient, x, y, true);
    }

    private static void renderTeamCountOverlay(GuiGraphicsExtractor context, Minecraft client, int configuredX, int configuredY, boolean preview) {
        List<TeamCount> counts = displayTeamCounts(client, preview);
        if (counts.isEmpty()) {
            return;
        }

        int baseWidth = teamCountOverlayWidth(client, counts);
        int baseHeight = teamCountOverlayHeight(counts);
        int width = scaledDimension(baseWidth);
        int height = scaledDimension(baseHeight);
        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();
        int x = configuredX;
        int y = configuredY;
        if (x < 0 || y < 0) {
            x = Math.max(0, screenWidth - width - TEAM_COUNT_MARGIN);
            y = Math.min(Math.max(0, TEAM_COUNT_DEFAULT_Y), Math.max(0, screenHeight - height));
        }
        x = clamp(x, 0, Math.max(0, screenWidth - width));
        y = clamp(y, 0, Math.max(0, screenHeight - height));

        float scale = LegionsClient.uiScaleFactor();
        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(scale, scale);
        renderTeamCountOverlayContents(context, client, counts, baseWidth, baseHeight);
        context.pose().popMatrix();
    }

    private static void renderTeamCountOverlayContents(GuiGraphicsExtractor context, Minecraft client,
                                                       List<TeamCount> counts, int width, int height) {
        context.fill(0, 0, width, height, 0x66000000);
        context.fill(0, 0, width, TEAM_COUNT_HEADER_HEIGHT, 0x99000000);
        String title = teamCountTitle();
        int titleX = width / 2 - client.font.width(title) / 2;
        context.text(client.font, title, titleX, 2, 0xFFFFFF55);

        int rowY = TEAM_COUNT_HEADER_HEIGHT + 2;
        for (int i = 0; i < counts.size(); i++) {
            TeamCount count = counts.get(i);
            int rowTop = rowY + i * TEAM_COUNT_ROW_HEIGHT;
            context.fill(0, rowTop, width, rowTop + TEAM_COUNT_ROW_HEIGHT, (i & 1) == 0 ? 0x52000000 : 0x3F000000);
            context.text(client.font, count.name(), TEAM_COUNT_PADDING_X, rowTop + 1, count.color());
            String value = count.valueText();
            int valueX = width - TEAM_COUNT_PADDING_X - client.font.width(value);
            context.text(client.font, value, valueX, rowTop + 1, 0xFFFFFFFF);
        }
    }

    private static int teamCountOverlayWidth(Minecraft client, List<TeamCount> counts) {
        int width = client.font.width(teamCountTitle()) + TEAM_COUNT_PADDING_X * 2;
        for (TeamCount count : counts) {
            String value = count.valueText();
            int rowWidth = client.font.width(count.name()) + client.font.width(value) + TEAM_COUNT_PADDING_X * 3 + 8;
            width = Math.max(width, rowWidth);
        }
        return Math.max(TEAM_COUNT_MIN_WIDTH, width);
    }

    private static int teamCountOverlayHeight(List<TeamCount> counts) {
        return TEAM_COUNT_HEADER_HEIGHT + 4 + counts.size() * TEAM_COUNT_ROW_HEIGHT;
    }

    private static List<TeamCount> displayTeamCounts(Minecraft client, boolean preview) {
        List<TeamCount> counts = collectTeamCounts(client);
        if (preview && counts.isEmpty()) {
            return SAMPLE_TEAM_COUNTS;
        }
        return counts;
    }

    private static List<TeamCount> collectTeamCounts(Minecraft client) {
        if (client == null || client.getConnection() == null) {
            clearTeamCountCache();
            return teamCountCache;
        }

        ClientPacketListener networkHandler = client.getConnection();
        int size = networkHandler.getOnlinePlayers().size();
        long tick = client.level == null ? Long.MIN_VALUE : client.level.getGameTime();
        if (teamCountCacheHandler == networkHandler && teamCountCacheTick == tick && teamCountCacheSize == size) {
            return teamCountCache;
        }

        teamCountScratch.clear();
        for (PlayerInfo entry : networkHandler.getOnlinePlayers()) {
            if (entry == null || entry.getGameMode() == GameType.SPECTATOR) {
                continue;
            }

            PlayerTeam team = entry.getTeam();
            if (team == null || team.getName() == null || team.getName().isBlank() || isSpectatorTeamName(team.getName())) {
                continue;
            }

            String key = team.getName();
            int quips = LegionsFeatures.getQuips(client, entry.getProfile().name());
            int quipTotal = quips < 0 ? 0 : quips;
            int knownQuips = quips < 0 ? 0 : 1;
            MutableTeamCount current = teamCountScratch.get(key);
            if (current == null) {
                teamCountScratch.put(key, new MutableTeamCount(teamDisplayName(team), teamTextColor(team),
                        quipTotal, knownQuips));
            } else {
                current.add(quipTotal, knownQuips);
            }
        }

        teamCountCacheHandler = networkHandler;
        teamCountCacheTick = tick;
        teamCountCacheSize = size;
        teamCountCache.clear();
        for (MutableTeamCount count : teamCountScratch.values()) {
            teamCountCache.add(count.toTeamCount());
        }
        teamCountScratch.clear();
        return teamCountCache;
    }

    private static void clearTeamCountCache() {
        teamCountCacheHandler = null;
        teamCountCacheTick = Long.MIN_VALUE;
        teamCountCacheSize = -1;
        teamCountCache.clear();
        teamCountScratch.clear();
    }

    private static String teamDisplayName(PlayerTeam team) {
        String name = team.getDisplayName() == null ? "" : team.getDisplayName().getString();
        if (name.isBlank()) {
            name = team.getName();
        }
        return formatTeamName(name);
    }

    private static String formatTeamName(String name) {
        if (name == null || name.isBlank()) {
            return "Team";
        }
        String trimmed = name.trim();
        return trimmed.length() <= 18 ? trimmed : trimmed.substring(0, 18);
    }

    private static int teamTextColor(PlayerTeam team) {
        Integer rgb = team.getColor().map(color -> color.rgb()).orElse(null);
        if (rgb == null) {
            return fallbackTeamColor(team.getName());
        }

        int color = rgb & 0xFFFFFF;
        return color <= 0x202020 ? 0xFFAAAAAA : 0xFF000000 | color;
    }

    private static boolean isSpectatorTeamName(String name) {
        return LegionsFeatures.isSpectatorTeamName(name);
    }

    private static String readableTeamName(String value) {
        if (value == null || value.isBlank()) {
            return "None";
        }
        String normalized = value.trim().replace('_', ' ').replace('-', ' ');
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("spectator")) {
            return "Spectator";
        }
        if (lower.contains("red")) {
            return "Red";
        }
        if (lower.contains("blue")) {
            return "Blue";
        }
        if (lower.contains("green")) {
            return "Green";
        }
        if (lower.contains("yellow")) {
            return "Yellow";
        }
        return normalized;
    }

    private static int fallbackTeamColor(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (normalized.contains("red")) {
            return 0xFFFF5555;
        }
        if (normalized.contains("blue")) {
            return 0xFF55A6FF;
        }
        if (normalized.contains("green")) {
            return 0xFF55FF55;
        }
        if (normalized.contains("yellow")) {
            return 0xFFFFFF55;
        }
        if (normalized.contains("spectator")) {
            return 0xFF9AA8B8;
        }
        return 0xFFE7F0FF;
    }

    private static String teamCountTitle() {
        return teamRatingTotalsEnabled() ? "Team Ratings" : TEAM_COUNT_TITLE;
    }

    private static boolean teamRatingTotalsEnabled() {
        return LegionsClient.CONFIG != null && LegionsClient.CONFIG.teamRatingTotalsEnabled;
    }

    private static String formatRatingTotal(int ratingTotal, int knownRatings, int count) {
        if (knownRatings <= 0) {
            return "?";
        }
        int whole = ratingTotal / 1000;
        int tenths = (ratingTotal % 1000) / 100;
        return whole + "." + tenths + (knownRatings < count ? "+" : "");
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int scaledDimension(int value) {
        return Math.max(0, (int) Math.ceil(value * LegionsClient.uiScaleFactor()));
    }

    private static final class TeamCount {
        private final String name;
        private final int color;
        private final int count;
        private final String countText;
        private final String ratingText;

        private TeamCount(String name, int color, int count, int ratingTotal, int knownRatings) {
            this.name = name;
            this.color = color;
            this.count = count;
            this.countText = Integer.toString(count);
            this.ratingText = countText + " | " + formatRatingTotal(ratingTotal, knownRatings, count);
        }

        private String name() {
            return name;
        }

        private int color() {
            return color;
        }

        private String valueText() {
            return teamRatingTotalsEnabled() ? ratingText : countText;
        }
    }

    private static final class MutableTeamCount {
        private final String name;
        private final int color;
        private int count = 1;
        private int ratingTotal;
        private int knownRatings;

        private MutableTeamCount(String name, int color, int ratingTotal, int knownRatings) {
            this.name = name;
            this.color = color;
            this.ratingTotal = ratingTotal;
            this.knownRatings = knownRatings;
        }

        private void add(int rating, int known) {
            count++;
            ratingTotal += rating;
            knownRatings += known;
        }

        private TeamCount toTeamCount() {
            return new TeamCount(name, color, count, ratingTotal, knownRatings);
        }
    }

    private static final class TeamHudState {
        private String name;
        private int color;

        private TeamHudState set(String name, int color) {
            this.name = name;
            this.color = color;
            return this;
        }

        private String name() {
            return name;
        }

        private int color() {
            return color;
        }
    }
}
