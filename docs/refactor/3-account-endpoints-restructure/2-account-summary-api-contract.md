# Account Summary API Contract

## Request

```
GET /api/v1/account/summary
GET /api/v1/account/summary?currency=USD
GET /api/v1/account/summary?period=YEAR_TO_DATE
```

Header profile is identical to `GET /api/v1/account/balance`:

| Header | Requirement |
| --- | --- |
| X-Request-Id | Required by the shared mandatory-header filter |
| Accept-Language | Optional; English fallback via the shared resolver |
| Account-Ref | Required by selected-account context resolution |
| Idempotency-Key | Absent; the operation is a read-only GET |

`currency` is optional and behaves exactly as on `/account/balance`: omission
means initial load and sends `curr: ""` to `TRPGetMemberAccBal`.

## Response envelope

`MutationResponse` with `ApiStatus.SUCCESS`, data under `result.data`. The
payload is the full `AccountBalanceDataResponse` shape plus one new sibling
section, `gainLossBreakdown`.

```
result.data
 ├─ accountNumber           (new)
 ├─ asOfDate                (unchanged, from TRPGetMemberAccBal as-of-date)
 ├─ currencySelection       (unchanged)
 ├─ accountOverview         (unchanged)
 ├─ investmentPortfolio     (unchanged)
 └─ gainLossBreakdown       (new)
```

Every existing Account Balance field keeps its current name, nullability,
ordering, and Jackson inclusion behaviour. No existing field is moved,
renamed, or removed.

## accountNumber

`accountNumber` is a non-null string composed from the selected account's
protected context:

```
accountNumber = AccountContext.policyNo + "-" + AccountContext.certNo
```

It is derived entirely inside the BFF from `PortalAccessContext`. It is not
requested from, or echoed by, `TRPGetMemberAccBal` or `TRPGetInvestGL`, and it
is not accepted as a request parameter. The front end cannot influence it.

The field is present on `/api/v1/account/summary` only. `/api/v1/account/balance`
and `/api/v1/account/gain-loss` are unchanged, so no existing consumer contract
moves.

Both components are left-padded to their configured width before joining:

```
accountNumber = leftPad(policyNo, 11) + "-" + leftPad(certNo, 4)
```

With `policy-no.width: 11`, `cert-no.width: 4`, and `pad-character: "0"` from
the global `account-identifiers` block, a member holding policy `8802`
certificate `230` receives `"00000008802-0230"`.

The value is always exactly 16 characters, so the front end can rely on a fixed
width. A component longer than its configured width is a contract violation and
maps to SYSTEM_FAILURE rather than being truncated.

Both `policyNo` and `certNo` are protected identifiers listed in
`apim.encryption.request-fields` and `response-fields`. Emitting their
concatenation to the front end is therefore a deliberate disclosure of two
otherwise-protected values in one field, approved as part of this requirement.
The composed value must never appear in logs or observability events.

## gainLossBreakdown section

```
gainLossBreakdown
 ├─ period                  SINCE_SCHEME_INCEPTION | YEAR_TO_DATE
 ├─ gainLossAsOfDate        dd/MM/yyyy, from TRPGetInvestGL as-of-date
 ├─ totalValue              string, grouped, NAV precision
 ├─ totalGainLoss           string, grouped, NAV precision, signed
 └─ holdings[]
        ├─ fundCode
        ├─ fundName         localized via Accept-Language
        ├─ fundSequence        from TRPGetInvestGL seq
        ├─ balance          string, NAV precision
        ├─ portfolioPercentage  string, downstream-supplied, ab-percentage precision
        └─ gainLoss         string, NAV precision, signed
```

Field mapping to the supplied Portfolio Overview UI:

| UI element | Response source |
| --- | --- |
| TOTAL VALUE | `gainLossBreakdown.totalValue` |
| Total Gain/Loss (HKD) | `gainLossBreakdown.totalGainLoss` |
| "As of <date>" above the total | `gainLossBreakdown.gainLossAsOfDate` |
| Since Scheme Inception / Year to Date toggle | `gainLossBreakdown.period` |
| Holdings → Fund Name | `holdings[].fundName` |
| Holdings → Balance (HKD) | `holdings[].balance` |
| Holdings → % of Portfolio | `holdings[].portfolioPercentage` |
| Holdings → Gain/Loss (HKD) | `holdings[].gainLoss` |
| Donut segment sizing and colour | Derived by FE from `portfolioPercentage` and list order |
| Highlighted single-fund card | Derived by FE from a selected `holdings[]` entry |

The BFF does not emit colours, chart geometry, fixed labels, currency symbols,
`+`/`-` prefixes, or the "As of" label text. Sign is conveyed by the numeric
string itself; presentation is FE-owned. This mirrors the existing Account
Balance and Account Gain/Loss contracts.

## Period selection

`period` is an optional query parameter accepting `SINCE_SCHEME_INCEPTION` or
`YEAR_TO_DATE`, defaulting to `SINCE_SCHEME_INCEPTION`, identical to
`/api/v1/account/gain-loss`. `gainLossBreakdown.period` always echoes the
effective value.

`currency` is forwarded to both upstream calls, so the balance sections and the
gain/loss breakdown are always expressed in the same currency.
