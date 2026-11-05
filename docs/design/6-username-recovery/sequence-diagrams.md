# Username Recovery Sequence Diagrams

These Mermaid diagrams describe the canonical no-OTP Username Recovery flow.

## 1. Captcha challenge and verification

```mermaid
sequenceDiagram
    autonumber
    actor U as Member
    participant FE as Angular FE
    participant CAP as Captcha API
    participant REDIS as Redis

    U->>FE: Click Forgot Username
    FE-->>U: Render Identity page
    U->>FE: Enter public identityType and identityValue
    FE-->>U: Render Details page
    FE->>CAP: POST /api/v1/auth/captcha/challenge
    CAP->>REDIS: Store challenge with CREATED status and TTL
    REDIS-->>CAP: Stored
    CAP-->>FE: challengeId, imageBase64, mimeType, expiry
    FE-->>U: Render captcha
    U->>FE: Enter captcha answer
    FE->>CAP: POST /api/v1/auth/captcha/verify
    CAP->>REDIS: Verify answer and atomically set CREATED to VERIFIED
    REDIS-->>CAP: VERIFIED
    CAP-->>FE: verified=true
    FE-->>U: Enable Continue
```

## 2. Captcha refresh

```mermaid
sequenceDiagram
    autonumber
    actor U as Member
    participant FE as Angular FE
    participant CAP as Captcha API
    participant REDIS as Redis

    U->>FE: Request another captcha
    FE->>CAP: POST /api/v1/auth/captcha/refresh
    CAP->>REDIS: Change old challenge to REFRESHED
    CAP->>REDIS: Store new challenge as CREATED
    REDIS-->>CAP: Refreshed and created
    CAP-->>FE: New challenge ID, image, expiry
    FE-->>U: Render new captcha
```

## 3. Successful start with registered schemes

```mermaid
sequenceDiagram
    autonumber
    actor U as Member
    participant FE as Angular FE
    participant BFF as Java BFF
    participant REDIS as Redis
    participant APIM as APIM
    participant TAS as TAS

    U->>FE: Enter DOB and exactly one contact
    FE->>BFF: POST /start with STAFF_NO and captchaChallengeId
    BFF->>BFF: Validate and map STAFF_NO to MemberIdentityType.STAFFNO
    BFF->>REDIS: Reserve start idempotency key
    REDIS-->>BFF: Reserved
    BFF->>REDIS: Atomically consume challenge only if VERIFIED
    REDIS-->>BFF: Status changed to CONSUMED
    BFF->>APIM: TRPSearchMember with search-by STAFFNO
    APIM->>TAS: Search complete identity and contact tuple
    TAS-->>APIM: Matched records
    APIM-->>BFF: Generic envelope and data list
    BFF->>BFF: Validate invariants and minimize candidate data
    BFF->>BFF: Keep every candidate, registered or not
    BFF->>BFF: Generate opaque Scheme-Refs
    BFF->>REDIS: Create ACTIVE protected session and CSRF hash, TTL 600s
    REDIS-->>BFF: Session persisted
    BFF->>REDIS: Commit idempotency replay snapshot
    REDIS-->>BFF: Committed
    BFF-->>FE: 200 SELECT_ACCOUNT, schemes, CSRF, Set-Cookie
    FE-->>U: Render scheme result page
```

## 4. Member not found

```mermaid
sequenceDiagram
    autonumber
    actor U as Member
    participant FE as Angular FE
    participant BFF as Java BFF
    participant REDIS as Redis
    participant APIM as APIM
    participant TAS as TAS

    FE->>BFF: POST /start with verified captcha ID
    BFF->>REDIS: Atomically set VERIFIED to CONSUMED
    REDIS-->>BFF: CONSUMED
    BFF->>APIM: TRPSearchMember
    APIM->>TAS: Search
    TAS-->>APIM: err.tpa.member.not.found
    APIM-->>BFF: Generic error body
    BFF->>BFF: Map to public verification failure
    Note over BFF,REDIS: Do not create recovery session, CSRF, or cookie
    BFF-->>FE: 422 BUSINESS_REJECTED and RESTART_FLOW
    FE-->>U: Display FE-owned hotline assistance label
```

## 5. Only unregistered schemes

```mermaid
sequenceDiagram
    autonumber
    actor U as Member
    participant FE as Angular FE
    participant BFF as Java BFF
    participant REDIS as Redis
    participant APIM as APIM

    BFF->>APIM: TRPSearchMember
    APIM-->>BFF: Records with isRegistered=false only
    BFF->>BFF: Generate opaque Scheme-Refs for every record
    BFF->>REDIS: Create ACTIVE protected session and CSRF hash, TTL 600s
    REDIS-->>BFF: Session persisted
    BFF-->>FE: 200 SELECT_ACCOUNT, schemes with isRegistered=false, CSRF, Set-Cookie
    FE-->>U: Show Registration reminder; request action disabled on every row
    Note over BFF,FE: /request for an unregistered Scheme-Ref returns UNREGISTERED_CANDIDATE, no delivery
```

## 6. Request username while another remains unrequested

