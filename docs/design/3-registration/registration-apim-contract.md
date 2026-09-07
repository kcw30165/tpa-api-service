# Registration APIM/TAS Contract

## 1. Purpose

This document defines the APIM/TAS endpoints used by the registration flow and the boundary rules for Java BFF integration.

The document is organized from the APIM/TAS endpoint point of view, not from the Java use-case point of view.

Java BFF is an orchestration layer. APIM/TAS owns:

- member existence;
- identity/contact matching;
- registration eligibility;
- not-yet-registered trust/account lookup;
- username availability;
- final credential setup;
- already-registered rejection;
- authoritative APIM/TAS error-message semantics.

Java must not reimplement APIM/TAS business decisions.

---

## 2. Existing APIM Infrastructure Reuse

Registration implementation must reuse existing APIM infrastructure for:

- token handling;
- certificate handling;
- encryption/decryption;
- transport errors;
- timeout handling;
- invalid response/envelope handling;
- centralized exception mapping.

Registration use cases own only orchestration, request/response mapping, and FE response composition.

---

## 3. Endpoint Inventory

The registration flow currently uses these APIM/TAS endpoints:

```text
POST /ws/NGTPA/v1/TRPSearchMember
POST /ws/NGTPA/v1/TRPChkCustId
POST /ws/NGTPA/v1/TRPRegMember
```

Usage summary:

```text
TRPSearchMember
- Used by registration/start for initial member/account verification.
- Used after otp/verify to retrieve the latest not-yet-registered trust/account list.

TRPChkCustId
- Used by registration/username/check to check username/customer ID availability.

TRPRegMember
- Used by registration/complete to complete registration for the selected trust/account and username.
```

Exact APIM/TAS DTOs for `TRPChkCustId` and `TRPRegMember` must be confirmed against APIM/TAS contract material. Until confirmed, Java implementation must keep request/response mappers isolated behind APIM adapter DTOs and must not leak placeholder shapes into FE contracts.

---

## 4. Common APIM/TAS Response Interpretation Rules

APIM/TAS responses are interpreted by endpoint-specific adapter mappers.

Java BFF must distinguish:

```text
APIM/TAS rejection
- APIM/TAS is reachable.
- Response envelope is technically valid.
- Endpoint returns an APIM/TAS business/error-message response.
- Java maps it to MutationResponse.errors[] through page-specific DSL/config.
- Log level follows tech-lead rule: ERROR.
- eventType should be DOWNSTREAM_REJECTION.

APIM/TAS technical failure
- Transport failure, timeout, unavailable APIM/TAS, invalid envelope, token/certificate issue, or encryption/decryption infrastructure issue.
- Java maps it through existing APIM technical exception handling.
- Log level follows tech-lead rule: FATAL.
- eventType should be DOWNSTREAM_FAILURE.
```

Java must not hardcode APIM/TAS error-message classifications in use cases. APIM/TAS error-message mapping must be resolved from page/step-specific DSL or configuration.

---

## 5. Page-Specific Downstream Error Classification

Downstream error classification is page/step-specific and should live in `bff-pages` DSL or page-level configuration.

This does not change the APIM/TAS endpoint contract. It only defines how Java maps endpoint-specific APIM/TAS error messages into FE-facing response codes, targets, event types, and idempotency cache policy.

Resolution order:

```text
1. Page/step-specific downstream error mapping
2. Scenario-level downstream error mapping, if configured
3. Global downstream fallback
4. Generic system/downstream error if classification itself fails
```

Recommended DSL shape:

