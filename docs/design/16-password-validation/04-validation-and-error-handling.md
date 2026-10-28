# 04 Validation, security and error handling

## Ordering and precedence
Issuance: authenticated transport/account -> bounded JSON/structure/password/purpose -> username throttle -> downstream password check -> atomic grant issuance. Invalid request does not call downstream. Throttling counts admitted structurally valid password-check requests, including correct passwords and downstream technical failures; it does not count token consumption. This counting boundary is an authored technical specification.

Mutation: authenticated transport/account -> token header and atomic ownership/expiry check-and-consume -> body decoding -> binding comparison -> existing structural/YAML/access validation -> existing update. Missing/invalid session, CSRF or account never consumes another grant. Wrong token never deletes the valid current grant. Correct current token plus malformed/empty body, changed payload, access failure, YAML failure or downstream failure remains consumed.

A request rejected before it reaches BFF, such as a proxy-level size rejection, cannot be promised to consume a token. BFF must consume before its own JSON decoding. Body logging, argument resolvers, validation annotations, media-type routing and decoder size limits must be exercised with full HTTP tests to establish the actual boundary. Do not claim universal first-byte network consumption.

## Approved HTTP/status matrix
| Outcome | HTTP | Public status | Token behavior |
|---|---:|---|---|
| Password correct and grant persisted | 200 | SUCCESS | new grant replaces same scope |
| Password wrong | 401 | BUSINESS_REJECTED | no new grant; old one unchanged |
| Account locked and session invalidated | 423 | BUSINESS_REJECTED | no new grant; session grants unusable |
| Authorization token invalid/missing/expired/consumed or payload mismatch | 403 | BUSINESS_REJECTED | mismatch consumes valid current grant; wrong token does not |
| Invalid request / malformed JSON | 400 | VALIDATION_FAILED | valid mutation grant already consumed |
| Local throttle | 429 | BUSINESS_REJECTED | no new grant |
| Downstream, Redis, unregistered cust-id, malformed response, cleanup failure | 503 | SYSTEM_ERROR | no issuance; consumed grant never restored |

Existing authentication/CSRF mappings are reused, not normalized in this feature. For technical failure use one public unavailable code; retain safe internal origin/reason distinctions for support. Never emit public DOWNSTREAM* statuses or message text.

## Account lock and revocation
Always clear the current browser authenticated cookie using the existing writer's path/security/SameSite settings. Attempt server-side invalidation through the lifecycle capability, not a controller-to-controller call. Cookie clearing must occur on both success and error paths, before response commitment. Invalidation failure returns 503 and a safe operational failure event; clearing a cookie alone is not proof that a copied session identifier is revoked. Do not swallow the failure or return 423 claiming cleanup success.

Current-session scope only. Other sessions may still exist; downstream lock enforcement remains authoritative. A broader username-wide session revocation requirement was not agreed and is not implied.

### Throttle specification (confirmed decision, source-grounded)

Chosen mechanism: reuse the existing generic `RateLimitPort` / `RedisRateLimitAdapter` capability wholesale; no new throttling infrastructure is introduced. `RedisRateLimitAdapter.checkAndConsume(RateLimitCommand)` already executes an atomic `INCR` + conditional `EXPIRE` Lua script (`INCREMENT_AND_TTL_SCRIPT`) keyed at `<prefix>:auth:rate:<bucket>:<bucketId>`: the first increment in a window sets the TTL to `windowSeconds`, and the window resets `windowSeconds` after that first admitted request -- this is exactly the approved "atomic first-admission-anchored fixed window" behavior; it does not need to be newly authored.

Required change: add one new constant to the existing `RateLimitBucket` enum (currently `REGISTRATION_START_BY_IP`, `OTP_SEND_BY_DESTINATION_HASH`, `OTP_VERIFY_BY_REGISTRATION_SESSION`) -- e.g. `PASSWORD_VALIDATION_BY_USERNAME` -- and construct `new RateLimitCommand(RateLimitBucket.PASSWORD_VALIDATION_BY_USERNAME, <opaque username digest>, enabled=true, maxAttempts=10, windowSeconds=60, correlationId)` at issuance, before the downstream password check. `bucketId` must be an opaque keyed digest of the username (never raw cust-id/username), consistent with the existing adapter's `bucketId` contract and with D12. `RateLimitResult.status()` of `BLOCKED` maps to 429 with `retryAfterSeconds()` as `Retry-After`; any `RateLimitPort` failure (the adapter wraps every failure as `CacheException`, never a raw exception) maps to 503, and the downstream TRPPwdValidation call must not be made.

This is independent of the TAS lock count (`fail-count`) by construction, since it is a separate local Redis counter unrelated to anything downstream returns. Configuration validation (positive `maxAttempts`/`windowSeconds` when `enabled`) is already enforced by `RateLimitCommand`'s compact constructor; no new validation code is needed for that part.

