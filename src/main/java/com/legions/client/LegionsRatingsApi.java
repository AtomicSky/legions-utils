package com.legions.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * API integration point. Set URL and adapt parseResponse once the real API contract is available.
 * The current JSON contract is provisional; see docs/ratings-api.md.
 */
final class LegionsRatingsApi {
    static final String URL = "";

    private LegionsRatingsApi() {
    }

    static boolean isConfigured() {
        return !URL.isBlank();
    }

    static HttpRequest createRequest() {
        return HttpRequest.newBuilder(URI.create(URL))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    static Map<String, Double> parseResponse(String body) {
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonArray()) {
            throw new IllegalArgumentException("Expected a JSON array of player ratings");
        }
        Map<String, Double> ratings = new HashMap<>();
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Expected a player rating object");
            }
            JsonObject row = element.getAsJsonObject();
            JsonElement name = row.get("username");
            JsonElement value = row.get("rating");
            if (name == null || !name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()
                    || value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Expected username string and numeric rating");
            }
            String key = name.getAsString().trim().toLowerCase(Locale.ROOT);
            double rating = value.getAsDouble();
            if (key.isEmpty() || !Double.isFinite(rating) || rating < 0.1 || rating > 2.0) {
                throw new IllegalArgumentException("Invalid username or rating outside 0.1–2.0");
            }
            Double previous = ratings.putIfAbsent(key, rating);
            if (previous != null && Double.compare(previous, rating) != 0) {
                throw new IllegalArgumentException("Conflicting ratings for the same username");
            }
        }
        return Map.copyOf(ratings);
    }
}
