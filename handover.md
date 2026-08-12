# Username Recovery Start Public Contract Fix Handover

## Stage
Focused contract repair v4 applied at 20260812-203556.

## Changes
- Assert numeric HTTP 422, independent of Spring reason-phrase aliases.
- Inspect every Set-Cookie header value.
- Assert the emitted scheme property isRegistered.
- Map the single production err.workflow.system.unavailable literal to err.system.unavailable across the actual main-source location.

## Preservation checks
- Every test replacement requires exact expected counts.
- Production replacement requires exactly one old literal, or accepts an already-corrected mapping.
- Unrelated workflow mappings are not bulk-replaced.

## Evidence
target/evidence/20260812-203556-username-recovery-start-public-contract-fix-v4
