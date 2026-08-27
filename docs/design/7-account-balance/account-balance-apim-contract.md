# Account Balance APIM Contract

**Feature:** 6.14 Account Balance  
**Baseline:** 27/08/2026  
**Status:** Consolidated design baseline, pending repository investigation  

## TRPGetMemberAccBal

Method: `POST`.

Initial request:

```json
{
  "login-user-id": "db230",
  "login-user-role": "role_user_mbr",
  "policy-no": "00000008802",
  "cert-no": 230,
  "ref-date": "31/08/2026",
  "curr": ""
}
```

Currency change sends the selected value in `curr`. The property is always present and is never null.

Successful `response.data` contains exactly one complete object with `displayOptions`, `curr`, `dispSrc`, `fundDtl`, and `accBal`. Null, empty, or multiple objects are technical failures.

## TRPGetGeneralMetaData

Method: `POST`.

Unfiltered request:

```json
{
  "env": "DB",
  "trust-code": "DBA",
  "policy-no": "00000008802",
  "metadata-name": "",
  "metadata-type": ""
}
```

Targeted request supplies Java-mapped downstream values such as `fund-pri` and `precision`.

Response rows contain:

```json
{
  "policy-no": "all",
  "metadata-name": "nav",
  "metadata-type": "precision",
  "metadata-value": "2"
}
```

TAS filters using request environment, trust, and policy. It decides whether returned rows use `policy-no=all` or the specific input policy. A response is not assumed to contain both forms.

## Java naming

```java
FUND_PRICE("fund-pri")
NET_ASSET_VALUE("nav")
UNIT_BALANCE("unit")
FX_RATE("fx-rate")

PRECISION("precision")
ROUNDING_METHOD("round-method")
```

Account Balance consumes precision only. Generic APIM transport/envelope behavior remains existing shared behavior.
