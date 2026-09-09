# Registration Sequence Diagrams

## Purpose

These diagrams show the major successful interaction flows and state transitions for the registration AuthFlow.

They intentionally omit detailed failure handling, validation branches, retry/idempotency branches, and low-level Redis read/write details.

For detailed behavior, use the purpose-specific contract documents:

- `registration-state-contract.md` — runtime state, Redis shapes, status transitions.
- `authflow-session-security-contract.md` — cookie, CSRF, Origin/Referer, rate limit, security rules.
- `registration-api-contract.md` — FE-facing endpoint requests/responses.
- `registration-apim-contract.md` — APIM/TAS request mapping and downstream classification.
- `registration-error-matrix.md` — all failure conditions, log severity, event type, state impact, idempotency behavior.
- `registration-implementation-checklist.md` — P9/P10 tasks and tests.

## Diagram Conventions

- Happy path is shown first.
- Error paths are summarized with notes and delegated to `registration-error-matrix.md`.
- Idempotency is shown as a guard, not expanded into all replay branches.
- Redis is hidden behind Java-owned capabilities unless the interaction is central to the flow.
- SMS and email are collapsed into Message Delivery.
- AuthFlowSession uses `auth_flow_sid` HttpOnly cookie and server-side Redis state.
- CSRF token is returned by `registration/start`, stored by FE in memory, and remains stable for the AuthFlowSession lifetime unless Java explicitly returns a new `result.csrfToken`.
- `auth/flow/resume` is out of current BRD scope. If FE loses in-memory CSRF token, FE restarts registration.

---

## Common Protected AuthFlow Request Preflight

Applies to:

- `POST /api/v1/auth/registration/otp/resend`
- `POST /api/v1/auth/registration/otp/verify`
- `POST /api/v1/auth/registration/username/check`
- `POST /api/v1/auth/registration/complete`
- `POST /api/v1/auth/registration/cancel`

Common BFF behavior:

1. Load AuthFlowSession using `auth_flow_sid` cookie.
2. Validate CSRF token from `X-XSRF-TOKEN`.
3. Validate Origin/Referer.
4. Validate current workflow state.
5. Apply action-scoped idempotency guard if the endpoint requires it.
6. Resolve failures through `registration-error-matrix.md`.

```mermaid
sequenceDiagram
    autonumber
    participant FE as Angular FE
    box rgb(232,245,255) Java BFF / Internal Capabilities
        participant BFF as Java BFF
        participant Session as AuthFlowSession Capability
        participant Csrf as CSRF / Origin Capability
        participant Idem as Idempotency Capability
    end

    FE->>BFF: Protected auth-flow POST<br/>auth_flow_sid + X-XSRF-TOKEN<br/>X-Request-Id + Accept-Language<br/>Idempotency-Key if required
    BFF->>Session: Load AuthFlowSession and validate state
    Session-->>BFF: Valid AuthFlowSession
    BFF->>Csrf: Validate CSRF + Origin/Referer
    Csrf-->>BFF: CSRF and origin accepted
    opt Endpoint requires idempotency
        BFF->>Idem: Apply action-scoped idempotency guard
        Idem-->>BFF: Proceed for this request intent
    end
    Note over BFF,FE: Failure behavior follows registration-error-matrix.md
```

---

## 1. Captcha Challenge and Verify

Captcha is confirmed for registration.

This diagram shows the normal captcha lifecycle. Invalid, expired, locked, consumed, and rate-limited cases are defined in `registration-error-matrix.md`.

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant FE as Angular FE
    box rgb(232,245,255) Java BFF / Internal Capabilities
        participant BFF as Java BFF
        participant Captcha as Captcha Challenge Capability
    end

    User->>FE: Open registration page
    FE->>BFF: POST /api/v1/auth/captcha/challenge
    BFF->>Captcha: Create captcha challenge<br/>status = CREATED
    Captcha-->>BFF: captchaChallengeId + display payload
    BFF-->>FE: Return captchaChallengeId + captcha display payload
    FE-->>User: Display captcha

    User->>FE: Enter captcha answer
    FE->>BFF: POST /api/v1/auth/captcha/verify<br/>captchaChallengeId + answer
    BFF->>Captcha: Verify answer + challenge status + TTL
    Captcha-->>BFF: Captcha verified<br/>status = VERIFIED
    BFF-->>FE: success=true<br/>verified=true
    FE-->>User: Enable registration submit

    Note over BFF,Captcha: registration/start later atomically consumes VERIFIED captcha
