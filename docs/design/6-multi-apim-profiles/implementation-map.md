# Multiple APIM Profiles Implementation Map

## Document Status

- **Purpose:** Governing implementation map for the multiple-APIM-profile change.
- **Implementation authority:** GitHub Copilot CLI using the explicitly pinned `gpt-5-mini` model will perform discovery, design review, and staged implementation through prompt files generated after this map.
- **Current phase:** Discovery and design control only.
- **Production code changes authorized by this document:** None.
- **Assumption policy:** No implementation stage may invent unresolved business, APIM, TAS, security, persistence, or configuration behavior.
- **Cumulative context:** Every stage must read this file and the latest `handover.md` before inspecting or changing code.

## 1. Objective

Refactor the existing misleading APIM credential-profile implementation into a strict, explicit, and extensible routing mechanism that selects the APIM credentials associated with the required physical backend database.

The routing model must support:

1. Authenticated member calls that obtain `actorUserRole` and `accountEnv` from `PortalAccessContext`.
2. Pre-authentication calls that have no `PortalAccessContext` but may obtain `accountEnv` from protected auth-flow session state.
3. A logical non-account APIM profile for calls that are not routed by account environment.
4. Future acting-on-behalf roles without defining unapproved routing behavior for those roles now.
5. Fail-closed behavior with structured `ERROR` logging for every mapping or profile-resolution failure.

## 2. Terminology

### 2.1 Physical database

`masterLog` and each specific `accountEnv` identify physical backend databases. Java does not connect directly to these databases. Java selects APIM credentials, and APIM uses those credentials or associated routing configuration to direct traffic to the intended database.

### 2.2 APIM credential profile

A Java-side configuration object representing the credentials and connection material required for an APIM route. Confirmed credential dimensions include:

- client ID
- client secret
- APIM API key

Other existing dimensions, such as base URL, token URI, scope, and certificate material, must be inventoried before deciding whether they remain global or become profile-specific.

### 2.3 Non-account profile

The logical Java-side profile used by calls that are not routed to a database identified by `accountEnv`.

The existing name `masterLog` is considered misleading on the Java side. Its final replacement name is not yet approved. `NON_ACCOUNT` and `nonAccount` are current candidates only.

The non-account profile is not:

- a default profile
- a fallback profile
- a recovery path for missing mappings
- a substitute for an unknown `accountEnv`

### 2.4 Account-environment profile

A configured APIM profile selected through a validated mapping from an authoritative `accountEnv`.

### 2.5 APIM call classification

A typed Java declaration describing whether an outbound APIM operation is:

- non-account, or
- account-environment-specific

The final enum and constant names are not yet approved.

### 2.6 APIM credential resolution context

The target context contains only:

- `actorUserRole`
- `accountEnv`

Operation classification is separate from this dynamic context.

## 3. Approved Decisions

The following requirements are approved and may be treated as implementation facts.

### 3.1 Actor role domain

`actorUserRole` must become an enum instead of a free-form string.

The complete approved value set is:

```text
ROLE_INPUT
ROLE_APPROVE
ROLE_USER
ROLE_AGENT
ROLE_ITADMIN
ROLE_CCD
ROLE_SA
ROLE_BROKER
ROLE_CCC
ROLE_OPERATION
```

No additional enum member may be added without approval.

### 3.2 Temporary portal-access context

The outdated temporary portal-access-context implementation and configuration must be removed, including its demo and legacy configuration shapes.

Removal must not remove or bypass the real authenticated-session and selected-account mechanism used to construct `PortalAccessContext`.

### 3.3 Minimal credential resolution context

`ApimCredentialResolutionContext` must contain only:

```text
actorUserRole
accountEnv
```

Unnecessary identity, policy, member, operation, and metadata fields must not remain in that context.

Request correlation remains a transport and logging concern, not profile-selection input.

### 3.4 Classification ownership

APIM operation classification belongs in typed Java code.

YAML owns only infrastructure configuration, including profile definitions and account-environment mappings.

Runtime reflection over serialized payloads must not be the routing mechanism.

### 3.5 Confirmed pre-authentication account-specific operations

The following operations are confirmed account-environment-specific:

```text
TRPRegMember
TRPResetPassword
```

Before either call, Java has already obtained `accountEnv` from an earlier downstream response and stored it in protected auth-flow session state.

### 3.6 Fail-closed behavior

All of the following must stop the APIM call:

- missing required `accountEnv`
- blank required `accountEnv`
- unmapped `accountEnv`
- mapped profile not configured
- incomplete required profile credentials
- malformed profile configuration
- contradictory authoritative routing values
- unsupported or malformed actor role