Source references: `RedisRateLimitAdapter.java`, `RateLimitCommand.java`, `RateLimitResult.java`, `RateLimitBucket.java`, `RedisAdapterConfig.java` (confirms `RateLimitPort` is already wired as a Spring bean: `redisRateLimitAdapter(...)`).

M2 composition: `PasswordValidationThrottleService` uses
`PasswordValidationBucketIdPort`, implemented by infrastructure
`HmacPasswordValidationBucketIdDeriver`, to derive HMAC-SHA256 of the
exact server-authenticated actor username with domain
`ngtpa:password-validation:username:v1:`. Session/account/purpose/request ID do
not enter this digest. Only the lowercase hex digest is passed as bucketId;
neither identity nor the HMAC secret is logged. All pods must share the same
secret and policy. Changing the secret resets the effective bucket namespace.
`password-validation.rate-limit.secret` / `PASSWORD_VALIDATION_RATE_LIMIT_SECRET`
is base64 of at least 32 cryptographically random bytes; there is no default key.
Absent configuration leaves existing application startup working but every
password-validation admission fails closed with UNAVAILABLE (future HTTP 503).
A configured malformed/short key fails startup. Positive policy settings are
`password-validation.rate-limit.max-attempts` /
`PASSWORD_VALIDATION_RATE_LIMIT_MAX_ATTEMPTS` (10) and
`password-validation.rate-limit.window-seconds` /
`PASSWORD_VALIDATION_RATE_LIMIT_WINDOW_SECONDS` (60). There is no disable switch.
Empty results, errors and unexpected DISABLED decisions fail closed without
retry. These services are wired in M2 but no endpoint invokes them until M3.

M3 invokes these services through the issuance use case. Operators must provision
`PASSWORD_VALIDATION_RATE_LIMIT_SECRET` as Base64 encoding of at least 32
cryptographically random bytes, with the **same value on every replica**. Supply
it through secret management, never checked-in YAML, example real keys, logs or
evidence. Absence still fails this endpoint closed with 503; no local substitute
key, per-replica fallback, bypass or disable switch is introduced.

## Binding and input safety
Compare complete JSON operation payload, including applyToAllAccounts. Duplicate keys must be rejected before they can be discarded by mapping. Do not hash only fields that happen to survive the current DTO mapper. Do not use FE hashes, Java Object.toString, iteration-order-dependent maps, locale-dependent formatting or binary floating-point serialization as the binding contract.

Password checks reuse login length configuration, preserve exact content and never apply new-password complexity or confirmation-password rules. No password persistence or cache. Use bounded request parsing; reuse existing body limits only after verifying them, rather than inventing deployment values.

## No replay and uncertain outcomes
No automatic retry of password check, grant consumption or business mutation is introduced. A lost grant-issuance response requires fresh confirmation. A consume timeout fails closed; do not dispatch because deletion may already have happened. A business update timeout may represent an applied update. FE must not automatically repeat it with a new grant. No exactly-once business execution claim is made.

### Observability and confidentiality (confirmed decision, source-grounded)

**D18 correction supersedes the earlier raw-path denylist and numeric order
below:** both filters use the one PasswordValidationRouteMatcher, with
pre-parsed PathPattern routing over pathWithinApplication. POST issuance and
PUT Personal Information receive the full body/error/header/no-store/CSRF
policy for matrix-parameter and encoded-segment variants. Logging is at
HIGHEST_PRECEDENCE, route policy +1, authenticated account context +2.
The historical future-token-gate +2 below is not valid after this adjustment;
M4 must select a strictly later order and reuse this matcher under its separate
authorization. No M4 gate is implemented or validated by this repair.
See design 01 for actual HTTP routing versus default-firewall 400 observations.

Confirmed filter order: `RequestLoggingWebFilter` runs at `@Order(Ordered.HIGHEST_PRECEDENCE)`; `PortalAccessContextWebFilter` runs at `@Order(Ordered.HIGHEST_PRECEDENCE + 1)`. `RequestLoggingWebFilter` only buffers/logs the request body when `request-logging.body-logging.enabled=true` globally AND an `EndpointRule` matching this request's method+path pattern exists under `request-logging.body-logging.endpoints` with `logRequestBody=true`, where matching uses Spring's `AntPathMatcher` against `rule.getPathPattern()`. **Correction (reviewer finding 3, major):** a config-scan test asserting that no `EndpointRule` *names* these two paths exactly is insufficient, because an unrelated broad wildcard rule added for a different endpoint (for example a pattern such as `/api/v1/**`) could still match these two paths via `AntPathMatcher` without ever naming them; configuration absence must not be the authoritative control. The required design is a hard, code-level exclusion inside `RequestLoggingWebFilter.findMatchingRule` (or an equivalent check applied before `shouldLogRequestBody`/`shouldLogResponseBody` return their result): an explicit denylist of the two exact paths that unconditionally forces `logRequestBody`/`logResponseBody` to `false` for those paths, evaluated so it overrides any configured `EndpointRule` match rather than merely hoping none exists. A config-scan test remains useful as secondary, defense-in-depth, but it supplements -- and must not replace -- this runtime override. This is a required production-code change in the shared `RequestLoggingWebFilter`, not a password-validation-feature-local change, and must include an adversarial test that configures a matching wildcard rule and asserts body logging still does not occur for these two paths.

