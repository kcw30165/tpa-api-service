# Fund Price Date Validation Contract

## Configurable formats

Use three strict configuration-backed formats:

- full-date FE input and downstream request, default representing `dd/MM/yyyy`;
- MONTHLY FE input, default representing `MM/yyyy`;
- available-date APIM response, default representing `yyyy-MM-dd`.

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