No unresolved account-specific call may use the non-account profile.

### 3.7 Forbidden fallbacks

The final implementation must not use:

- configured default-profile fallback
- first-profile fallback
- legacy global OAuth or encryption fallback
- non-account fallback for an account-specific operation
- operation-name inference as a substitute for explicit classification

### 3.8 Logging

Every profile mapping or resolution failure must produce a structured `ERROR` log.

No mapping failure or resolution exception may be silently swallowed, converted to an empty publisher, or hidden behind an unsafe fallback.

Logs must not expose credentials, tokens, certificates, passwords, or unredacted sensitive request content.

### 3.9 Future extension

The resolver must remain open to future acting-on-behalf behavior involving non-member roles.

No routing behavior for those future roles is approved yet.

## 4. Verified Codebase Findings Requiring Revalidation

These findings came from the supplied codebase snapshot. The discovery stage must revalidate each item against the working repository before implementation.

### 4.1 Existing profile infrastructure

The codebase contains an APIM profile resolver abstraction, a credential resolution context, a default resolver, profile configuration, and an APIM WebClient facade.

The existing default resolver appears to select a configured default profile, fall back to the first configured profile when the default is absent, and construct a legacy profile when no configured profile list exists. The discovery stage must locate the exact source and tests and record the actual behavior without inference.

### 4.2 Existing resolution-context usage

Several APIM transports appear to construct the current credential resolution context with most routing fields set to `null`, leaving operation information and request metadata as the primary populated values.

Every constructor and factory call must be inventoried.

### 4.3 Temporary portal-access context artifacts

The supplied snapshot indicates the existence of artifacts equivalent to:

```text
TemporaryPortalAccessContextAdapter
TemporaryPortalAccessContextProperties
temporary-portal-access-context
```

The configuration includes demo sessions and historical string role values. Exact production wiring, active profiles, tests, and property registration must be verified before removal.

### 4.4 Protected pre-authentication contexts

The codebase contains protected auth-flow contexts for registration, login, and Forgot Password. The exact field layouts, encryption boundaries, serializers, Redis records, and migration effects must be inspected.

### 4.5 Registration operations

The current codebase appears to include:

```text
TRPSearchUnregMember
TRPChkCustId
TRPRegMember
```

`TRPSearchUnregMember` response items include environment and trust information. `TRPRegMember` serializes an APIM request property named `env`.

The discovery stage must prove where the selected registration `accountEnv` is persisted and how it reaches registration completion.

### 4.6 Forgot Password operations

The current codebase appears to include:

```text
TRPChkCustId
TRPSearchRegedMember
TRPResetPassword
```

`TRPSearchRegedMember` response data includes `env`. `TRPResetPassword` is confirmed account-specific even though its request payload may not serialize `env`.

The discovery stage must prove where the returned environment is currently stored or identify the exact missing persistence path.

### 4.7 Authenticated account-scoped operations

Several authenticated APIM requests appear to serialize `env`, including member information, messages, notification updates, reference date, and member-information updates.

Other authenticated account-scoped operations may rely on profile routing while omitting `env` from the APIM payload. Therefore, payload shape alone is not authoritative.

## 5. Explicitly Unresolved Decisions

No implementation prompt may decide these items without recorded approval or conclusive existing-contract evidence where the decision is purely technical and behavior-preserving.

### 5.1 Final Java-side non-account name

Candidates include:

```text
NON_ACCOUNT
nonAccount
```

The final type name, enum constant, profile ID, property name, and display name are unresolved.

### 5.2 Account environment to profile mapping shape

Unresolved alternatives include:

```text
accountEnv value equals profile ID
```

or:

```text
accountEnv maps explicitly to an independent profile ID
```

No YAML shape may be finalized before this decision is approved.

### 5.3 Profile-specific property scope

It is not yet approved whether the following vary by profile or remain global:

- APIM base URL
- OAuth token URI
- OAuth scope
- certificate path or certificate material
- encryption keys or related material
- retry configuration
- timeout configuration

The discovery stage must report current structure and actual usage.

### 5.4 Role migration compatibility

The target domain uses the approved enum. It is unresolved whether existing persisted sessions or configuration values such as `MEMBER` or lowercase role strings require temporary read compatibility, a migration, invalidation, or no support.

No compatibility alias may be added without approval.

### 5.5 Full operation classification

Only `TRPRegMember` and `TRPResetPassword` are presently confirmed as pre-authentication account-environment operations.

Other operations must be inventoried and proposed for approval. They must not be classified by assumption.

