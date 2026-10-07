# Fund Fact Sheet APIM/TAS Contract

## Operation: TRPGetFundFactSheet

### Path

```text
/ws/NGTPA/v1/TRPGetFundFactSheet
```

Configured as `apim.fund-fact-sheet.path` (default `${APIM_FUND_FACT_SHEET_PATH:/ws/NGTPA/v1/TRPGetFundFactSheet}`, matching the Account Balance and Gain/Loss form). Startup fails when blank, following `FundPriceModeApimProperties` / `FundPriceModeApimValidationConfig`.

### Request

```json
{
  "policy-no": "00000008802",
  "ref-date": "05/05/2026"
}
```

| Field | Source |
|---|---|
| `policy-no` | `PortalAccessContext.account().policyNo()` |
| `ref-date` | `ReferenceDatePort.resolveReferenceDate(accountEnv)` |

Formatter: a feature-local `DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)`. `uuuu` (proleptic year) is used because `yyyy` (year-of-era) rejects valid dates under STRICT resolution. The existing `fundPriceFullDateFormatter` bean is not reused. A shared date-format helper is a deferred refactor (decisions doc).

### Success response

```json
{
  "response": {
    "err-message": "",
    "data": [
      {
        "FundFactSheet": [
          {
            "seq": 10010,
            "name-eng": "Fidelity HKD Money Market Fund",
            "name-chi": "Fidelity HKD Money Market Fund",
            "link-eng": "https://www.bcthk.com/f/PUP/DB/publications/DBA_dhkm_ffs.pdf",
            "link-chi": "https://www.bcthk.com/f/PUP/DB/publications/DBA_dhkm_ffs.pdf"
          }
        ]
      }
    ]
  }
}
```

The full sample contains 8 rows (`seq` 10010-10080). In the sample, `name-chi` for `seq` 10020 reads "PFidelity USD Money Market Fund"; this is sample data, not a rule.

### No-fund response (confirmed by the downstream developer)

```json
{
  "response": {
    "err-message": "err.tpa.no.fund.fact.sheet"
  }
}
```

| Field | DTO type | Meaning |
|---|---|---|
| `err-message` | String | Empty on success. Known values: `err.tpa.no.fund.fact.sheet` (no funds; `data` absent), `err.scheme.invalid` |
| `data[]` | List | One or more items on success; absent with `err.tpa.no.fund.fact.sheet` |
| `FundFactSheet[]` | List | Fact sheet rows |
| `seq` | `Integer` (nullable, so a missing value is detectable) | Unique row id; uniqueness guaranteed downstream |
| `name-eng` | String | English fund name; mandatory downstream |
| `name-chi` | String | Chinese fund name; may be empty |
| `link-eng` | String | English fact sheet URL; empty when missing |
| `link-chi` | String | Chinese fact sheet URL; empty when missing |

DTOs keep exact wire names, use `@JsonIgnoreProperties(ignoreUnknown = true)`, and stay inside `adapter.out.apim.fundfactsheet`.

## Envelope handling in the adapter

| Envelope | Adapter result | Adapter log |
|---|---|---|
| `err-message` = `err.tpa.no.fund.fact.sheet` (`data` absent or present) | Success, zero rows | None (executor logs `apim.business.rejected` at WARN) |
| `err-message` empty, `data` non-empty, all items and rows non-null | Success | None |
| `response` null or `err-message` null | `systemFailure()` | Reason only |
| Any other non-empty `err-message` (including `err.scheme.invalid`) | `systemFailure()` | Sanitized downstream code |
| `err-message` empty, `data` null | `systemFailure()` | Reason only |
| `err-message` empty, `data` `[]` | `systemFailure()` | Reason only (TAS signals no funds with `err.tpa.no.fund.fact.sheet`) |
| Null data item, null `FundFactSheet` list, or null row entry | `systemFailure()` | Reason only |
| Transport, timeout, decryption, deserialization error | `systemFailure()` | Exception type only (executor already logs `apim.exchange.failed`) |

An empty `FundFactSheet` list inside a non-null item is valid and contributes zero rows. Adapter logs never include response bodies, names, links, `seq`, policy number or account identifiers.

## Routing

`ApimExchangeRequest` with `ApimDatabaseScope.ACCOUNT_ENV`, `ApimCredentialResolutionContext(actorUserRole, accountEnv)` and `ApimRetryEligibility.SAFE_BUSINESS_CALL`. Feature code must not use the facade, token, certificate, crypto or profile services directly.

## Routing ledger

Add to `ApimRoutingLedgerCharacterizationTest`:

```text
operation:        TRPGetFundFactSheet
file:             fundfactsheet/ApimFundFactSheetSourceAdapter.java
currentScope:     ACCOUNT_ENV
currentRole:      command.actorUserRole()
accountEnvSource: command.accountEnv()
retry:            SAFE_BUSINESS_CALL
encryptionOwner:  EXECUTOR
target:           ACCOUNT_ENV
```

Expected size changes from 19 to 20. The existing omission of `TRPGetFundPrice` from the ledger is recorded as a separate defect and is not fixed in this delivery.
