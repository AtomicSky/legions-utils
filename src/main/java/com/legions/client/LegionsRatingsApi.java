package com.legions.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.UUID;

/**
 * Per-player ratings API integration; see docs/ratings-api.md.
 */
final class LegionsRatingsApi {
    static final String BASE_URL = "http://170.205.24.39:35201/";

    private LegionsRatingsApi() {
    }

    static HttpRequest createRequest(UUID playerUuid) {
        URI uri = URI.create(BASE_URL + "?uuid=" + playerUuid);
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    static double parseRating(String body, UUID requestedUuid) {
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("Expected a player rating object");
        }
        JsonObject row = root.getAsJsonObject();
        JsonElement playerId = row.get("playerId");
        JsonElement value = row.get("rating");
        if (playerId == null || !playerId.isJsonPrimitive() || !playerId.getAsJsonPrimitive().isString()
                || value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected playerId string and numeric rating");
        }
        UUID returnedUuid;
        try {
            returnedUuid = UUID.fromString(playerId.getAsString());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ratings API returned an invalid playerId", e);
        }
        if (!requestedUuid.equals(returnedUuid)) {
            throw new IllegalArgumentException("Ratings API returned a different playerId");
        }
        double rating = value.getAsDouble();
        if (!Double.isFinite(rating) || rating < 0.1 || rating > 2.0) {
            throw new IllegalArgumentException("Invalid rating outside 0.1-2.0");
        }
        return rating;
    }
}