### 5.6 Future non-member routing

No role-specific routing policy is approved for:

```text
ROLE_INPUT
ROLE_APPROVE
ROLE_AGENT
ROLE_ITADMIN
ROLE_CCD
ROLE_SA
ROLE_BROKER
ROLE_CCC
ROLE_OPERATION
```

The implementation may preserve extension points but must not create behavior for these roles.

### 5.7 Username Recovery routing

The exact downstream operations and their database scopes require codebase verification. Forgot Password behavior must not be copied into Username Recovery without evidence.

## 6. Architectural Invariants

### 6.1 Separation of concerns

The implementation must keep these concepts separate:

```text
APIM operation classification
    describes the required database scope

APIM credential resolution context
    describes actorUserRole and accountEnv

APIM credential profile
    describes infrastructure credentials and profile-specific connection material
```

### 6.2 Authoritative `accountEnv`

For authenticated requests, the authoritative environment is obtained from the validated `PortalAccessContext` created from the authenticated session and selected `Account-Ref`.

For pre-authentication account-specific requests, the authoritative environment is obtained from protected auth-flow session state populated from an earlier validated downstream response or validated selection.

FE input must not become the sole authoritative source for account-specific routing.

### 6.3 Payload consistency

Where an account-specific APIM request serializes `env`, the implementation must enforce:

```text
request.env == resolutionContext.accountEnv == authoritative session/context accountEnv
```

A mismatch is a technical routing failure, must be logged at `ERROR`, and must prevent transmission.

### 6.4 Account-specific requests without `env`

The absence of an `env` request property does not imply a non-account call. `TRPResetPassword` is the confirmed example.

### 6.5 Response `env`

An `env` field in an APIM response does not prove that the current operation is account-specific. It may establish routing context for a later call.

### 6.6 Failure timing

Profile resolution and all routing validation must complete before:

- OAuth token acquisition for the outbound operation
- certificate retrieval specific to the outbound operation, where applicable
- APIM request transmission

### 6.7 Safe diagnostics

A routing failure log may include sanitized fields such as:

```text
event
operation
database scope
actor role
account environment
profile ID
failure reason
request ID
```

It must not include:

```text
client secret
API key
access token
password
private key
certificate contents
unredacted APIM payload
```

## 7. Planned Delivery Stages

The stage count may change only if discovery proves that safe boundaries differ from this map. Any change must be recorded in `handover.md` before implementation continues.

### Stage 001: Repository discovery and decision ledger

**Type:** Read-only investigation.

**Objectives:**

1. Locate exact profile resolver, context, property, facade, token, API-key, certificate, and WebClient classes.
2. Record exact fallback and exception behavior.
3. Inventory every `ApimCredentialResolutionContext` construction.
4. Inventory every outbound APIM operation and transport.
5. Audit request and response DTOs for serialized `env`.
6. Trace authoritative environment sources through registration and Forgot Password sessions.
7. Inventory actor-role types and string values across production, test, YAML, Redis records, and protected contexts.
8. Inventory temporary portal-access-context artifacts and active wiring.
9. Identify all unresolved decisions and contradictions.
10. Create or update `handover.md` with evidence and blockers.

**Production changes:** Forbidden.

**Completion condition:** A source-backed decision ledger exists and no finding is represented as fact without a file path and code reference.

### Stage 002: Actor role enum migration

**Prerequisites:**

- persistence and deserialization impact is known
- compatibility policy is approved
- all string-role usages are inventoried

**Objectives:**

1. Introduce the approved enum.
2. Replace domain and session string roles within approved scope.
3. Update serializers, protectors, mappers, configuration, and tests.
4. Reject unsupported values according to the approved migration policy.
5. Preserve authenticated-session behavior.

**Stop condition:** Existing persisted formats require an unapproved compatibility decision.

### Stage 003: Remove temporary portal-access context

**Prerequisites:**

- the active authenticated `PortalAccessContextPort` implementation is proven
- removal leaves exactly the intended production resolution path

**Objectives:**

1. Remove temporary adapter and properties.
2. Remove YAML and demo sessions.
3. Remove obsolete property registration and compatibility wiring.
4. Preserve authenticated session plus selected `Account-Ref` resolution.
5. Update tests and documentation.

**Stop condition:** Removal would leave missing or ambiguous production bean wiring.

### Stage 004: Strict APIM routing foundation

**Prerequisites:**

- final non-account naming is approved
- mapping shape is approved
- profile-specific property scope is approved

**Objectives:**

