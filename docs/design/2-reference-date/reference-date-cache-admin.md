# Reference Date Cache Admin

## Purpose

The reference-date cache admin feature provides internal operational endpoints to inspect and clean up Redis-backed reference-date entries.

```http
GET    /api/v1/internal/reference-date/all
DELETE /api/v1/internal/reference-date/all
```

These endpoints are internal/admin operations under the existing `ReferenceDateController` resource path. They are not selected-account member APIs and do not require `Account-Ref`.

## Architecture

The controller remains thin and delegates to application use cases:

```text
ReferenceDateController
  -> GetAllReferenceDatesUseCase
      -> CacheAdminPort
          -> RedisCacheAdminAdapter

ReferenceDateController
  -> CleanUpAllReferenceDatesUseCase
      -> CacheAdminPort
          -> RedisCacheAdminAdapter
```

The cache administration port is capability-scoped through `CacheCapability.REFERENCE_DATE`; application code does not provide arbitrary Redis patterns.

## Redis key scope

The adapter scans/deletes only keys in the configured reference-date namespace:

```text
${redis-cache.key-prefix}:reference-date:*
```

Default runtime pattern:

```text
ngtpa:reference-date:*
```

The key qualifier after `${redis-cache.key-prefix}:reference-date:` is exposed as `accountEnv` in the GET response.

## API contract

### GET `/api/v1/internal/reference-date/all`

Returns all reference-date cache entries sorted by `accountEnv`.

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "referenceDates": [
      {
        "accountEnv": "HK",
        "refDate": "01/01/2026"
      }
    ]
  },
  "messages": [],
  "errors": []
}
```

### DELETE `/api/v1/internal/reference-date/all`

Deletes all matching reference-date cache entries. If no entries exist, the endpoint still succeeds with `deletedCount = 0`.

```json
{
  "success": true,
  "status": "UPDATED",
  "result": {
    "deletedCount": 0
  },
  "messages": [],
  "errors": []
}
```

## Safety rules

- Do not expose raw Redis pattern deletion to application use cases.
- Use `CacheCapability.REFERENCE_DATE` for these endpoints.
- Respect `redis-cache.key-prefix` / `REDIS_KEY_PREFIX`.
- Return deterministic GET output sorted by `accountEnv`.
- Treat DELETE with no matching keys as success with `deletedCount = 0`.
