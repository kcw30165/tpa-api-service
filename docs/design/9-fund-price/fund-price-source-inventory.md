# Fund Price Source Inventory

## Sources

- BRD: Fund Price Information, version 0.2 final draft.
- Owner-confirmed decisions recorded during design review.
- Downstream operation schema: `TRPGetFundPrice`.
- Downstream metadata schema: revised `TRPGetFundPriceMode`.
- Repository baseline: branch `features/impl-fund-price`, HEAD `19a4b41c061ae58a34cb156232435e3eaf7d6845`.
- Repository architecture: `ApimExchangeExecutor`, authenticated selected-account context, `ReferenceDatePort`, standard public response and error handling, Contribution XLSX precedent.

## Authority order

1. Explicit owner-confirmed decision for Fund Price.
2. Approved downstream schema for the relevant operation.
3. Current repository architecture and durable guards.
4. BRD business intent.
5. Proposed implementation detail in this pack.

No implementation assumption may override a higher-authority source.
