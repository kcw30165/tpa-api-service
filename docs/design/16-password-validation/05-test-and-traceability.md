# 05 Test and traceability

All tests below are planned acceptance criteria, not executed results. R identifiers trace approved requirements; T identifiers are proposed test cases. Unit tests alone cannot prove filter/body ordering or Redis atomicity.

| Requirement | Acceptance cases | Layer |
|---|---|---|
| R01 One endpoint, initial purpose only | T01 Personal Information accepted; future/unknown purpose rejected before APIM | HTTP + use case |
| R02 Server identity and existing transport | T02 FE cust-id cannot override actor; missing cookie/CSRF/account; wrong-account ownership; actor provenance regression | HTTP + integration |
| R03 Exact password handling | T03 spaces/case preserved; null/blank/length boundaries; no registration complexity; no password echo | unit + adapter |
| R04 Exact reviewed changes | T04 key reorder equal; changed value/type/array order/null presence unequal; apply-all/formVersion change unequal | canonicalizer + HTTP |
| R05 Five-minute expiry | T05 before/at/after expiry; session expiry earlier; TTL never extended by grant | clock + Redis |
| R06 One active grant per scope | T06 successful replacement invalidates old; failed password preserves old; different scopes coexist; concurrent issue commits | Redis integration |
| R07 First submission consumes | T07 success, YAML/access/structure failure, empty body and malformed JSON all consume; second use rejected | full HTTP |
| R08 Atomic ownership and consumption | T08 parallel same-token requests: one winner; wrong token/session/account/purpose does not delete valid grant | Redis + HTTP |
| R09 No downstream retry | T09 one business attempt for timeout, HTTP failure, crypto failure and connection failure; no hidden use-case retry | transport integration |
| R10 Wrong password code only | T10 false success -> 401, no token/fail-count; fail-count never triggers local lock | adapter + HTTP |
| R11 Locked account cleanup | T11 423 and cookie cleared on successful revoke; 503 and cookie cleared on revoke failure; no token | HTTP + lifecycle |
| R12 Unregistered cust-id | T12 recognized code -> 503, no account-existence details | adapter + HTTP |
| R13 Downstream shape | T13 exact one row; missing/null/empty/multiple rows; null/missing success; recognized error without data; unknown error; contradictory error/data | parameterized adapter |
| R14 Username-wide throttle | T14 first ten admitted; eleventh 429; reset; same user across sessions/accounts; different user isolated; concurrent admission | Redis integration |
| R15 Fail closed | T15 limiter/store/consume/session-state failures -> 503, no mutation; consume uncertainty never retried | fault injection |
| R16 No sensitive logging | T16 all body/header/APIM/toString/error paths exclude secrets, including malformed JSON and logging enabled | logging guards |
| R17 Preserve existing Personal Information | T17 existing result schema, apply-all downstream behavior, YAML/access cache refresh and error targets unchanged | regression |
| R18 No business replay | T18 consumed token rejected on retry; timeout not converted into success/failure certainty; no automatic FE retry | HTTP + FE |
| R19 Session lifecycle | T19 logout/expiry makes grants unusable; concurrent revocation boundary; session cannot be resurrected by grant write | Redis + lifecycle |
| R20 Public contract | T20 200/400/401/403/423/429/503 and generic envelope; no DOWNSTREAM*; null messages; no-store; Retry-After | contract |

## Additional canonicalization vectors
T04a nested object order and escaped-string equivalence. T04b decimal 1 versus 1.0 equal; string "1" distinct. T04c absent versus null distinct. T04d duplicate keys rejected at both endpoints. T04e Unicode strings are not normalized. T04f unknown submitted fields remain in binding even if mapper ignores them. T04g version mismatch rejects rather than reinterprets old digest. T04h oversized/deep JSON follows verified limits and never bypasses authorization.

## Required full-chain proofs
- Enable global body logging and an explicit endpoint logging rule; verify the hard exclusion and consume-before-decode behavior still hold.
- Missing Account-Ref must not bypass protection merely because the existing context filter skips resolution when the header is absent.
- Exercise real route matching, exception handlers and controller argument resolution, not a mocked direct controller call.
- Exercise real Redis scripts against the supported deployment Redis version, with concurrent issuance/consume/revocation and fault paths.
- Assert mutation dispatch count is zero for every pre-dispatch rejection and at most one for concurrent use of the same grant.
- Exercise the generalized session-lifecycle CAS mechanism end to end: an expired ACTIVE session is transitioned to EXPIRED by loadAndTouch rather than rejected as ineligible.
- Read back the actual Redis key TTL (not just the stored idleExpiresAt/status fields) after a successful touch, and confirm it reflects the new sliding idle-expiry window.
- Read back the actual Redis key TTL after a terminal transition (logout, revoke, discovered expiry), and confirm it equals the configured fixed terminal-retention duration, not the sliding TTL that was in effect beforehand.
- Exercise both stale-write races (a loadAndTouch in flight racing a concurrent terminal transition, and a loadAndTouch in flight racing a concurrent grant consumption) against the real generalized CAS mechanism and confirm neither can overwrite the newer state.

## Evidence and gates
Per subtask: fail-first test evidence, implementation, compile, test-compile, targeted new/changed tests plus existing tests for every changed class. Stop and repair cumulative compile failures. Preserve exact command, exit code and build-report manifest; do not duplicate full successful Maven output into evidence text.

At milestone closure: full test suite, formatting check, coverage review, startup smoke and SonarQube quality gate. No claims of passed gates without actual reports or explicit user confirmation. Suggested feature coverage follows the project's tiered policy: pure logic/validators/records >=95% instruction and branch; orchestration >=95% instruction and 85-90% branch with justified defensive gaps. Confirm gate configuration against repository policy before enforcing it.
