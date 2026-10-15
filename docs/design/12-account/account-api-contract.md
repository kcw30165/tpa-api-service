# Account Endpoints -- API Contract

## 1. List all accounts

```
POST /api/v1/accounts
Cookie: portal_sid=<session id>
X-XSRF-TOKEN: <csrf token>
```

No request body.

Success response (`200 OK`):

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "data": {
      "accounts": [
        {
          "accountRef": "acc_CEoNbrhgjmYgu2YgrdoqfB8psucGR7-X",
          "policyNo": "00000000112",
          "certNo": "0582",
          "accountEnv": "TB",
          "trustCode": "HKBU",
          "schemeType": "OE"
        },
        {
          "accountRef": "acc_ibSsUgtP8zeG8AxixnOkUkt3GZQ6v_Ce",
          "policyNo": "00000004203",
          "certNo": "0186",
          "accountEnv": "TB",
          "trustCode": "MSIG",
          "schemeType": "OE"
        }
      ]
    }
  },
  "messages": [],
  "errors": []
}
```

An authenticated session with zero accounts (should not normally occur, but
is not treated as an error) returns `"accounts": []`.

## 2. Get one account

```
POST /api/v1/account
Cookie: portal_sid=<session id>
X-XSRF-TOKEN: <csrf token>
Content-Type: application/json

{
  "accountRef": "acc_xzxMWFN5aVMVyOTThpAe4wN0kObWotWb"
}
```

`accountRef` is validated against the existing confirmed format
`^acc_[A-Za-z0-9_-]{20,124}$`.

Success response (`200 OK`):

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "data": {
      "accountRef": "acc_xzxMWFN5aVMVyOTThpAe4wN0kObWotWb",
      "policyNo": "1111111111111111111111111",
      "certNo": "1",
      "accountEnv": "AA",
      "trustCode": "BB",
      "schemeType": "CC"
    }
  },
  "messages": [],
  "errors": []
}
```

## 3. Schema correction from the original design

The previous `AccountSwitchResponse` was passed directly as the
`MutationResponse<T>` type parameter, so the shared envelope's `result` field
held it directly -- there was no `data` nesting layer, which was incorrect.

This pack follows the existing, already-shipped convention used by
`AccountSummaryResultResponse`/`AccountSummaryDataResponse`: a one-line
wrapper record whose sole field is named `data`, carried as the
`MutationResponse<T>` type parameter. Both new endpoints use the equivalent
pattern (see `account-implementation-plan.md` for exact type names).

## 4. Field naming

Use `trustCode` (not `trust`) in both responses, matching `AccountContext`'s
existing field name and this pack's confirmed field list.
