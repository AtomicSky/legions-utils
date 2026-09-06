# Ratings API integration

Minecraft 1.21.11 / Legions Utils 1.6 uses the API only. The endpoint is intentionally
blank until the API is available. A blank endpoint makes no requests and leaves
backend ratings unavailable; existing server-provided tab ratings can still display.

## When the API arrives

Edit `src/main/java/com/legions/client/LegionsRatingsApi.java`:

1. Set `URL` to the ratings endpoint.
2. Adjust `createRequest()` if the real API needs different request headers or a method.
3. Adjust `parseResponse()` to the actual response format.
4. Build and verify against a real API response before release.

The provisional contract is one GET returning all ratings as HTTP 200 JSON:

```json
[
  {"username": "PlayerOne", "rating": 1.9},
  {"username": "PlayerTwo", "rating": 2.0}
]
```

This is a placeholder contract, not a claim about the future API. Ratings use
the 0.1–2.0 scale, not 100–2000. Names are trimmed and matched without case.
Malformed responses, out-of-range ratings, and conflicting duplicates are rejected.
An empty array is a valid empty snapshot.

The cache loads asynchronously once per game session. Failed requests may retry
after 60 seconds when a consumer requests missing ratings. Requests time out after
10 seconds. Successful results are immutable and shared by existing rating consumers.
No Google Sheets URL, CSV parser, or fallback remains.

If the real API is paginated, per-player, or requires expiring authentication,
adapt the fetch flow as well; setting a URL alone will not handle those contracts.
Do not embed a private API secret in a distributed mod JAR.
