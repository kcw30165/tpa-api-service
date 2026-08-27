# Account Balance Master Design

**Feature:** 6.14 Account Balance  
**Baseline:** 27/08/2026  
**Status:** Consolidated design baseline, pending repository investigation  

## Logical flow

```text
FE request
  -> shared header validation
  -> Account-Ref context resolution
  -> ReferenceDate resolution
  -> shared general-metadata resolver
       -> Redis first
       -> TRPGetGeneralMetaData on unresolved scope/item
  -> TRPGetMemberAccBal
  -> operation-content validation
  -> joins and aggregation
  -> localization and display-scale rendering
  -> generic FE response
```

## Reactive boundary

`ReferenceDatePort` returns `Mono<LocalDate>`. The Account Balance use case must compose context, metadata, and APIM calls without `block()`.

## APIM architecture

Both operations use the existing canonical APIM executor and generic envelope handling. The feature must not create its own OAuth, certificate, encryption, retry, or header-propagation implementation.

## Data call count

On metadata cache hit: one Account Balance APIM call. On unresolved metadata: one metadata APIM call plus one Account Balance APIM call. Targeted metadata reload is used only when a required item remains missing.

## No startup metadata call

There is no `PortalAccessContext` at application startup. Population is lazy on the first authenticated dashboard or feature request and shared through Redis.
