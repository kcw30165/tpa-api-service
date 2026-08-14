# Shared Auth-Flow Revision CAS Handover

## Permanent shared fix

- Shared replace and delete operations compare `sessionRevision`, not regenerated JSON.
- Replacement preserves remaining TTL.
- Legacy initialization retains whole-document proof because legacy records have no revision token.
- Temporary CAS diagnostics are removed.

## Repair 007

The focused Cancel policy test proved the production policy emits source `BFF` for `REVISION_CONFLICT`. Repair 006 retained the incorrect test expectation `SERVER`. Repair 007 changes only the expected source to `BFF`, while preserving `VALIDATION_FAILED`, `RETURN_TO_LOGIN`, `AUTH_FLOW_INVALID`, and HTTP 409. The conflict is still no longer mislabeled as idempotency-key reuse.
