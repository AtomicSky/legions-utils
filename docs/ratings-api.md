# Ratings API integration

Minecraft 1.21.11 / Legions Utils 1.6 uses a per-player ratings API. Existing
server-provided tab ratings remain the first choice; the API supplies ratings when
the tab list does not.

## Contract

The client sends one GET per requested player UUID:

```text
http://170.205.24.39:35201/?uuid=<player UUID>
```

A successful response is an HTTP 200 JSON object such as:

```json
{"playerId":"75502d4c-bb95-4dea-b3c0-cb3a13ae5fee","playerName":"Devidur","rating":1.3}
```

The response may contain other player statistics, but Legions Utils reads only
`playerId` and `rating`. It rejects a response whose `playerId` does not match the
requested UUID, as well as a missing, nonnumeric, or out-of-range rating. Ratings use
the 0.1-2.0 scale, not 100-2000.

Each UUID is loaded asynchronously on demand and cached for the game session. Failed
requests may retry after 60 seconds. Requests time out after 10 seconds.
