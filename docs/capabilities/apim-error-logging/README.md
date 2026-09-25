## APIM Error Logging Capability

**Status:** Draft — pending review before implementation.
**Scope:** Log-only observability for APIM outbound exchange outcomes (network/transport failures, non-empty `err-message`, malformed response schema), applied uniformly across all governed APIM operations.
**Audience:** Developers, reviewers, support engineers.

### Start Here

Read [architecture-and-design.md](architecture-and-design.md) for:
- the confirmed single integration point (`DefaultApimExchangeExecutor.postEnvelope`);
- the exact malformed-schema definition and worked examples;
- the three logging outcomes, event names, and levels;
- the new pure schema-classifier contract;
- explicit in-scope / out-of-scope boundaries;
- known related defect (tracked, not fixed here);
- coverage tiers and test strategy;
- validation commands.

### Relationship to Other APIM Documentation

This capability is a thin, additive observability layer on top of the existing [APIM Exchange Executor Architecture and Developer Guide](../../integration/apim/apim-exchange-executor-architecture-and-developer-guide.md). It does not change routing, retry, credential, or business-envelope-interpretation ownership described there.

### Relationship to Future Work

- **Project-wide centralized logging** (unifying `ExecutionLoggingAspect`, `RequestLoggingWebFilter`, `ApiExceptionHandler`, and ad hoc APIM logging into one mechanism) is a known future direction, intentionally deferred to the planned entire-project error-mechanism refactor. This capability is scoped strictly to the APIM outbound boundary and must not be expanded to project-wide logging without a separate, explicit decision.
- **Migrating the six pre-existing `ApimResponseValidator`-based adapters** (Contribution Summary, Member Info, Notice Message, Notification Read-Status, Reference Data Countries, Update Personal Information) to the newer per-operation envelope-interpretation style is explicitly out of scope for this capability.
- **Removing the incorrect unconditional `throw` on non-empty `err-message`** inside `ApimResponseValidator.requireSuccessData` is a confirmed defect, tracked here as a known issue, and intentionally not fixed as part of this capability (see architecture-and-design.md §7).
- **The three event name string literals** used by `ApimExchangeOutcomeLogger` (`apim.contract.invalid`, `apim.business.rejected`, `apim.exchange.failed`) are intentionally hardcoded, matching the codebase's existing convention for lightweight structured log tags (e.g. `RequestLoggingWebFilter`, `ApimRequestLoggingExchangeFilter`). Consolidating these into a shared, typed vocabulary (mirroring the `WorkflowVocabulary`-style enum precedent already used elsewhere for higher-stakes contracts) is a reasonable follow-up, but is deliberately deferred to the planned project-wide centralized logging / error-mechanism refactor rather than addressed here, to avoid introducing a local pattern that effort would likely replace.
