# Fund Price Source Inventory

## Sources

- BRD: Fund Price Information, version 0.2 final draft.
- Owner-confirmed decisions recorded during design review.
- Downstream operation schema: `TRPGetFundPrice`.
- Downstream metadata schema: revised `TRPGetFundPriceMode`.
- Repository baseline: branch `features/impl-fund-price`, HEAD
  `4fb53ffa663222ceeb26646b11d39005ca559c76` (rebased onto dev 2026-09-22).
- Repository architecture: `ApimExchangeExecutor`, authenticated selected-account context, `ReferenceDatePort`, standard public response and error handling, Contribution XLSX precedent.

## Authority order

1. Explicit owner-confirmed decision for Fund Price.
2. Approved downstream schema for the relevant operation.
3. Current repository architecture and durable guards.
4. BRD business intent.
5. Proposed implementation detail in this pack.

No implementation assumption may override a higher-authority source.
