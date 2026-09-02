# Phase 3D5B: Remove Shared Redis Sentinel Password Fallback

## Implemented

- Replaced the shared non-empty `REDIS_SENTINEL_PASSWORD` fallback with an empty external placeholder.
- Preserved optional Sentinel authentication behavior. `RedisAdapterConfig` applies the Sentinel password only when the configured value has text.
- Added a focused resource guard preventing a non-empty shared fallback from returning.

## Preserved

`REDIS_PASSWORD`, Redis enablement, Sentinel master and nodes, key prefix, timeouts, pool settings, SSL configuration, conditional activation, Jenkins values, external Secrets, APIM governance, and Production Harbor Proxy Cache behavior were not changed.
