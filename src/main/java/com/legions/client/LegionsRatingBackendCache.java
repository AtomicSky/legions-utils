package com.legions.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LegionsRatingBackendCache {
    private static final String RATING_SHEET_URL = "https://docs.google.com/spreadsheets/d/12Z7eMO_fSv5SMlFybVJBLIpMWQ7wC6YaV9wVQQVY1ko/export?format=csv&gid=1710836922";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final AtomicBoolean LOAD_STARTED = new AtomicBoolean();
    private static volatile Map<String, Double> cache = Map.of();

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "LegionsClient-RatingSheet");
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
        if (LOAD_STARTED.compareAndSet(false, true)) {
            EXECUTOR.execute(LegionsRatingBackendCache::fetchRatings);
        }
    }

    public static void preload(String playerName) {
        String key = normalizeKey(playerName);
        if (key.isEmpty()) {
            return;
        }
        preloadAll();
    }

    private static void fetchRatings() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RATING_SHEET_URL))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return;
            }

            Map<String, Double> ratings = parseRatingSheet(response.body());
            if (!ratings.isEmpty()) {
                cache = Map.copyOf(ratings);
                LegionsClient.LOGGER.info("Loaded {} Legions ratings from Google Sheet.", ratings.size());
            }
        } catch (Exception e) {
            LegionsClient.LOGGER.debug("Failed to fetch Legions rating sheet.", e);
        }
    }

    private static Map<String, Double> parseRatingSheet(String csv) {
        List<List<String>> rows = parseCsv(csv);
        if (rows.isEmpty()) {
            return Map.of();
        }

        ArrayList<RatingColumn> ratingColumns = ratingColumns(rows.get(0));
        HashMap<String, Double> ratings = new HashMap<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            for (RatingColumn column : ratingColumns) {
                if (column.index >= row.size()) {
                    continue;
                }

                String playerName = row.get(column.index).trim();
                String key = normalizeKey(playerName);
                if (!key.isEmpty()) {
                    ratings.putIfAbsent(key, column.rating);
                }
            }
        }
        return ratings;
    }

    private static ArrayList<RatingColumn> ratingColumns(List<String> header) {
        ArrayList<RatingColumn> columns = new ArrayList<>();
        for (int index = 0; index < header.size(); index++) {
            Double rating = parseRatingHeader(header.get(index));
            if (rating != null) {
                columns.add(new RatingColumn(index, rating));
            }
        }
        return columns;
    }

    private static Double parseRatingHeader(String value) {
        if (value == null) {
            return null;
        }

        try {
            double rating = Double.parseDouble(value.trim());
            double tenths = rating * 10.0;
            if (!Double.isFinite(rating)
                    || Math.abs(tenths - Math.rint(tenths)) > 0.0001
                    || rating < 0.1
                    || rating > 2.0) {
                return null;
            }
            return rating;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<List<String>> parseCsv(String csv) {
        ArrayList<List<String>> rows = new ArrayList<>();
        ArrayList<String> row = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;

        for (int index = 0; index < csv.length(); index++) {
            char character = csv.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
                        value.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    value.append(character);
                }
                continue;
            }

            if (character == '"') {
                quoted = true;
            } else if (character == ',') {
                row.add(value.toString());
                value.setLength(0);
            } else if (character == '\n') {
                row.add(value.toString());
                rows.add(row);
                row = new ArrayList<>();
                value.setLength(0);
            } else if (character == '\r') {
                if (index + 1 >= csv.length() || csv.charAt(index + 1) != '\n') {
                    row.add(value.toString());
                    rows.add(row);
                    row = new ArrayList<>();
                    value.setLength(0);
                }
            } else {
                value.append(character);
            }
        }

        if (!row.isEmpty() || !value.isEmpty()) {
            row.add(value.toString());
            rows.add(row);
        }
        return rows;
    }

    private static String normalizeKey(String playerName) {
        return playerName == null ? "" : playerName.trim().toLowerCase(Locale.ROOT);
    }

    private record RatingColumn(int index, double rating) {
    }
}