Pre-body-consumption gate: introduce PasswordValidationTokenGateWebFilter at Ordered.HIGHEST_PRECEDENCE + 2, after PortalAccessContextWebFilter, scoped to PUT /api/v1/personal-information. Require the resolved authenticated account context explicitly; missing Account-Ref must not bypass the gate. Read the token header and complete atomic ownership/expiry checking and consumption before invoking chain.filter(exchange). On success place the consumed binding in request-local exchange attributes; body decoding and comparison follow downstream. Selected error-rendering path: render gate failures directly using the existing ErrorResponseRenderer and the actual MutationResponse/ApiError contracts, with JSON content type, request correlation and no-store. Token rejection is 403 BUSINESS_REJECTED; a technical failure from this gate's consumption operation is 503 SYSTEM_ERROR. Scope technical-error conversion to that operation before chain.filter; do not globally catch CacheException or wrap downstream controller/business failures. Existing authentication/CSRF exceptions and their handlers remain unchanged; this design does not claim the context-resolution handler handles CSRF. No new global WebExceptionHandler is required. Full HTTP tests must prove the envelope/status, zero body reads on pre-body rejection, malformed-body consumption, and unchanged authentication/CSRF behavior. Do not infer correctness solely from @RequestBody Mono laziness.

Open verification item: effective inbound body/depth limits must be verified from actual application/framework configuration and deployed overrides before implementation. Do not assume a framework default or invent production values. This is distinct from outbound APIM codec limits.

M3 source/HTTP verification: this repository has no inbound max-in-memory codec
override. The resolved Spring Framework 7.0.7 AbstractDataBufferDecoder initializes
maxInMemorySize to 256 * 1024 bytes; StringDecoder aggregation uses that bound.
The framework argument resolver translates DataBufferLimitException to
ContentTooLargeException, which the new route converts to its 400 validation
contract without diagnostic text. Real HTTP tests reject a 300,000-character
field before throttling. The private Jackson parser and M1 canonicalizer retain
their resolved default StreamReadConstraints (nesting depth 1000); HTTP rejects
1100 nested arrays. No global ObjectMapper/codec/limit changes are made.
Deployed codec/proxy overrides and upstream rejection behavior remain unverified
release concerns; these tests certify the repository's local framework path only.
This limit evidence does not implement M4's consume-before-decode gate.

Logging/redaction: `RequestLoggingWebFilter` logs headers only when `request-logging.log-headers=true` and only for names present in `request-logging.header-allowlist` (default: `User-Agent`, `Accept`, `Content-Type`). **Correction (reviewer finding, major, second review).** Relying on "never add the token header to the allowlist" plus a config-scan test is insufficient for the same reason the earlier body-logging configuration reliance was insufficient: it is a configuration-absence guarantee, not a runtime one. The required design is a hard, code-level, case-insensitive exclusion inside `RequestLoggingWebFilter.buildAllowedHeaders` (or an equivalent check applied to its output) that unconditionally removes `X-Password-Validation-Token` from the headers actually logged, regardless of whether an operator has added it to `request-logging.header-allowlist` in any environment. The config-scan test asserting it is absent from the allowlist remains useful as secondary, defense-in-depth, but must not be the sole or primary protection. This requires an adversarial test that configures the header into the allowlist and asserts it is still never present in the logged headers map. Follow the existing `<redacted>`-style `toString()` convention demonstrated by `CaptchaChallengeResponse`/`CaptchaRefreshRequest`/`CaptchaRefreshResponse` for every new password-validation DTO/command (print field names but never values for `password`, `token`, `cust-id`, or the operation payload). Note: the package-private `SecurityAuditMetadataSanitizer` (`adapter.out.security.audit`) is a *different*, narrower mechanism for audit-event metadata maps (redacts by forbidden-key-name and value-pattern matching) and is not the same code path as `RequestLoggingWebFilter`'s `LoggingSanitizer`; do not assume the audit sanitizer's forbidden-key list alone covers HTTP request/response/header logging for this feature -- both paths must be independently tested.

Source references: `RequestLoggingWebFilter.java`, `RequestLoggingProperties.java`, `PortalAccessContextWebFilter.java`, `PersonalInformationController.java` (confirms `@RequestBody Mono<T>` lazy-subscription timing), `SecurityAuditMetadataSanitizer.java`, `CaptchaChallengeResponse.java`/`CaptchaRefreshRequest.java`/`CaptchaRefreshResponse.java` (redaction convention precedent).
