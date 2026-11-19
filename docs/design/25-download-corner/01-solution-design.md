# 01 — Solution Design

## 1. Scope

Download Corner is the post-login document-list page described by BRD 6.9, feature version V0.3. The BFF retrieves the categories and bilingual document metadata for the selected account and returns one language-specific list.

**In scope:** `GET /api/v1/download-corner`; authenticated session and selected `Account-Ref`; one `TRPGetDownloadCorner` read through the shared APIM exchange boundary; Chinese per-field English fallback; blank-link filtering; stable sequence ordering; empty-category preservation; the E2 DS field validation rules; standard response and retrieval-unavailable handling; a read-only post-login E2E pack that leaves the shared selector unchanged.

**Frontend-owned:** search, page wording, “Important Notes,” and empty-state text. Java returns data and does not implement frontend search, labels or page copy.

**Out of scope:** BRD security-setting changes, remote file-existence checks, link validation or rewriting, caching, Redis writes, mutations, and any Vesting Scale change.

The full BRD 6.9 artifact was not found in the repository evidence. The scope and behavior in this pack are based on the V0.3 feature decisions supplied for this task; see [open items and follow-ups](08-decision-log.md#open-items-and-follow-ups).

## 2. Ownership

| Concern | Owner |
|---|---|
| Document metadata, bilingual values, links and sequence values | DS |
| Resolving selected account and actor from the authenticated session and `Account-Ref` | Existing BFF session/context flow |
| Selecting one presentation language, Chinese fallback, blank-link filtering, ordering, and BFF response shape | Java BFF |
| Search, page wording, “Important Notes,” and empty-state text | FE |
| File availability and any attempt to fetch a remote file | Not part of this feature |

## 3. Component chain

```text
GET controller
  -> GetDownloadCornerUseCase
  -> GetDownloadCornerService
  -> DownloadCornerPort
  -> ApimDownloadCornerAdapter
  -> ApimExchangeExecutor.postEnvelope(...)
  -> TRPGetDownloadCorner
```

The proposed application and adapter packages are feature-colocated as `downloadcorner`, following the corresponding `vestingscale` layers. APIM request/response DTOs remain under `adapter/out/apim`; public response DTOs and language-specific presentation rules remain under `adapter/in/web`.

Vesting Scale’s controller uses `/api/v1` plus a feature GET mapping, resolves the current `PortalAccessContext`, checks the selected account, resolves language, and wraps the mapper result in `MutationResponse` (`VestingScaleController.java:33-35,54-85,90-107`). Its adapter sends the typed exchange through `ApimExchangeExecutor` with `ACCOUNT_ENV`, `SAFE_BUSINESS_CALL`, recovery policy, and terminal-caller incident ownership (`ApimVestingScaleAdapter.java:72-103`). Download Corner follows these boundaries without changing shared authorization.

## 4. Request context and authorization

- Require the authenticated session and a selected `Account-Ref`, as for other post-login selected-account reads.
- Get `policyNo`, `certNo`, and `accountEnv` from the account already resolved for that Account-Ref; get `actorUserId` and `actorUserRole` from its actor context.
- Do not resolve an account manually from raw headers in the controller, require Account-Ref globally, or add account identifiers to query/body parameters.
- Keep the shared APIM credential-profile allow-list and all existing post-login authorization unchanged. The shared resolver currently accepts `ROLE_USER_MBR` and `ROLE_INPUT`, and selects an `ACCOUNT_ENV` profile from `accountEnv` (`DefaultApimCredentialProfileResolver.java:27-56`).
- No feature-local authorization rule is added.

## 5. Layering of rules

| Rule | Layer |
|---|---|
| APIM typed request/response mapping; envelope, `err-message`, cardinality and malformed-field checks; failure event | APIM adapter |
| Carrying selected account/actor data to the outbound port | Controller/use case and application command |
| Selecting English or Chinese; per-field fallback; drop only blank resolved links; stable sort; preserve empty categories; construct public DTOs | Web mapper |
| Session, Account-Ref and shared authorization behavior | Existing request-context/security infrastructure |

No runtime or feature data is cached. The endpoint performs no Redis writes.