```yaml
bff-pages:
  registration-start:
    downstreamErrorClassification:
      source: APIM_TAS
      endpoint: TRPSearchMember
      mappings:
        err.tpa.member.not.found:
          eventType: DOWNSTREAM_REJECTION
          logLevel: ERROR
          responseCode: registration.start.verificationFailed
          target: PAGE
          idempotencyCacheable: false
      fallback:
        eventType: DOWNSTREAM_REJECTION
        logLevel: ERROR
        responseCode: downstream.generic.businessRejected
        target: PAGE
        idempotencyCacheable: false

  registration-otp-verification:
    downstreamErrorClassification:
      source: APIM_TAS
      endpoint: TRPSearchMember
      fallback:
        eventType: DOWNSTREAM_REJECTION
        logLevel: ERROR
        responseCode: downstream.generic.businessRejected
        target: PAGE
        idempotencyCacheable: false

  registration-username-check:
    downstreamErrorClassification:
      source: APIM_TAS
      endpoint: TRPChkCustId
      mappings:
        err.tpa.username.taken:
          eventType: DOWNSTREAM_REJECTION
          logLevel: ERROR
          responseCode: registration.username.taken
          target: username
          idempotencyCacheable: true
          cacheTtlSeconds: 60
      fallback:
        eventType: DOWNSTREAM_REJECTION
        logLevel: ERROR
        responseCode: downstream.generic.businessRejected
        target: PAGE
        idempotencyCacheable: false

  registration-password-setup:
    downstreamErrorClassification:
      source: APIM_TAS
      endpoint: TRPRegMember
      mappings:
        err.tpa.username.taken:
          eventType: DOWNSTREAM_REJECTION
          logLevel: ERROR
          responseCode: registration.username.taken
          target: username
          idempotencyCacheable: true
          cacheTtlSeconds: 60
      fallback:
        eventType: DOWNSTREAM_REJECTION
        logLevel: ERROR
        responseCode: registration.complete.failed
        target: PAGE
        idempotencyCacheable: false
```

Rules:

- Page-specific mapping wins over scenario/global fallback.
- Unknown APIM/TAS business error-message maps to configured fallback.
- Technical failure does not use the business fallback; it maps to `DOWNSTREAM_FAILURE`.
- `idempotencyCacheable` is false by default unless explicitly configured true.
- APIM/TAS business rejections are non-cacheable by default because downstream truth may change during the AuthFlowSession lifecycle.
- Username taken/unavailable can be cacheable for short TTL when configured.

---

## 6. TRPSearchMember

### 6.1 Usage

`TRPSearchMember` is used in two places:

```text
1. registration/start
   - Initial identity/contact verification.
   - Confirms registration can proceed.
   - Confirms at least one not-yet-registered trust/account exists.

2. otp/verify
   - After OTP verification succeeds.
   - Retrieves the latest not-yet-registered trust/account list.
   - Determines whether FE should go to USERNAME_CHECK or SELECT_TRUST.
```

### 6.2 Endpoint

```http
POST /ws/NGTPA/v1/TRPSearchMember
Content-Type: application/json
Accept: application/json
Authorization: Bearer <token>
```

### 6.3 Request Mapping

FE registration-start request:

```json
{
  "identityType": "GPFS",
  "identityValue": "1234567890",
  "dateOfBirth": "31/01/1980",
  "email": "member@example.com",
  "mobile": null
}
```

APIM/TAS request:

```json
{
  "search-by": "GPFS",
  "search-value": "1234567890",
  "dob": "31/01/1980",
  "email": "member@example.com",
  "mobile-number": ""
}
```

Mapping:

```text
identityType  -> search-by
identityValue -> search-value
dateOfBirth   -> dob
email         -> email, null becomes empty string
mobile        -> mobile-number, null becomes empty string
```

Rules:

- `identityType` maps directly to APIM/TAS `search-by`.
- `identityTypeSearchScope` is not used for this endpoint.
- Date format is `dd/MM/yyyy` for FE and APIM/TAS.
- Java does not send `DataDomain`; APIM handles DataDomain toward TAS/Progress.
- During `otp/verify`, Java should reuse the protected registration context stored in AuthFlowSession to call this endpoint again.

### 6.4 Success Interpretation

Success example:

```json
{
  "response": {
    "err-message": "",
    "data": [
      {
        "email": "member@example.com",
        "mobile-number": "",
        "env": "OI",
        "trust-code": "JPM"
      }
    ]
  }
}
```

Success means:

```text
response.err-message is empty
AND response.data is non-empty
```

For `registration/start` success:

