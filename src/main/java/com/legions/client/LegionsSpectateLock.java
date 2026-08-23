package com.legions.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class LegionsSpectateLock {
    private static final double LOOK_RANGE = 128.0;
    private static final double MAX_SECOND_PLAYER_DISTANCE = 96.0;
    private static final double MAX_SECOND_PLAYER_DISTANCE_SQUARED =
            MAX_SECOND_PLAYER_DISTANCE * MAX_SECOND_PLAYER_DISTANCE;
    private static final String MISSING_SECOND_PLAYER_REASON =
            "none nearby within " + (int) MAX_SECOND_PLAYER_DISTANCE + " blocks";
    private static String lockedPlayerName;
    private static boolean savedAtomicsState;
    private static boolean savedDualSpectateEnabled;
    private static boolean savedDualSpectateAutoFill;
    private static String savedDualSpectatePlayerOne = "";
    private static String savedDualSpectatePlayerTwo = "";
    private static String lastSecondPlayerName = "";
    private static String lastLoggedLockedName = "";
    private static String lastLoggedSecondName = "";
    private static String lastLoggedMissingReason = "";
    private static Class<?> atomicsClientClass;
    private static final Map<Class<?>, Map<String, Field>> FIELD_CACHE = new HashMap<>();

    private LegionsSpectateLock() {
    }

    public static void toggleLockToPlayer(MinecraftClient client, String playerName) {
        if (!LegionsClient.isAtomicsClientLoaded()) {
            sendAction(client, "Atomics Client is required for dual spectate lock");
            return;
        }
        if (!isLockAvailable(client)) {
            sendAction(client, "Dual spectate lock is unavailable");
            return;
        }
        if (playerName == null || playerName.isBlank()) {
            sendAction(client, "Enter a player name to lock dual spectate");
            return;
        }

        String name = playerName.trim();
        if (name.equalsIgnoreCase(lockedPlayerName)) {
            unlock(client, true);
        } else {
            lockTo(client, name);
        }
    }

    public static boolean isLockedTo(String playerName) {
        return lockedPlayerName != null && playerName != null && lockedPlayerName.equalsIgnoreCase(playerName.trim());
    }

    public static boolean hasLock() {
        return lockedPlayerName != null;
    }

    public static boolean isLockedPair(PlayerEntity first, PlayerEntity second) {
        if (lockedPlayerName == null || first == null || second == null || first.getUuid().equals(second.getUuid())) {
            return false;
        }
        return isExternalSpectateCandidate(first)
                && isExternalSpectateCandidate(second)
                && (isLockedTo(LegionsFeatures.realUsername(first)) || isLockedTo(LegionsFeatures.realUsername(second)));
    }

    public static void handleKeyPress(MinecraftClient client) {
        if (!LegionsClient.isAtomicsClientLoaded()) {
            sendAction(client, "Atomics Client is required for dual spectate lock");
            return;
        }
        if (!isLockAvailable(client)) {
            sendAction(client, "Dual spectate lock is unavailable");
            return;
        }
        PlayerEntity target = findLookedAtPlayer(client);
        if (target == null) {
            if (lockedPlayerName != null) {
                unlock(client, true);
            } else {
                sendAction(client, "Look at a player to lock dual spectate");
            }
            return;
        }

        String name = LegionsFeatures.realUsername(target);
        if (name.equalsIgnoreCase(lockedPlayerName)) {
            unlock(client, true);
            return;
        }

        lockTo(client, name);
    }

    public static void tick(MinecraftClient client) {
        if (lockedPlayerName == null) {
            return;
        }
        if (!LegionsClient.isAtomicsClientLoaded() || !isLockAvailable(client) || client.player == null
                || client.world == null) {
            unlock(client, false);
            return;
        }

        Object pvp;
        boolean autoFill;
        try {
            pvp = atomicsPvp();
            if (!getBooleanField(pvp, "dualSpectateEnabled")) {
                unlock(client, true, false);
                return;
            }
            autoFill = getBooleanField(pvp, "dualSpectateAutoFill");
            if (savedAtomicsState) {
                savedDualSpectateAutoFill = autoFill;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LegionsClient.LOGGER.debug("Failed to read Atomics dual spectate lock state.", e);
            unlock(client, false);
            return;
        }

        PlayerEntity lockedPlayer = findPlayer(client, lockedPlayerName);
        if (!isDualSpectateCandidate(client, lockedPlayer)) {
            if (autoFill) {
                lockedPlayer = findAutoFillReplacement(client, pvp, lockedPlayerName);
                if (lockedPlayer != null) {
                    lockedPlayerName = LegionsFeatures.realUsername(lockedPlayer);
                    lastSecondPlayerName = "";
                    clearLastLoggedPair();
                    sendAction(client, "Dual spectate relocked: " + lockedPlayerName);
                }
            }

            if (!isDualSpectateCandidate(client, lockedPlayer)) {
                clearAtomicsPair(autoFill);
                lastSecondPlayerName = "";
                logPairChange(lockedPlayerName, "", -1.0, "target not currently loaded");
                return;
            }
        }

        try {
            updateLockedPair(client, pvp, lockedPlayer, autoFill);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LegionsClient.LOGGER.debug("Failed to update Atomics dual spectate lock.", e);
            unlock(client, false);
        }
    }

    private static PlayerEntity findAutoFillReplacement(MinecraftClient client, Object pvp, String previousLockedName) {
        PlayerEntity previousSecond = findReplacementPlayer(client, lastSecondPlayerName, previousLockedName);
        if (previousSecond != null) {
            return previousSecond;
        }

        try {
            PlayerEntity playerOne = findReplacementPlayer(client, getStringField(pvp, "dualSpectatePlayerOne"), previousLockedName);
            if (playerOne != null) {
                return playerOne;
            }
            PlayerEntity playerTwo = findReplacementPlayer(client, getStringField(pvp, "dualSpectatePlayerTwo"), previousLockedName);
            if (playerTwo != null) {
                return playerTwo;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LegionsClient.LOGGER.debug("Failed to read Atomics auto-filled dual spectate pair.", e);
        }

        return findBestAutoFillPlayer(client, previousLockedName);
    }

    private static PlayerEntity findReplacementPlayer(MinecraftClient client, String playerName, String previousLockedName) {
        if (playerName == null || playerName.isBlank()
                || previousLockedName != null && playerName.trim().equalsIgnoreCase(previousLockedName.trim())) {
            return null;
        }

        PlayerEntity player = findPlayer(client, playerName);
        return isDualSpectateCandidate(client, player) ? player : null;
    }

    private static PlayerEntity findBestAutoFillPlayer(MinecraftClient client, String previousLockedName) {
        PlayerEntity paired = findBestAutoFillPlayer(client, previousLockedName, true);
        if (paired != null) {
            return paired;
        }

        paired = findBestAutoFillPlayer(client, previousLockedName, false);
        if (paired != null) {
            return paired;
        }

        PlayerEntity best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        Entity camera = client.getCameraEntity() == null ? client.player : client.getCameraEntity();
        for (PlayerEntity candidate : client.world.getPlayers()) {
            if (!isReplacementCandidate(client, candidate, previousLockedName)) {
                continue;
            }

            double distance = camera.squaredDistanceTo(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static PlayerEntity findBestAutoFillPlayer(MinecraftClient client, String previousLockedName,
                                                       boolean requireOpponent) {
        PlayerEntity bestFirst = null;
        PlayerEntity bestSecond = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (PlayerEntity first : client.world.getPlayers()) {
            if (!isReplacementCandidate(client, first, previousLockedName)) {
                continue;
            }

            for (PlayerEntity second : client.world.getPlayers()) {
                if (!isDualSpectateCandidate(client, second)
                        || first.getUuid().equals(second.getUuid())
                        || requireOpponent && !LegionsFeatures.isOpponent(first, second)) {
                    continue;
                }

                double distance = first.squaredDistanceTo(second);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestFirst = first;
                    bestSecond = second;
                }
            }
        }

        return closerToCamera(client, bestFirst, bestSecond);
    }

    private static boolean isReplacementCandidate(MinecraftClient client, PlayerEntity player, String previousLockedName) {
        return isDualSpectateCandidate(client, player)
                && (previousLockedName == null || !LegionsFeatures.realUsername(player).equalsIgnoreCase(previousLockedName.trim()));
    }

    private static PlayerEntity closerToCamera(MinecraftClient client, PlayerEntity first, PlayerEntity second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }

        Entity camera = client.getCameraEntity() == null ? client.player : client.getCameraEntity();
        return camera.squaredDistanceTo(first) <= camera.squaredDistanceTo(second) ? first : second;
    }

    private static void updateLockedPair(MinecraftClient client, Object pvp, PlayerEntity lockedPlayer,
                                         boolean autoFill) throws ReflectiveOperationException {
        setField(pvp, "dualSpectateEnabled", true);
        setField(pvp, "dualSpectateAutoFill", autoFill);
        String lockedName = LegionsFeatures.realUsername(lockedPlayer);
        setField(pvp, "dualSpectatePlayerOne", lockedName);

        PlayerEntity second = findBestSecondPlayer(client, lockedPlayer);
        String secondName = second == null ? "" : LegionsFeatures.realUsername(second);
        setField(pvp, "dualSpectatePlayerTwo", secondName);
        lastSecondPlayerName = secondName;
        logPairChange(lockedName, secondName,
                second == null ? -1.0 : lockedPlayer.squaredDistanceTo(second),
                MISSING_SECOND_PLAYER_REASON);
    }

    private static void lockTo(MinecraftClient client, String playerName) {
        try {
            Object pvp = atomicsPvp();
            if (!savedAtomicsState) {
                savedDualSpectateEnabled = getBooleanField(pvp, "dualSpectateEnabled");
                savedDualSpectateAutoFill = getBooleanField(pvp, "dualSpectateAutoFill");
                savedDualSpectatePlayerOne = getStringField(pvp, "dualSpectatePlayerOne");
                savedDualSpectatePlayerTwo = getStringField(pvp, "dualSpectatePlayerTwo");
                savedAtomicsState = true;
            }

            lockedPlayerName = playerName;
            lastSecondPlayerName = "";
            clearLastLoggedPair();
            setField(pvp, "dualSpectateEnabled", true);
            tick(client);
            String suffix = lastSecondPlayerName == null || lastSecondPlayerName.isBlank() ? "" : " + " + lastSecondPlayerName;
            sendAction(client, "Dual spectate locked: " + playerName + suffix);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LegionsClient.LOGGER.debug("Failed to lock Atomics dual spectate.", e);
            sendAction(client, "Could not lock Atomics dual spectate");
        }
    }

    private static void unlock(MinecraftClient client, boolean notify) {
        unlock(client, notify, true);
    }

    private static void unlock(MinecraftClient client, boolean notify, boolean restoreEnabled) {
        String previous = lockedPlayerName;
        lockedPlayerName = null;
        lastSecondPlayerName = "";
        clearLastLoggedPair();
        try {
            if (savedAtomicsState) {
                Object pvp = atomicsPvp();
                setField(pvp, "dualSpectateAutoFill", savedDualSpectateAutoFill);
                setField(pvp, "dualSpectatePlayerOne", savedDualSpectatePlayerOne);
                setField(pvp, "dualSpectatePlayerTwo", savedDualSpectatePlayerTwo);
                setField(pvp, "dualSpectateEnabled", restoreEnabled && savedDualSpectateEnabled);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LegionsClient.LOGGER.debug("Failed to restore Atomics dual spectate settings.", e);
        } finally {
            savedAtomicsState = false;
            savedDualSpectatePlayerOne = "";
            savedDualSpectatePlayerTwo = "";
        }
        if (notify) {
            sendAction(client, previous == null || previous.isBlank() ? "Dual spectate unlocked" : "Dual spectate unlocked: " + previous);
        }
    }

    private static PlayerEntity findBestSecondPlayer(MinecraftClient client, PlayerEntity lockedPlayer) {
        PlayerEntity opponent = findBestSecondPlayer(client, lockedPlayer, true);
        return opponent == null ? findBestSecondPlayer(client, lockedPlayer, false) : opponent;
    }

    private static void clearAtomicsPair(boolean autoFill) {
        try {
            Object pvp = atomicsPvp();
            setField(pvp, "dualSpectateEnabled", true);
            setField(pvp, "dualSpectateAutoFill", autoFill);
            setField(pvp, "dualSpectatePlayerOne", autoFill || lockedPlayerName == null ? "" : lockedPlayerName);
            setField(pvp, "dualSpectatePlayerTwo", "");
        } catch (ReflectiveOperationException | RuntimeException e) {
            LegionsClient.LOGGER.debug("Failed to clear pending Atomics dual spectate lock.", e);
        }
    }

    private static PlayerEntity findBestSecondPlayer(MinecraftClient client, PlayerEntity lockedPlayer, boolean requireOpponent) {
        PlayerEntity best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (PlayerEntity candidate : client.world.getPlayers()) {
            if (!isDualSpectateCandidate(client, candidate)
                    || candidate.getUuid().equals(lockedPlayer.getUuid())
                    || requireOpponent && !LegionsFeatures.isOpponent(lockedPlayer, candidate)) {
                continue;
            }

            double distance = lockedPlayer.squaredDistanceTo(candidate);
            if (distance > MAX_SECOND_PLAYER_DISTANCE_SQUARED) {
                continue;
            }

            double facingBonus = Math.max(0.0, facingDot(lockedPlayer, candidate))
                    + Math.max(0.0, facingDot(candidate, lockedPlayer));
            double score = distance - facingBonus * 10.0;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static void logPairChange(String lockedName, String secondName, double secondDistanceSquared,
                                      String missingReason) {
        if (lockedName.equals(lastLoggedLockedName)
                && secondName.equals(lastLoggedSecondName)
                && missingReason.equals(lastLoggedMissingReason)) {
            return;
        }

        lastLoggedLockedName = lockedName;
        lastLoggedSecondName = secondName;
        lastLoggedMissingReason = missingReason;
        if (secondName == null || secondName.isBlank()) {
            LegionsClient.LOGGER.info("Dual spectate lock pair: {} + {}", lockedName, missingReason);
        } else {
            LegionsClient.LOGGER.info("Dual spectate lock pair: {} + {} ({} blocks)",
                    lockedName, secondName, Math.round(Math.sqrt(secondDistanceSquared)));
        }
    }

    private static void clearLastLoggedPair() {
        lastLoggedLockedName = "";
        lastLoggedSecondName = "";
        lastLoggedMissingReason = "";
    }

    private static double facingDot(PlayerEntity from, PlayerEntity to) {
        Vec3d direction = to.getEyePos().subtract(from.getEyePos());
        if (direction.lengthSquared() < 0.0001) {
            return 1.0;
        }
        return from.getRotationVec(1.0f).normalize().dotProduct(direction.normalize());
    }

    private static boolean isLockAvailable(MinecraftClient client) {
        return LegionsClient.CONFIG != null
                && LegionsClient.CONFIG.enabled
                && client != null
                && client.player != null
                && client.world != null;
    }

    private static boolean isDualSpectateCandidate(MinecraftClient client, PlayerEntity player) {
        return player != null
                && client != null
                && client.player != null
                && !player.getUuid().equals(client.player.getUuid())
                && isExternalSpectateCandidate(player);
    }

    private static boolean isExternalSpectateCandidate(PlayerEntity player) {
        return player != null
                && !player.isRemoved()
                && !player.isDead()
                && player.isAlive();
    }

    private static PlayerEntity findLookedAtPlayer(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            return null;
        }
        if (client.crosshairTarget instanceof EntityHitResult entityHitResult
                && entityHitResult.getEntity() instanceof PlayerEntity player
                && isDualSpectateCandidate(client, player)) {
            return player;
        }
        if (client.targetedEntity instanceof PlayerEntity player && isDualSpectateCandidate(client, player)) {
            return player;
        }

        Entity camera = client.getCameraEntity() == null ? client.player : client.getCameraEntity();
        Vec3d start = camera.getCameraPosVec(1.0f);
        Vec3d direction = camera.getRotationVec(1.0f);
        Vec3d end = start.add(direction.multiply(LOOK_RANGE));

        PlayerEntity best = null;
        double bestDistanceSq = LOOK_RANGE * LOOK_RANGE;
        for (PlayerEntity candidate : client.world.getPlayers()) {
            if (!isDualSpectateCandidate(client, candidate)) {
                continue;
            }

            Box box = candidate.getBoundingBox().expand(Math.max(0.35, candidate.getTargetingMargin() + 0.3));
            Optional<Vec3d> hit = box.raycast(start, end);
            if (hit.isEmpty()) {
                continue;
            }

            double distanceSq = start.squaredDistanceTo(hit.get());
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                best = candidate;
            }
        }
        return best;
    }

    private static PlayerEntity findPlayer(MinecraftClient client, String name) {
        if (client == null || client.world == null || name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim();
        for (PlayerEntity player : client.world.getPlayers()) {
            if (LegionsFeatures.realUsername(player).equalsIgnoreCase(normalized)) {
                return player;
            }
        }
        return null;
    }

    private static Object atomicsPvp() throws ReflectiveOperationException {
        Object config = getStaticField(atomicsClientClass(), "CONFIG");
        if (config == null) {
            throw new NoSuchFieldException("Atomics CONFIG is null");
        }
        Object pvp = getField(config, "pvp");
        if (pvp == null) {
            throw new NoSuchFieldException("Atomics pvp config is null");
        }
        return pvp;
    }

    private static Class<?> atomicsClientClass() throws ClassNotFoundException {
        if (atomicsClientClass == null) {
            atomicsClientClass = Class.forName("com.atomics.client.AtomicsClient");
        }
        return atomicsClientClass;
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

    private static String getStringField(Object owner, String name) throws ReflectiveOperationException {
        Object value = getField(owner, name);
        return value instanceof String text ? text : "";
    }

    private static void setField(Object owner, String name, Object value) throws ReflectiveOperationException {
        cachedField(owner.getClass(), name).set(owner, value);
    }

    private static Field cachedField(Class<?> owner, String name) throws NoSuchFieldException {
        Map<String, Field> ownerFields = FIELD_CACHE.computeIfAbsent(owner, ignored -> new HashMap<>());
        Field field = ownerFields.get(name);
        if (field == null) {
            field = owner.getDeclaredField(name);
            field.setAccessible(true);
            ownerFields.put(name, field);
        }
        return field;
    }

    private static void sendAction(MinecraftClient client, String message) {
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal(message), true);
        }
    }
}
