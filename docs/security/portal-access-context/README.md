# Portal Access and Account Context

> **Status:** Documents the current implementation and clearly identified planned `ActorContext` evolution represented by the reviewed codebase snapshot and the current design direction.  
> **Audience:** New team members, backend developers, security reviewers, frontend developers, testers, and APIM integration developers.  
> **Scope:** Authenticated portal context resolution, actor and selected-account context, opaque `Account-Ref`, authenticated-session lookup, CSRF validation, Reactor Context propagation, downstream use, temporary adapters, and safe extension.

## Start here

`PortalAccessContext` is the per-request authorization context used after Login. It combines the authenticated actor with the one account selected for the current request.

```text
Browser request
  + portal_sid cookie
  + X-XSRF-TOKEN where required
  + Account-Ref for account-scoped APIs
        |
        v
PortalAccessContextWebFilter
  -> validate and touch authenticated session
  -> match Account-Ref to an account stored in that session
  -> create PortalAccessContext
  -> place it in exchange attributes and Reactor Context
        |
        v
Controller / application service / APIM adapter
```

Read the documents in this order:

1. **[Architecture and resolution](architecture-and-resolution.md)** — understand the context model, authenticated-session source, filtering, Reactor propagation, account selection, and APIM boundary.
2. **[Integration and evolution guide](integration-and-evolution-guide.md)** — follow the implementation recipe for authenticated endpoints and the planned `ActorContext` expansion.
3. **[Operations and testing](operations-and-testing.md)** — troubleshoot, secure, test, migrate, and release context changes.

## Current context model

The current public actor record is:

```java
public record ActorContext(
        String actorUserId,
        String actorUserRole
) {}
```

`PortalAccessContext` combines this actor with `AccountContext`, which carries the selected account’s opaque reference and downstream account attributes.

## Planned `ActorContext` evolution

The current two-field record is not sufficient. The planned actor context will add:

```text
actorUsername
actorUserType
```

The current and planned meanings are:

- `actorUserId` — the TAS user identity identifier. Depending on identity type, this can correspond to an HKID, passport number, staff number, or another approved TAS identity identifier.
- `actorUserRole` — the actor's role/authorization classification currently supplied for access decisions and downstream context.
- `actorUsername` — planned; the actor's login username, kept distinct from identity ID.
- `actorUserType` — planned; the actor category/type, kept distinct from role.

The exact allowed values and downstream field mapping for `actorUserType` and the final distinction between type and role must be governed by the approved TAS/APIM contract. These two fields must be documented as **planned, not currently implemented**.

## Security classification

`actorUserId` can contain a direct personal identity identifier. It is sensitive and must not be logged, exposed to the frontend, used in Redis keys, used as an `Account-Ref`, or placed into metric labels. The same conservative treatment should apply to `actorUsername` when introduced.

## Account context

The selected account context includes concepts such as:

- `accountRef`
- `accountEnv`
- `policyNo`
- `certNo`
- `trustCode`
- `schemeType`
- `termStatus`
- `termCompletionDate`

Only `Account-Ref` is supplied by the frontend. The BFF resolves the remaining values from the validated authenticated session. citeturn14search59

## Core rules

1. `PortalAccessContext` is server-derived, never client-constructed.
2. The authenticated session is the authorization source.
3. `Account-Ref` is opaque and session-scoped.
4. A supplied account reference must belong to the loaded session.
5. Actor identity and account fields are never trusted from request bodies or arbitrary headers.
6. Session and CSRF validation occur before context use.
7. Context is request-scoped and propagated through Reactor Context.
8. APIM receives only the operation-specific context it requires.
9. `actorUserId`, `actorUsername`, policy number, and certificate number are sensitive.
10. Temporary YAML/profile adapters are not the target production authority.
11. Context model changes require session-persistence and rolling-deployment compatibility review.
12. Role and type must remain semantically distinct unless the approved contract says otherwise.
