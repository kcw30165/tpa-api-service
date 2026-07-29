> Status: Design baseline v0.1  
> Date: 20 July 2026  
> Scope: Subsequent member login plus reusable authentication foundations  
> Source baseline: Login BRD V0.3, discovery decisions in 02-brd.md through 10-brd.md, and the supplied current codebase snapshot  
> Open external dependency: detailed `TRPGetAccountList` business failure-code mapping remains TBC.


# Login Configuration Contract

The exact binding class names may follow repository conventions. Values are illustrative agreed baselines; Java contains no hardcoded security fallback.

```yaml
auth-workflows:
  login:
    session:
      idle-timeout-seconds: 900
    credentials:
      username:
        trim: true
        normalization: ASCII_LOWERCASE
        max-length: 120
        allowed-pattern: "<deployment-configured>"
      password:
        trim: false
        case-sensitive: true
        max-length: "<configured>"
    otp:
      length: 6
      numeric-only: true
      ttl-seconds: 300
      resend-cooldown-seconds: 60
      max-verify-attempts: 5
      max-resend-attempts: 5
      invalidate-previous-on-resend: true
      reset-verify-attempts-on-resend: true
    idempotency:
      credentials-ttl-seconds: 300
      otp-send-ttl-seconds: 300
      otp-resend-ttl-seconds: 300
      otp-verify-ttl-seconds: 300
    account-identifiers:
      policy-no:
        width: 11
        pad-character: "0"
      cert-no:
        width: 4
        pad-character: "0"

portal-session:
  idle-timeout-seconds: 1800
  sliding: true
  cookie:
    name: portal_sid
    secure: true
    http-only: true
    same-site: None
    path: /api
  context-protection:
    enabled: true
    key-version: v1
    secret: ${PORTAL_SESSION_CONTEXT_SECRET:}

apim:
  retry:
    business-call:
      max-attempts: 3
      # Existing global backoff settings remain authoritative.
```

## Validation

Startup fails for missing/invalid:

- Session timeouts
- Cookie security policy
- Identifier widths/pad character
- Login credential policy
- OTP policy
- Authenticated-session context secret/key version
- APIM retry attempt policy

## Non-production destination override

Login uses the same physical-boundary override design as registration, with login-specific configuration values. Original APIM destinations remain authoritative for masking, destination IDs, OTP subject binding and session state.
