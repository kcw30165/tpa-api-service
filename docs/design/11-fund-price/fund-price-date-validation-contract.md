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

MONTHLY does not differ from DAILY in downstream range derivation. In both modes,
Java sends the single validated date `D` as both the downstream `from-date` and
`to-date`, formatted using the configured full-date request formatter. Mode has no
effect on how Java shapes the downstream request; mode exists solely so FE can decide
whether to render a day selector (DAILY) or a month selector (MONTHLY) -- FE still
submits one exact date value from `availableFundPriceDates` in both cases. (Correction,
Nick: an earlier version of this contract incorrectly specified a first/last-calendar-day
expansion for MONTHLY; there is no such expansion.)

## Boundary semantics

- Bounds are inclusive.
- `LocalDate.minusMonths` governs the lower bound for both DAILY and MONTHLY, since both validate the same full date `D`.
- Invalid dates and invalid leap-day inputs fail strict parsing.
- Validation failures must invoke APIM zero times.

## Downstream range derivation

Mode does not affect the downstream request range at all. In both modes, Java sends
the single validated date `D` as both `from-date` and `to-date`:
- DAILY: `from-date` = `to-date` = `D`.
- MONTHLY: `from-date` = `to-date` = `D` (identical to DAILY; there is NO calendar-month
expansion). This corrects an earlier version of this contract that incorrectly derived
a first/last-calendar-day range for MONTHLY: FE always submits one specific date (one of
the exact values in `availableFundPriceDates`) regardless of mode, and Java's only
MONTHLY-specific responsibility is validating that date against `availableFundPriceDates`,
identically to DAILY.

## Java pattern for the full-date format

The wire/display format is `dd/MM/yyyy` text (for example `10/07/2026`), but the Java
`DateTimeFormatter` pattern configured in `fund-price.date-format.full-date` MUST be
`dd/MM/uuuu`. Parsing is `ResolverStyle.STRICT`, and under STRICT the `yyyy` letter
(year-of-era) cannot resolve a `LocalDate` without an era field, so a `dd/MM/yyyy`
pattern formats correctly but rejects every input -- which previously made every
`/fund-price/mode` call fail with 503 and every FE `date` fail with 400. `uuuu`
(proleptic year) parses correctly and still rejects impossible dates such as
`31/02/2026` and `29/02/2026`. This matches every other STRICT date formatter in the
service.

`FundPriceDateFormatProperties.validate()` enforces this at startup with a strict
format-then-parse round trip of a probe date; a pattern that cannot parse back, or that
does not reproduce the same date (for example a two-digit `uu` year), fails startup
instead of failing per request. Tests use the production formatter
(`new FundPriceDateFormatProperties().fullDateFormatter()`) rather than a lenient
locally built `ofPattern(...)`, so this class of defect is covered by unit tests.