- Java creates AuthFlowSession.
- Java creates CSRF token and stores hash.
- Java stores protected registration context.
- Java creates OTP challenge and sends OTP to FE-submitted contact.
- Java does not return APIM/TAS `data[]` to FE from registration/start.
- Java does not need to store APIM/TAS `data[]` for later trust selection.

For `otp/verify` success:

- Java returns current unregistered trust list to FE.
- If exactly one trust exists, `nextStep=USERNAME_CHECK`.
- If multiple trusts exist, `nextStep=SELECT_TRUST`.

### 6.5 Rejection Interpretation

Failure example:

```json
{
  "response": {
    "err-message": "err.tpa.member.not.found",
    "data": []
  }
}
```

Rules:

- A technically valid response with `err-message` is `DOWNSTREAM_REJECTION`.
- Log as `ERROR` per tech-lead requirement.
- Resolve user-facing response through page-specific DSL.
- `err.tpa.member.not.found` should be non-cacheable by default for idempotency because downstream truth may change.

---

## 7. TRPChkCustId

### 7.1 Usage

`TRPChkCustId` is used by:

```text
POST /api/v1/auth/registration/username/check
```

Purpose:

```text
Check whether the requested username/customer ID is available for the selected trust/account registration context.
```

### 7.2 Endpoint

```http
POST /ws/NGTPA/v1/TRPChkCustId
Content-Type: application/json
Accept: application/json
Authorization: Bearer <token>
```

### 7.3 Request Mapping

FE registration-complete request:

```json
{
    "selectedTrust": {
        "trustCode": "TL",
        "accountEnv": "ENV1"
    },
    "username": "db06"
}
```

APIM/TAS request:

```json
{
    "cust-id": "db01"
}
```

APIM/TAS response:

if the cust-id (username) can be proceeded,

```json
{
    "response": {
        "err-message": "",
        "data": [
            {
                "cust-id": "db01",
                "isExist": false
            }
        ]
    }
}
```

if the cust-id (username) cannot be proceeded,

```json
{
    "response": {
        "err-message": "",
        "data": [
            {
                "cust-id": "db01",
                "isExist": true
            }
        ]
    }
}
```

Java-side source values:

```text
username from FE maps to cust-id
```

Rules:

- Java must validate selectedTrust shape and username format before calling APIM/TAS.
- Username availability is first-come, first-served.
- `username/check` is not action-idempotent.
- Java stores selectedTrust + username only after `TRPChkCustId` confirms availability.
- If the same selectedTrust + username is submitted again while state is `USERNAME_VERIFIED`, Java may return success without changing state.
- If selectedTrust or username changes before `registration/complete` starts, Java should re-call `TRPChkCustId` and replace `usernameCheck` only after success.
- Java needs to verify if the cust-id in response payload is the same with the field in request payload in a case-insensitive way.

### 7.4 Success Interpretation

Exact success response DTO is pending confirmation.

Required Java outcome on success:

```text
- store usernameCheck.selectedTrust
- store usernameCheck.username
- transition AuthFlowSession to USERNAME_VERIFIED
- return nextStep=PASSWORD_SETUP
```

### 7.5 Rejection Interpretation

Username unavailable/taken is a `DOWNSTREAM_REJECTION` and maps through `registration-username-check` page DSL.

Username unavailable/taken may be cacheable for short TTL only when configured by DSL.

---

## 8. TRPRegMember

### 8.1 Usage

`TRPRegMember` is used by:

```text
POST /api/v1/auth/registration/complete
```

Purpose:

```text
Complete registration for the selected trust/account and username stored in AuthFlowSession.
```

### 8.2 Endpoint

```http
POST /ws/NGTPA/v1/TRPRegMember
Content-Type: application/json
Accept: application/json
Authorization: Bearer <token>
```

### 8.3 Request Mapping

FE registration-complete request:

```json
{
  "password": "Password1!",
  "confirmPassword": "Password1!"
}
```

APIM/TAS request:

```json
{
    "env": "DB",
    "trust-code": "DBA",
    "cust-id": "db02",
    "user-id": "3011825",
    "password": "p@ssw0rd",
    "dob": "15/07/1969",
    "mobile-number": "66290597",
    "email": ""
}
```

