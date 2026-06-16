# Handover — 0020 PortalAccessContext Per-Request Migration

## Status

Completed through subtask `0020-015`.

This migration establishes the final responsibility split for selected-account context handling:

```text
Request
  -> RequestLoggingWebFilter / request header context
  -> PortalAccessContextWebFilter
       - reads inbound Account-Ref/session source
       - calls PortalAccessContextPort once
       - stores PortalAccessContext into exchange attributes and Reactor Context
  -> Controllers / use cases
       - do not re-resolve Account-Ref
       - use CurrentPortalAccessContextProvider.current() only when selected-account data is needed
```

## Final Responsibility Split

### PortalAccessContextPort

`PortalAccessContextPort` is the external context-resolution port.

It should be used by `PortalAccessContextWebFilter` only.

Current temporary source may be YAML/config-backed. Future source may be Redis/session-backed, e.g. `session-id + Account-Ref -> selected PortalAccessContext`.

### PortalAccessContextWebFilter

`PortalAccessContextWebFilter` resolves selected-account context once per request and stores it in:

- `PortalAccessContextKeys.ATTRIBUTE_KEY`
- `PortalAccessContextKeys.CONTEXT_KEY`

### CurrentPortalAccessContextProvider

`CurrentPortalAccessContextProvider` is a current request-context accessor.

It reads the already-resolved `PortalAccessContext` from Reactor Context. It must not call external config/Redis/APIM resolution.

Method names remain:

```java
Mono<PortalAccessContext> current();
Mono<PortalAccessContext> currentOrEmpty();
```

## Completed API Boundary Changes

Selected-account commands no longer carry frontend `Account-Ref` as the use-case command boundary:

- `GetPersonalInformationCommand`
- `UpdatePersonalInformationCommand`
- `GetNotificationsCommand`
- `UpdateNotificationsReadStatusCommand`
- `GetContributionSummaryCommand`
- `ExportContributionSummaryCommand`

`GetReferenceDataCountriesCommand` is language-only and context-free because country list is currently a global reference-data lookup.

## Selected-Account Use Cases

These use cases now read current context through `CurrentPortalAccessContextProvider.current()`:

- `GetPersonalInformationService`
- `UpdatePersonalInformationService`
- `GetNotificationsService`
- `UpdateNotificationsReadStatusService`
- `GetContributionSummaryService`
- `ExportContributionSummaryService`

## Reference Data Countries

Reference-data countries remains context-free:

```text
ReferenceDataController
  -> GetReferenceDataCountriesCommand(language)
  -> GetReferenceDataCountriesService
  -> ApimReferenceDataCountriesPort.fetchCountryList()
```

No `PortalAccessContextPort`, no `CurrentPortalAccessContextProvider`, and no selected Account-Ref validation should be added unless the APIM contract becomes selected-account scoped later.

## Guard Tests Added

Key guard tests now protect the architecture:

- `PortalAccessContextResponsibilityBoundaryGuardTest`
- `PortalAccessContextFinalMigrationEvidenceTest`
- `ContributionCurrentContextBoundaryGuardTest`
- `NotificationCurrentContextBoundaryGuardTest`
- `UpdatePersonalInformationCurrentContextBoundaryGuardTest`
- `GetReferenceDataCountriesCommandBoundaryGuardTest`

## Recommended Regression Commands

Focused final guard command:

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21" && export M2_HOME="/d/Tools/apache-maven-3.9.15" && export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH" && mvn -Dtest=PortalAccessContextFinalMigrationEvidenceTest,PortalAccessContextResponsibilityBoundaryGuardTest,PortalAccessContextArchitectureGuardTest,ContributionCurrentContextBoundaryGuardTest,NotificationCurrentContextBoundaryGuardTest,UpdatePersonalInformationCurrentContextBoundaryGuardTest,GetReferenceDataCountriesCommandBoundaryGuardTest test
```

Broader regression command:

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21" && export M2_HOME="/d/Tools/apache-maven-3.9.15" && export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH" && mvn test
```

## Commit Message

```text
test(context): add final portal context migration evidence
```
