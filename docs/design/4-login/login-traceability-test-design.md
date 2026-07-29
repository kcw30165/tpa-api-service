> Status: Design baseline v0.1  
> Date: 20 July 2026  
> Scope: Subsequent member login plus reusable authentication foundations  
> Source baseline: Login BRD V0.3, discovery decisions in 02-brd.md through 10-brd.md, and the supplied current codebase snapshot  
> Open external dependency: detailed `TRPGetAccountList` business failure-code mapping remains TBC.


# Login Traceability and Test Design

## 1. TDD rule

Every implementation subtask begins with a failing contract/unit/integration test and preserves evidence of the failure and subsequent pass. Registration tests are mandatory regression gates for shared-foundation refactoring.

## 2. Core scenarios

### Credentials

- Configured username normalization and validation
- Password preserved exactly and case-sensitive
- Empty/malformed APIM envelope
- Invalid credentials and locked account mapping
- Destination list validation/masking

### OTP reuse

- Shared OTP port used by registration and login
- Initial send not counted as resend
- Five configured resends
- Five verification attempts per challenge
- Replacement invalidates old challenge
- Resend resets verification allowance
- Exhaustion and HTTP mappings unchanged from shared contract
- Idempotent replay creates no duplicate delivery

### Accounts

- Exact `TRPGetAccountList` request from protected context
- Policy/certificate validation and configurable zero-padding
- Termination status/date pair validation
- Partial malformed records omitted and login succeeds
- Zero valid records returns HTTP 502
- Empty/unavailable result returns stable fallback
- AccountRefs unique and session-scoped

### Sessions

- Complete-context encryption before Redis persistence
- No sensitive plaintext in Redis or keys
- Atomic session creation/replacement
- Failure leaves previous session active
- Last concurrent commit wins
- Old session returns `err.login.session.replaced`
- Sliding expiry only on valid requests
- Logout and internal revocation

### Portal context

- Missing AccountRef -> 400
- Foreign AccountRef -> 403
- Valid AccountRef builds exact `PortalAccessContext`
- `schemeType` null is tolerated
- `Session-Id` header no longer accepted
- Temporary YAML adapter absent from production wiring

### APIM

- Shared envelope only
- Existing crypto/token/certificate handling reused
- Three safe business-call attempts for transient failures
- Token/certificate recovery outside business-call budget
- No retry for business/contract/decrypt failures
- Non-idempotent operation opt-out

## 3. Regression gates

- Full registration suite
- APIM facade retry/crypto tests
- Redis security/key tests
- CSRF/cookie tests
- Architecture/ArchUnit tests
- Existing selected-account controller tests

## 4. Traceability matrix

| Requirement | Design owner | Primary test area |
|---|---|---|
| Always return destination choices | API/workflow | Credential success contract |
| Java owns OTP | Shared OTP design | OTP contract/integration |
| TAS owns password locking | APIM adapter | Credential outcome mapping |
| AccountRef every scoped request | Session security | Portal context filter |
| Last login wins | Session contract | Atomic Redis concurrency |
| Complete context encrypted | State/security | Redis plaintext guard |
| Codes only | Error/API | JSON contract tests |
| Global APIM retry | Shared foundation | APIM facade resilience |
