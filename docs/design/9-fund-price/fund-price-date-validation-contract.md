# Fund Price Date Validation Contract

## Configurable formats

Use a single strict configuration-backed full-date format, default representing
`dd/MM/yyyy`, for FE input, downstream request, and the `available-fund-price-dates`
values returned by `TRPGetFundPriceMode`. There is NO separate MONTHLY input format:
in both DAILY and MONTHLY mode, FE sends one of the exact `availableFundPriceDates`
values Java already returned via the `/mode` endpoint, in this same full-date format.

The format pattern must not be embedded as an operational Java literal. Invalid configuration fails startup.

## DAILY

For requested day `D`, reference date `R`, and maximum months `M`:

```text
R.minusMonths(M) <= D <= R
```

The requested day must also exactly match one parsed available date. Downstream `from-date` and `to-date` both equal `D`.

## MONTHLY

MONTHLY validates the requested day `D` identically to DAILY above -- the same
`R.minusMonths(M) <= D <= R` bound and the same requirement that `D` exactly match one
parsed available date. There is no independent month-level bound check.

MONTHLY differs from DAILY only in how the downstream range is derived from the
now-validated `D`: Java derives the calendar month containing `D` and uses that
month's first and last calendar day as the downstream `from-date` and `to-date`, both
formatted using the configured full-date request formatter.

## Boundary semantics

- Bounds are inclusive.
- `LocalDate.minusMonths` governs the lower bound for both DAILY and MONTHLY, since both validate the same full date `D`.
- Invalid dates and invalid leap-day inputs fail strict parsing.
- Validation failures must invoke APIM zero times.

## Downstream range derivation

The cached mode governs only how the validated date `D` maps to the downstream
request range -- not what shape FE sends, since FE always sends the same full-date
format in both modes:

- DAILY: `from-date` = `to-date` = `D`.
- MONTHLY: `from-date` = first calendar day of `D`'s month, `to-date` = last calendar
  day of `D`'s month, both formatted with the configured full-date request format.
