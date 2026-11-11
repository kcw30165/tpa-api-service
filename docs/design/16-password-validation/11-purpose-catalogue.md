# 11 Purpose catalogue

How `operationPayload` is defined for each purpose, and what it takes to add one. Sections 1 to 3 describe behaviour as built. Section 4 is design guidance that has not been implemented or reviewed.

## 1. The rule

`POST /api/v1/password-validation` carries a generic envelope:

```json
{ "password": "<entered password>", "purpose": "<PURPOSE>", "operationPayload": { } }
```

`operationPayload` is **exactly the JSON body of the protected request** for that purpose. The frontend freezes the body the member reviewed, sends it here with the password, and later sends the same body, unchanged, to the protected route together with the returned token. The server binds the token to the session, the selected account, the purpose and that exact body. The M5 implementation uses its existing canonicalizer (not raw-byte equality): object member order does not matter, while values, JSON kinds, array order and member presence do. The approved M6 target retains that canonicalizer. See [08 Frontend integration](08-frontend-integration.md), section 3.

## 2. Supported purposes (M5 as built)

| Purpose | Protected request | `operationPayload` |
|---|---|---|
| `PERSONAL_INFORMATION_UPDATE` | `PUT /api/v1/personal-information` | The PUT body: `formVersion`, `applyToAllAccounts`, `fields` |
| `CHANGE_INVESTMENT_CHOICE` | `POST /api/v1/investment-choice/instructions` | The complete submit body: `switchSources`, `allocations` |
| `CANCEL_INVESTMENT_CHOICE` | `POST /api/v1/investment-choice/instructions/cancel` | The complete cancel body: `refNo` |

Current M5 issuance checks the Personal Information structure (`formVersion`
non-blank string, `applyToAllAccounts` boolean and `fields` non-empty object),
CIM submit V1-V8, and CIM cancel CV1-CV2. CIM issuance failures currently use
the generic password-validation request-invalid 400 contract. Submit snapshot
rules S1-S3 and cancel pending-reference membership are currently checked at
protected execution. Personal Information's existing PUT validation remains
authoritative. These are current implementation facts, not the approved M6
target contract.

Apply to all accounts: TAS applies an apply-all update to every account bound to the username. The token binds the submitted flag and fields, not the list of accounts (decision D21).

## 3. Current implementation boundary

By M5, all three purposes in section 2 are supported. The M6 design below
changes where CIM snapshot-dependent checks run and how CIM issuance
violations are rendered; it does not add the CIM purposes themselves.
At the original M1 baseline, CIM purposes were not accepted; that historical
status was superseded by the M1/M4 implementation milestones.

## 4. Approved purpose-owned design (not implemented)

The approved target retains the three purposes in section 2. Shared issuance
does not parse feature payloads or
contain purpose-specific branches. Each purpose has one registered decoder and
one application precheck policy; missing, duplicate or mismatched registrations
fail composition. Adding a purpose requires explicit purpose, route and bean
registration, but no edit to the shared parser or issuance algorithm.

The minimal decoder result must preserve the information that the policy needs
to diagnose its own feature violations: object member presence, JSON kinds,
unknown member names and decoded values. It is not a general-purpose JSON
framework. Jackson parsing stays in the adapter; application payload types and
policies remain framework-free. The decoder also provides strict typed
construction for execution without rerunning business or snapshot rules.

| Purpose | Issuance precheck | Protected execution |
|---|---|---|
| `PERSONAL_INFORMATION_UPDATE` | Existing generic structure check and existing 400 behavior remain unchanged | Existing PUT YAML/access/business validation and update behavior remain unchanged |
| `CHANGE_INVESTMENT_CHOICE` | Submit V1-V8 and S1-S3 before throttle and password verification | Require trusted gate binding; strict body decoding and typed construction; no feature precheck rerun |
| `CANCEL_INVESTMENT_CHOICE` | CV1-CV2 and pending-reference membership before throttle and password verification | Require trusted gate binding; strict body decoding and typed construction; no snapshot precheck or membership revalidation |

For submit, V1 structural failure returns the existing FORM violation without
a snapshot read. Structurally safe input retains V2-V8 violations and reads
the account-owned snapshot once. When available, independent S1-S3 violations
are appended after V1-V8 in S1, S2, S3 order, preserving original array
indexes and skipping missing/blank operands already invalid under V2/V5. If
the snapshot is missing or unreadable while V errors are known, return those
422 errors and do not publish the feature-owned 503 incident. With no V
violations, submit snapshot unavailability returns the feature-specific HTTP
503 `SYSTEM_ERROR` and exactly one feature-owned `LOCAL_SYSTEM` `FailureEvent`.
Cancel snapshot-miss behavior is unchanged. Expected violations produce no
technical incident, and shared issuance must not publish a duplicate event.
Generic envelope errors retain their existing 400 contract.
Wrong-password behavior is unchanged.

The existing token gate binding attribute is the trusted handoff. A
`PayloadDigest` alone is not proof of authorization: trust comes from the
gate's successful scope/token consumption and payload-binding checks before
controller dispatch. Payload equality uses the existing canonicalizer, not raw
byte equality. The CIM controller boundary and command must require the
attribute; a missing binding or rejected gate check prevents dispatch.

Submit execution retains existing downstream dispatch, reconciliation and
post-success snapshot updates. Cancel execution does not read a snapshot before
dispatch. Any snapshot access for post-success cleanup is best effort; missing
or unreadable state or cleanup failure cannot block cancellation or turn
downstream success into failure. Progress retains its existing current-state
enforcement; do not assume it repeats every Java validation rule. Tokens do not
reserve or freeze business state.

The accepted design does not change token scope, lifetime, consumption or
canonicalization. It requires no legacy-token migration, grant versioning, or
rolling-deployment handling. Personal Information issuance and PUT behavior
remain compatibility exceptions and are not changed by CIM prechecks.
Former O-12 asked how strictly purposes validate payloads at issuance. Current
M5 issuance behavior is documented in section 2; the M6 target is documented
here, but its implementation and runtime verification remain pending.
