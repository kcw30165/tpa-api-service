# Account Balance Configuration and Observability

**Feature:** 6.14 Account Balance  
**Baseline:** 27/08/2026  
**Status:** Consolidated design baseline, pending repository investigation  

## Configuration

```yaml
account-balance:
  date-format: dd/MM/yyyy

redis-cache:
  general-metadata:
    ttl: PT20M
```

APIM paths remain runtime configuration and startup-validated through existing integration practice.

## Safe telemetry

Safe: X-Request-Id, operation, phase, duration, cache hit/miss, normalized failure category, validated collection counts, and display-option booleans.

Forbidden: Account-Ref, session ID, actor ID, policy number, certificate number, raw APIM bodies, financial values, metadata payloads, and full factsheet URLs.

Recommended events:

```text
account-balance.request.started
account-balance.reference-date.failed
account-balance.metadata.cache.hit
account-balance.metadata.cache.miss
account-balance.metadata.failed
account-balance.data.failed
account-balance.contract.invalid
account-balance.request.completed
```