1. Introduce typed operation classification.
2. Reduce the credential resolution context to the approved two fields.
3. Implement strict profile and mapping validation.
4. remove default, first-profile, and legacy fallbacks.
5. implement sanitized structured `ERROR` logs.
6. ensure failure occurs before token acquisition and transmission.
7. add reusable request-`env` consistency validation.

**Stop condition:** Existing token, certificate, or WebClient architecture cannot preserve per-profile isolation without an unapproved redesign.

### Stage 005: Registration routing

**Prerequisites:**

- Stage 004 is complete
- the registration protected-context lifecycle is proven
- operation classifications used in this stage are approved

**Confirmed account-specific operation:**

```text
TRPRegMember
```

**Objectives:**

1. Persist or retain the authoritative environment in protected registration state.
2. Resolve the completion profile from that environment.
3. validate request `env` against protected state.
4. fail closed with `ERROR` logging on missing or inconsistent routing data.
5. preserve idempotency, replay, CSRF, OTP, and additional-trust behavior.

### Stage 006: Forgot Password routing

**Prerequisites:**

- Stage 004 is complete
- the exact `TRPSearchRegedMember` response-to-session path is proven
- operation classifications used in this stage are approved

**Confirmed account-specific operation:**

```text
TRPResetPassword
```

**Objectives:**

1. Capture the authoritative returned environment in protected Forgot Password state.
2. retain it through OTP verification and reset authorization.
3. resolve the reset profile from protected state.
4. fail before request transmission when routing data is missing or invalid.
5. preserve idempotency and unknown-outcome behavior.

### Stage 007: Login and authenticated routing

**Prerequisites:**

- login operation classifications are approved
- authenticated context resolution is stable
- Stage 004 is complete

**Objectives:**

1. Apply approved non-account classifications to pre-auth login operations.
2. use `PortalAccessContext.actor.actorUserRole` and `PortalAccessContext.account.accountEnv` for authenticated operations.
3. implement the approved current-member `ROLE_USER` policy.
4. preserve an extension boundary for future acting-on-behalf roles.
5. validate request `env` where serialized.
6. cover all approved authenticated APIM transports.

**Stop condition:** A transport's physical database scope cannot be proven or approved.

### Stage 008: Repository-wide audit, regression, and evidence

**Objectives:**

1. Audit every outbound APIM operation and scope declaration.
2. Audit every request DTO serializing `env`.
3. Audit every credential resolution context construction.
4. prove no string actor-role domain fields remain within approved scope.
5. prove temporary portal configuration is removed.
6. prove forbidden profile fallbacks are absent.
7. prove mapping failures are logged at `ERROR` and propagated.
8. run focused, cumulative, full, compile, and static checks.
9. update `handover.md` with final decisions, evidence, residual risks, and follow-up items.

## 8. GitHub Copilot CLI Prompt Rules

Every generated prompt must:

1. Read `implementation-map.md` and the latest `handover.md` first.
2. Inspect the cumulative repository state rather than assuming the initial snapshot.
3. List exact files and symbols before editing them.
4. Distinguish approved facts, verified repository facts, unresolved decisions, and derived invariants.
5. Avoid placeholders, guessed mappings, compatibility aliases, and speculative role behavior.
6. Stop with a blocker report when a prerequisite is unresolved.
7. Preserve unrelated behavior and explicitly describe preservation checks.
8. Use fail-first tests where production behavior changes.
9. State that the execution agent is GitHub Copilot CLI and pin the model explicitly with `--model gpt-5-mini` in every execution command.
10. Never reference or invoke Cline, `.cline` scripts, Cline prompt conventions, or Username Recovery task procedures in this APIM-profile workstream.
11. Run Maven synchronously in the foreground using the repository's supported Maven command and the Java/Maven environment recorded in `handover.md`; do not use background processes, PID files, polling loops, or newest-evidence-directory discovery.
12. Create a timestamped evidence directory under `target/` before validation.
13. Capture sequential evidence files for pre-state, fail-first tests, focused tests, cumulative regressions, full tests, compile, audits, diff checks, final status, diff statistics, name status, command exit codes, and preserved failure logs.
14. Update `handover.md` in the same stage.
15. Provide a stage-specific commit message.
16. Never commit automatically unless a later prompt explicitly authorizes it.

## 9. Required Discovery Deliverables

Stage 001 must produce at least the following artifacts or sections in `handover.md`.

### 9.1 Exact source inventory

For every relevant class:

```text
file path
class or record name
purpose
current dependencies
current bean wiring
current tests
change impact
```

### 9.2 APIM operation ledger

For every outbound APIM operation:

