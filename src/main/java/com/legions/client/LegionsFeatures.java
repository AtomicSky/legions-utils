package com.legions.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import com.legions.client.render.LegionsPlayerOverlayColorContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LegionsFeatures {
    private static final boolean ATOMICS_CLIENT_LOADED = FabricLoader.getInstance().isModLoaded("atomics_client");
    private static final int QUIP_UNKNOWN_COLOR = 0xA0A0A0;
    private static final int MIN_QUIP_OVERLAY_RATING = 100;
    private static final int MAX_QUIP_OVERLAY_RATING = 2000;
    private static final int MIN_HIGHLIGHT_OVERLAY_ALPHA = 128;
    private static final int MAX_HIGHLIGHT_OVERLAY_ALPHA = 255;
    private static final Set<UUID> visibleOpponentCache = new HashSet<>();
    private static final Map<String, TabListTag> tabListTagCache = new HashMap<>();
    private static final Map<String, TabListTag> backendRatingTagCache = new HashMap<>();
    private static final Map<String, String> normalizedPlayerNameCache = new HashMap<>();
    private static final Map<String, Boolean> spectatorTeamNameCache = new HashMap<>();
    private static final Map<String, Integer> namedTeamColorCache = new HashMap<>();
    private static final Map<Class<?>, Map<String, Field>> reflectionFieldCache = new HashMap<>();
    private static final TabListTag UNKNOWN_RATING_TAG = new TabListTag("?", -1);
    private static final TabListTag[] parsedRatingTags = new TabListTag[100];
    private static UUID[] visibleOpponentUuids = new UUID[0];
    private static double[] visibleOpponentDistances = new double[0];
    private static String cachedRawServerAddress;
    private static String cachedNormalizedServerAddress;
    private static String cachedCheckedServerAddress;
    private static ArrayList<String> cachedAllowedServerAddresses;
    private static boolean cachedServerAllowed;
    private static long visibleOpponentCacheTick = Long.MIN_VALUE;
    private static UUID visibleOpponentCacheLocalPlayer;
    private static int visibleOpponentCacheLimit = -1;
    private static boolean visibleOpponentCacheEnabled;
    private static int visibleOpponentCachePlayerCount = -1;
    private static Object tabListTagCacheHandler;
    private static long tabListTagCacheTick = Long.MIN_VALUE;
    private static int tabListTagCacheSize = -1;
    private static long atomicsTierSlotCacheTick = Long.MIN_VALUE;
    private static boolean atomicsTierSlotEnabledCache;
    private static Class<?> atomicsClientClass;
    private static Method atomicsTierSuffixMethod;
    private static boolean atomicsTierSuffixMethodChecked;
    private LegionsFeatures() {
    }

    public static boolean isLegionsServer(MinecraftClient client) {
        if (client == null) {
            return false;
        }
        ServerInfo server = client.getCurrentServerEntry();
        if (server == null || server.address == null) {
            return client.isIntegratedServerRunning();
        }
        String address = normalizedCurrentServerAddress(server.address);
        if (LegionsClient.CONFIG == null || LegionsClient.CONFIG.allowedServerAddresses == null) {
            return address.contains("legions");
        }
        if (address.equals(cachedCheckedServerAddress)
                && LegionsClient.CONFIG.allowedServerAddresses.equals(cachedAllowedServerAddresses)) {
            return cachedServerAllowed;
        }

        cachedCheckedServerAddress = address;
        cachedAllowedServerAddresses = new ArrayList<>(LegionsClient.CONFIG.allowedServerAddresses);
        cachedServerAllowed = false;
        for (String allowedAddress : cachedAllowedServerAddresses) {
            String normalizedAllowedAddress = normalizeServerAddress(allowedAddress);
            if (!normalizedAllowedAddress.isBlank()
                    && (address.equals(normalizedAllowedAddress) || address.contains(normalizedAllowedAddress))) {
                cachedServerAllowed = true;
                break;
            }
        }
        return cachedServerAllowed;
    }

    public static String serverAddressesText() {
        if (LegionsClient.CONFIG == null || LegionsClient.CONFIG.allowedServerAddresses == null) {
            return "legions";
        }
        return String.join(", ", LegionsClient.CONFIG.allowedServerAddresses);
    }

    public static void setServerAddressesText(String text) {
        if (LegionsClient.CONFIG == null) {
            return;
        }
        LegionsClient.CONFIG.allowedServerAddresses = new ArrayList<>();
        if (text != null) {
            for (String part : text.split(",")) {
                String address = normalizeServerAddress(part);
                if (!address.isBlank() && !LegionsClient.CONFIG.allowedServerAddresses.contains(address)) {
                    LegionsClient.CONFIG.allowedServerAddresses.add(address);
                }
            }
        }
        LegionsClient.CONFIG.normalize();
    }

    private static String normalizeServerAddress(String address) {
        if (address == null) {
            return "";
        }
        String normalized = address.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("http://")) {
            normalized = normalized.substring("http://".length());
        } else if (normalized.startsWith("https://")) {
            normalized = normalized.substring("https://".length());
        }
        int slash = normalized.indexOf('/');
        return slash >= 0 ? normalized.substring(0, slash) : normalized;
    }

    private static String normalizedCurrentServerAddress(String address) {
        if (!address.equals(cachedRawServerAddress)) {
            cachedRawServerAddress = address;
            cachedNormalizedServerAddress = normalizeServerAddress(address);
        }
        return cachedNormalizedServerAddress;
    }

    public static Text customizeNametag(PlayerEntity player, Text original) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!LegionsClient.ratingNametagsEnabled(client)) {
            return original;
        }
        if (shouldUseAtomicsTierSlot(player)) {
            return original;
        }
        TabListTag tag = getRatingTag(client, realUsername(player));
        if (tag == null) {
            return original;
        }
        Text suffix = getNametagSuffix(player, tag);
        if (suffix == null) {
            return original;
        }
        Text base = ATOMICS_CLIENT_LOADED ? Text.literal(realUsername(player)) : original;
        String originalText = original.getString();
        if (!ATOMICS_CLIENT_LOADED && originalText.endsWith(suffix.getString())) {
            return original;
        }
        return Text.empty().append(base).append(suffix);
    }

    private static Text getNametagSuffix(PlayerEntity player, TabListTag tag) {
        if (tag.isUnknown()) {
            Text atomicsTier = getAtomicsTierSuffix(player);
            if (atomicsTier != null && !atomicsTier.getString().isBlank()) {
                return Text.empty().append(Text.literal(" ")).append(atomicsTier);
            }
        }

        String separator = ATOMICS_CLIENT_LOADED ? " " : " | ";
        return Text.empty().append(Text.literal(separator)).append(formatLegionsTag(tag));
    }

    public static int getOutlineColor(PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!LegionsClient.enabled(client) || client.player == null) {
            return 0;
        }
        int markedPlayerColor = LegionsPingController.enabledMarkedPlayerColor(player);
        if (markedPlayerColor != 0) {
            return markedPlayerColor;
        }
        if (shouldHighlightTeamAsSpectator(client, player)) {
            return getTeamOutlineColor(player);
        }
        if (LegionsClient.CONFIG.enemyHighlightsEnabled && isOpponent(client.player, player)) {
            return getTeamOutlineColor(player);
        }
        return 0;
    }

    public static int getOverlayStyle(PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!LegionsPingController.isMarkedPlayer(player) && isAutomaticFoeOverlay(client, player)) {
            return LegionsPlayerOverlayColorContext.STYLE_ARMOR_FULL;
        }
        return LegionsPlayerOverlayColorContext.STYLE_FULL;
    }

    public static int getFilledOverlayColor(PlayerEntity player, int overlayColor, int overlayStyle) {
        if (overlayColor == 0 || !usesFilledOverlay(overlayStyle)) {
            return -1;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (!usesHighlightOverlayAlpha(client, player)) {
            return overlayColor;
        }

        int alpha = highlightOverlayAlpha(player);
        return (overlayColor & 0x00FFFFFF) | (alpha << 24);
    }

    private static boolean usesFilledOverlay(int overlayStyle) {
        return overlayStyle == LegionsPlayerOverlayColorContext.STYLE_FULL
                || overlayStyle == LegionsPlayerOverlayColorContext.STYLE_OUTLINE_FULL
                || overlayStyle == LegionsPlayerOverlayColorContext.STYLE_PULSE
                || overlayStyle == LegionsPlayerOverlayColorContext.STYLE_ARMOR_FULL;
    }

    private static boolean usesHighlightOverlayAlpha(MinecraftClient client, PlayerEntity player) {
        return !LegionsPingController.isMarkedPlayer(player)
                && (isAutomaticFoeOverlay(client, player) || shouldHighlightTeamAsSpectator(client, player));
    }

    private static boolean isAutomaticFoeOverlay(MinecraftClient client, PlayerEntity player) {
        return LegionsClient.enabled(client)
                && client.player != null
                && player != null
                && LegionsClient.CONFIG.enemyHighlightsEnabled
                && isOpponent(client.player, player)
                && !shouldHighlightTeamAsSpectator(client, player);
    }

    private static int highlightOverlayAlpha(PlayerEntity player) {
        if (LegionsClient.CONFIG == null || !LegionsClient.CONFIG.dynamicHighlightOpacityEnabled) {
            return MAX_HIGHLIGHT_OVERLAY_ALPHA;
        }
        if (player == null) {
            return MIN_HIGHLIGHT_OVERLAY_ALPHA;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        TabListTag tag = getRatingTag(client, realUsername(player));
        if (tag == null || tag.isUnknown()) {
            return MIN_HIGHLIGHT_OVERLAY_ALPHA;
        }

        int clampedRating = clamp(tag.numericRating, MIN_QUIP_OVERLAY_RATING, MAX_QUIP_OVERLAY_RATING);
        float opacity = (float) (clampedRating - MIN_QUIP_OVERLAY_RATING)
                / (MAX_QUIP_OVERLAY_RATING - MIN_QUIP_OVERLAY_RATING);
        return MIN_HIGHLIGHT_OVERLAY_ALPHA + Math.round(opacity * (MAX_HIGHLIGHT_OVERLAY_ALPHA - MIN_HIGHLIGHT_OVERLAY_ALPHA));
    }

    public static boolean shouldHidePlayerModel(PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!LegionsClient.enabled(client) || client.world == null || client.player == null || player == null) {
            return false;
        }
        if (player == client.player || LegionsPingController.isMarkedPlayer(player)) {
            return false;
        }
        if (LegionsClient.CONFIG.spectatorGlowEnabled && isSpectatorTeam(client.player)) {
            return false;
        }

        boolean opponent = isOpponent(client.player, player);
        if (opponent && opponentLimitEnabled() && !visibleOpponentCache(client).contains(player.getUuid())) {
            return true;
        }

        return shouldCullForRenderOptimization(client, player);
    }

    private static boolean shouldCullForRenderOptimization(MinecraftClient client, PlayerEntity player) {
        if (!playerRenderOptimizationEnabled()) {
            return false;
        }

        int renderDistance = LegionsAdaptivePerformance.effectivePlayerRenderDistance(
                LegionsClient.CONFIG.playerRenderDistance);
        double maxDistanceSquared = (double) renderDistance * renderDistance;
        return player.squaredDistanceTo(client.player) > maxDistanceSquared;
    }

    private static Set<UUID> visibleOpponentCache(MinecraftClient client) {
        int playerCount = client.world.getPlayers().size();
        long tick = client.world.getTime();
        int visibleOpponents = LegionsAdaptivePerformance.effectiveOpponentLimit(
                LegionsClient.CONFIG.opponentLimit);
        boolean limitEnabled = opponentLimitEnabled();
        if (visibleOpponentCacheTick == tick
                && visibleOpponentCacheLocalPlayer != null
                && visibleOpponentCacheLocalPlayer.equals(client.player.getUuid())
                && visibleOpponentCacheLimit == visibleOpponents
                && visibleOpponentCacheEnabled == limitEnabled
                && visibleOpponentCachePlayerCount == playerCount) {
            return visibleOpponentCache;
        }

        visibleOpponentCache.clear();
        visibleOpponentCacheTick = tick;
        visibleOpponentCacheLocalPlayer = client.player.getUuid();
        visibleOpponentCacheLimit = visibleOpponents;
        visibleOpponentCacheEnabled = limitEnabled;
        visibleOpponentCachePlayerCount = playerCount;

        if (!limitEnabled) {
            return visibleOpponentCache;
        }

        PlayerEntity localPlayer = client.player;
        if (visibleOpponents == 0) {
            return visibleOpponentCache;
        }

        ensureVisibleOpponentCapacity(visibleOpponents);
        int selectedOpponents = 0;
        for (PlayerEntity candidate : client.world.getPlayers()) {
            if (isOpponent(localPlayer, candidate)) {
                selectedOpponents = insertVisibleOpponent(candidate.getUuid(), candidate.squaredDistanceTo(localPlayer),
                        visibleOpponents, selectedOpponents);
            }
        }
        for (int i = 0; i < selectedOpponents; i++) {
            visibleOpponentCache.add(visibleOpponentUuids[i]);
        }
        Arrays.fill(visibleOpponentUuids, 0, selectedOpponents, null);
        return visibleOpponentCache;
    }

    private static boolean opponentLimitEnabled() {
        return LegionsClient.CONFIG.opponentLimitEnabled || LegionsAdaptivePerformance.isActivelyReducing();
    }

    private static boolean playerRenderOptimizationEnabled() {
        return LegionsClient.CONFIG.playerRenderOptimizationEnabled || LegionsAdaptivePerformance.isActivelyReducing();
    }

    private static void ensureVisibleOpponentCapacity(int capacity) {
        if (visibleOpponentUuids.length >= capacity) {
            return;
        }
        visibleOpponentUuids = new UUID[capacity];
        visibleOpponentDistances = new double[capacity];
    }

    private static int insertVisibleOpponent(UUID uuid, double distanceSquared, int limit, int size) {
        int insertionIndex = Math.min(size, limit - 1);
        while (insertionIndex > 0 && distanceSquared < visibleOpponentDistances[insertionIndex - 1]) {
            insertionIndex--;
        }
        if (size >= limit && insertionIndex == limit - 1
                && distanceSquared >= visibleOpponentDistances[insertionIndex]) {
            return size;
        }

        int movedEntries = Math.min(size, limit - 1) - insertionIndex;
        if (movedEntries > 0) {
            System.arraycopy(visibleOpponentUuids, insertionIndex, visibleOpponentUuids, insertionIndex + 1, movedEntries);
            System.arraycopy(visibleOpponentDistances, insertionIndex, visibleOpponentDistances, insertionIndex + 1, movedEntries);
        }
        visibleOpponentUuids[insertionIndex] = uuid;
        visibleOpponentDistances[insertionIndex] = distanceSquared;
        return Math.min(size + 1, limit);
    }

    public static boolean shouldHidePlayerRenderState(PlayerEntityRenderState state) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || state == null) {
            return false;
        }
        Entity entity = client.world.getEntityById(state.id);
        return entity instanceof PlayerEntity player && shouldHidePlayerModel(player);
    }

    public static int getRating(MinecraftClient client, String playerName) {
        TabListTag tag = getRatingTag(client, playerName);
        return tag == null ? -1 : tag.numericRating;
    }

    public static int getQuips(MinecraftClient client, String playerName) {
        return getRating(client, playerName);
    }

    public static boolean shouldSuppressAtomicsTierSuffix(PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!LegionsClient.ratingNametagsEnabled(client) || player == null) {
            return false;
        }
        TabListTag tag = getRatingTag(client, realUsername(player));
        return tag != null && !tag.isUnknown();
    }

    public static Text getAtomicsTierSlotReplacement(PlayerEntity player) {
        if (!shouldUseAtomicsTierSlot(player)) {
            return null;
        }
        TabListTag tag = getRatingTag(MinecraftClient.getInstance(), realUsername(player));
        return tag == null || tag.isUnknown() ? null : formatLegionsTag(tag);
    }

    public static Text getAtomicsUnknownTierSlotFallback(PlayerEntity player) {
        if (!shouldUseAtomicsTierSlot(player)) {
            return null;
        }
        TabListTag tag = getRatingTag(MinecraftClient.getInstance(), realUsername(player));
        return tag != null && tag.isUnknown() ? formatLegionsTag(tag) : null;
    }

    private static boolean shouldUseAtomicsTierSlot(PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ATOMICS_CLIENT_LOADED || player == null || !LegionsClient.ratingNametagsEnabled(client)) {
            return false;
        }
        return atomicsTierSlotEnabled(client);
    }

    private static boolean atomicsTierSlotEnabled(MinecraftClient client) {
        long tick = client.world == null ? Long.MIN_VALUE : client.world.getTime();
        if (atomicsTierSlotCacheTick == tick) {
            return atomicsTierSlotEnabledCache;
        }

        atomicsTierSlotCacheTick = tick;
        try {
            Object config = getStaticField(atomicsClientClass(), "CONFIG");
            if (config == null || !getBooleanField(config, "enabled")) {
                atomicsTierSlotEnabledCache = false;
                return false;
            }
            Object pvp = getField(config, "pvp");
            atomicsTierSlotEnabledCache = pvp != null && getBooleanField(pvp, "opponentStatsNametagEnabled");
            return atomicsTierSlotEnabledCache;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LegionsClient.LOGGER.debug("Atomics tier slot config is not available.", e);
            atomicsTierSlotEnabledCache = false;
            return false;
        }
    }

    private static TabListTag getTabListTag(MinecraftClient client, String playerName) {
        if (client == null || client.getNetworkHandler() == null || playerName == null) {
            return null;
        }
        refreshTabListTagCache(client);
        return tabListTagCache.get(normalizedPlayerName(playerName));
    }

    private static TabListTag getRatingTag(MinecraftClient client, String playerName) {
        TabListTag tabListTag = getTabListTag(client, playerName);
        if (tabListTag != null && !tabListTag.isUnknown()) {
            return tabListTag;
        }

        TabListTag backendTag = getBackendRatingTag(client, playerName);
        if (backendTag != null) {
            return backendTag;
        }
        if (LegionsClient.enabled(client)) {
            return tabListTag;
        }
        return LegionsClient.ratingNametagsEnabled(client) ? unknownRatingTag() : null;
    }

    private static TabListTag getBackendRatingTag(MinecraftClient client, String playerName) {
        if (!canUseBackendRatings(client) || playerName == null || playerName.isBlank()) {
            return null;
        }

        String key = normalizedPlayerName(playerName.trim());
        TabListTag cachedTag = backendRatingTagCache.get(key);
        if (cachedTag != null) {
            return cachedTag;
        }

        Double rating = LegionsRatingBackendCache.getCachedNormalized(key);
        if (rating == null) {
            LegionsRatingBackendCache.preloadAll();
            return null;
        }
        TabListTag tag = backendRatingTag(rating);
        backendRatingTagCache.put(key, tag);
        return tag;
    }

    private static boolean canUseBackendRatings(MinecraftClient client) {
        return LegionsClient.enabled(client) || LegionsClient.ratingNametagsEnabled(client);
    }

    private static void refreshTabListTagCache(MinecraftClient client) {
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        int size = networkHandler.getPlayerList().size();
        long tick = client.world == null ? Long.MIN_VALUE : client.world.getTime();
        if (tabListTagCacheHandler == networkHandler && tabListTagCacheTick == tick && tabListTagCacheSize == size) {
            return;
        }

        tabListTagCache.clear();
        tabListTagCacheHandler = networkHandler;
        tabListTagCacheTick = tick;
        tabListTagCacheSize = size;

        for (PlayerListEntry entry : networkHandler.getPlayerList()) {
            Text displayName = entry.getDisplayName();
            String text = displayName == null ? entry.getProfile().name() : displayName.getString();
            TabListTag tag = parseTabListTag(text);
            if (tag != null) {
                tabListTagCache.put(normalizedPlayerName(entry.getProfile().name()), tag);
            }
        }
    }

    private static Text getAtomicsTierSuffix(PlayerEntity player) {
        Method method = getAtomicsTierSuffixMethod();
        if (method == null) {
            return null;
        }
        try {
            Object value = method.invoke(null, player);
            return value instanceof Text text ? text : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LegionsClient.LOGGER.debug("Failed to get Atomics tier suffix for {}.", realUsername(player), e);
            return null;
        }
    }

    private static Method getAtomicsTierSuffixMethod() {
        if (!ATOMICS_CLIENT_LOADED) {
            return null;
        }
        if (atomicsTierSuffixMethodChecked) {
            return atomicsTierSuffixMethod;
        }
        atomicsTierSuffixMethodChecked = true;
        try {
            Class<?> manager = Class.forName("com.atomics.client.TierWeightManager");
            atomicsTierSuffixMethod = manager.getDeclaredMethod("getNameSuffix", PlayerEntity.class);
            atomicsTierSuffixMethod.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LegionsClient.LOGGER.debug("Atomics tier suffix fallback is not available.", e);
            atomicsTierSuffixMethod = null;
        }
        return atomicsTierSuffixMethod;
    }

    private static TabListTag parseTabListTag(String text) {
        int lastStart = text.length() - 3;
        for (int start = 0; start <= lastStart; start++) {
            if (text.charAt(start) != '[') {
                continue;
            }
            char first = text.charAt(start + 1);
            if (first == '?' && text.charAt(start + 2) == ']') {
                return UNKNOWN_RATING_TAG;
            }
            if (start + 4 >= text.length()
                    || first < '0' || first > '9'
                    || text.charAt(start + 2) != '.'
                    || text.charAt(start + 3) < '0' || text.charAt(start + 3) > '9'
                    || text.charAt(start + 4) != ']') {
                continue;
            }

            int whole = first - '0';
            int tenth = text.charAt(start + 3) - '0';
            int index = whole * 10 + tenth;
            TabListTag tag = parsedRatingTags[index];
            if (tag == null) {
                tag = new TabListTag(whole + "." + tenth, whole * 1000 + tenth * 100);
                parsedRatingTags[index] = tag;
            }
            return tag;
        }
        return null;
    }

    private static TabListTag backendRatingTag(double rating) {
        int ratingTenths = (int) Math.round(rating * 10.0);
        String value = ratingTenths / 10 + "." + ratingTenths % 10;
        int numericRating = (int) Math.round(rating * 1000.0);
        return new TabListTag(value, numericRating);
    }

    private static TabListTag unknownRatingTag() {
        return UNKNOWN_RATING_TAG;
    }

    private static Style quipStyle(TabListTag tag) {
        Style style = Style.EMPTY.withColor(TextColor.fromRgb(quipColor(tag)));
        return tag.numericRating >= 2000 ? style.withBold(true) : style;
    }

    private static Text formatLegionsTag(TabListTag tag) {
        Style style = quipStyle(tag);
        return Text.empty()
                .append(Text.literal("[").setStyle(style))
                .append(Text.literal(tag.value).setStyle(style))
                .append(Text.literal("]").setStyle(style));
    }

    private static int quipColor(TabListTag tag) {
        int rating = tag.numericRating;
        if (rating < 0) {
            return QUIP_UNKNOWN_COLOR;
        }
        if (rating >= 2000) {
            return 0xFC3200;
        }
        if (rating >= 1900) {
            return 0xFC5400;
        }
        if (rating >= 1800) {
            return 0xFC8700;
        }
        if (rating >= 1700) {
            return 0xEBA800;
        }
        if (rating >= 1600) {
            return 0xC9C900;
        }
        if (rating >= 1500) {
            return 0xA8EB00;
        }
        if (rating >= 1400) {
            return 0x85C700;
        }
        if (rating >= 1300) {
            return 0x64A811;
        }
        if (rating >= 1200) {
            return 0x448744;
        }
        if (rating >= 1100) {
            return 0x226575;
        }
        if (rating >= 1000) {
            return 0x1143A8;
        }
        if (rating >= 900) {
            return 0x1064C9;
        }
        if (rating >= 800) {
            return 0x3285D9;
        }
        if (rating >= 700) {
            return 0x729BE8;
        }
        if (rating >= 600) {
            return 0xA7C1F2;
        }
        if (rating >= 500) {
            return 0xCBDAF7;
        }
        if (rating >= 400) {
            return 0xDCE7FC;
        }
        if (rating >= 300) {
            return 0xF0F5FF;
        }
        if (rating >= 200) {
            return 0xF0F0F0;
        }
        if (rating >= 100) {
            return 0xFFFFFF;
        }
        return 0xFCFCFC;
    }

    private static Object getStaticField(Class<?> owner, String name) throws ReflectiveOperationException {
        return cachedField(owner, name).get(null);
    }

    private static Object getField(Object owner, String name) throws ReflectiveOperationException {
        return cachedField(owner.getClass(), name).get(owner);
    }

    private static boolean getBooleanField(Object owner, String name) throws ReflectiveOperationException {
        Object value = getField(owner, name);
        return value instanceof Boolean bool && bool;
    }

    private static Field cachedField(Class<?> owner, String name) throws NoSuchFieldException {
        Map<String, Field> ownerFields = reflectionFieldCache.computeIfAbsent(owner, ignored -> new HashMap<>());
        Field field = ownerFields.get(name);
        if (field == null) {
            field = owner.getDeclaredField(name);
            field.setAccessible(true);
            ownerFields.put(name, field);
        }
        return field;
    }

    private static Class<?> atomicsClientClass() throws ClassNotFoundException {
        if (atomicsClientClass == null) {
            atomicsClientClass = Class.forName("com.atomics.client.AtomicsClient");
        }
        return atomicsClientClass;
    }

    private static int getTeamOutlineColor(PlayerEntity player) {
        Team team = player.getScoreboardTeam();
        if (team == null) {
            return 0xFFFFFFFF;
        }

        int namedColor = getNamedTeamColor(team.getName());
        if (namedColor != 0) {
            return namedColor;
        }

        Formatting color = team.getColor();
        Integer rgb = color == null ? null : color.getColorValue();
        return rgb == null ? 0xFFFFFFFF : 0xFF000000 | rgb;
    }

    private static int getNamedTeamColor(String name) {
        return namedTeamColorCache.computeIfAbsent(name, LegionsFeatures::computeNamedTeamColor);
    }

    private static int computeNamedTeamColor(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.contains("red")) {
            return 0xFFFF5555;
        }
        if (normalized.contains("blue")) {
            return 0xFF5555FF;
        }
        if (normalized.contains("green")) {
            return 0xFF55FF55;
        }
        if (normalized.contains("yellow")) {
            return 0xFFFFFF55;
        }
        return 0;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean shouldHighlightTeamAsSpectator(MinecraftClient client, PlayerEntity player) {
        return client != null
                && LegionsClient.CONFIG.spectatorGlowEnabled
                && client.player != null
                && player != null
                && player != client.player
                && isSpectatorTeam(client.player)
                && !isSpectatorTeam(player);
    }

    public static boolean isTeammate(PlayerEntity local, PlayerEntity other) {
        if (local == null || other == null) {
            return false;
        }
        if (local == other) {
            return true;
        }
        Team localTeam = local.getScoreboardTeam();
        Team otherTeam = other.getScoreboardTeam();
        return localTeam != null && otherTeam != null && localTeam.getName().equals(otherTeam.getName());
    }

    public static boolean isOpponent(PlayerEntity local, PlayerEntity other) {
        if (local == null || other == null || local == other) {
            return false;
        }
        if (isSpectatorTeam(local) || isSpectatorTeam(other)) {
            return false;
        }
        Team localTeam = local.getScoreboardTeam();
        Team otherTeam = other.getScoreboardTeam();
        if (localTeam != null && otherTeam != null) {
            return !localTeam.getName().equals(otherTeam.getName());
        }
        return true;
    }

    public static boolean isSpectatorTeam(PlayerEntity player) {
        if (player == null || player.isSpectator()) {
            return true;
        }
        Team team = player.getScoreboardTeam();
        return team != null && isSpectatorTeamName(team.getName());
    }

    static boolean isSpectatorTeamName(String name) {
        return name != null && spectatorTeamNameCache.computeIfAbsent(name,
                value -> value.toLowerCase(Locale.ROOT).contains("spectator"));
    }

    static String normalizedPlayerName(String name) {
        return normalizedPlayerNameCache.computeIfAbsent(name, value -> value.toLowerCase(Locale.ROOT));
    }

    public static String realUsername(PlayerEntity player) {
        return player.getGameProfile().name();
    }

    private static final class TabListTag {
        private final String value;
        private final int numericRating;

        private TabListTag(String value, int numericRating) {
            this.value = value;
            this.numericRating = numericRating;
        }

        private boolean isUnknown() {
            return numericRating < 0;
        }
    }
}
