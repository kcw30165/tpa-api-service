# Fund Fact Sheet APIM/TAS Contract

> Contract changed on 2026-10-08 (change notice; decisions C-1 to C-7): the request carries `policy-no`, `cert-no` and `sch-type`; there is no reference date; no funds is an empty `FundFactSheet` list.

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
  "cert-no": "230",
  "sch-type": "OE"
}
```

| Field | Type | Source |
|---|---|---|
| `policy-no` | JSON String | `PortalAccessContext.account().policyNo()` |
| `cert-no` | JSON String | `PortalAccessContext.account().certNo()`, passed through unchanged (no numeric conversion) |
| `sch-type` | JSON String | `PortalAccessContext.account().schemeType()` |

All three values come only from the trusted account context. If any of them is null or blank, APIM is not called (decisions C-7; see "Account identifiers" below).

`cert-no` is sent as a JSON String (decisions C-6). The owner tested a String value against the downstream API and it is accepted; Account Balance already sends `cert-no` as a String. `policy-no` and `cert-no` are in the configured `apim.encryption.request-fields` list, so the executor encrypts them; `sch-type` is a scheme code that is not in that list and is sent as is. The encryption configuration is unchanged.

The request carries no date. The earlier contract also had a `ref-date` field (resolved through `ReferenceDatePort` and formatted `dd/MM/uuuu`); it was removed by the downstream change together with the feature-local formatter (decisions C-2).

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

### No-fund response (changed by the downstream developer; owner-confirmed)

```json
{
  "response": {
    "err-message": "",
    "data": [
      {
        "FundFactSheet": []
      }
    ]
  }
}
```

| Field | DTO type | Meaning |
|---|---|---|
| `err-message` | String | Empty on success, including no funds. A non-empty value (for example `err.scheme.invalid`) is a system failure |
| `data[]` | List | One or more items on success; for no funds, one item whose `FundFactSheet` list is empty. Absent or empty is malformed |
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
| `err-message` empty, `data` non-empty, and every `FundFactSheet` list empty (no funds: `data: [{"FundFactSheet": []}]`) | Success, zero rows | None |
| `err-message` empty, `data` non-empty, all items and rows non-null | Success | None |
| `response` null or `err-message` null | `systemFailure()` | None (executor logs `apim.contract.invalid` at ERROR) |
| Any non-empty `err-message` (including `err.scheme.invalid`, and `err.tpa.no.fund.fact.sheet`, which no longer exists as a no-fund signal and gets no special handling) | `systemFailure()` | None (executor logs `apim.business.rejected` at WARN with the sanitized code) |
| `err-message` empty, `data` null | `systemFailure()` | None (executor logs `apim.contract.invalid` at ERROR) |
| `err-message` empty, `data` `[]` | `systemFailure()` | None (executor logs `apim.contract.invalid`; no funds is a non-empty `data` with an empty `FundFactSheet` list, never an empty `data`) |
| Null data item, null `FundFactSheet` list, or null row entry | `systemFailure()` | Adapter logs a value-free reason (the executor classifies these envelopes as success and logs nothing) |
| Transport, timeout, decryption, deserialization error | `systemFailure()` | None (executor logs `apim.exchange.failed`) |

An empty `FundFactSheet` list inside a non-null item is valid and contributes zero rows. Adapter logs never include response bodies, names, links, `seq`, policy number or account identifiers. The adapter uses a static SLF4J logger and `event=fund-fact-sheet.<name>` lines, as `ApimAccountGainLossSourceAdapter` does.

## Routing

`ApimExchangeRequest` with `ApimDatabaseScope.ACCOUNT_ENV`, `ApimCredentialResolutionContext(actorUserRole, accountEnv)` and `ApimRetryEligibility.SAFE_BUSINESS_CALL`. Feature code must not use the facade, token, certificate, crypto or profile services directly. Architecture guards scan source text, so those type names, `PortalAccessContext` and the other names listed in the implementation plan section 1.7 must not appear under `adapter/out`, even in comments. The adapter builds the `ApimExchangeRequest` inside `Mono.defer` and names its parameter `command`; the Fetch command validates its fields in its constructor, so a blank `accountEnv` never reaches `ApimExchangeRequest`.

## Account identifiers

`policyNo`, `certNo` and `schemeType` come only from the trusted account context. If any of them is null or blank, the use case logs the value-free event `event=fund-fact-sheet.missing-account-identifier` and fails with `FundFactSheetRetrievalException.systemFailure()` before the adapter is called, so APIM is not called (decisions C-7). `FetchFundFactSheetCommand` also rejects a null or blank `policyNo`, `certNo`, `schemeType` or `accountEnv` in its constructor as a second line of defence.

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
