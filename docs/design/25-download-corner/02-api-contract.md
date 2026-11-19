# 02 — BFF API Contract: GET /api/v1/download-corner

## Request

| Item | Value |
|---|---|
| Method / path | `GET /api/v1/download-corner` |
| Headers | `Account-Ref` (required for this selected-account API), existing session/CSRF headers, required nonblank `X-Request-Id`, optional `Accept-Language` |
| Cookie | Existing authenticated portal session cookie |
| Body / query | None |
| Authorization | Existing post-login authorization and shared APIM credential-profile allow-list, unchanged |

`X-Request-Id` is required on `/api/` requests by `RequiredRequestIdWebFilter`; a missing or blank value returns the existing HTTP 400 validation response. The shared request-logging filter returns the request ID in the response header. The ID is not included in the response body.

The selected-account GET pattern is `/api/v1/{feature}`: Vesting uses class-level `/api/v1` and `@GetMapping("/vesting-scale")` (`VestingScaleController.java:33-35,54`). Thus the locked Download Corner path follows that pattern; no L1 naming discrepancy was found.

## Success response (200)

Use the existing base wrapper and camelCase Java record field style. Vesting’s controller returns `MutationResponse.success(...)` (`VestingScaleController.java:76-84`); `MutationResponse` serializes `success`, `status`, `result`, `messages`, and `errors` (`MutationResponse.java:5-20`). `VestingScaleResultResponse` wraps the feature data in a `data` field (`VestingScaleResultResponse.java:1-3`).

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "data": {
      "categories": [
        {
          "categoryName": "Forms",
          "items": [
            {
              "itemName": "Member form",
              "itemLink": "https://documents.example.test/member-form.pdf",
              "fileName": "member-form.pdf"
            }
          ]
        }
      ]
    }
  },
  "messages": [],
  "errors": []
}
```

The example URL is plain text, not HTML. The payload does not expose `env`, `category-seq`, or `item-seq`. An empty result is a successful response with `categories: []`; an empty category remains present with `items: []`.

## Field table

| BFF field | Source (APIM) | Rule |
|---|---|---|
| `result.data.categories[]` | `response.data[0].categories[]` | Sort by `category-seq` ascending, stably; preserve empty categories |
| `categories[].categoryName` | `category-name-eng` / `category-name-chi` | Selected language; Chinese blank falls back to English |
| `categories[].items[]` | `categories[].items[]` | Sort by `item-seq` ascending, stably; drop only when resolved link is blank |
| `items[].itemName` | `item-name-eng` / `item-name-chi` | Selected language; Chinese blank falls back to English |
| `items[].itemLink` | `item-link-eng` / `item-link-chi` | Selected language; Chinese blank falls back to English; return selected source value unchanged |
| `items[].fileName` | `file-name-eng` / `file-name-chi` | Selected language; Chinese blank falls back to English |

## Errors

| Case | HTTP | Public code |
|---|---:|---|
| Any non-empty APIM `err-message`, malformed response, or normalized downstream retrieval failure | 503 | `err.download-corner.retrieval.unavailable` (**NEW, proposed**) |
| Missing Account-Ref, session, or other shared context/security failure | Existing filter/controller behavior; unchanged | Existing shared behavior |

The feature retrieval response uses the existing base error wrapper. The feature-scoped retrieval mapping is planned as `NEW (proposed) DownloadCornerExceptionMappings` and must be explicitly registered in `ExceptionMappingRegistryConfig`.
