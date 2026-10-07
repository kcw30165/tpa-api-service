# Unit 04 — ApimGeneralMetadataSourceAdapter (java:S3655) Investigation

Date: 2026-10-06T11:52:05.569+08:00

Scope: investigate the java:S3655 finding in
src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/metadata/ApimGeneralMetadataSourceAdapter.java

Goal: produce an evidence-backed, smallest behavior-preserving correction that removes the Sonar-flagged repeated Optional access while keeping all semantics and tests unchanged.

---

1) Exact Sonar issue key, file, method, and flagged expression

- Sonar issue key (authoritative from plan): c618c372-a6ed-4fe5-b418-ed50192b5c71 (java:S3655)
- File: src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/metadata/ApimGeneralMetadataSourceAdapter.java
- Method: private FetchGeneralMetadataResult combine(List<FetchGeneralMetadataResult> results)
- Flagged expression (lines 96–102): repeated calls to result.resolutionSource():

```java
if (source.isPresent()
    && result.resolutionSource().isPresent()
    && source.get() != result.resolutionSource().get()) {
  throw new IllegalArgumentException("Conflicting general metadata resolution sources");
}
if (source.isEmpty() && result.resolutionSource().isPresent())
  source = result.resolutionSource();
```

Sonar flags repeated Optional access patterns (call to result.resolutionSource().isPresent() and then get()), recommending capturing the Optional to a local variable and reusing it.

2) Complete method and surrounding metadata-combination logic (summary)

- combine(List<FetchGeneralMetadataResult> results) iterates each result and:
  - tracks a single Optional<GeneralMetadataResolutionSource> source (initially Optional.empty()) that represents the accumulated resolution source for all merged results;
  - for each result, if both the accumulated source and the current result's resolutionSource() are present and their contained GeneralMetadataResolutionSource differ, it throws IllegalArgumentException("Conflicting general metadata resolution sources");
  - if the accumulated source is empty and result.resolutionSource() is present, it assigns source = result.resolutionSource();
  - then merges result.entries() into a LinkedHashMap, detecting conflicting values for identical keys.
  - returns new FetchGeneralMetadataResult(source, entriesList).

3) Are repeated result.resolutionSource() calls present between isPresent() / get() / comparison?

- Yes. In the flagged block the code calls result.resolutionSource() up to two times within the same expression and again immediately after for assignment. Concretely, for each iteration:
  - Before implementation, result.resolutionSource() could appear up to four times per loop iteration (subject to short-circuit evaluation). Optional.get() was used once — in the conflicting-source comparison.
  - This is exactly the pattern Sonar reports.

4) Inspect the actual result type and the resolutionSource() accessor semantics

- FetchGeneralMetadataResult is a Java record:

```java
public record FetchGeneralMetadataResult(
    Optional<GeneralMetadataResolutionSource> resolutionSource,
    List<ResolvedGeneralMetadata> entries) { ... }
```

- resolutionSource is a record component; its accessor method resolutionSource() simply returns the record field (the Optional instance) and is not dynamically computed. The record's canonical constructor validates invariants but does not change behavior of the accessor.
- GeneralMetadataResolutionSource is an enum (DEFAULT_ALL, POLICY_SPECIFIC) and is stable; identity (`!=`) comparison is appropriate.

Conclusion: result.resolutionSource() is a stable, cheap accessor that returns the same Optional instance for the lifetime of that result object.

5) Inspect every use of result.resolutionSource() in this class

- All uses in this class are inside combine() at the lines above. Before implementation, result.resolutionSource() appeared up to four times per loop iteration; Optional.get() was used once in the conflicting-source comparison.

6) Inspect existing tests covering resolution-source scenarios

- src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/metadata/ApimGeneralMetadataSourceAdapterTest.java contains tests that exercise:
  - Normal single-key targeted fetch -> non-empty entries and single resolved source (oneKeyProducesOneTargetedAuthenticatedSafeExchange).
  - Multiple targeted keys combined deterministically (multipleKeysAreTargetedInDeterministicOrderAndCombined).
  - Empty keys -> unfiltered request (emptyKeysUseOneExplicitUnfilteredRequest).
  - Conflicting sources and technical failures preserved (conflictingSourcesAndTechnicalFailuresArePreserved) — constructs an executor that returns rows whose policy differs across calls and asserts IllegalArgumentException with message "Conflicting general metadata resolution sources".
  - Malformed operation content and conflicting values tests exercising other failure modes.

Conclusion: tests exist that validate the conflict detection and merging behavior covered by combine(). The test conflictingSourcesAndTechnicalFailuresArePreserved directly asserts the exception thrown when two results have different resolution sources.

7) Git history for the flagged block (intent semantics)

