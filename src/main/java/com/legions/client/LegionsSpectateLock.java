package com.legions.client;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

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

    public static void toggleLockToPlayer(Minecraft client, String playerName) {
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

    public static boolean isLockedPair(Player first, Player second) {
        if (lockedPlayerName == null || first == null || second == null || first.getUUID().equals(second.getUUID())) {
            return false;
        }
        return isExternalSpectateCandidate(first)
                && isExternalSpectateCandidate(second)
                && (isLockedTo(LegionsFeatures.realUsername(first)) || isLockedTo(LegionsFeatures.realUsername(second)));
    }

    public static void handleKeyPress(Minecraft client) {
        if (!LegionsClient.isAtomicsClientLoaded()) {
            sendAction(client, "Atomics Client is required for dual spectate lock");
            return;
        }
        if (!isLockAvailable(client)) {
            sendAction(client, "Dual spectate lock is unavailable");
            return;
        }
        Player target = findLookedAtPlayer(client);
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

    public static void tick(Minecraft client) {
        if (lockedPlayerName == null) {
            return;
        }
        if (!LegionsClient.isAtomicsClientLoaded() || !isLockAvailable(client) || client.player == null
                || client.level == null) {
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

        Player lockedPlayer = findPlayer(client, lockedPlayerName);
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

    private static Player findAutoFillReplacement(Minecraft client, Object pvp, String previousLockedName) {
        Player previousSecond = findReplacementPlayer(client, lastSecondPlayerName, previousLockedName);
        if (previousSecond != null) {
            return previousSecond;
        }

        try {
            Player playerOne = findReplacementPlayer(client, getStringField(pvp, "dualSpectatePlayerOne"), previousLockedName);
            if (playerOne != null) {
                return playerOne;
            }
            Player playerTwo = findReplacementPlayer(client, getStringField(pvp, "dualSpectatePlayerTwo"), previousLockedName);
            if (playerTwo != null) {
                return playerTwo;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LegionsClient.LOGGER.debug("Failed to read Atomics auto-filled dual spectate pair.", e);
        }

        return findBestAutoFillPlayer(client, previousLockedName);
    }

    private static Player findReplacementPlayer(Minecraft client, String playerName, String previousLockedName) {
        if (playerName == null || playerName.isBlank()
                || previousLockedName != null && playerName.trim().equalsIgnoreCase(previousLockedName.trim())) {
            return null;
        }

        Player player = findPlayer(client, playerName);
        return isDualSpectateCandidate(client, player) ? player : null;
    }

    private static Player findBestAutoFillPlayer(Minecraft client, String previousLockedName) {
        Player paired = findBestAutoFillPlayer(client, previousLockedName, true);
        if (paired != null) {
            return paired;
        }

        paired = findBestAutoFillPlayer(client, previousLockedName, false);
        if (paired != null) {
            return paired;
        }

        Player best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        Entity camera = client.getCameraEntity() == null ? client.player : client.getCameraEntity();
        for (Player candidate : client.level.players()) {
            if (!isReplacementCandidate(client, candidate, previousLockedName)) {
                continue;
            }

            double distance = camera.distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static Player findBestAutoFillPlayer(Minecraft client, String previousLockedName,
                                                       boolean requireOpponent) {
        Player bestFirst = null;
        Player bestSecond = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Player first : client.level.players()) {
            if (!isReplacementCandidate(client, first, previousLockedName)) {
                continue;
            }

            for (Player second : client.level.players()) {
                if (!isDualSpectateCandidate(client, second)
                        || first.getUUID().equals(second.getUUID())
                        || requireOpponent && !LegionsFeatures.isOpponent(first, second)) {
                    continue;
                }

                double distance = first.distanceToSqr(second);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestFirst = first;
                    bestSecond = second;
                }
            }
        }

        return closerToCamera(client, bestFirst, bestSecond);
    }

    private static boolean isReplacementCandidate(Minecraft client, Player player, String previousLockedName) {
        return isDualSpectateCandidate(client, player)
                && (previousLockedName == null || !LegionsFeatures.realUsername(player).equalsIgnoreCase(previousLockedName.trim()));
    }

    private static Player closerToCamera(Minecraft client, Player first, Player second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }

        Entity camera = client.getCameraEntity() == null ? client.player : client.getCameraEntity();
        return camera.distanceToSqr(first) <= camera.distanceToSqr(second) ? first : second;
    }

    private static void updateLockedPair(Minecraft client, Object pvp, Player lockedPlayer,
                                         boolean autoFill) throws ReflectiveOperationException {
        setField(pvp, "dualSpectateEnabled", true);
        setField(pvp, "dualSpectateAutoFill", autoFill);
        String lockedName = LegionsFeatures.realUsername(lockedPlayer);
        setField(pvp, "dualSpectatePlayerOne", lockedName);

        Player second = findBestSecondPlayer(client, lockedPlayer);
        String secondName = second == null ? "" : LegionsFeatures.realUsername(second);
        setField(pvp, "dualSpectatePlayerTwo", secondName);
        lastSecondPlayerName = secondName;
        logPairChange(lockedName, secondName,
                second == null ? -1.0 : lockedPlayer.distanceToSqr(second),
                MISSING_SECOND_PLAYER_REASON);
    }

    private static void lockTo(Minecraft client, String playerName) {
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

    private static void unlock(Minecraft client, boolean notify) {
        unlock(client, notify, true);
    }

    private static void unlock(Minecraft client, boolean notify, boolean restoreEnabled) {
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

    private static Player findBestSecondPlayer(Minecraft client, Player lockedPlayer) {
        Player opponent = findBestSecondPlayer(client, lockedPlayer, true);
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

    private static Player findBestSecondPlayer(Minecraft client, Player lockedPlayer, boolean requireOpponent) {
        Player best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Player candidate : client.level.players()) {
            if (!isDualSpectateCandidate(client, candidate)
                    || candidate.getUUID().equals(lockedPlayer.getUUID())
                    || requireOpponent && !LegionsFeatures.isOpponent(lockedPlayer, candidate)) {
                continue;
            }

            double distance = lockedPlayer.distanceToSqr(candidate);
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

    private static double facingDot(Player from, Player to) {
        Vec3 direction = to.getEyePosition().subtract(from.getEyePosition());
        if (direction.lengthSqr() < 0.0001) {
            return 1.0;
        }
        return from.getViewVector(1.0f).normalize().dot(direction.normalize());
    }

    private static boolean isLockAvailable(Minecraft client) {
        return LegionsClient.CONFIG != null
                && LegionsClient.CONFIG.enabled
                && client != null
                && client.player != null
                && client.level != null;
    }

    private static boolean isDualSpectateCandidate(Minecraft client, Player player) {
        return player != null
                && client != null
                && client.player != null
                && !player.getUUID().equals(client.player.getUUID())
                && isExternalSpectateCandidate(player);
    }

    private static boolean isExternalSpectateCandidate(Player player) {
        return player != null
                && !player.isRemoved()
                && !player.isDeadOrDying()
                && player.isAlive();
    }

    private static Player findLookedAtPlayer(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            return null;
        }
        if (client.hitResult instanceof EntityHitResult entityHitResult
                && entityHitResult.getEntity() instanceof Player player
                && isDualSpectateCandidate(client, player)) {
            return player;
        }
        if (client.crosshairPickEntity instanceof Player player && isDualSpectateCandidate(client, player)) {
            return player;
        }

        Entity camera = client.getCameraEntity() == null ? client.player : client.getCameraEntity();
        Vec3 start = camera.getEyePosition(1.0f);
        Vec3 direction = camera.getViewVector(1.0f);
        Vec3 end = start.add(direction.scale(LOOK_RANGE));

        Player best = null;
        double bestDistanceSq = LOOK_RANGE * LOOK_RANGE;
        for (Player candidate : client.level.players()) {
            if (!isDualSpectateCandidate(client, candidate)) {
                continue;
            }

            AABB box = candidate.getBoundingBox().inflate(Math.max(0.35, candidate.getPickRadius() + 0.3));
            Optional<Vec3> hit = box.clip(start, end);
            if (hit.isEmpty()) {
                continue;
            }

            double distanceSq = start.distanceToSqr(hit.get());
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                best = candidate;
            }
        }
        return best;
    }

    private static Player findPlayer(Minecraft client, String name) {
        if (client == null || client.level == null || name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim();
        for (Player player : client.level.players()) {
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

    private static void sendAction(Minecraft client, String message) {
        if (client != null && client.player != null) {
            client.player.sendOverlayMessage(Component.literal(message));
        }
    }
}
