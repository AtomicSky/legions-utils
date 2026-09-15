package com.legions.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.legions.client.LegionsClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LegionsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "legions_utils.json";
    private static final String LEGACY_CONFIG_FILE_NAME = "legions_client.json";

    public boolean enabled = true;
    public int uiScale = 100;
    public List<String> allowedServerAddresses = new ArrayList<>(List.of("legions"));
    public boolean ratingNametagsEnabled = true;
    public boolean ratingNametagsIgnoreServerList = true;
    public boolean enemyHighlightsEnabled = true;
    public boolean quipOutlinesEnabled = false;
    public int quipOutlineWidth = 3;
    public boolean dynamicHighlightOpacityEnabled = true;
    public boolean teammateAttackWarningEnabled = false;
    public int enemyHighlightMinOpacity = 50;
    public int enemyHighlightMaxOpacity = 100;
    public boolean spectatorGlowEnabled = true;
    public boolean customWorldBorderEnabled = true;
    public String customWorldBorderColor = "#ffa500";
    public int customWorldBorderOpacity = 50;
    public boolean customWorldBorderHideGlitterParticles = true;
    public boolean teamPingEnabled = false;
    public boolean blockPingDistanceLabelEnabled = false;
    public boolean offscreenPingArrowsEnabled = true;
    public boolean offscreenPingArrowDistanceEnabled = false;
    public int offscreenPingArrowScale = 100;
    public boolean offscreenPingArrowDistanceFadeEnabled = true;
    public int offscreenPingArrowMinOpacity = 35;
    public int offscreenPingArrowMaxOpacity = 100;
    public boolean teamFightDetectorEnabled = false;
    public boolean teamFightDetectorSpectatorOnly = false;
    public int teamFightDetectionRadius = 24;
    public int teamFightMinPlayers = 3;
    public int teamFightMinTeams = 2;
    public int teamFightMarkerDurationSeconds = 6;
    public String teamFightMarkerColor = "#ffa500";
    public boolean teamFightDistanceLabelEnabled = true;
    public boolean teamFightSmoothingEnabled = true;
    public int teamFightSmoothingStrength = 35;
    public int teamFightFadeOutSeconds = 1;
    public boolean teamHudEnabled = true;
    public boolean teamCountOverlayEnabled = true;
    public boolean teamRatingTotalsEnabled = true;
    public boolean opponentLimitEnabled = false;
    public boolean playerRenderOptimizationEnabled = false;
    public boolean adaptivePerformanceEnabled = false;
    public int opponentLimit = 6;
    public int playerRenderDistance = 160;
    public int pingDurationSeconds = 10;
    public int pingRecentTargetTimeoutSeconds = 15;
    public List<PingRow> pingRows = defaultPingRows();
    public int teamHudX = 294;
    public int teamHudY = 2;
    public int teamCountOverlayX = 495;
    public int teamCountOverlayY = 0;

    public static LegionsConfig load() {
        Path path = configPath();
        Path legacyPath = legacyConfigPath();
        LegionsConfig config = null;
        if (Files.exists(path)) {
            config = loadFrom(path);
        }
        if (config == null && Files.exists(legacyPath)) {
            config = loadFrom(legacyPath);
            if (config != null) {
                LegionsClient.LOGGER.info("Migrated Legions Utils config from {} to {}.", legacyPath.getFileName(), path.getFileName());
            }
        }
        if (config == null) {
            config = new LegionsConfig();
        }

        config.normalize();
        config.save(path);
        return config;
    }

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
    }

    private static Path legacyConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(LEGACY_CONFIG_FILE_NAME);
    }

    private static LegionsConfig loadFrom(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement element = JsonParser.parseReader(reader);
            migrateLegacyFields(element);
            return GSON.fromJson(element, LegionsConfig.class);
        } catch (IOException | RuntimeException e) {
            LegionsClient.LOGGER.warn("Failed to load Legions Utils config from {}", path.getFileName(), e);
            return null;
        }
    }

    public LegionsConfig normalize() {
        allowedServerAddresses = normalizeServerAddresses(allowedServerAddresses);
        uiScale = clamp(uiScale, 50, 200);
        opponentLimit = clamp(opponentLimit, 1, 20);
        playerRenderDistance = clamp(playerRenderDistance, 16, 160);
        pingDurationSeconds = clamp(pingDurationSeconds, 1, 25);
        pingRecentTargetTimeoutSeconds = clamp(pingRecentTargetTimeoutSeconds, 1, 60);
        offscreenPingArrowScale = clamp(offscreenPingArrowScale, 50, 200);
        enemyHighlightMinOpacity = clamp(enemyHighlightMinOpacity, 0, 100);
        enemyHighlightMaxOpacity = clamp(enemyHighlightMaxOpacity, enemyHighlightMinOpacity, 100);
        quipOutlineWidth = clamp(quipOutlineWidth, 1, 8);
        offscreenPingArrowMinOpacity = clamp(offscreenPingArrowMinOpacity, 10, 100);
        offscreenPingArrowMaxOpacity = clamp(offscreenPingArrowMaxOpacity, offscreenPingArrowMinOpacity, 100);
        customWorldBorderColor = normalizeColor(customWorldBorderColor);
        customWorldBorderOpacity = clamp(customWorldBorderOpacity, 5, 100);
        teamFightDetectionRadius = clamp(teamFightDetectionRadius, 8, 96);
        teamFightMinPlayers = clamp(teamFightMinPlayers, 2, 20);
        teamFightMinTeams = clamp(teamFightMinTeams, 2, 8);
        teamFightMarkerDurationSeconds = clamp(teamFightMarkerDurationSeconds, 1, 20);
        teamFightMarkerColor = normalizeColor(teamFightMarkerColor);
        teamFightSmoothingStrength = clamp(teamFightSmoothingStrength, 5, 100);
        teamFightFadeOutSeconds = clamp(teamFightFadeOutSeconds, 1, 10);
        pingRows = normalizePingRows(pingRows);
        teamHudX = clamp(teamHudX, 0, 10000);
        teamHudY = clamp(teamHudY, 0, 10000);
        teamCountOverlayX = clamp(teamCountOverlayX, -1, 10000);
        teamCountOverlayY = clamp(teamCountOverlayY, -1, 10000);
        return this;
    }

    public LegionsConfig copy() {
        LegionsConfig copy = new LegionsConfig();
        copy.enabled = enabled;
        copy.uiScale = uiScale;
        copy.allowedServerAddresses = new ArrayList<>(allowedServerAddresses);
        copy.ratingNametagsEnabled = ratingNametagsEnabled;
        copy.ratingNametagsIgnoreServerList = ratingNametagsIgnoreServerList;
        copy.enemyHighlightsEnabled = enemyHighlightsEnabled;
        copy.quipOutlinesEnabled = quipOutlinesEnabled;
        copy.quipOutlineWidth = quipOutlineWidth;
        copy.dynamicHighlightOpacityEnabled = dynamicHighlightOpacityEnabled;
        copy.enemyHighlightMaxOpacity = enemyHighlightMaxOpacity;
        copy.enemyHighlightMinOpacity = enemyHighlightMinOpacity;
        copy.teammateAttackWarningEnabled = teammateAttackWarningEnabled;
        copy.spectatorGlowEnabled = spectatorGlowEnabled;
        copy.customWorldBorderEnabled = customWorldBorderEnabled;
        copy.customWorldBorderColor = customWorldBorderColor;
        copy.customWorldBorderOpacity = customWorldBorderOpacity;
        copy.customWorldBorderHideGlitterParticles = customWorldBorderHideGlitterParticles;
        copy.teamPingEnabled = teamPingEnabled;
        copy.blockPingDistanceLabelEnabled = blockPingDistanceLabelEnabled;
        copy.offscreenPingArrowsEnabled = offscreenPingArrowsEnabled;
        copy.offscreenPingArrowDistanceEnabled = offscreenPingArrowDistanceEnabled;
        copy.offscreenPingArrowScale = offscreenPingArrowScale;
        copy.offscreenPingArrowDistanceFadeEnabled = offscreenPingArrowDistanceFadeEnabled;
        copy.offscreenPingArrowMinOpacity = offscreenPingArrowMinOpacity;
        copy.offscreenPingArrowMaxOpacity = offscreenPingArrowMaxOpacity;
        copy.teamFightDetectorEnabled = teamFightDetectorEnabled;
        copy.teamFightDetectorSpectatorOnly = teamFightDetectorSpectatorOnly;
        copy.teamFightDetectionRadius = teamFightDetectionRadius;
        copy.teamFightMinPlayers = teamFightMinPlayers;
        copy.teamFightMinTeams = teamFightMinTeams;
        copy.teamFightMarkerDurationSeconds = teamFightMarkerDurationSeconds;
        copy.teamFightMarkerColor = teamFightMarkerColor;
        copy.teamFightDistanceLabelEnabled = teamFightDistanceLabelEnabled;
        copy.teamFightSmoothingEnabled = teamFightSmoothingEnabled;
        copy.teamFightSmoothingStrength = teamFightSmoothingStrength;
        copy.teamFightFadeOutSeconds = teamFightFadeOutSeconds;
        copy.teamHudEnabled = teamHudEnabled;
        copy.teamCountOverlayEnabled = teamCountOverlayEnabled;
        copy.teamRatingTotalsEnabled = teamRatingTotalsEnabled;
        copy.opponentLimitEnabled = opponentLimitEnabled;
        copy.playerRenderOptimizationEnabled = playerRenderOptimizationEnabled;
        copy.adaptivePerformanceEnabled = adaptivePerformanceEnabled;
        copy.opponentLimit = opponentLimit;
        copy.playerRenderDistance = playerRenderDistance;
        copy.pingDurationSeconds = pingDurationSeconds;
        copy.pingRecentTargetTimeoutSeconds = pingRecentTargetTimeoutSeconds;
        copy.pingRows = copyPingRows(pingRows);
        copy.teamHudX = teamHudX;
        copy.teamHudY = teamHudY;
        copy.teamCountOverlayX = teamCountOverlayX;
        copy.teamCountOverlayY = teamCountOverlayY;
        return copy.normalize();
    }

    public boolean sameSettings(LegionsConfig other) {
        return other != null
                && enabled == other.enabled
                && uiScale == other.uiScale
                && allowedServerAddresses.equals(other.allowedServerAddresses)
                && ratingNametagsEnabled == other.ratingNametagsEnabled
                && ratingNametagsIgnoreServerList == other.ratingNametagsIgnoreServerList
                && enemyHighlightsEnabled == other.enemyHighlightsEnabled
                && quipOutlinesEnabled == other.quipOutlinesEnabled
                && quipOutlineWidth == other.quipOutlineWidth
                && dynamicHighlightOpacityEnabled == other.dynamicHighlightOpacityEnabled
                && enemyHighlightMaxOpacity == other.enemyHighlightMaxOpacity
                && enemyHighlightMinOpacity == other.enemyHighlightMinOpacity
                && teammateAttackWarningEnabled == other.teammateAttackWarningEnabled
                && spectatorGlowEnabled == other.spectatorGlowEnabled
                && customWorldBorderEnabled == other.customWorldBorderEnabled
                && customWorldBorderColor.equals(other.customWorldBorderColor)
                && customWorldBorderOpacity == other.customWorldBorderOpacity
                && customWorldBorderHideGlitterParticles == other.customWorldBorderHideGlitterParticles
                && teamPingEnabled == other.teamPingEnabled
                && blockPingDistanceLabelEnabled == other.blockPingDistanceLabelEnabled
                && offscreenPingArrowsEnabled == other.offscreenPingArrowsEnabled
                && offscreenPingArrowDistanceEnabled == other.offscreenPingArrowDistanceEnabled
                && offscreenPingArrowScale == other.offscreenPingArrowScale
                && offscreenPingArrowDistanceFadeEnabled == other.offscreenPingArrowDistanceFadeEnabled
                && offscreenPingArrowMinOpacity == other.offscreenPingArrowMinOpacity
                && offscreenPingArrowMaxOpacity == other.offscreenPingArrowMaxOpacity
                && teamFightDetectorEnabled == other.teamFightDetectorEnabled
                && teamFightDetectorSpectatorOnly == other.teamFightDetectorSpectatorOnly
                && teamFightDetectionRadius == other.teamFightDetectionRadius
                && teamFightMinPlayers == other.teamFightMinPlayers
                && teamFightMinTeams == other.teamFightMinTeams
                && teamFightMarkerDurationSeconds == other.teamFightMarkerDurationSeconds
                && teamFightMarkerColor.equals(other.teamFightMarkerColor)
                && teamFightDistanceLabelEnabled == other.teamFightDistanceLabelEnabled
                && teamFightSmoothingEnabled == other.teamFightSmoothingEnabled
                && teamFightSmoothingStrength == other.teamFightSmoothingStrength
                && teamFightFadeOutSeconds == other.teamFightFadeOutSeconds
                && teamHudEnabled == other.teamHudEnabled
                && teamCountOverlayEnabled == other.teamCountOverlayEnabled
                && teamRatingTotalsEnabled == other.teamRatingTotalsEnabled
                && opponentLimitEnabled == other.opponentLimitEnabled
                && playerRenderOptimizationEnabled == other.playerRenderOptimizationEnabled
                && adaptivePerformanceEnabled == other.adaptivePerformanceEnabled
                && opponentLimit == other.opponentLimit
                && playerRenderDistance == other.playerRenderDistance
                && pingDurationSeconds == other.pingDurationSeconds
                && pingRecentTargetTimeoutSeconds == other.pingRecentTargetTimeoutSeconds
                && pingRowsEqual(pingRows, other.pingRows)
                && teamHudX == other.teamHudX
                && teamHudY == other.teamHudY
                && teamCountOverlayX == other.teamCountOverlayX
                && teamCountOverlayY == other.teamCountOverlayY;
    }

    public void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException | RuntimeException e) {
            LegionsClient.LOGGER.warn("Failed to save Legions Utils config", e);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void migrateLegacyFields(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        migrateField(object, "automaticFoeOutlinesEnabled", "enemyHighlightsEnabled");
        migrateField(object, "teamQuipTotalsEnabled", "teamRatingTotalsEnabled");
        migrateField(object, "warningParticlesEnabled", "customWorldBorderEnabled");
        migrateInvertedBooleanField(object, "customWorldBorderParticlesVisible",
                "customWorldBorderHideGlitterParticles");
    }

    private static void migrateField(JsonObject object, String oldName, String newName) {
        if (!object.has(newName) && object.has(oldName)) {
            object.add(newName, object.get(oldName));
        }
    }

    private static void migrateInvertedBooleanField(JsonObject object, String oldName, String newName) {
        if (!object.has(newName) && object.has(oldName)) {
            object.addProperty(newName, !object.get(oldName).getAsBoolean());
        }
    }

    private static String normalizeColor(String value) {
        if (value == null) {
            return "#ff5555";
        }
        String cleaned = value.trim().toLowerCase(Locale.ROOT);
        if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        }
        return isSixDigitHex(cleaned) ? "#" + cleaned : "#ff5555";
    }

    private static boolean isSixDigitHex(String value) {
        if (value.length() != 6) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (!(character >= '0' && character <= '9') && !(character >= 'a' && character <= 'f')) {
                return false;
            }
        }
        return true;
    }

    private static List<String> normalizeServerAddresses(List<String> addresses) {
        ArrayList<String> normalized = new ArrayList<>();
        if (addresses != null) {
            for (String address : addresses) {
                if (address == null) {
                    continue;
                }
                String cleaned = cleanServerAddress(address);
                if (!cleaned.isBlank() && !normalized.contains(cleaned)) {
                    normalized.add(cleaned);
                }
            }
        }
        if (normalized.isEmpty()) {
            normalized.add("legions");
        }
        return normalized;
    }

    private static String cleanServerAddress(String address) {
        String cleaned = address.trim().toLowerCase(Locale.ROOT);
        if (cleaned.startsWith("http://")) {
            cleaned = cleaned.substring("http://".length());
        } else if (cleaned.startsWith("https://")) {
            cleaned = cleaned.substring("https://".length());
        }
        int slash = cleaned.indexOf('/');
        return slash >= 0 ? cleaned.substring(0, slash) : cleaned;
    }

    private static List<PingRow> normalizePingRows(List<PingRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return defaultPingRows();
        }

        ArrayList<PingRow> normalized = new ArrayList<>();
        for (PingRow row : rows) {
            normalized.add((row == null ? new PingRow() : row).normalize());
        }
        return normalized.isEmpty() ? defaultPingRows() : normalized;
    }

    private static ArrayList<PingRow> copyPingRows(List<PingRow> rows) {
        ArrayList<PingRow> copy = new ArrayList<>();
        for (PingRow row : normalizePingRows(rows)) {
            copy.add(row.copy());
        }
        return copy;
    }

    private static boolean pingRowsEqual(List<PingRow> first, List<PingRow> second) {
        List<PingRow> normalizedFirst = normalizePingRows(first);
        List<PingRow> normalizedSecond = normalizePingRows(second);
        if (normalizedFirst.size() != normalizedSecond.size()) {
            return false;
        }
        for (int i = 0; i < normalizedFirst.size(); i++) {
            if (!normalizedFirst.get(i).sameSettings(normalizedSecond.get(i))) {
                return false;
            }
        }
        return true;
    }

    public static ArrayList<PingRow> defaultPingRows() {
        ArrayList<PingRow> rows = new ArrayList<>();

        PingRow ping1 = new PingRow();
        ping1.keyType = 0;
        ping1.keyCode = 96;
        ping1.presses = 1;
        ping1.targetSource = 0;
        ping1.targetType = 3;
        ping1.message = "Focus {player}";
        ping1.teammateMessage = "{player} needs help!";
        ping1.enemyMessage = "Focus {player}";
        ping1.color = "#ffa500";
        ping1.visualAudience = 0;
        ping1.icon = 0;
        ping1.messageIcon = 3;
        ping1.teammateMessageIcon = 11;
        ping1.enemyMessageIcon = 3;
        rows.add(ping1.normalize());

        PingRow ping2 = new PingRow();
        ping2.keyType = 0;
        ping2.keyCode = 96;
        ping2.presses = 2;
        ping2.targetSource = 0;
        ping2.targetType = 4;
        ping2.message = "Go to {x} {y} {z}";
        ping2.teammateMessage = "{player} needs help!";
        ping2.enemyMessage = "Focus {player}";
        ping2.color = "#ffa500";
        ping2.visualAudience = 0;
        ping2.icon = 1;
        ping2.messageIcon = 13;
        ping2.teammateMessageIcon = 11;
        ping2.enemyMessageIcon = 3;
        rows.add(ping2.normalize());

        PingRow ping3 = new PingRow();
        ping3.keyType = 0;
        ping3.keyCode = 96;
        ping3.presses = 3;
        ping3.targetSource = 3;
        ping3.targetType = 2;
        ping3.message = "I need help!";
        ping3.teammateMessage = "{player} needs help!";
        ping3.enemyMessage = "Focus {player}";
        ping3.color = "#ffa500";
        ping3.visualAudience = 0;
        ping3.icon = 2;
        ping3.messageIcon = 11;
        ping3.teammateMessageIcon = 11;
        ping3.enemyMessageIcon = 3;
        rows.add(ping3.normalize());

        return rows;
    }

    public static class PingRow {
        public static final int KEY_TYPE_KEYBOARD = 0;
        public static final int KEY_TYPE_MOUSE = 1;

        public static final int TARGET_SOURCE_CROSSHAIR = 0;
        public static final int TARGET_SOURCE_LAST_ATTACKER = 1;
        public static final int TARGET_SOURCE_LAST_ATTACKED = 2;
        public static final int TARGET_SOURCE_SELF = 3;

        public static final int TARGET_TYPE_TEAMMATES_ONLY = 0;
        public static final int TARGET_TYPE_ENEMIES_ONLY = 1;
        public static final int TARGET_TYPE_ALL_PLAYERS_SAME_MESSAGE = 2;
        public static final int TARGET_TYPE_ALL_PLAYERS_DIFFERENT_MESSAGE = 3;
        public static final int TARGET_TYPE_BLOCKS_ONLY = 4;

        public static final int VISUAL_AUDIENCE_TEAMMATES = 0;
        public static final int VISUAL_AUDIENCE_OPPONENTS = 1;
        public static final int VISUAL_AUDIENCE_EVERYONE = 2;

        public static final int ICON_DEFAULT = 0;
        public static final int ICON_AXE = 1;
        public static final int ICON_PICKAXE = 2;
        public static final int ICON_SWORD = 3;
        public static final int ICON_BOW = 4;
        public static final int ICON_STAR = 5;
        public static final int ICON_FIRE = 6;
        public static final int ICON_LIGHTNING = 7;
        public static final int ICON_GALAXY = 8;
        public static final int ICON_DIAMOND = 9;
        public static final int ICON_DOT = 10;
        public static final int ICON_HEART = 11;
        public static final int ICON_HOURGLASS = 12;
        public static final int ICON_HOME = 13;
        public static final int ICON_COMET = 14;

        public int keyType = KEY_TYPE_KEYBOARD;
        public int keyCode = 71;
        public int presses = 1;
        public int targetSource = TARGET_SOURCE_CROSSHAIR;
        public int targetType = TARGET_TYPE_ALL_PLAYERS_DIFFERENT_MESSAGE;
        public String message = "Focus {PLAYER}";
        public String teammateMessage = "{PLAYER} NEEDS HELP!";
        public String enemyMessage = "Focus {PLAYER}";
        public String color = "#ffa500";
        public int visualAudience = VISUAL_AUDIENCE_TEAMMATES;
        public int icon = 0;
        public int messageIcon = -1;
        public int teammateMessageIcon = -1;
        public int enemyMessageIcon = -1;

        public PingRow normalize() {
            keyType = keyType == KEY_TYPE_MOUSE ? KEY_TYPE_MOUSE : KEY_TYPE_KEYBOARD;
            keyCode = keyType == KEY_TYPE_MOUSE ? clamp(keyCode, 0, 7) : clamp(keyCode, 32, 348);
            presses = clamp(presses, 1, 5);
            targetSource = clamp(targetSource, TARGET_SOURCE_CROSSHAIR, TARGET_SOURCE_SELF);
            targetType = clamp(targetType, TARGET_TYPE_TEAMMATES_ONLY, TARGET_TYPE_BLOCKS_ONLY);
            visualAudience = clamp(visualAudience, VISUAL_AUDIENCE_TEAMMATES, VISUAL_AUDIENCE_EVERYONE);
            int migratedIcon = migrateLegacyIcon(icon);
            icon = normalizeIconOrDefault(icon, migratedIcon);
            messageIcon = normalizeIconOrDefault(messageIcon, migratedIcon);
            teammateMessageIcon = normalizeIconOrDefault(teammateMessageIcon, ICON_HEART);
            enemyMessageIcon = normalizeIconOrDefault(enemyMessageIcon, ICON_SWORD);
            message = cleanMessage(message, "Focus {PLAYER}");
            teammateMessage = cleanMessage(teammateMessage, "{PLAYER} NEEDS HELP!");
            enemyMessage = cleanMessage(enemyMessage, "Focus {PLAYER}");
            color = normalizeColor(color);
            return this;
        }

        public PingRow copy() {
            PingRow copy = new PingRow();
            copy.keyType = keyType;
            copy.keyCode = keyCode;
            copy.presses = presses;
            copy.targetSource = targetSource;
            copy.targetType = targetType;
            copy.message = message;
            copy.teammateMessage = teammateMessage;
            copy.enemyMessage = enemyMessage;
            copy.color = color;
            copy.visualAudience = visualAudience;
            copy.icon = icon;
            copy.messageIcon = messageIcon;
            copy.teammateMessageIcon = teammateMessageIcon;
            copy.enemyMessageIcon = enemyMessageIcon;
            return copy.normalize();
        }

        private boolean sameSettings(PingRow other) {
            PingRow normalizedOther = other == null ? new PingRow() : other.copy();
            PingRow normalizedThis = copy();
            return normalizedThis.keyType == normalizedOther.keyType
                    && normalizedThis.keyCode == normalizedOther.keyCode
                    && normalizedThis.presses == normalizedOther.presses
                    && normalizedThis.targetSource == normalizedOther.targetSource
                    && normalizedThis.targetType == normalizedOther.targetType
                    && normalizedThis.message.equals(normalizedOther.message)
                    && normalizedThis.teammateMessage.equals(normalizedOther.teammateMessage)
                    && normalizedThis.enemyMessage.equals(normalizedOther.enemyMessage)
                    && normalizedThis.color.equals(normalizedOther.color)
                    && normalizedThis.visualAudience == normalizedOther.visualAudience
                    && normalizedThis.icon == normalizedOther.icon
                    && normalizedThis.messageIcon == normalizedOther.messageIcon
                    && normalizedThis.teammateMessageIcon == normalizedOther.teammateMessageIcon
                    && normalizedThis.enemyMessageIcon == normalizedOther.enemyMessageIcon;
        }

        private static String cleanMessage(String value, String fallback) {
            String cleaned = value == null ? "" : value.trim();
            return cleaned.isBlank() ? fallback : cleaned;
        }

        private static String normalizeColor(String value) {
            if (value == null) {
                return "#ffa500";
            }
            String cleaned = value.trim().toLowerCase(Locale.ROOT);
            if (cleaned.startsWith("#")) {
                cleaned = cleaned.substring(1);
            }
            return isSixDigitHex(cleaned) ? "#" + cleaned : "#ffa500";
        }

        private static int normalizeIconOrDefault(int value, int fallback) {
            if (value < ICON_DEFAULT || value > ICON_COMET) {
                return fallback;
            }
            return value;
        }

        private static int migrateLegacyIcon(int value) {
            return switch (value) {
                case 0 -> ICON_SWORD;
                case 1 -> ICON_HEART;
                case 2 -> ICON_HOME;
                case 3 -> ICON_LIGHTNING;
                case 4 -> ICON_STAR;
                case 5 -> ICON_PICKAXE;
                case 6 -> ICON_STAR;
                default -> normalizeIconOrDefault(value, ICON_SWORD);
            };
        }
    }
}