```

---

## 2. Registration Start + AuthFlowSession + OTP Send

This diagram shows successful registration-start verification, AuthFlowSession creation, CSRF issuance, OTP creation, and OTP delivery.

Detailed validation failures, APIM/TAS business rejections, captcha failures, message delivery failures, and idempotency replay behavior are defined in `registration-error-matrix.md`.

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant FE as Angular FE
    box rgb(232,245,255) Java BFF / Internal Capabilities
        participant BFF as Java BFF
        participant Captcha as Captcha Challenge Capability
        participant Session as AuthFlowSession Capability
        participant Csrf as CSRF Token Capability
        participant OTP as OTP Challenge Capability
        participant Msg as Message Delivery Service
        participant Idem as Idempotency Capability
    end
    participant APIM
    participant TAS as TAS / Progress

    User->>FE: Submit registration form
    FE->>BFF: POST /api/v1/auth/registration/start<br/>Idempotency-Key + X-Request-Id + Accept-Language<br/>identity + DOB + email/mobile<br/>captchaChallengeId if required

    BFF->>Idem: Apply registration-start idempotency guard
    Idem-->>BFF: Proceed for this request intent

    BFF->>BFF: Validate request shape<br/>identity + DOB + exactly one contact
    opt Captcha required
        BFF->>Captcha: Atomically consume verified captchaChallengeId
        Captcha-->>BFF: Captcha consumed
    end

    BFF->>APIM: POST TRPSearchMember<br/>search-by + search-value + dob + email/mobile-number
    APIM->>TAS: Verify member and unregistered registration eligibility
    TAS-->>APIM: Verification success with unregistered trust data
    APIM-->>BFF: err-message empty + non-empty data[]

    BFF->>Session: Create AuthFlowSession<br/>scenario=REGISTRATION<br/>status=OTP_SENT<br/>protected registration context<br/>masked destination
    Session-->>BFF: AuthFlowSessionId

    BFF->>Csrf: Create CSRF token and store hash
    Csrf-->>BFF: csrfToken

    BFF->>OTP: Create OTP challenge<br/>scenario=REGISTRATION
    OTP-->>BFF: activeOtpChallengeId + OTP code metadata

    BFF->>Session: Store activeOtpChallengeId
    Session-->>BFF: Session updated

    BFF->>Msg: Send REGISTRATION_OTP<br/>channel derived from email/mobile
    Msg-->>BFF: Message accepted

    BFF->>Idem: Mark completed for this request intent
    Idem-->>BFF: Idempotency updated

    BFF-->>FE: 200 OK<br/>Set-Cookie auth_flow_sid<br/>Body nextStep=VERIFY_OTP<br/>csrfToken + maskedDestination + OTP metadata
    FE-->>User: Show OTP verification page

    Note over BFF,FE: If FE changes registration-start payload after validation failure, FE must use a new Idempotency-Key
```

---

## 3. OTP Resend

This diagram shows the successful resend flow only.

Cooldown, max resend, invalid session, CSRF failure, message delivery failure, and duplicate/replay behavior are defined in `registration-error-matrix.md`.

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant FE as Angular FE
    box rgb(232,245,255) Java BFF / Internal Capabilities
        participant BFF as Java BFF
        participant Session as AuthFlowSession Capability
        participant OTP as OTP Challenge Capability
        participant Msg as Message Delivery Service
        participant Idem as Idempotency Capability
    end

    User->>FE: Click Resend OTP
    FE->>BFF: POST /api/v1/auth/registration/otp/resend<br/>auth_flow_sid + X-XSRF-TOKEN<br/>Idempotency-Key + X-Request-Id + Accept-Language

    BFF->>Session: Common protected AuthFlow preflight
    Session-->>BFF: Valid AuthFlowSession with activeOtpChallengeId
    BFF->>Idem: Apply otp-resend idempotency guard
    Idem-->>BFF: Proceed for this resend intent

    BFF->>OTP: Create replacement OTP<br/>invalidate old active OTP
    OTP-->>BFF: New activeOtpChallengeId + OTP metadata

    BFF->>Session: Update activeOtpChallengeId
    Session-->>BFF: Session updated

    BFF->>Msg: Send REGISTRATION_OTP to protected destination
    Msg-->>BFF: Message accepted

    BFF->>Idem: Mark completed for this resend intent
    Idem-->>BFF: Idempotency updated

    BFF-->>FE: success=true<br/>nextStep=VERIFY_OTP<br/>expiry + cooldown + remainingResendAttempts
    FE-->>User: Show resend confirmation

    Note over BFF,OTP: Replay with same requestHash must not create another OTP or send another message
