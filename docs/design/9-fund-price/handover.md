# Fund Price Design Handover

## Current state

- BRD review completed.
- Downstream schemas reviewed and revised mode schema incorporated.
- Repository architecture investigations completed.
- Owner decisions incorporated.
- Design pack generated as a proposed baseline.
- Production implementation has not started.

## Required next steps

1. Copy this pack into `docs/design/9-fund-price/` on `features/impl-fund-price`.
2. Review the Git diff and confirm no non-document files changed.
3. Run the repository documentation checks required by `.github` guidance.
4. Commit the design pack.
5. Regenerate the GPT-5 mini implementation prompt set so every stage cites this committed design pack.
6. Begin Prompt 001 as design-characterization and fail-first tests only.

## Suggested documentation commit

```text
docs(fund-price): define implementation design baseline
```

## Implementation control

The first implementation prompt must verify the committed design baseline and stop if repository evidence contradicts it. Each later stage starts from the cumulative committed state. The final feature gate requires the full Maven suite, Spring startup smoke test, architecture guards, and at least 95.00% aggregate JaCoCo instruction coverage for the enumerated Fund Price class set.
