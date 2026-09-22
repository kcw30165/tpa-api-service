# Fund Price Date Validation Contract

## Configurable formats

Use two strict configuration-backed formats:

- full-date FE input and downstream request, default representing `dd/MM/yyyy`;
- MONTHLY FE input, default representing `MM/yyyy`.

Available dates returned by `TRPGetFundPriceMode` use the SAME configured full-date
format (`dd/MM/yyyy`) as the FE input and downstream request. It is NOT an
independently configured pattern and must not be given its own property.

The format patterns must not be embedded as operational Java literals. Invalid configuration fails startup.

## DAILY

For requested day `D`, reference date `R`, and maximum months `M`:

```text
R.minusMonths(M) <= D <= R
```

The requested day must also exactly match one parsed available date. Downstream `from-date` and `to-date` both equal `D`.

## MONTHLY

For requested month `Q`:

```text
YearMonth(R.minusMonths(M)) <= Q <= YearMonth(R)
```

At least one available date must fall in `Q`. Downstream range is the first through last calendar day of `Q`, formatted using the configured full-date request formatter.

## Boundary semantics

- Bounds are inclusive.
- `LocalDate.minusMonths` governs DAILY lower-bound behavior.
- `YearMonth` governs MONTHLY boundaries.
- Invalid dates and invalid leap-day inputs fail strict parsing.
- Validation failures must invoke APIM zero times.

## Mode/input-shape agreement

The cached mode governs the accepted FE input shape:

- DAILY requires a full date in the configured full-date format (dd/MM/yyyy).
- MONTHLY requires a month in the configured month format (MM/yyyy).
- A DAILY-shaped value under MONTHLY, or a MONTHLY-shaped value under DAILY, is a
  standard HTTP 400 validation failure and must invoke APIM zero times.

Downstream range derivation:

- DAILY: from-date = to-date = the requested day.
- MONTHLY: from-date = first calendar day of the month, to-date = last calendar day
  of the month, both formatted with the configured full-date request format.
