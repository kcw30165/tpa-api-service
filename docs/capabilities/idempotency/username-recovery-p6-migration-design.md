# Username Recovery P6 Shared Idempotency Migration Design

## 1. Decision and baseline

P6 migrates the existing Username Recovery response-idempotency lifecycle at exact starting Git HEAD `49fb6adc3cab82b753b9bbd7e20c05b42fc7820f` to the repository-wide shared idempotency capability. P5 remains closed unless P6 exposes a concrete regression.

The repository proves three distinct protected HTTP actions:

- `POST /api/v1/auth/username-recovery/start`
- `POST /api/v1/auth/username-recovery/request`
- `POST /api/v1/auth/username-recovery/cancel`

All three currently require `Idempotency-Key`, have feature-local action identities, fingerprints, ownership-checked Redis lifecycle records, replay codecs, use-case orchestration, Spring composition, and behavioral tests. CANCEL therefore remains in P6 scope. This is not a new idempotency requirement.

## 2. Action matrix

### START

- Shared identity: capability `username-recovery`, action `start`.
- Scope: anonymous versioned constant. Raw identity and contact data are not an authorized scope.
- Request equivalence: preserve the current versioned canonical semantics for identity type, normalized identity value, date of birth, supplied email/mobile semantics, and captcha challenge identity only where the existing fingerprint proves it participates. Accept-Language, request ID, cookies, CSRF, origin, and referer are excluded unless current code proves semantic participation.
- Acquired ordering: validate, reserve shared lifecycle, consume one-time captcha, dispatch APIM member lookup, build protected context, create the revisioned auth-flow session, establish CSRF continuity, then capture and complete.
- Side effects: captcha consumption, APIM identity lookup, protected context creation, auth-flow session creation/update, CSRF state, and cookie instruction.
- Protected continuity: required for `SESSION_CREATED`. The existing encrypted auth-flow session contains identity, candidates, username, verified destination, session identity, CSRF hash, and future request state. The shared snapshot may carry only an opaque protected replay reference.
- Safe snapshot: original HTTP classification, public status/outcome, next step, safe scheme projections, messages, errors, expiry metadata, and allowlisted safe headers. It must not contain raw identity, date of birth, email, mobile, username, captcha values, session ID, CSRF, cookie value, Scheme-Ref secret, or downstream payload.
- Failure classification: failures proven before captcha consumption and APIM/session effects may be `PROVEN_NO_SIDE_EFFECT`; any failure after captcha consumption, APIM dispatch, or uncertain session persistence is `POSSIBLE_SIDE_EFFECT`.

### REQUEST

- Shared identity: capability `username-recovery`, action `request`.
- Scope: opaque hash of the authorized auth-flow session identity.
- Request equivalence: session-bound selected opaque Scheme-Ref using the established versioned canonical fingerprint. Raw session ID and Scheme-Ref must not enter shared persistence.
- Acquired ordering: authorize session and scenario, validate expiry/origin/referer/CSRF, load protected context, reserve the candidate with revisioned session CAS, resolve effective destination, dispatch exactly one message containing the recovered username, persist post-delivery workflow state, then complete.
- Side effects: protected-context/session reservation, destination resolution, message delivery, post-delivery session mutation, workflow completion/blocking.
- Protected continuity: required. The current replay codec directly embeds an auth-flow session identifier and must not be carried forward into the shared terminal snapshot. Replace it with an opaque protected replay reference resolved through separately protected state or authoritative encrypted session continuity.
- Safe snapshot: original HTTP classification, public status/outcome, next step, masked destination, destination type, messages, errors, and allowlisted safe headers. It must not contain username, raw destination, sender reference, auth-flow session ID, CSRF, Scheme-Ref, cookie, or message payload.
- Failure classification: deterministic validation and business outcomes are completed; proven pre-dispatch failures may be retry-safe; delivery acceptance or uncertainty, post-delivery CAS failure, and uncertain completion are `POSSIBLE_SIDE_EFFECT` and must become `UNKNOWN_OUTCOME` without blind resend.

### CANCEL

- Shared identity: capability `username-recovery`, action `cancel`.
- Scope: opaque hash of the authorized auth-flow session identity.
- Request equivalence: preserve the existing action-separated, session-bound constant/versioned semantics.
- Acquired ordering: load and authorize the session, preserve current absent/expired/already-cancelled/terminal idempotent-success behavior, validate origin/referer/CSRF where required, validate retained protected context, perform revision-aware cancellation, verify retained protected state and original expiry, then complete.
- Side effects: revision-aware session cancellation and auth-flow cookie clearing instruction.
- Protected continuity: no new replay store is required for the successful public body. Existing encrypted context remains workflow state until original session TTL expiry and is not response-idempotency infrastructure.
- Safe snapshot: original HTTP classification, public status/outcome, `RETURN_TO_LOGIN`, messages, errors, and allowlisted safe headers. Cookie values and session identity are excluded.
- Failure classification: failures before cancellation is attempted may be proven no-side-effect only with direct evidence; uncertain revisioned cancellation or completion-persistence failure must fail closed and cannot execute cancellation again.

## 3. Terminal classification

