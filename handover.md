# Shared Auth-Flow Revision CAS Handover

## Permanent shared fix

- Shared replace and delete operations compare `sessionRevision`, not regenerated JSON.
- Replacement preserves remaining TTL.
- Legacy initialization retains whole-document proof because legacy records have no revision token.
- Temporary CAS diagnostics are removed.

## Repair 007

The focused Cancel policy test proved the production policy emits source `BFF` for `REVISION_CONFLICT`. Repair 006 retained the incorrect test expectation `SERVER`. Repair 007 changes only the expected source to `BFF`, while preserving `VALIDATION_FAILED`, `RETURN_TO_LOGIN`, `AUTH_FLOW_INVALID`, and HTTP 409. The conflict is still no longer mislabeled as idempotency-key reuse.

## APIM Routing and Request Context Modernization

### Prompt 00: Investigation and program orientation

- Working branch: `feature/apim-routing-context-modernization`.
- Program purpose: remove repeated APIM profile/certificate/encryption/recovery/retry orchestration and replace hidden authenticated request-context access with explicit inbound-to-application-to-outbound contracts.
- Fixed context invariant: authenticated session and Account-Ref resolve once at the inbound web boundary; Account-Ref is opaque; accountEnv and account metadata come only from the matched session AccountContext; application services and outbound adapters do not resolve ambient portal context.
- Fixed routing invariant: database scope and actor role are caller supplied; ACCOUNT_ENV requires authoritative accountEnv; scope is never inferred from blank accountEnv; generic execution must not hardcode NON_ACCOUNT or ROLE_USER_MBR.
- Fixed execution invariant: one profile per logical call; profile owns URL/token/API key/certificate/business call; certificate refresh regenerates encrypted payload; response verification uses the successful attempt key; retry eligibility and business-envelope/failure interpretation remain operation-specific.
- Confirmed context finding: `PortalAccessContextWebFilter` and `AuthenticatedAccountContextResolver` already resolve session-scoped selected-account context, but six application services still inject `CurrentPortalAccessContextResolver`: notifications get/update, contribution get/export, and personal-information get/update.
- Confirmed outbound boundary finding: `TrpSearchMemberAdapter` still accesses Reactor context.
- Confirmed APIM finding: most feature adapters own encryption branches, profile resolution, direct certificate access, encryption, post, and decryption. Selected shared transports use `postEncrypted`, but compatibility paths remain.
- Confirmed key-consistency defect: `ApimWebClientFacade` regenerates encrypted payload after `invalid_public_key` only when an encryption function is attached; legacy resolved-profile paths can retry stale ciphertext and decrypt with a key captured before the successful attempt.
- Routing ledger summary: 15 APIM operations were identified. Notifications retrieval infers scope from blank accountEnv; notification update uses ACCOUNT_ENV but hardcodes role; country-list currently uses NON_ACCOUNT despite the agreed authenticated ACCOUNT_ENV target; Forgot Password completion and TRPChkCustId retain mixed routing paths.
- Duplication clusters: ordinary facade calls; feature-owned encrypted execution; facade-owned `postEncrypted`; shared envelope/reflection conversion; and operation-specific failure policies that must remain outside the generic executor.
- Stashes: Username Recovery emergency backup is superseded duplicated orchestration; broad dev WIP and Forgot Password Redis/CAS WIP are unrelated; the older ContributionSummary clean-architecture stash is a routing/context assumption requiring review. No stash was applied.
- Stage 01 recommendation: characterization only, covering routing ledger, profile resolution count, token/certificate recovery, encrypted request regeneration, successful-attempt key consistency, no routing fallback, retry classification, encryption-field selection, and ambient-context dependency inventory.
- Architect questions: explicit pre-auth actor role; unconfirmed operation scopes; mutation retry safety; country-list HTTP authentication cutover; and expected-failing treatment of legacy stale-payload behavior.
- Prompt 00 made documentation changes only. No production or test implementation was authorized or performed. Stage 01 was not started.
- Prompt 00 final status: `READY_FOR_ARCHITECT_REVIEW`.
- Suggested commit message after architect approval: `docs(architecture): document APIM routing modernization baseline`.