```

---

## 4. OTP Verify + Current Unregistered Trust Lookup

This diagram shows OTP verification and latest trust lookup.

OTP invalid, expired, locked, APIM/TAS lookup failure, idempotency replay, and session/CSRF failures are defined in `registration-error-matrix.md`.

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant FE as Angular FE
    box rgb(232,245,255) Java BFF / Internal Capabilities
        participant BFF as Java BFF
        participant Session as AuthFlowSession Capability
        participant OTP as OTP Challenge Capability
        participant Idem as Idempotency Capability
    end
    participant APIM
    participant TAS as TAS / Progress

    User->>FE: Submit OTP code
    FE->>BFF: POST /api/v1/auth/registration/otp/verify<br/>auth_flow_sid + X-XSRF-TOKEN<br/>Idempotency-Key + OTP code

    BFF->>Session: Common protected AuthFlow preflight
    Session-->>BFF: Valid AuthFlowSession with activeOtpChallengeId
    BFF->>Idem: Apply otp-verify idempotency guard
    Idem-->>BFF: Proceed for this OTP verify intent

    BFF->>OTP: Verify OTP using activeOtpChallengeId
    OTP-->>BFF: OTP verified

    BFF->>Session: Mark status=OTP_VERIFIED
    Session-->>BFF: Session updated

    BFF->>APIM: Get latest unregistered trusts/accounts
    APIM->>TAS: Query current unregistered trusts/accounts
    TAS-->>APIM: Current trust list
    APIM-->>BFF: unregisteredTrusts[]

    alt Exactly one unregistered trust
        BFF->>Idem: Mark completed with response snapshot
        Idem-->>BFF: Idempotency updated
        BFF-->>FE: success=true<br/>nextStep=USERNAME_CHECK<br/>unregisteredTrusts[1]
        FE-->>User: Show username page or auto-select trust
    else Multiple unregistered trusts
        BFF->>Idem: Mark completed with response snapshot
        Idem-->>BFF: Idempotency updated
        BFF-->>FE: success=true<br/>nextStep=SELECT_TRUST<br/>unregisteredTrusts[]
        FE-->>User: Show trust selection page
    end

    Note over BFF,OTP: Replay with same requestHash must not re-run OTP verification or increment attempts
```

---

## 5. Username Check

This diagram shows username availability checking and storing the current registration target in AuthFlowSession.

Username unavailable, invalid state, invalid username, APIM/TAS rejection, and technical failures are defined in `registration-error-matrix.md`.

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant FE as Angular FE
    box rgb(232,245,255) Java BFF / Internal Capabilities
        participant BFF as Java BFF
        participant Session as AuthFlowSession Capability
    end
    participant APIM
    participant TAS as TAS / Progress

    User->>FE: Select trust and enter username
    FE->>BFF: POST /api/v1/auth/registration/username/check<br/>auth_flow_sid + X-XSRF-TOKEN<br/>selectedTrust + username

    BFF->>Session: Common protected AuthFlow preflight<br/>allowed state OTP_VERIFIED or TRUST_SELECTION_READY
    Session-->>BFF: Valid AuthFlowSession

    BFF->>BFF: Validate selectedTrust + username format
    BFF->>APIM: Check username availability<br/>for selectedTrust
    APIM->>TAS: Check username availability
    TAS-->>APIM: Username available
    APIM-->>BFF: Username available

    BFF->>Session: Store usernameCheck<br/>selectedTrust + username + checkedAt<br/>status=USERNAME_VERIFIED
    Session-->>BFF: Session updated

    BFF-->>FE: success=true<br/>nextStep=PASSWORD_SETUP
    FE-->>User: Show password setup page

    Note over BFF,Session: username/check is not action-idempotent because username availability is first-come, first-served