Every deterministic public result, including validation failure, member-not-found, registration-required, invalid workflow/session/CSRF/Scheme-Ref, already-requested, completed-workflow, known delivery rejection, absent/expired/already-cancelled terminal cancellation, and successful outcomes, is completed and replayable when it occurs inside the acquired boundary.

`COMPLETED_REPLAY` is internal lifecycle metadata. Hydration restores the original public result and never maps replay to public success. `DUPLICATE_IN_PROGRESS`, key reuse, failed-existing, unknown-existing, corrupt snapshot, unsupported snapshot version, and protected-state resolution failure remain protocol or fail-closed outcomes and are not persisted as replacement business success.

## 4. Compatibility policy

The feature-local namespace is structurally separate from the shared HMAC namespace:

- legacy: `{redis-cache.key-prefix}:username-recovery:idempotency:{username-recovery-start|username-recovery-request|username-recovery-cancel}:{client-key}`
- shared: `{redis-cache.key-prefix}:idempotency:v1:{HMAC-derived-identity}`

The audit found session TTL configuration of 600 seconds and legacy transitions that preserve the existing record TTL. Before P6-F, the exact idempotency TTL source and maximum effective legacy TTL must be proven from production composition and configuration.

Because P6 begins before Username Recovery shared migration deployment, the default repository policy is namespace isolation with one active writer per action and no dual-write. Legacy records are not interpreted by the shared reader. If rollout evidence later proves an overlap requiring replay, add only a bounded read-only compatibility reader. Ambiguous, corrupt, expired, or unsupported legacy records fail closed. No Redis flush or broad key deletion is permitted.

## 5. Stale-component inventory

After all three production consumers use the shared contract and compatibility gates pass, remove the feature-local response-idempotency lifecycle:

- `UsernameRecoveryIdempotencyPort` and its reserve/complete/unknown commands;
- action, key, fingerprint, operation-reference, reservation, outcome, record, status, and replay-snapshot lifecycle DTOs that have no remaining non-legacy consumer;
- `RedisUsernameRecoveryIdempotencyAdapter`, JSON mapper, wire record, key factory, and Lua transition resource;
- feature-local operation-reference generation and duplicated reserve/resolve/complete/unknown orchestration in START, REQUEST, and CANCEL;
- obsolete fingerprint generators and replay codecs after their useful versioned concepts are represented through the shared integration contract;
- Spring beans creating the feature-local adapter;
- tests and architecture guards that assert the retired lifecycle rather than replacement behavior.

Retain the AES-GCM Username Recovery auth-flow context protector, encrypted context binding, revisioned auth-flow session state, destination resolution, message delivery, controller policies, and protected workflow tests. These are workflow/security facilities, not duplicate response-idempotency infrastructure.

## 6. Acceptance matrix

For each action and each deterministic terminal result:

- first HTTP status equals replay HTTP status;
- first public response body equals replay public response body;
- original public status, next step, messages, errors, and approved safe headers are identical;
- protected operation invocation count is exactly one;
- every externally visible side-effect invocation count is exactly one.

Cumulative required scenarios:

- same key with a different fingerprint returns key-reuse conflict and invokes no protected operation;
- concurrent duplicates invoke the protected operation once;
- failure before the first protected effect follows the explicitly proven retry-safe classification;
- failure after dispatch or possible acceptance becomes unknown outcome and never blindly retries;
- response-capture and completion-persistence failures fail closed and never re-execute;
- corrupt and unsupported-version snapshots fail closed;
- unsafe headers are rejected;
- required protected replay state missing, malformed, expired, or unavailable fails closed;
- legacy namespace isolation or bounded compatibility is proven;
- expiry permits key reuse only after authoritative expiry;
- cancellation replay preserves current revision behavior and does not repeat cancellation or cleanup;
- captcha consumption, APIM lookup, identity verification, username reveal/message delivery, session mutation, CSRF mutation, cancellation, and cleanup are never repeated.

## 7. Validated delivery sequence

- P6-A: this evidence-backed design, action matrix, compatibility policy, stale inventory, acceptance matrix, and handover baseline.
- P6-B: Username Recovery shared identity/fingerprint/classifier policy, strict action-tagged response envelope, typed transient boundaries, safe codecs, protected-reference contracts, and replay hydration. No production switch.
- P6-C: START protected-operation extraction, protected replay continuity, shared coordinator, tests, atomic composition switch, and exact legacy START coordinator cleanup.
- P6-D: REQUEST protected-operation extraction preserving session-first/no-blind-resend ordering, protected replay resolution, shared coordinator, tests, atomic switch, and exact legacy REQUEST coordinator cleanup.
- P6-E: CANCEL protected-operation extraction, shared coordinator, revision-aware replay tests, atomic switch, and exact legacy CANCEL coordinator cleanup.
- P6-F: prove compatibility closure and delete the remaining feature-local response-idempotency port, Redis adapter, records, codecs, key factory, Lua resource, Spring beans, and obsolete guards.
- P6-G: one Spotless pass, compile, test-compile, focused behavioral/concurrency/corruption/compatibility/architecture validation, full Maven suite gate, normal Spring startup gate, documentation closure, and final commit.

A full Maven suite and successful Spring startup are mandatory before every major implementation-stage transition. Intermediate rounds run only `mvn -DskipTests compile` and `mvn -DskipTests test-compile` and defer `spotless:apply` until P6-G.