APIM/TAS response:

```json
{
    "response": {
        "err-message": "",
        "data": [
            {
                "success": true,
                "cust-id": "db01",
                "create-date": "08/07/2026",
                "create-time": "16:49:23"
            }
        ]
    }
}
```

```json
{
    "response": {
        "err-message": "err.member.registered"
    }
}
```

```json
{
    "response": {
        "err-message": "err.member.not.exist"
    }
}
```

```json
{
    "response": {
        "err-message": "err.cust.id.duplicated"
    }
}
```

Mapping:

Java-side source values:

```text
cust-id, trust-code and from protected context AuthFlowSession.data.attributes.usernameCheckCiphertext stored in username/check
password from FE registration/complete request
user-id, dob and mobile-number/email from protected registration context from AuthFlowSession.data.attributes.registrationContextCiphertext
user-id is identityValue
```

Rules:

- `registration/complete` request does not accept selectedTrust.
- Java loads selectedTrust + username from `attributes.usernameCheckCiphertext` in AuthFlowSession.
- Java validates password and confirmPassword before calling APIM/TAS.
- Java transitions AuthFlowSession from `USERNAME_VERIFIED` to `COMPLETING` when completion starts.

### 8.4 Success Interpretation

Exact success response DTO is pending confirmation.

Required Java outcome on success:

```text
- return REGISTRATION_SUCCESS page model resolved from bff-pages DSL
- if no remaining unregistered trusts exist: transition COMPLETING -> COMPLETED
- if remaining unregistered trusts exist: transition COMPLETING -> TRUST_SELECTION_READY
- when transitioning to TRUST_SELECTION_READY: clear usernameCheck / current selected trust
- include remaining unregisteredTrusts in response for immediate FE rendering when available
```

Whether remaining unregistered trusts are returned directly by `TRPRegMember` or require another `TRPSearchMember` call must be confirmed by APIM/TAS contract.

### 8.5 Rejection Interpretation

A valid APIM/TAS business/error-message response from `TRPRegMember` is `DOWNSTREAM_REJECTION` and maps through `registration-password-setup` page DSL.

Rules:

- Complete failure must map to `MutationResponse.errors[]`.
- Complete failure must not return `REGISTRATION_SUCCESS` or success page.
- Username already taken can be cacheable for short TTL if configured.
- Other APIM/TAS business rejections are non-cacheable by default unless configured.

---

## 9. Technical Failure Handling

Technical APIM/TAS failures are endpoint-independent.

Classify as:

```text
eventType=DOWNSTREAM_FAILURE
logLevel=FATAL
```

Examples:

- APIM unavailable;
- TAS unavailable;
- timeout;
- network failure;
- invalid APIM envelope;
- token/certificate infrastructure failure;
- encryption/decryption infrastructure failure.

If native `FATAL` level is unavailable, log at `ERROR` with structured `severity=FATAL`.

For idempotency, if a downstream request may have been accepted or processed but Java did not receive a reliable response, mark idempotency state as `UNKNOWN_OUTCOME` and do not blindly repeat the downstream side effect on replay.

---

## 10. Open APIM/TAS Contract Items

The following items must be confirmed before final P9/P10 implementation:

```text
TRPChkCustId request DTO
TRPChkCustId success response DTO
TRPChkCustId username unavailable/error-message table
TRPRegMember request DTO
TRPRegMember success response DTO
TRPRegMember business error-message table
Whether TRPRegMember returns remaining unregistered trusts directly
Whether TRPRegMember needs TRPSearchMember follow-up to refresh remaining trusts
Whether trust identity requires trustCode + accountEnv only
```

### Registration eligibility projection

The registration flow retains the complete `TRPSearchMember` response at the APIM boundary, but FE trust-selection projections are restricted to records whose `isRegistered` value is explicitly `false`. OTP verification persists the same unregistered-only set as server-owned authorization state. Username check rejects any submitted `accountEnv + trustCode` pair outside that set, including pairs returned by TAS with `isRegistered=true`. After completion, only explicitly unregistered records are returned as remaining trusts. A null registration flag is not considered unregistered.
