# Fund Price Authenticated-Session Contract

## Purpose

The mode endpoint stores validated metadata so subsequent price and export requests can validate and derive ranges without recalling the mode operation.

## Scope

Metadata is selected-account scoped, not global to the login session and not part of `ActorContext`.

Conceptual value:

```json
{
  "accountRefBinding": "server-controlled association",
  "accountEnv": "DB",
  "policyNo": "00000008802",
  "referenceDate": "18/09/2026",
  "mode": "DAILY",
  "fundPriceType": "BIDOFFER PRICE",
  "availableFundPriceDates": ["18/09/2026", "18/05/2026"],
  "maximumFundPriceHistoryMonths": 36
}
```

Physical storage must follow the existing encrypted authenticated-session model. Protected identifiers and dates must not be logged.

## Compatibility

Existing authenticated sessions without Fund Price metadata remain readable. FE initializes metadata by calling `/api/v1/fund-price/mode`.

## Validity

Cached metadata is usable only when it matches the selected account, account environment, policy number, and authoritative reference date. A mismatch or malformed value fails closed as HTTP 503 `SYSTEM_ERROR`.

## Mutation safety

Investigation confirmed that whole-record blind overwrite (the existing `AuthenticatedSessionPort.store`/`touch`) can lose concurrent changes. `AesGcmAuthenticatedSessionProtector` uses a fresh random nonce on every `protect()` call, so re-encrypting the same semantic session twice produces different ciphertext even when no field changed. A CAS mechanism that compares the whole encrypted blob (as the existing CSRF rotation port does) would therefore falsely conflict on any concurrent write to an unrelated field, not just genuine races on the same field.

**Decision**: introduce a new **generic, reusable** compare-and-swap session-mutation port at the `AuthenticatedSession` level, mirroring the mechanism already proven for `AuthFlowSession` (shared today across the Registration, Login, Forgot Password, and Username Recovery scenarios via one generic `AuthFlowSessionPort`) rather than the CSRF rotation port's blob-compare mechanism. `AuthFlowSession`'s CAS discriminator is a separately-generated **opaque revision token** (`SessionRevision`), compared via `replaceIfCurrent(key, expectedRevision, newJson)` in an inline Lua script. This decouples the compare-and-swap check from encryption nondeterminism: two writers touching different fields at different revisions are correctly detected as a conflict only when they actually race on the same expected revision, not whenever ciphertext happens to differ.

The new port adds an equivalent opaque revision component to the authenticated-session envelope, generated fresh on every mutation. Callers follow the same discipline already used by every `AuthFlowSession` mutation site (for example `RegistrationStartProtectedOperation`, `ForgotPasswordOtpResendProtectedOperation`): load the current session with its revision, copy every field verbatim except the one field under mutation, call the generic port with the expected revision and the replacement session, and treat any non-`APPLIED` outcome as a lost race rather than a silent overwrite. This inherently preserves all actor data, accounts, CSRF state, lifecycle timestamps, TTL, and unrelated attributes, because preservation is the caller's copy-and-replace responsibility, not the port's.

It must not reuse the existing CSRF-specific compare-and-set port (`AuthenticatedSessionCsrfRotationPort` / `AuthenticatedLoginReplaySessionPort`) for Fund Price. That existing port and its consumer (`AuthenticatedSessionCsrfRefreshService`) are left unchanged and are not refactored onto the new generic port in this delivery, to avoid regression risk to working login code. Fund Price mode-metadata caching is the first consumer of the new generic revision-based port; future single-field authenticated-session updates should reuse it rather than introduce another feature-specific CAS mechanism.

Because this project is under active development, no `AuthFlowSession`-style legacy-revision-initialization mechanism is required for the new port. There is no pre-existing authenticated-session data whose absent revision field needs graceful migration, so the port does not need a second CAS script, a mutation lock, or a startup compatibility gate analogous to `AuthFlowSessionPersistenceCompatibilityState`. A session record found with a missing or malformed revision fails the mutation closed with a generic system-failure outcome rather than being adopted on first write. All `AuthenticatedSession` creation paths must be updated to stamp an initial revision at creation time, so no session reachable through normal application flow is ever missing one.

## Cached field revision (2026-09-24 correction)

The cached authenticated-session mode metadata is corrected to the following five
mode-derived scalars only: `mode`, `fundPriceType`, `availableFundPriceDates`,
`maximumFundPriceHistoryMonths`, `maxRiskLevel`.

`accountRef`, `accountEnv`, `policyNo`, and `referenceDate` are explicitly REMOVED from
the cached field list. They are resolver-backed and must not be duplicated in session
state: `accountEnv`/`policyNo` resolve fresh on every request via
`CurrentPortalAccessContextResolver.current().account()`, and `referenceDate` resolves
fresh via `ReferenceDatePort.resolveReferenceDate(accountEnv)`. There is no
account/environment/policy binding check on the cached mode metadata, because nothing is
bound -- the four fields that would have required binding validation no longer live in
the session.

`maxRiskLevel` is the new fifth field, a string holding a floating-point value (e.g.
`"5"`, `"0.5"`) sourced from `TRPGetFundPriceMode`'s `max-risk-level` field. Like its
downstream source, it is never validated by Java; blank or absent is legitimate.

### Mutation mechanism (confirmed)

The generic `AuthenticatedSessionCasUpdatePort` (docs/capabilities -- see the port's own
Javadoc) is wrapped by a new concurrency service, structurally analogous to
`AuthFlowSessionConcurrencyService`, using the same `Retry.backoff(2,
Duration.ofMillis(10))` pattern on revision-conflict outcomes. This is a new service, not
a reuse of `AuthFlowSessionConcurrencyService` itself, which is hard-wired to
`AuthFlowSessionPort` and cannot operate on `AuthenticatedSession`. The Fund Price mode
use case is the first caller of this new service; it is written generically so future
authenticated-session mutations can reuse it rather than re-implementing retry-on-conflict
per feature.

## No hidden refresh

Price and export endpoints do not silently call the mode operation. Missing or stale metadata requires FE to reload the page/mode endpoint or receives the approved system failure according to the final web policy.
