> Status: Design baseline v0.1  
> Date: 20 July 2026  
> Scope: Subsequent member login plus reusable authentication foundations  
> Source baseline: Login BRD V0.3, discovery decisions in 02-brd.md through 10-brd.md, and the supplied current codebase snapshot  
> Open external dependency: detailed `TRPGetAccountList` business failure-code mapping remains TBC.


# Login Master Design

## 1. Purpose

This document defines the end-to-end architecture for member login and the reusable authentication capabilities required by later forgot-password, username-recovery, and change-password development.

Registration is already implemented. Login must reuse and generalize the existing OTP, auth-flow session, CSRF, idempotency, message-delivery, workflow, MutationResponse, APIM, crypto, retry, logging, and Redis foundations. It must not build parallel implementations.

## 2. Scope

### Included

- Username/password submission and configured Java-side validation
- `TRPMemberLogin` integration
- OTP destination discovery, masking, selection, delivery, resend, and verification
- Reuse/generalization of the existing registration OTP capability
- Pre-authentication session lifecycle
- `TRPGetAccountList` integration after OTP verification
- Account-record validation and configurable identifier formatting
- AccountRef generation
- Authenticated portal-session creation and complete-context protection
- Last-successful-login-wins concurrent-session replacement
- `portal_sid` and authenticated CSRF establishment
- Logout and internal revocation by username
- Session-backed `PortalAccessContext`
- Permanent removal of the production `TemporaryPortalAccessContextAdapter`
- Removal of the `Session-Id` request header
- Global APIM transient retry capability for eligible business calls

### Excluded

- Forgot-password implementation
- Username-recovery implementation
- Change-password implementation
- Changes to Progress-owned password lock policy
- Java calculation of terminated-member grace periods
- New OTP state, status, counter, error, or HTTP semantics
- Reimplementation of APIM transport, OAuth, certificate, encryption, or decryption

## 3. Architectural principles

1. **Reuse before extension.** Existing generic OTP, CSRF, message delivery, AuthFlowSession, workflow and APIM foundations are authoritative.
2. **No authentication in AuthFlowSession.** A pre-auth session never grants portal access.
3. **Explicit selected-account context.** `portal_sid` identifies the user session; `Account-Ref` identifies the account for each request.
4. **No mutable current-account authority.** FE selection does not replace per-request AccountRef validation.
5. **Complete-context protection.** Sensitive authenticated-session context is encrypted as one protected envelope before Redis persistence.
6. **Atomic session replacement.** New session creation, active-session index replacement, old-session revocation, and replacement marking form one Redis transaction/script.
7. **Codes only.** Java returns stable codes and structural metadata; FE owns user-facing login messages.
8. **Adapter-owned APIM mechanics.** Login domain/application logic is unaware of OAuth, certificates, encrypted fields, retries, or APIM envelopes.
9. **Configuration owns policy values.** Java must not hardcode username rules, identifier widths, session timeouts, OTP limits, retry counts, cookie policy, or crypto secrets.

## 4. End-to-end flow

1. FE submits username and password.
2. Java trims and ASCII-lowercases the username and applies the configured login username/password acceptance policy.
3. Java calls `TRPMemberLogin` through the existing APIM infrastructure.
4. Progress validates credentials, owns password failure counting/locking, and returns one result with `cust-type`, optional `user-role`, `fail-count`, and destination entries.
5. On success, Java creates a LOGIN `AuthFlowSession`, protects the login context, binds a CSRF hash, sets `auth_flow_sid`, and returns all masked destinations—even when only one exists—plus plaintext CSRF token in the response body.
6. FE submits an opaque destination ID with `auth_flow_sid` and `X-XSRF-TOKEN`.
7. Java resolves the protected destination and uses the existing generic OTP/message-delivery implementation.
8. FE submits the OTP. Existing OTP semantics apply unchanged.
9. On OTP success, Java calls `TRPGetAccountList` using values from protected pre-auth context only.
10. Java validates account records, omits malformed records when at least one valid record remains, formats policy/certificate identifiers, and generates AccountRefs.
11. Java builds and encrypts the complete authenticated-session context.
12. A dedicated Redis adapter atomically persists the new session, updates the active-login index, revokes the previous session, and writes a replacement marker.
13. Java rotates to `portal_sid`, creates a new CSRF token/hash, deletes the pre-auth flow, clears pre-auth transport, and returns one account entry per policy/certificate pair.
14. FE stores the selected AccountRef and submits it on every account-scoped request.
15. Java resolves `(portal_sid, Account-Ref)` into `PortalAccessContext`; valid requests refresh the configurable sliding idle timeout.

