# APIM Integration and Reuse Guide

> **Status: Superseded**
>
> This guide has been replaced by [`apim-exchange-executor-architecture-and-developer-guide.md`](apim-exchange-executor-architecture-and-developer-guide.md).

## Adding or Changing an APIM Operation

Follow the replacement guide sections covering:

1. current-flow and caller inventory;
2. operation path and DTO confirmation;
3. database-scope classification;
4. actor-role and account-environment authority;
5. retry and unknown-outcome classification;
6. focused outbound command design;
7. typed `ApimExchangeRequest` construction;
8. `ApimExchangeExecutor.postEnvelope(...)` execution;
9. operation-specific envelope and public-result mapping;
10. genuine fail-first and preservation evidence;
11. routing-ledger and architecture-guard updates;
12. focused and full validation requirements.

## Mandatory Integration Rule

The approved flow is:

```text
application use case
    -> focused outbound command
    -> application outbound port
    -> operation adapter or approved business-neutral shared transport
    -> typed ApimExchangeRequest
    -> ApimExchangeExecutor.postEnvelope(...)
    -> operation-specific result mapping
```

Do not add a feature-owned:

- `ApimWebClientFacade` dependency;
- token, certificate, crypto, or credential-profile dependency;
- manual plain-versus-encrypted branch;
- hardcoded role or inferred database scope;
- default account-environment fallback;
- production test transport or alternate test constructor;
- duplicate Spring execution path.

## Operational Companion

For environment configuration, troubleshooting, logging, test coverage, release evidence, and production readiness, see:

[`operations-and-testing.md`](operations-and-testing.md)
