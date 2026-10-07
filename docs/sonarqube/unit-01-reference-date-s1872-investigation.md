# Unit 01: Reference Date `java:S1872` Investigation and Validation

## Scope

This document records the investigation, approved implementation, and validation evidence for Implementation Unit 1 only. Unit 1 covers the four `java:S1872` findings in:

- `src/test/java/com/bct/ngtpa/apiservice/application/usecase/ReferenceDateUseCaseBoundaryTest.java`
- `src/test/java/com/bct/ngtpa/apiservice/application/usecase/RefreshReferenceDateServiceTest.java`

Production code, configuration, build files, and Units 2 through 7 were excluded.

## SonarQube findings

- `874ae16c-d1c7-482e-b4cc-207bce163927`
- `0b63b37e-07c2-448c-b79e-842e2e495a04`
- `cb468a7d-9667-4c0b-8bb9-dd52f7c3ee67`
- `7512930a-a155-4e3a-b292-f3c55042d969`

All four findings were caused by class-name string comparisons in architecture-boundary tests.

## Investigation evidence

Git history established that:

- Commit `75784788` introduced `ReferenceDateConfigPort` and the related configuration-service flow.
- Commit `de55e83b` intentionally removed `ReferenceDateConfigPort` and its adapters from the Reference Date runtime flow.
- `ConfigServicePort` still exists in the current compile classpath.
- `RefreshReferenceDateService` no longer depends on Config Service ports. Its current runtime dependencies remain `ApimReferenceDateRefreshPort` and `ReferenceDateCacheUpdatePort`.

The two `ReferenceDateConfigPort` assertions were therefore stale guards against a type that had been intentionally removed. Replacing those assertions with reflection would have created a no-op path and would not have added a meaningful architecture check.

## Approved implementation

The implementation changed only the two authorized test files:

1. Existing `ConfigServicePort` checks now use direct class identity:

```java
field.getType() == ConfigServicePort.class
```

2. The obsolete `ReferenceDateConfigPort` name-based assertions were removed.

3. Existing checks for current approved and forbidden ports remain intact.

The implementation did not:

- add a compile-time dependency on `ReferenceDateConfigPort`;
- use `Class.forName(...)`;
- add an empty `ClassNotFoundException` catch;
- add a SonarQube suppression;
- add a Java comment containing a Git commit hash;
- modify production code.

## Behavior preserved

The tests continue to enforce the current Reference Date architecture boundaries:

- approved input and output ports remain present;
- `ConfigServicePort` remains prohibited as a dependency;
- unrelated generic ports remain prohibited;
- `RefreshReferenceDateService` constructor and field boundaries remain covered;
- functional refresh behavior tests remain unchanged.

## Validation evidence

The following commands completed successfully:

```bash
mvn -DskipTests compile
mvn -DskipTests test-compile
mvn -Dtest=ReferenceDateUseCaseBoundaryTest,RefreshReferenceDateServiceTest test
```

Targeted-test result:

```text
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
```

`git diff --check` also reported no whitespace errors.

## Decision

**VALIDATED**

The Unit 1 implementation is ready for independent pre-commit review. Unit 1 is not yet marked `COMMITTED`, and no commit hash is recorded until the commit exists.

## Proposed commit message

```text
test(reference-date): replace name-based type checks
```
