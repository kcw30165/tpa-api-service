# 03 — APIM Contract: POST /ws/NGTPA/v1/TRPGetDownloadCorner

## Request

```json
{
  "policy-no": "policy from resolved account",
  "cert-no": "certificate from resolved account",
  "login-user-id": "actorUserId from actor context",
  "login-user-role": "actorUserRole in the existing APIM wire format"
}
```

The field names and provenance are locked. The Vesting request DTO binds `login-user-id`, `login-user-role`, `policy-no`, and `cert-no` to those same account/actor values (`TrpGetVestingScaleApimRequest.java:5-15`; `VestingScaleController.java:90-99`). Download Corner does not send language, session ID, `Account-Ref`, or `accountEnv` in this body.

## Response envelope

```json
{
  "response": {
    "err-message": "",
    "data": [
      {
        "env": "DB",
        "categories": [
          {
            "category-seq": 1,
            "category-name-eng": "Forms",
            "category-name-chi": "表格",
            "items": [
              {
                "item-seq": 1,
                "item-name-eng": "Member form",
                "item-name-chi": "成員表格",
                "item-link-eng": "https://documents.example.test/member-form.pdf",
                "item-link-chi": "https://documents.example.test/member-form-zh.pdf",
                "file-name-eng": "member-form.pdf",
                "file-name-chi": "member-form-zh.pdf"
              }
            ]
          }
        ]
      }
    ]
  }
}
```

All sample links are plain URLs with no HTML. The names and response structure above are from the locked feature contract; they are not independently confirmed against a live DS endpoint.

## Envelope and data rules

| Path | Rule | Failure handling |
|---|---|---|
| `response` | Required | Missing/null is malformed response |
| `response.err-message` | Any non-empty value is an error and takes precedence over parsing `data` | Known account-context errors are classified as such; any other non-empty value is unmapped |
| `response.data` | Exactly one element required when `err-message` is empty | Missing, empty, null, or cardinality other than one is malformed |
| `response.data[0].env` | Ignore; Java already has `accountEnv` in the session | Never forwarded |
| `response.data[0].categories` and nested item fields | Parse according to the agreed schema | Any DTO/tree parse or shape failure is malformed; identify the malformed path in the failure event |

The executor returns the shared typed response envelope. As in Vesting, the feature adapter uses `postEnvelope`, then checks response/error/cardinality and maps the accepted one data item (`ApimVestingScaleAdapter.java:83-103,113-138`). The Download Corner request is an account-scoped safe read: use `ACCOUNT_ENV`, `SAFE_BUSINESS_CALL`, the same recovery setting and the shared read retry behavior. The request constructor enforces a nonblank path and nonblank `accountEnv` for `ACCOUNT_ENV` (`ApimExchangeRequest.java:62-87`).

## Endpoint path configuration

The Vesting configuration uses `apim.vesting-scale.path` with a YAML default (`VestingScaleApimProperties.java:3-23`; `application.yml:305-306`). Download Corner's corresponding YAML path is **`NEW (proposed) `apim.download-corner.path`**, defaulting to the locked `/ws/NGTPA/v1/TRPGetDownloadCorner`. Exact environment-variable naming for overriding it is **UNVERIFIED** and must follow the repository's existing property convention when implemented.

## Wire-shape items requiring confirmation

The locked contract names every field but does not state scalar types, nullability, whether `categories`/`items` may be null, or whether sequence values can be absent, fractional, or encoded as strings. Treat a parse/shape failure as malformed per L10; verify the wire details with the DS contract before M1 finalizes strict DTO validation. Do not invent coercion behavior.
