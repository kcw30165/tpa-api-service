# Username Recovery CAS Diagnostic Handover

## Purpose

Temporarily diagnose whether a Redis whole-value CAS mismatch represents a real revision change or a representation-only mismatch.

## Safe WARN event

`event=auth-flow-session.cas-conflict-diagnostic`

Fields are limited to scenario, expected and updated lifecycle statuses, record/parse booleans, revision-equality boolean, document-equality boolean, and document lengths. No session ID, Redis key, idempotency key, Scheme-Ref, CSRF token, revision value, JSON, ciphertext, fingerprint, or personal data is logged.

## Interpretation

- `currentRevisionEqualsExpected=false`: the authoritative Redis revision differs from the loaded snapshot.
- `currentRevisionEqualsExpected=true` and `currentDocumentEqualsExpected=false`: the revision is unchanged but whole-document CAS failed because the serialized representations differ.
- No CAS diagnostic event for a public 409: the 409 did not arise from Redis replace return code `0`; investigate the idempotency classification branch instead.

Remove the temporary diagnostics after the DEV cause is confirmed and the permanent fix is validated.