```

---

## 6. Registration Complete + Multi-Trust Continuation

This diagram shows registration completion and the multi-trust continuation state.

APIM/TAS business failures, password validation, unknown downstream outcome, and idempotency replay behavior are defined in `registration-error-matrix.md`.

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant FE as Angular FE
    box rgb(232,245,255) Java BFF / Internal Capabilities
        participant BFF as Java BFF
        participant Session as AuthFlowSession Capability
        participant Pages as bff-pages DSL Resolver
        participant Idem as Idempotency Capability
    end
    participant APIM
    participant TAS as TAS / Progress

    User->>FE: Submit password + confirmPassword
    FE->>BFF: POST /api/v1/auth/registration/complete<br/>auth_flow_sid + X-XSRF-TOKEN<br/>Idempotency-Key + password + confirmPassword

    BFF->>Session: Common protected AuthFlow preflight<br/>require USERNAME_VERIFIED + usernameCheck
    Session-->>BFF: Valid AuthFlowSession<br/>selectedTrust + username available
    BFF->>Idem: Apply registration-complete idempotency guard
    Idem-->>BFF: Proceed for this complete intent

    BFF->>BFF: Validate password + confirmPassword<br/>and password policy
    BFF->>Session: Mark status=COMPLETING
    Session-->>BFF: Session updated

    BFF->>APIM: Complete registration<br/>using session selectedTrust + username
    APIM->>TAS: Register credentials for selected trust
    TAS-->>APIM: Registration success + latest remaining trusts if available
    APIM-->>BFF: Registration success

    alt No remaining unregistered trusts
        BFF->>Session: Mark status=COMPLETED
        Session-->>BFF: Session completed
        BFF->>Pages: Resolve registration-success<br/>variant=noRemainingTrusts
        Pages-->>BFF: Localized success page<br/>Return to Login only
        BFF->>Idem: Mark completed with response snapshot
        Idem-->>BFF: Idempotency updated
        BFF-->>FE: success=true<br/>nextStep=RETURN_TO_LOGIN<br/>unregisteredTrusts=[]<br/>successPage noRemainingTrusts
        FE-->>User: Show success page<br/>Return to Login
    else Remaining unregistered trusts exist
        BFF->>Session: Mark status=TRUST_SELECTION_READY<br/>clear previous usernameCheck / selectedTrust
        Session-->>BFF: Session ready for next trust selection
        BFF->>Pages: Resolve registration-success<br/>variant=hasRemainingTrusts
        Pages-->>BFF: Localized success page<br/>Continue Registration + Return to Login
        BFF->>Idem: Mark completed with response snapshot
        Idem-->>BFF: Idempotency updated
        BFF-->>FE: success=true<br/>nextStep=SELECT_TRUST<br/>unregisteredTrusts[]<br/>successPage hasRemainingTrusts
        FE-->>User: Show success page<br/>Continue Registration / Return to Login
    end
```

---

## 7. Continue Registration From Remaining Trusts

This diagram shows how FE continues from the success page when remaining trusts exist.

The user does not repeat captcha, registration/start, or OTP while the AuthFlowSession remains valid.

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant FE as Angular FE
    box rgb(232,245,255) Java BFF / Internal Capabilities
        participant BFF as Java BFF
        participant Session as AuthFlowSession Capability
    end
    participant APIM
    participant TAS as TAS / Progress

    User->>FE: Click Continue Registration
    FE-->>User: Show trust selection page<br/>using unregisteredTrusts returned by registration/complete
    User->>FE: Select another trust and enter username
    FE->>BFF: POST /api/v1/auth/registration/username/check<br/>auth_flow_sid + X-XSRF-TOKEN<br/>selectedTrust + username

    BFF->>Session: Common protected AuthFlow preflight<br/>require TRUST_SELECTION_READY
    Session-->>BFF: Valid continuation session

    BFF->>APIM: Check username availability<br/>and validate selectedTrust as required
    APIM->>TAS: Check current downstream state
    TAS-->>APIM: Username/trust check result
    APIM-->>BFF: Username available

    BFF->>Session: Store new usernameCheck<br/>status=USERNAME_VERIFIED
    Session-->>BFF: Session updated

    BFF-->>FE: success=true<br/>nextStep=PASSWORD_SETUP
    FE-->>User: Show password setup page

    Note over FE,BFF: The next registration/complete repeats the complete flow and may return TRUST_SELECTION_READY again
```

---

## 8. Cancel / Back

Cancel is intentionally small here. CSRF/session failure behavior is in `registration-error-matrix.md`.

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant FE as Angular FE
    box rgb(232,245,255) Java BFF / Internal Capabilities
        participant BFF as Java BFF
        participant Session as AuthFlowSession Capability
    end

    User->>FE: Click Back / Cancel
    FE->>BFF: POST /api/v1/auth/registration/cancel<br/>auth_flow_sid + X-XSRF-TOKEN<br/>reason
    BFF->>Session: Common protected AuthFlow preflight
    Session-->>BFF: Valid AuthFlowSession
    BFF->>Session: Mark CANCELLED / remove temporary state<br/>invalidate active OTP if required
    Session-->>BFF: Session cancelled
    BFF-->>FE: success=true<br/>clear auth_flow_sid cookie<br/>cancelled=true
    FE-->>User: Return to previous page or login
```

Lost CSRF behavior:

- If FE lost the in-memory CSRF token, FE must not call `registration/cancel`.
- The old AuthFlowSession expires naturally.
