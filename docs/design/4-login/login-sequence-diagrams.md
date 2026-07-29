> Status: Design baseline v0.1  
> Date: 20 July 2026  
> Scope: Subsequent member login plus reusable authentication foundations  
> Source baseline: Login BRD V0.3, discovery decisions in 02-brd.md through 10-brd.md, and the supplied current codebase snapshot  
> Open external dependency: detailed `TRPGetAccountList` business failure-code mapping remains TBC.


# Login Sequence Diagrams

## 1. Successful login

```mermaid
sequenceDiagram
    autonumber
    participant FE
    participant BFF as Java BFF
    participant APIM
    participant TAS as Progress/TAS
    participant OTP as Shared OTP + Delivery
    participant Redis

    FE->>BFF: POST credentials(username, password)
    BFF->>BFF: Normalize/validate configured login policy
    BFF->>APIM: TRPMemberLogin
    APIM->>TAS: Verify credentials
    TAS-->>APIM: Verified + cust-type + destinations
    APIM-->>BFF: Shared envelope
    BFF->>Redis: Create LOGIN AuthFlowSession + protected context + CSRF hash
    BFF-->>FE: auth_flow_sid + nextStep SELECT_OTP_DESTINATION + masked destinations + csrfToken

    FE->>BFF: Select opaque destinationId + X-XSRF-TOKEN
    BFF->>OTP: Create/send OTP using protected destination
    OTP->>Redis: Persist hash-only challenge
    BFF-->>FE: nextStep VERIFY_OTP

    FE->>BFF: Verify OTP + X-XSRF-TOKEN
    BFF->>OTP: Verify active challenge
    OTP-->>BFF: VERIFIED
    BFF->>APIM: TRPGetAccountList(session-owned fields)
    APIM->>TAS: Get account list
    TAS-->>APIM: Account records
    APIM-->>BFF: Shared envelope
    BFF->>BFF: Validate, format IDs, generate AccountRefs
    BFF->>Redis: Atomic create portal session + active index + replace old session
    Redis-->>BFF: Committed
    BFF->>Redis: Consume pre-auth session
    BFF-->>FE: portal_sid + new csrfToken + nextStep SELECT_ACCOUNT + accounts
```

## 2. OTP verification exhausted but resend remains

```mermaid
sequenceDiagram
    participant FE
    participant BFF
    participant OTP
    participant Redis

    loop Until configured verification limit
        FE->>BFF: Submit invalid OTP
        BFF->>OTP: Verify active challenge
        OTP-->>BFF: INVALID, remaining attempts
        BFF-->>FE: Existing shared OTP error contract
    end
    OTP->>Redis: Mark current challenge unusable
    BFF-->>FE: HTTP 429 + err.login.otp.verifyAttemptsExceeded + resend next step
    FE->>BFF: Resend OTP
    BFF->>OTP: Replace challenge using existing shared lifecycle
    OTP->>Redis: Invalidate previous#59; create fresh challenge
    BFF-->>FE: VERIFY_OTP with reset verification allowance
```

## 3. Resend exhausted

```mermaid
sequenceDiagram
    participant FE
    participant BFF
    participant OTP
    participant Redis

    FE->>BFF: Resend beyond configured limit
    BFF->>OTP: Replace challenge
    OTP-->>BFF: RESEND_LIMIT_REACHED
    BFF->>Redis: Invalidate challenge and cancel pre-auth flow
    BFF-->>FE: HTTP 429 + err.login.otp.resendAttemptsExceeded + LOGIN_CREDENTIALS
    Note over BFF,FE: auth_flow_sid/pre-auth transport cleared#59; account is not locked
```

## 4. Account-list unusable

```mermaid
sequenceDiagram
    participant FE
    participant BFF
    participant APIM
    participant Redis

    BFF->>APIM: TRPGetAccountList
    loop Up to three eligible business-call attempts
        APIM-->>BFF: Transient technical failure
        BFF->>APIM: Shared global retry
    end
    alt Empty/unavailable final outcome
        BFF->>Redis: Cancel pre-auth flow
        BFF-->>FE: SYSTEM_ERROR + err.login.accountList.unavailable + LOGIN_CREDENTIALS
    else Records returned but zero valid
        BFF->>BFF: ERROR logs without sensitive values
        BFF->>Redis: Cancel pre-auth flow
        BFF-->>FE: HTTP 502 + err.login.accountList.unavailable + LOGIN_CREDENTIALS
    end
```

## 5. Last login wins

```mermaid
sequenceDiagram
    participant DeviceA
    participant DeviceB
    participant BFF
    participant Redis

    Note over DeviceA,Redis: Session A is active
    DeviceB->>BFF: Completes OTP and account retrieval
    BFF->>Redis: Atomic create B + active(username)=B + revoke A + marker A
    Redis-->>BFF: Success
    BFF-->>DeviceB: portal_sid=B
    DeviceA->>BFF: Request with portal_sid=A
    BFF->>Redis: Load session A / replacement marker
    Redis-->>BFF: Replaced
    BFF-->>DeviceA: HTTP 401 err.login.session.replaced
```

## 6. Account-scoped request

```mermaid
sequenceDiagram
    participant FE
    participant Filter as PortalAccessContextWebFilter
    participant Session as Authenticated Session Adapter
    participant UseCase

    FE->>Filter: Request + portal_sid + Account-Ref
    Filter->>Session: Resolve session and account
    Session->>Session: Decrypt context#59; validate active index#59; validate AccountRef
    Session-->>Filter: PortalAccessContext
    Filter->>UseCase: Continue with Reactor Context
    UseCase-->>FE: Response
    Session->>Session: Refresh sliding expiry after valid request
```
