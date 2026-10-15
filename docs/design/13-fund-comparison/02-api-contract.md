# 2. Public API Contract

## 2.1 Common envelope

Both successful endpoints use the existing shared response mechanism. `messages` and `errors` are present as top-level arrays. Successful feature data is under `result.data`.
All public date fields (`fromDate`, `toDate`, and `priceDate`) use `dd/MM/yyyy`.

## 2.2 Retrieve options

### Request

```http
POST /api/v1/fund-comparison/options
Accept-Language: en | zh_HK
X-Request-Id: <request-id>
```

No request body is required. `policyNo` comes from authenticated account context.

### Success response

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "data": {
      "timeRanges": ["10D", "1M", "3M", "6M", "1Y", "YTD", "3Y"],
      "defaultTimeRange": "10D",
      "funds": [
        {
          "fundCode": "FAEU",
          "fundName": "Fidelity Advantage Portfolio Fund - Asia Pacific Equity Fund"
        }
      ]
    }
  },
  "messages": [],
  "errors": []
}
```

If the downstream options content contains no valid time-range token after normalization, the endpoint fails with HTTP 503. It must not return an empty successful options object.

### Invalid request response

Fund Comparison request validation uses the same public pattern as Fund Price:

```json
{
  "success": false,
  "status": "VALIDATION_FAILED",
  "result": null,
  "messages": [],
  "errors": [
    {
      "type": "VALIDATION",
      "code": "err.fund-comparison.request.invalid",
      "targets": ["SERVER"]
    }
  ]
}
```

HTTP status: `400 BAD_REQUEST`.

### Retrieval-unavailable response

Unusable options content, including a missing, blank, or wholly invalid `time-range`, uses:

```json
{
  "success": false,
  "status": "SYSTEM_ERROR",
  "result": null,
  "messages": [],
  "errors": [
    {
      "type": "SYSTEM",
      "code": "err.fund-comparison.retrieval.unavailable",
      "targets": ["SERVER"]
    }
  ]
}
```

HTTP status: `503 SERVICE_UNAVAILABLE`.

The implementation must reuse the existing shared Fund Price exception and response factories so serialized error details remain consistent with the repository implementation.

## 2.3 Retrieve prices

### Request

```http
POST /api/v1/fund-comparison/prices
Content-Type: application/json
Accept-Language: en | zh_HK
X-Request-Id: <request-id>
```

```json
{
  "fundCodes": ["FAEU", "FBAU"],
  "timeRange": "10D"
}
```

### Success response

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "data": {
      "fromDate": "06/10/2026",
      "toDate": "15/10/2026",
      "series": [
        {
          "fundCode": "FAEU",
          "prices": [
            {
              "priceDate": "06/10/2026",
              "unitPrice": 159.7628
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

### Legitimate no-data response

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "data": {
      "fromDate": "06/10/2026",
      "toDate": "15/10/2026",
      "series": []
    }
  },
  "messages": [],
  "errors": []
}
```

## 2.4 Ordering

- Options funds reuse the existing Fund Price localized-name comparator.
- Time ranges preserve normalized downstream order.
- Price points within each series are ordered by date ascending.
- Series order follows the request's first occurrence order after validation/deduplication.
