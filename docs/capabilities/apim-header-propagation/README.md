# APIM Outbound Header Propagation

## Purpose

The BFF forwards three headers to APIM on every outbound business call:

| Header | Source | Pre-login value |
| --- | --- | --- |
| `X-Request-Id` | Inbound FE request header | generated UUID if absent |
| `X-Trust` | `PortalAccessContext.account().trustCode()` | `""` |
| `X-User-Role` | `PortalAccessContext.actor().actorUserRole()` | `""` |

`X-Request-Id` keeps its established casing. `X-User-Role` is the plain enum
name via `toString()` (for example `ROLE_USER_MBR`) with no wire mapping
applied; `toApimValue()` is deliberately **not** used here.

## Architecture constraint

Outbound adapters must never reference portal identity types. Two guards
enforce this by scanning `adapter/out` source text:

- `AuthenticatedContextExplicitUseCaseBoundaryTest` - rejects any file
  containing `PortalAccessContext`, `PortalAccessContextKeys`,
  `CurrentPortalAccessContextResolver`, `ACTOR_CONTEXT`, or `ACCOUNT_CONTEXT`.
- `PortalAccessContextArchitectureGuardTest` - rejects application-global
  storage of `PortalAccessContext`.

Both checks are whitespace-insensitive, so even a helper signature such as
`private static PortalAccessContext foo(...)` trips the global-state guard.

Consequently the identity values are **derived inbound and published as plain
Strings** on the Reactor Context, mirroring the existing request-id contract:

```
PortalAccessContextWebFilter  (adapter/in)
  -> PortalIdentityCorrelation.TRUST_CODE_CONTEXT_KEY  : String
  -> PortalIdentityCorrelation.USER_ROLE_CONTEXT_KEY   : String
        |
        v
ApimContextPropagationExchangeFilter  (adapter/out)
  reads plain Strings only, never identity objects
```

### Trade-off on record

Carrying identity as ambient context strings satisfies the guards, but it is
still identity travelling ambiently. The fully intent-respecting alternative is
threading trust/role explicitly through every command, as existing APIM
adapters already do with `actorUserRole()`. That is a far larger refactor and
cannot cover pre-login calls, so the pragmatic route was taken deliberately.

## Effective client wiring

`ApimWebClientFacade.sendWithProfile` builds a **per-profile client** by cloning
the base `webClientBuilder`. Filters registered on the `apimWebClient` bean do
**not** run for those business calls. Both filters are therefore attached to
the cloned client:

```java
WebClient client =
    webClientBuilder
        .clone()
        .baseUrl(baseUrl)
        .filter(contextPropagationExchangeFilter.filter())
        .filter(requestLoggingExchangeFilter.filter())
        .build();
```

`ApimEffectiveClientFilterChainTest` guards this wiring.

## Verified runtime behaviour

Both a post-login and a pre-login call were captured end to end.

Account balance (`TRPGetMemberAccBal`), authenticated:

```
X-Request-Id: <client value, unchanged>
X-Trust:      DBA
X-User-Role:  ROLE_USER_MBR
```

Login (`TRPMemberLogin`), pre-authentication:

```
X-Request-Id: <client value, unchanged>
X-Trust:      ""
X-User-Role:  ""
```

## Downstream note on `X-Request-Id`

An APIM gateway trace may show a different `x-request-id` than the value the
BFF sent. Envoy regenerates `x-request-id` unless `preserve_external_request_id`
is enabled. That is gateway-side configuration and outside this capability.

When diagnosing, compare against the BFF's own `apim.request` log event: its
`requestId` field and `X-Request-Id` header are both read from the Reactor
Context in-process, so they show what the BFF actually sent.

## Related tests

- `ApimContextPropagationExchangeFilterTest`
- `ApimContextPropagationExchangeFilterHeaderTest`
- `ApimEffectiveClientFilterChainTest`
- `AuthenticatedContextExplicitUseCaseBoundaryTest`
- `PortalAccessContextArchitectureGuardTest`