```text
operation name
path
transport class
request DTO
request contains env?
response contains env?
authoritative accountEnv source
current resolution context
candidate scope
scope evidence
approval status
```

Candidate scope is not an approved classification.

### 9.3 Role migration ledger

For every role-bearing field:

```text
file path
field type
serialized name
storage boundary
existing values
producer
consumer
migration risk
```

### 9.4 Profile configuration ledger

For every profile property:

```text
property path
global or profile-specific today
consumer
validation behavior
secret-handling behavior
fallback behavior
startup failure behavior
```

### 9.5 Session environment lineage

For registration and Forgot Password:

```text
downstream response field
mapping code
protected context field
session serializer field
reload path
outbound command field
APIM transport resolution context
```

### 9.6 Blocker ledger

Each blocker must state:

```text
blocker ID
question
evidence
affected stages
unsafe assumption that was avoided
required decision owner
```

## 10. Validation Strategy

### 10.1 Fail-first tests

Behavior-changing stages must first demonstrate failure for the intended invariant, including as applicable:

- unknown actor role rejected
- missing account environment rejected
- unmapped environment rejected
- missing mapped profile rejected
- incomplete credentials rejected
- forbidden default fallback rejected
- forbidden first-profile fallback rejected
- forbidden legacy fallback rejected
- request `env` mismatch rejected
- profile failure occurs before APIM transmission
- profile failure logged once at `ERROR`

### 10.2 Focused tests

Each stage must run the narrowest relevant unit and integration tests before broader regression suites.

### 10.3 Cumulative regression

Each implementation stage must include tests from all prior completed stages.

### 10.4 Full validation

The final audit stage must run:

- focused feature tests
- cumulative authentication workflow tests
- APIM adapter and transport tests
- authenticated-session tests
- full test suite
- compile or package check as supported by the repository
- static source audits
- configuration binding tests
- serialization round-trip tests
- log-capture tests for required `ERROR` behavior

## 11. Required Static Audits

The final evidence must prove the intended absence or controlled presence of:

```text
TemporaryPortalAccessContext
temporary-portal-access-context
default-profile fallback
first profile fallback
legacy APIM credential fallback
String actorUserRole
role_user
MEMBER as an actor-role domain value
silent onErrorResume around profile resolution
Mono.empty from required profile resolution
unclassified outbound APIM transports
ACCOUNT_ENV operations without an authoritative accountEnv source
```

Audit expressions must account for legitimate documentation, migration tests, or error messages and must not rely on naive zero-match checks when controlled references are required.

## 12. Error and Public Contract Policy

Profile resolution and mapping failures are technical failures.

The implementation must:

1. log a sanitized structured event at `ERROR`;
2. prevent token acquisition and outbound APIM transmission;
3. propagate through the existing application boundary;
4. map to the approved public `SYSTEM_ERROR` behavior;
5. never expose internal profile IDs, credential details, database topology, or raw exception messages to FE.

Feature-specific public error codes are not approved by this map. Existing canonical system-error contracts must be preserved unless a later decision explicitly changes them.

## 13. Security and Privacy Constraints

- Never log client secrets, API keys, tokens, passwords, private keys, or certificate content.
- Never include secrets in evidence files.
- Redact or omit sensitive request and response payloads.
- Preserve one-time captcha, CSRF, idempotency, OTP, origin/referer, session protection, and unknown-outcome controls.
- Do not allow FE to choose a profile ID directly.
- Do not trust FE-provided `accountEnv` without validating it against authoritative protected or authenticated state.
- Do not infer a profile from user-controlled operation names, headers, or arbitrary metadata.

## 14. Change-Control Rules

This map must be updated before implementation proceeds when:

- an approved decision changes;
- discovery disproves a stated codebase finding;
- a stage boundary changes;
- a new blocker is discovered;
- a new APIM operation enters scope;
- a persistence migration becomes necessary;
- the final non-account naming or mapping shape is approved.

Changes must preserve a short decision history in `handover.md`.

## 15. Immediate Next Action

Generate and execute the Stage 001 GitHub Copilot CLI discovery prompt with `--model gpt-5-mini`.

Stage 001 is read-only except for documentation and evidence artifacts. It must not change production code, production configuration, or tests.

## 16. Username Recovery integration boundary:

The current APIM-profile worktree must not inspect or modify the separate
Username Recovery worktree.

The common APIM routing foundation must require every outbound APIM
operation to declare its database scope explicitly.

When Username Recovery is ready to merge, its implementation must provide
an operation-routing manifest and adopt the common resolver contract.
Any unclassified Username Recovery APIM transport must fail validation
rather than inherit the non-account profile.