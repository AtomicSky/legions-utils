package com.legions.client;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LegionsRatingBackendCache {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final AtomicBoolean LOADING = new AtomicBoolean();
    private static volatile boolean loaded;
    private static volatile long retryAfterNanos;
    private static volatile Map<String, Double> cache = Map.of();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "LegionsClient-RatingsApi");
        thread.setDaemon(true);
        return thread;
    });

    private LegionsRatingBackendCache() {
    }

    public static Double getCached(String playerName) {
        String key = normalizeKey(playerName);
        return key.isEmpty() ? null : cache.get(key);
    }

    static Double getCachedNormalized(String normalizedPlayerName) {
        return cache.get(normalizedPlayerName);
    }

    public static void preloadAll() {
        if (!LegionsRatingsApi.isConfigured() || loaded
                || (retryAfterNanos != 0 && System.nanoTime() - retryAfterNanos < 0)) {
            return;
        }
        if (LOADING.compareAndSet(false, true)) {
            EXECUTOR.execute(LegionsRatingBackendCache::fetchRatings);
        }
    }

    public static void preload(String playerName) {
        if (!normalizeKey(playerName).isEmpty()) {
            preloadAll();
        }
    }

    private static void fetchRatings() {
        try {
            HttpResponse<String> response = CLIENT.send(
                    LegionsRatingsApi.createRequest(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Ratings API returned HTTP " + response.statusCode());
            }
            Map<String, Double> ratings = LegionsRatingsApi.parseResponse(response.body());
            cache = Map.copyOf(ratings);
            loaded = true;
            LegionsClient.LOGGER.info("Loaded {} Legions ratings from API.", ratings.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LegionsClient.LOGGER.debug("Ratings API request interrupted.", e);
        } catch (Exception e) {
            LegionsClient.LOGGER.debug("Failed to fetch Legions ratings from API.", e);
        } finally {
            if (!loaded) {
                retryAfterNanos = System.nanoTime() + Duration.ofSeconds(60).toNanos();
            }
            LOADING.set(false);
        }
    }

    private static String normalizeKey(String playerName) {
        return playerName == null ? "" : playerName.trim().toLowerCase(Locale.ROOT);
    }
}