- Blame/line history shows combine() and the conflict-detection block are long-standing and that the semantics are: if two result fragments report different resolution sources, fail-fast with IllegalArgumentException("Conflicting general metadata resolution sources"). No historical commit indicates an intention for `resolutionSource()` to be dynamic or for any fallback behavior.

8) Smallest behavior-preserving correction

- Capture the Optional result.resolutionSource() once into a local variable at the top of the loop iteration, then use that local variable for the isPresent/get/comparison/assignment. Example minimal change (in pseudo-diff):

```java
for (var result : results) {
  Optional<GeneralMetadataResolutionSource> resultSource = result.resolutionSource();
  if (source.isPresent() && resultSource.isPresent() && source.get() != resultSource.get()) {
    throw new IllegalArgumentException("Conflicting general metadata resolution sources");
  }
  if (source.isEmpty() && resultSource.isPresent()) source = resultSource;
  // merge entries as before
}
```

Rationale:
- Preserves the exact existing conflict-detection semantics and ordering.
- Uses the same identity comparison against enum values.
- Eliminates repeated accessor calls that Sonar flags, closing java:S3655.
- Introduces no functional change, no fallback, and no new compile-time dependencies.

9) Do not invent fallback or default behavior

- The proposed correction does not introduce any fallback/default. It only captures the existing Optional once per loop iteration and relies on the existing branch logic. It preserves the fail-fast behavior when conflicting sources are detected.

10) Exact smallest implementation file set

- Single file: src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/metadata/ApimGeneralMetadataSourceAdapter.java
  - Only method combine(List<FetchGeneralMetadataResult> results) needs the minimal edit described above (introduce local Optional resultSource variable and replace repeated calls). No other source, tests, properties, or YAML files need changes.

11) Are existing tests sufficient or is a focused regression test required?

- Existing tests already exercise the key scenarios:
  - conflictingSourcesAndTechnicalFailuresArePreserved triggers the conflicting-source exception.
  - oneKeyProducesOneTargetedAuthenticatedSafeExchange and multipleKeys... exercise normal single-source and multi-key merges.
- Because the proposed change is strictly a local refactor (capturing a local variable) and preserves behavior, no new regression test is strictly required.
- Recommendation: rely on current ApimGeneralMetadataSourceAdapterTest suite as the targeted test when validating the edit. If conservative confirmation is desired, add a tiny focused test that emulates the exact conflict case using two FetchGeneralMetadataResult instances with differing resolutionSource() values and asserts the IllegalArgumentException — however the existing conflictingSourcesAndTechnicalFailuresArePreserved test already covers this.

12) Compile and test-compile gates (do NOT run in investigation)

- mvn -DskipTests compile
- mvn -DskipTests test-compile

13) Exact targeted-test commands

- mvn -Dtest=ApimGeneralMetadataSourceAdapterTest test
- Optionally run the broader adapter tests that may exercise normalization and merge behavior:
  - mvn -Dtest=ApimResponseValidationBoundaryTest,ApimRoutingLedgerCharacterizationTest test

14) Risks, blocked conditions, preserved behavior, review checkpoints

- Risks: none material. The accessor is stable and the change is local and purely mechanical. The only risk would be if some unseen code relied on side effects of multiple resolutionSource() calls — but resolutionSource() is a record accessor with no side effects.
- Blocked conditions: none identified. Implementation may proceed.
- Preserved behavior: exact conflict-detection semantics, exception messages, entry merging and ordering, and the final returned FetchGeneralMetadataResult are unchanged.
- Review checkpoints prior to commit:
  1. Show the one-file exact diff (combine method only).
  2. Run mvn -DskipTests compile and mvn -DskipTests test-compile.
  3. Run mvn -Dtest=ApimGeneralMetadataSourceAdapterTest test and confirm no regression.

15) Provisional conventional commit message

- `fix(apim-metadata): capture resolution source once`

16) Decision and Validation

- Status: VALIDATED

Implemented correction (exact):

```java
// At the start of each loop iteration inside combine():
Optional<GeneralMetadataResolutionSource> resultSource = result.resolutionSource();
// Subsequent references in that iteration use resultSource instead of calling
// result.resolutionSource() repeatedly.
```

Preserved behavior: no fallback/default source, no exception-message change, no merge-ordering change, and no change to entry merging logic. The change only captures the existing Optional into a local variable and reuses it.

Validation evidence (exact):

- mvn -DskipTests compile: SUCCESS
- mvn -DskipTests test-compile: SUCCESS
- mvn -Dtest=ApimGeneralMetadataSourceAdapterTest test: SUCCESS
  - Tests run: 6
  - Failures: 0
  - Errors: 0
  - Skipped: 0
- git diff --check: SUCCESS

Proposed commit message (approved):

- `fix(apim-metadata): capture resolution source once`

Commit hash: (leave empty until commit)

---

Investigation author: Copilot CLI

Status: VALIDATED
