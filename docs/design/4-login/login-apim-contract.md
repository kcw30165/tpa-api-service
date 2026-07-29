> Status: Design baseline v0.1
> Date: 20 July 2026
> Scope: Subsequent member login plus reusable authentication foundations
> Source baseline: Login BRD V0.3, discovery decisions in 02-brd.md through 10-brd.md, and the supplied current codebase snapshot
> Open external dependency: detailed `TRPGetAccountList` business failure-code mapping remains TBC.

# Login APIM Contract

## 1. Shared APIM requirements

Login uses the existing shared APIM envelope, WebClient facade, OAuth/token handling, credential profiles, certificate handling, encryption/decryption, request-ID propagation, logging and failure translation.

No endpoint-specific envelope, WebClient, OAuth, certificate, encryption, decryption or retry implementation is permitted.

### response schema

```json
{
    "response": {
        "err-message": "",
        "data": []
    }
}
```

## 2. `TRPMemberLogin`

### Request

```json
{
  "cust-id": "normalized-username",
  "password": "case-sensitive-password"
}
```

Mapping:

```text
cust-id  <- normalized configured login username
password <- exact submitted password; never trim or store
```

### Success item (inside data)

```json
{
  "success": true,
  "user-role": "role_user",
  "cust-type": "MBR",
  "fail-count": 0,
  "otp": [
    {
      "channel": "email",
      "destination": "member@example.com"
    }
  ]
}
```

Contract rules:

- Exactly one data item.
- Channel values are `email` or `mobile`; use validated strings, not a new enum.
- No duplicates, blanks or unsupported channels are expected.
- Destinations may later be configured as encrypted response fields; the login application contract does not change.
- Empty envelope maps to `err.login.credentials.unavailable`.
- Progress owns password failure counting and lock state.

### err-messages

- err.tpa.wrong.password
- err.tpa.cust.id.not.registered
- err.tpa.no.valid.account
- err.tpa.account.locked

## 3. `TRPGetAccountList`

### Request

```json
{
  "cust-id": "normalized-username",
  "cust-type": "MBR",
  "search-key": "cust-id",
  "search-value": "normalized-username"
}
```

All request fields come from protected pre-auth context. `cust-id` and `search-value` are configured request-encryption fields.

### Success item

```json
{
  "env": "DB",
  "trust-code": "DBA",
  "policy-no": "8802",
  "sch-type": "OE",
  "cert-no": 1,
  "term-status": "T",
  "term-complete-date": "20/07/2026"
}
```

Java validates and maps records; Progress owns grace-period filtering.

### rules

- term-status and term-complete-date are empty or non-empty at the same time
- term-status just has T or empty, representing terminated or not

### Failure handling

- HTTP 200 with non-empty `err-message`: configuration-driven business/system classification; exact mappings TBC.
- HTTP 200 with both `err-message` and data empty: `err.login.accountList.unavailable`.
- HTTP 4xx/5xx: existing APIM handling.
- No response contains both a business error and successful data.
- No partial-success envelope exists.

## 4. Retry

Safe login business calls use the global transient retry policy:

- Three business-call attempts total.
- Existing global backoff.
- Token/certificate acquisition and recovery are excluded from this budget.
- No retry for HTTP 200 business outcomes, empty/invalid envelopes, decryption failures, mapping failures or malformed account records.
- Mutation/non-idempotent operations may opt out globally.

## 5. Error privacy

Raw Progress/APIM codes, payloads, destinations, credentials and crypto details are not exposed to FE. One-to-one downstream mappings are configuration-owned.
