# Fund Fact Sheet APIM/TAS Contract

## Operation: TRPGetFundFactSheet

### Path

```text
/ws/NGTPA/v1/TRPGetFundFactSheet
```

Configured as `apim.fund-fact-sheet.path` (default `${APIM_FUND_FACT_SHEET_PATH:/ws/NGTPA/v1/TRPGetFundFactSheet}`, matching the Account Balance and Gain/Loss form). Startup fails when blank, following `FundPriceModeApimProperties` / `FundPriceModeApimValidationConfig`. The key is added under the existing top-level `apim:` block of `application.yml` only; `application.properties` is not extended.

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

DTOs keep exact wire names, use `@JsonIgnoreProperties(ignoreUnknown = true)`, and live in `adapter.out.apim.fundfactsheet.dto`, the repository convention for wire DTOs.

## Envelope handling in the adapter

| Envelope | Adapter result | Adapter log |
|---|---|---|
| `err-message` = `err.tpa.no.fund.fact.sheet` (`data` absent or present) | Success, zero rows | None (executor logs `apim.business.rejected` at WARN) |
| `err-message` empty, `data` non-empty, all items and rows non-null | Success | None |
| `response` null or `err-message` null | `systemFailure()` | None (executor logs `apim.contract.invalid` at ERROR) |
| Any other non-empty `err-message` (including `err.scheme.invalid`) | `systemFailure()` | None (executor logs `apim.business.rejected` at WARN with the sanitized code) |
| `err-message` empty, `data` null | `systemFailure()` | None (executor logs `apim.contract.invalid` at ERROR) |
| `err-message` empty, `data` `[]` | `systemFailure()` | None (executor logs `apim.contract.invalid`; TAS signals no funds with `err.tpa.no.fund.fact.sheet`) |
| Null data item, null `FundFactSheet` list, or null row entry | `systemFailure()` | Adapter logs a value-free reason (the executor classifies these envelopes as success and logs nothing) |
| Transport, timeout, decryption, deserialization error | `systemFailure()` | None (executor logs `apim.exchange.failed`) |

An empty `FundFactSheet` list inside a non-null item is valid and contributes zero rows. Adapter logs never include response bodies, names, links, `seq`, policy number or account identifiers. The adapter uses a static SLF4J logger and `event=fund-fact-sheet.<name>` lines, as `ApimAccountGainLossSourceAdapter` does.

## Routing

`ApimExchangeRequest` with `ApimDatabaseScope.ACCOUNT_ENV`, `ApimCredentialResolutionContext(actorUserRole, accountEnv)` and `ApimRetryEligibility.SAFE_BUSINESS_CALL`. Feature code must not use the facade, token, certificate, crypto or profile services directly. Architecture guards scan source text, so those type names, `PortalAccessContext` and the other names listed in the implementation plan section 1.7 must not appear under `adapter/out`, even in comments. The adapter builds the `ApimExchangeRequest` inside `Mono.defer` and names its parameter `command`; the Fetch command validates its fields in its constructor, so a blank `accountEnv` never reaches `ApimExchangeRequest`.

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

Three edits in `ApimRoutingLedgerCharacterizationTest`: add the entry; change `.hasSize(19)` to `.hasSize(20)`; add "TRPGetFundFactSheet" to the `containsExactlyInAnyOrder` list. The test also reads the adapter source and requires the literals `command.actorUserRole()`, `SAFE_BUSINESS_CALL` and `ApimDatabaseScope.ACCOUNT_ENV`, resolving `file` relative to `adapter/out/apim`. The existing omission of `TRPGetFundPrice` from the ledger is recorded as a separate defect and is not fixed in this delivery.
