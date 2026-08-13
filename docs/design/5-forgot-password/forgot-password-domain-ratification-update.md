# Feature 0061 - Business and TAS Ratification Update

**Date:** 2026-07-30  
**Gate:** 3 - Detailed behavioral and technical contracts  
**Status:** Business domain approved; TAS domain approved with an unresolved retry-safety contradiction

## D3-008 - Business and content ratification

The designated business authority approved the following without conditions:

1. Forgot Password is a pre-login journey.
2. The member enters username, fixed identity type, identity value, DOB, and either email or Hong Kong mobile.
3. After TAS confirms that the submitted details and contact match, OTP is sent immediately to the submitted contact. There is no separate destination-selection step.
4. Username and details failures use approved generic customer-facing outcomes.
5. An expired OTP remains on the verification page and permits Resend when available.
6. The fifth incorrect OTP verification and sixth resend request terminate the flow and direct the member to Call Member Hotline.
7. Successful reset shows acknowledgement and Return to Login.
8. The supplied English and zh_HK SMS/email templates are approved.
9. E0002 is represented by the supplied template content for Feature 0061.

**Approver:** BA  
**Role:** BA  
**Approval date:** 2026-07-30  
**Conditions:** None

### Effect

Business-domain ratification is complete. Q3-001 and Q3-004 are closed. The supplied notification content is authoritative for this feature unless the business owner approves a later change.

## D3-009 - TAS domain ratification

TAS approved the following without stated conditions:

1. TAS owns username existence/eligibility, member-detail matching, password-policy validation, and password-reset result.
2. Username and trust have a one-to-one relationship for this flow.
3. The BFF uses POST for:
   - `/ws/NGTPA/v1/TRPChkCustId`
   - `/ws/NGTPA/v1/TRPSearchRegedMember`
   - `/ws/NGTPA/v1/TRPResetPassword`
4. Request/response envelopes and recognized errors are those in `0-description.md` and the Gate 3 APIM contract.
5. `TRPChkCustId` and `TRPSearchRegedMember` are read-like POST operations governed by the existing `ApimRetryEligibility` policy.
6. `TRPResetPassword` has no TAS idempotency or duplicate-request protection.
7. TAS does not know the authoritative outcome when a timeout occurs after the reset request may have reached TAS.
8. Password is not included in APIM field-level encryption/decryption configuration.

**Approver:** TAS  
**Role:** TAS  
**Approval date:** 2026-07-30  
**Conditions:** None stated

## C3-002 - TRPResetPassword retry contradiction

### Conflicting inputs

- Gate 2 decision D2-007 requires reuse of bounded `ApimRetryEligibility`; mutations may retry only where retry safety is established, and unknown outcomes must not be retried blindly.
- TAS stated: “POST AS MANY AS YOU CAN. NO PROTECTION AT ALL.”
- TAS also stated that the outcome after a possible post-transmission timeout is unknown.

### Analysis

“No protection at all” means the downstream mutation is not idempotent and has no duplicate-suppression reference. An unknown post-transmission timeout means the BFF cannot prove whether the password was already reset. Therefore an unrestricted retry could apply the mutation more than once and cannot be accepted as a safe technical contract merely because TAS permits repeated POST requests.

A non-empty `response.err-message` is an application-level TAS/APIM response, not a network failure. A true network timeout or connection failure may produce no valid response envelope and must be classified by the shared APIM transport layer. Unknown or unrecognized non-empty `err-message` values still follow the approved fallback mapping to service unavailable; they must not be reclassified as network failures.

### Gate 3 working rule

Pending resolution, `TRPResetPassword` must be classified conservatively:

1. Recognized TAS business errors are not retried.
2. A failure proven to have occurred before request transmission may follow the existing bounded retry policy if `ApimRetryEligibility` supports that distinction.
3. A timeout, connection loss, or other failure after transmission may have occurred is an `UNKNOWN_OUTCOME` and must not cause an automatic repeat of `TRPResetPassword`.
4. BFF idempotency prevents duplicate entry through the BFF while its record exists, but it cannot prove that TAS did not execute after a timeout.
5. No unlimited or unbounded retry is permitted.

### Required decision

The solution-design and security authorities must approve one of these before the Gate 3 APIM contract is finalized:

- **Recommended:** classify `TRPResetPassword` as non-retryable after possible transmission; use `UNKNOWN_OUTCOME` recovery for ambiguous timeouts.
- TAS introduces an idempotency/business reference or status/reconciliation operation that permits safe retry.
- TAS explicitly defines a demonstrably idempotent reset semantic and supplies contract evidence.

**Priority:** Critical  
**Blocking:** Blocks Gate 3 approval and Gate 4 consistency review  
**Owners:** Nick, TAS, designated security authority

## Domain status

| Domain | Status | Remaining action |
|---|---|---|
| Business/content | Approved | None for Gate 3 baseline |
| TAS operations/contracts | Approved, except retry safety remains contradictory | Resolve C3-002 with solution/security authority |
| Security | Pending | Ratify pre-auth session, CSRF, OTP, anti-abuse, terminal state, logging/privacy, unencrypted password field, masking, and C3-002 working rule |
| Solution design | Gate 2 conditionally approved; Gate 3 in progress | Approve final C3-002 disposition and Q3-006 outcome |

## Readiness

- **Gate 3 approval:** Not ready. Security ratification, Q3-006 repository investigation, and C3-002 remain open.
- **Gate 4:** Not authorized.
