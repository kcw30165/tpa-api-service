# APIM Architecture and Contracts

> **Status: Superseded**
>
> This document has been replaced by [`apim-exchange-executor-architecture-and-developer-guide.md`](apim-exchange-executor-architecture-and-developer-guide.md), which is the authoritative reference for APIM execution architecture, routing, credentials, retry classifications, operation contracts, testing, and extension guidance.

## Authoritative Architecture

Use the replacement guide for:

- `ApimExchangeExecutor` and `ApimExchangeRequest` contracts;
- `ACCOUNT_ENV` and `NON_ACCOUNT` routing;
- actor-role and account-environment authority;
- credential-profile, token, certificate, and crypto ownership;
- retry classifications and mutation unknown outcomes;
- operation-specific envelope and error mapping;
- the final 15-operation routing ledger;
- architecture guards and review requirements;
- guidance for adding a future APIM operation.

## Important Compatibility Notice

The following historical patterns are no longer approved:

- feature adapters directly using `ApimWebClientFacade`;
- feature adapters directly using certificate, crypto, token, or credential-profile services;
- feature-owned plain-versus-encrypted execution branches;
- operation transports that bypass `ApimExchangeExecutor`;
- production test transports or alternate test-only constructors;
- adapter-level role substitution;
- database-scope inference from nullable `accountEnv`;
- response-derived routing of the same APIM call.

All governed operations must use one canonical typed executor-backed production path.

## Companion Runbook

For runtime configuration, troubleshooting, observability, test inventories, release evidence, and production-readiness checks, see:

[`operations-and-testing.md`](operations-and-testing.md)