```mermaid
sequenceDiagram
    autonumber
    actor U as Member
    participant FE as Angular FE
    participant BFF as Java BFF
    participant REDIS as Redis
    participant OVR as Destination Override Policy
    participant MSG as MessageDeliveryPort
    participant SENDER as Email or SMS Sender

    U->>FE: Click Request My Username
    FE->>BFF: POST /request with Cookie, CSRF, Scheme-Ref, Idempotency-Key
    BFF->>BFF: Validate origin, referer, and headers
    BFF->>REDIS: Load session, validate CSRF, reserve candidate
    REDIS-->>BFF: Candidate AVAILABLE and session ACTIVE
    BFF->>OVR: Resolve effective destination
    alt PROD or DR
        OVR-->>BFF: Verified member destination
    else Non-production
        OVR-->>BFF: Dedicated configured override or fail closed
    end
    BFF->>MSG: Deliver stored username to effective destination
    MSG->>SENDER: Submit localized message
    SENDER-->>MSG: ACCEPTED with sender reference
    MSG-->>BFF: ACCEPTED
    BFF->>REDIS: Mark candidate REQUESTED and evaluate registered candidates
    REDIS-->>BFF: PARTIALLY_REQUESTED committed
    BFF-->>FE: 200 SELECT_ACCOUNT, status PARTIALLY_REQUESTED
    FE-->>U: Show acknowledgement and remaining schemes
```

## 7. Final username request completes workflow

```mermaid
sequenceDiagram
    autonumber
    actor U as Member
    participant FE as Angular FE
    participant BFF as Java BFF
    participant REDIS as Redis
    participant MSG as MessageDeliveryPort
    participant SENDER as Sender

    U->>FE: Request final unrequested registered username
    FE->>BFF: POST /request with security and idempotency headers
    BFF->>REDIS: Reserve final AVAILABLE candidate
    REDIS-->>BFF: Reserved
    BFF->>MSG: Submit username notification
    MSG->>SENDER: Send
    SENDER-->>MSG: ACCEPTED
    MSG-->>BFF: ACCEPTED
    BFF->>REDIS: Mark candidate REQUESTED
    BFF->>REDIS: Atomically mark session COMPLETED
    REDIS-->>BFF: COMPLETED committed
    BFF-->>FE: 200 RETURN_TO_LOGIN, status COMPLETED
    FE-->>U: Show final acknowledgement and return to login
```

## 8. Idempotent replay and no resend

```mermaid
sequenceDiagram
    autonumber
    actor U as Member
    participant FE as Angular FE
    participant BFF as Java BFF
    participant REDIS as Redis

    U->>FE: Retry after response loss
    FE->>BFF: POST /request with same Idempotency-Key and Scheme-Ref
    BFF->>REDIS: Load idempotency record
    REDIS-->>BFF: COMPLETED replay snapshot
    BFF-->>FE: Replay original response and nextStep
    Note over BFF,REDIS: MessageDeliveryPort is not called

    U->>FE: Retry with different key for same Scheme-Ref
    FE->>BFF: POST /request with new Idempotency-Key
    BFF->>REDIS: Resolve candidate state
    REDIS-->>BFF: Candidate already REQUESTED
    BFF-->>FE: 409 already requested, no resend
```

## 9. Delivery unknown outcome

```mermaid
sequenceDiagram
    autonumber
    actor U as Member
    participant FE as Angular FE
    participant BFF as Java BFF
    participant REDIS as Redis
    participant MSG as MessageDeliveryPort

    FE->>BFF: POST /request
    BFF->>REDIS: Reserve candidate and idempotency
    REDIS-->>BFF: Reserved
    BFF->>MSG: Submit username notification
    MSG--xBFF: Timeout after sender may have accepted
    BFF->>REDIS: Commit UNKNOWN_OUTCOME
    REDIS-->>BFF: Committed
    BFF-->>FE: 503 SYSTEM_ERROR and WAIT
    Note over BFF,MSG: Never blind-send with another key
    FE->>BFF: Retry with same Idempotency-Key
    BFF->>REDIS: Resolve stable unknown outcome
    REDIS-->>BFF: UNKNOWN_OUTCOME retained
    BFF-->>FE: Stable non-duplicate response
```

## 10. Mapped system failure with no silent catch

```mermaid
sequenceDiagram
    autonumber
    participant FE as Angular FE
    participant BFF as Controller and Use Case
    participant ADAPTER as APIM, Redis, Context, Template, or Sender Adapter
    participant AUDIT as Failure Event and Audit

    FE->>BFF: Workflow request
    BFF->>ADAPTER: Infrastructure operation
    ADAPTER--xBFF: Exception
    BFF->>AUDIT: Record sanitized correlated failure
    BFF->>BFF: Map to internal SYSTEM_FAILED
    BFF-->>FE: Public SYSTEM_ERROR and generic code
    Note over BFF: No exception is ignored or converted to null/success
```

## 11. Cancel

```mermaid
sequenceDiagram
    autonumber
    actor U as Member
    participant FE as Angular FE
    participant BFF as Java BFF
    participant REDIS as Redis

    U->>FE: Cancel or Return to Login
    FE->>BFF: POST /cancel with Cookie, CSRF, Idempotency-Key
    BFF->>BFF: Validate origin, referer, and CSRF
    BFF->>REDIS: Delete or cancel revisioned auth-flow session
    REDIS-->>BFF: Deleted or already absent
    BFF->>REDIS: Commit cancel idempotency result
    REDIS-->>BFF: Committed
    BFF-->>FE: 200 RETURN_TO_LOGIN and clear exact-path cookie
    FE-->>U: Render Login page
```

## 12. Local development through Angular proxy

```mermaid
sequenceDiagram
    autonumber
    actor DEV as FE Developer Browser
    participant NG as Angular Dev Server localhost:4200
    participant API as Java API

    DEV->>NG: POST relative /api/v1/auth/username-recovery/start
    NG->>API: Proxy request to configured API target
    API-->>NG: Response and Strict auth_flow_sid cookie
    NG-->>DEV: Same-origin proxied response
    Note over DEV,NG: Browser stores and sends cookie through localhost origin
    DEV->>NG: POST relative /api/v1/auth/username-recovery/request
    NG->>API: Proxy with Cookie and X-XSRF-TOKEN
    API-->>NG: Result
    NG-->>DEV: Result
```
