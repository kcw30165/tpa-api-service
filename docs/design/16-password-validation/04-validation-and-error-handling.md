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

## Throttle specification
Approved: 10 requests per authenticated username per 60 seconds across all sessions/accounts/purposes; configurable and independent of TAS lock count. Authored implementation: atomic first-admission-anchored fixed window; allow first ten, reject eleventh until reset. Use an opaque keyed identity digest for bucket keys, never raw cust-id. Return 429 and Retry-After; limiter infrastructure failure -> 503, no APIM. Configuration validates positive limits/windows and token TTL. Do not copy the number five from downstream into local policy.

## Binding and input safety
Compare complete JSON operation payload, including applyToAllAccounts. Duplicate keys must be rejected before they can be discarded by mapping. Do not hash only fields that happen to survive the current DTO mapper. Do not use FE hashes, Java Object.toString, iteration-order-dependent maps, locale-dependent formatting or binary floating-point serialization as the binding contract.

Password checks reuse login length configuration, preserve exact content and never apply new-password complexity or confirmation-password rules. No password persistence or cache. Use bounded request parsing; reuse existing body limits only after verifying them, rather than inventing deployment values.

## No replay and uncertain outcomes
No automatic retry of password check, grant consumption or business mutation is introduced. A lost grant-issuance response requires fresh confirmation. A consume timeout fails closed; do not dispatch because deletion may already have happened. A business update timeout may represent an applied update. FE must not automatically repeat it with a new grant. No exactly-once business execution claim is made.

## Observability and confidentiality
E1 shows RequestLoggingWebFilter can join the request body before PortalAccessContextWebFilter when body logging is enabled. Add hard exclusions for issuance and protected PUT, including response bodies, rather than relying solely on disabled defaults. Ensure APIM logs and exception messages cannot contain password/cust-id. Token header must be excluded even when header logging is enabled. Redact DTO/command toString and safe event dimensions. Test malformed JSON, serialization failures, plain/encrypted APIM and debug logging paths. No raw payload or digest in logs.
