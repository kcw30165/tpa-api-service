> Status: Design baseline v0.1  
> Date: 20 July 2026  
> Scope: Subsequent member login plus reusable authentication foundations  
> Source baseline: Login BRD V0.3, discovery decisions in 02-brd.md through 10-brd.md, and the supplied current codebase snapshot  
> Open external dependency: detailed `TRPGetAccountList` business failure-code mapping remains TBC.


# Login Shared Foundation Design

## 1. Objective

Extract genuinely generic authentication capabilities from registration without changing completed registration behaviour.

## 2. Preserve unchanged

- Generic `OtpChallengePort` and challenge state/services
- AuthFlowSession domain and Redis persistence
- CSRF generation, hashing and validation
- Session ID and cookie instruction foundations
- Message-delivery infrastructure
- MutationResponse and workflow helpers
- APIM client/crypto/credential infrastructure
- Logging, audit and sanitizer foundations

## 3. Generalize technical facades

Candidate registration technical facades should become scenario-aware shared contracts where their semantics are identical:

```text
RegistrationOtpPort          -> AuthFlowOtpPort
RegistrationMessagePort      -> AuthFlowMessagePort
RegistrationIdempotencyPort  -> AuthFlowIdempotencyPort
Registration protected context mechanism -> ProtectedAuthFlowContext capability
```

Do not generalize registration business capabilities such as member lookup, username availability, trust selection or completion.

## 4. Login-specific ports

```text
LoginCredentialVerificationPort
LoginAccountAuthorizationPort
AuthenticatedSessionPort
ActiveLoginSessionPort
AuthenticatedSessionContextProtectorPort
AccountRefGeneratorPort
PortalSessionRevocationPort
```

## 5. Extraction sequence

1. Add fail-first characterization tests around registration OTP/session/message/idempotency behaviour.
2. Introduce shared capability contracts beside existing registration contracts.
3. Adapt registration services without changing web/API behaviour.
4. Run complete registration suite.
5. Add login policy/configuration and login-specific use cases.
6. Remove superseded registration technical contracts only after no production code references them.
7. Keep registration business ports and error vocabulary intact.

## 6. APIM global retry extension

Extend the existing APIM facade/configuration with operation retry eligibility and a shared transient policy. Do not add retry operators inside login adapters.

Suggested eligibility concepts:

```text
SAFE_BUSINESS_CALL
IDEMPOTENT_MUTATION
NON_RETRYABLE
```

Token and certificate recovery remain existing independent mechanisms.

## 7. Portal context migration

Replace `TemporaryPortalAccessContextAdapter` permanently with authenticated-session resolution. Remove `Session-Id` header handling and temporary YAML profiles after login cutover. Existing controllers continue depending on `PortalAccessContext`, not session internals.