## 5. Main components

### Existing shared components to retain

- `OtpChallengePort` and OTP challenge services
- `AuthFlowSession` and `AuthFlowSessionPort`
- Generic session ID, cookie instruction, CSRF, origin/referer, audit and idempotency foundations
- `MessageDeliveryPort` and physical delivery adapters
- `MutationResponse` and lightweight workflow result helpers
- Shared APIM envelope, WebClient facade, credential profiles, OAuth, token/certificate caching, crypto, request ID propagation and logging
- `PortalAccessContext` as the request-level account projection

### Existing registration-specific technical boundaries to generalize

- Registration OTP orchestration port
- Registration message boundary
- Registration session facade
- Registration idempotency boundary
- Registration protected-context mechanism

Registration business ports remain registration-specific.

### New login capabilities

- `LoginCredentialVerificationPort`
- `LoginAccountAuthorizationPort`
- Login credential policy adapter
- OTP destination masking and opaque-ID mapping
- `AuthenticatedSessionPort`
- `ActiveLoginSessionPort`
- `AuthenticatedSessionContextProtectorPort`
- Dedicated Redis authenticated-session adapter
- `AccountRefGeneratorPort`
- Session-backed `PortalAccessContextPort`
- Logout use case
- Internal username-session revocation use case

## 6. Session boundaries

### Pre-auth session

- Cookie: `auth_flow_sid`
- Scenario: `LOGIN`
- Configurable idle TTL: initial baseline 15 minutes
- Stores CSRF hash and protected login context
- Contains no password or raw OTP
- Is consumed after authenticated-session creation

### Authenticated session

- Cookie: `portal_sid`
- Configurable sliding idle TTL: initial baseline 30 minutes
- No absolute lifetime
- Complete context encrypted before Redis persistence
- Contains actor identity, account map, authorization data when available, CSRF hash, authentication timestamps and concurrency metadata
- Does not use `AuthFlowSession`

## 7. Account context

`portal_sid` proves the authenticated session. `Account-Ref` selects the request account. Java must verify that the AccountRef exists in the current session before creating `PortalAccessContext`.

Missing AccountRef on an account-scoped endpoint is HTTP 400. A supplied AccountRef not owned by the session is HTTP 403.

`schemeType` remains nullable and must not be derived. Variant resolution skips the dimension when null.

## 8. Concurrent login

Concurrency is keyed by normalized username. Last successful authenticated-session commit wins.

Old session A receives HTTP 401 with `err.login.session.replaced`. If creation of session B fails, A remains active and B is not exposed.

## 9. Failure principles

- Credential and business failures use stable login codes.
- APIM details are never exposed.
- Account-list empty/unavailable final outcomes use `err.login.accountList.unavailable`.
- Zero valid account records after validation use HTTP 502, `SYSTEM_ERROR`, and restart login.
- Authenticated-session persistence failure uses HTTP 503, `err.login.session.unavailable`, and restart login.
- OTP outcomes reuse the existing registration OTP semantics and shared mappings.

## 10. Open item

The detailed one-to-one Progress error mapping for `TRPGetAccountList` remains TBC. Once supplied, it must be added through the existing configuration-driven downstream classification mechanism, not hardcoded.
