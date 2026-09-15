# Outbound Header Contract (BFF to APIM)

## Header names

Declared in `ApimPropagationHeaders`:

```java
public static final String TRUST_HEADER     = "X-Trust";
public static final String USER_ROLE_HEADER = "X-User-Role";
```

`X-Request-Id` remains in `RequestCorrelation.REQUEST_ID_HEADER`.

## Emission rules

| Condition | `X-Request-Id` | `X-Trust` | `X-User-Role` |
| --- | --- | --- | --- |
| Authenticated request | propagated | trust code | role name |
| Pre-login (login, registration, captcha, OTP, username recovery) | propagated | `""` | `""` |
| Context value null or absent | omitted | `""` | `""` |

`X-Trust` and `X-User-Role` are **always emitted**, empty rather than absent, so
APIM sees a stable header set. `X-Request-Id` is omitted only when the Reactor
Context holds no value, which does not occur in practice because
`RequestLoggingWebFilter` generates one when the inbound header is missing.

## Context keys

| Key | Holder | Type |
| --- | --- | --- |
| `REQUEST_ID_CONTEXT_KEY` | `RequestCorrelation` | `String` |
| `TRUST_CODE_CONTEXT_KEY` | `PortalIdentityCorrelation` | `String` |
| `USER_ROLE_CONTEXT_KEY` | `PortalIdentityCorrelation` | `String` |

Only inbound web filters write these keys. Outbound adapters read only.

## Request-id resolution

`RequestLoggingWebFilter.resolveRequestId` uses the inbound `X-Request-Id` when
present and non-blank, otherwise generates a UUID. A generated value appears in
both the log event and the outbound header, which is the expected signature of a
request that arrived without the header.

## Adding a new propagated header

1. Add the name to `ApimPropagationHeaders`.
2. Add a plain-String context key to `PortalIdentityCorrelation`.
3. Publish the derived value from the relevant inbound web filter.
4. Read it in `ApimContextPropagationExchangeFilter` - **plain String only**.
5. Extend `ApimContextPropagationExchangeFilterHeaderTest`.

Never import an identity type into `adapter/out`; the architecture guards fail
the build on source text, before any behaviour is exercised.
