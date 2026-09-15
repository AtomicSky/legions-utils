package com.legions.client;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LegionsRatingBackendCache {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ConcurrentMap<UUID, Double> CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> LOADING = ConcurrentHashMap.newKeySet();
    private static final ConcurrentMap<UUID, Long> RETRY_AFTER_NANOS = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "LegionsClient-RatingsApi");
        thread.setDaemon(true);
        return thread;
    });

    private LegionsRatingBackendCache() {
    }

    public static Double getCached(UUID playerUuid) {
        return playerUuid == null ? null : CACHE.get(playerUuid);
    }

    public static void preload(UUID playerUuid) {
        if (playerUuid == null || CACHE.containsKey(playerUuid)) {
            return;
        }
        long retryAfter = RETRY_AFTER_NANOS.getOrDefault(playerUuid, 0L);
        if ((retryAfter == 0 || System.nanoTime() - retryAfter >= 0) && LOADING.add(playerUuid)) {
            EXECUTOR.execute(() -> fetchRating(playerUuid));
        }
    }

    private static void fetchRating(UUID playerUuid) {
        try {
            HttpResponse<String> response = CLIENT.send(
                    LegionsRatingsApi.createRequest(playerUuid), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Ratings API returned HTTP " + response.statusCode());
            }
            CACHE.put(playerUuid, LegionsRatingsApi.parseRating(response.body(), playerUuid));
            RETRY_AFTER_NANOS.remove(playerUuid);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LegionsClient.LOGGER.debug("Ratings API request for {} interrupted.", playerUuid, e);
        } catch (Exception e) {
            LegionsClient.LOGGER.debug("Failed to fetch Legions rating for {} from API.", playerUuid, e);
        } finally {
            if (!CACHE.containsKey(playerUuid)) {
                RETRY_AFTER_NANOS.put(playerUuid, System.nanoTime() + Duration.ofSeconds(60).toNanos());
            }
            LOADING.remove(playerUuid);
        }
    }
}
