# Unit 05 — WorkflowKeyValidator (java:S5998) Investigation

Date: 2026-10-06T12:48:57.170+08:00

Scope: investigate the java:S5998 finding in
src/main/java/com/bct/ngtpa/apiservice/application/dto/workflow/WorkflowKeyValidator.java

Goal: produce an evidence-backed, smallest behavior-preserving correction that removes the Sonar-flagged regex-complexity risk while preserving the validator's semantics (detect eight or more consecutive digits anywhere) and the repository's existing tests and contracts.

---

1) Exact Sonar issue key, file, line, field, method, and flagged regex

- Sonar issue key (from plan/evidence): 805e10b7-1b74-4441-972a-ebee2879cdc1 (java:S5998)
- File: src/main/java/com/bct/ngtpa/apiservice/application/dto/workflow/WorkflowKeyValidator.java
- Line / field (current):

```java
private static final Pattern LONG_DIGITS = Pattern.compile(".*\\d{8,}.*");
```

- Method(s) where used: `containsUnsafeToken(String value)` calls `LONG_DIGITS.matcher(value).matches()`
- Flagged regex: the leading/trailing `.*` around `\d{8,}` (the literal `".*\\d{8,}.*"`) — Sonar flags possible catastrophic backtracking / complexity due to unbounded wildcard quantifiers surrounding a quantified token.

2) Complete WorkflowKeyValidator implementation and validation rules (evidence)

Source (abridged, exact file present in repo):

- Class: `final class WorkflowKeyValidator`
- Fields (exact names):

```java
private static final Pattern CODE_LIKE =
    Pattern.compile("^[a-z][A-Za-z0-9]*(\\.[a-z][A-Za-z0-9]*)+$");
private static final Pattern LONG_DIGITS = Pattern.compile(".*\\d{8,}.*");
```

- Public surface:
  - `static String validate(String value, String safeErrorMessage)`
    - `null` -> `IllegalArgumentException(safeErrorMessage)`
    - trimmed empty, or not matching `CODE_LIKE`, or `containsUnsafeToken(trimmed)` -> `IllegalArgumentException(safeErrorMessage)`
    - otherwise return trimmed
- `containsUnsafeToken(String value)` returns true when ANY of:
  - value contains whitespace chars
  - value contains `"@"`
  - `LONG_DIGITS.matcher(value).matches()`
  - lower-case value contains `"csrf_"`, `"otp_"`, `"bearer"`, `"cookie"`, or password-like keys
  - other password-like heuristics (see `isUnsafePasswordLikeKey`)

3) Confirm the current regex and matching method

- Current pattern and call (exact):

```java
// current
private static final Pattern LONG_DIGITS = Pattern.compile(".*\\d{8,}.*");
// usage
LONG_DIGITS.matcher(value).matches();
```

- `matches()` requires the entire input sequence to match the regex. With the current pattern (leading/trailing `.*`), `matches()` is effectively testing "does the input contain eight consecutive digits anywhere?" because the `.*` can consume any prefix/suffix.

4) Characterize exact current behavior (by class of inputs)

For each example below, `current` indicates the observable result with the existing code (`LONG_DIGITS.matches()`):

- `"err.code.abc1234567xyz"` (seven consecutive digits): current = false (allowed by digit predicate)
- `"err.code.abc12345678xyz"` (exactly eight consecutive digits): current = true (rejected by digit predicate)
- `"err.code.abc123456789xyz"` (nine consecutive digits): current = true (rejected)
- `"err.code.a1234.b5678.c12"` (separated digit groups, none reach 8): current = false (allowed)
- blank or null: null -> rejected by `validate()` earlier; blank trimmed strings are rejected by `validate()` before `containsUnsafeToken` runs
- long keys: WorkflowKeyValidator imposes no explicit maximum-length limit; behavior depends only on digit runs and other token checks
- long syntactically valid key with no 8-digit run: current = false (allowed); Sonar flagged the `.*...*` pattern as potentially risky for maliciously crafted inputs

5) Proposed safe replacement and equivalence check (WorkflowKeyValidator scope only)

- Proposed replacement (behavior-preserving for the predicate "contains 8+ consecutive digits anywhere"):

```java
// proposed
private static final Pattern LONG_DIGITS = Pattern.compile("\\d{8,}");
// usage change
LONG_DIGITS.matcher(value).find();
```

- Equivalence rationale:
  - `".*\\d{8,}.*"` with `matches()` returns true iff the input contains an 8+ digit run.
  - `"\\d{8,}"` with `find()` returns true under the same condition (find locates substring matches).
  - This removes unbounded surrounding wildcards and therefore avoids the backtracking pattern Sonar flagged while preserving the boolean semantics required by `WorkflowKeyValidator`.

6) Inspect every use of the regex field and validator (Unit 5 scope)

- Within Unit 5 scope the pattern is used only inside `WorkflowKeyValidator.containsUnsafeToken` via `LONG_DIGITS.matcher(value).matches()`.
- Deferred observation (out of scope for Unit 5): an identical literal appears in `RegistrationIdempotencyKey.java`. That file is explicitly OUT OF SCOPE for Unit 5 and must be handled by a separate, approved investigation if it is to be changed.

7) Inspect existing WorkflowKeyValidator tests and related contract tests

- Existing tests that exercise `validate()` semantics:
  - `src/test/java/com/bct/ngtpa/apiservice/application/dto/workflow/WorkflowKeyValidatorLoginErrorPrefixTest.java`
  - `src/test/java/com/bct/ngtpa/apiservice/application/dto/workflow/WorkflowKeyValidatorRegistrationErrorPrefixTest.java`
- No existing tests directly exercise the LONG_DIGITS predicate. Focused tests are recommended (see section 10) and must ensure each test value also satisfies the `CODE_LIKE` rule where intended.

8) Git history for the regex — intended security/validation purpose

- The pattern was added as a defensive heuristic to reject keys that contain long runs of digits (likely numeric identifiers) which are considered unsafe in workflow key namespaces. No evidence that the surrounding `.*` is required beyond expressing a "contains" predicate.

9) Exact implementation file set for Unit 5 (authorized)

- Implementation (Unit 5 scope) should touch only:
  - `src/main/java/com/bct/ngtpa/apiservice/application/dto/workflow/WorkflowKeyValidator.java`

- Test files (if added as part of Unit 5):
  - `src/test/java/com/bct/ngtpa/apiservice/application/dto/workflow/WorkflowKeyValidatorLongDigitsTest.java` (new)

- Documentation files to update as part of Unit 5:
  - `docs/sonarqube/unit-05-workflow-key-validator-s5998-investigation.md` (this file)
  - `docs/sonarqube/sonarqube-bug-remediation-plan.md` (authoritative plan is recorded elsewhere and will be updated per-unit)

10) Tests required and exact cases (each test value must satisfy all other `WorkflowKeyValidator` rules)

- New focused regression tests (exact cases):
  - File: `src/test/java/com/bct/ngtpa/apiservice/application/dto/workflow/WorkflowKeyValidatorLongDigitsTest.java`
  - Tests (exact values and expected outcome):
    1. Accepts when only seven consecutive digits present:
       - value: `"err.code.abc1234567xyz"` -> `validate(...)` should return the trimmed value (no exception)
    2. Rejects when exactly eight consecutive digits present:
       - value: `"err.code.abc12345678xyz"` -> `validate(...)` should throw `IllegalArgumentException`
    3. Rejects when nine (or more) consecutive digits present:
       - value: `"err.code.abc123456789xyz"` -> `validate(...)` should throw `IllegalArgumentException`
    4. Accepts separated digit groups that never reach eight consecutive digits:
       - value: `"err.code.a1234.b5678.c12"` -> `validate(...)` should return the trimmed value
    5. Bounded long non-match (syntactically valid and does not violate any observed length rules):
       - observation: `WorkflowKeyValidator` imposes no explicit max-length; therefore choose a conservative bounded size such as 10_000 characters to exercise performance behavior without being unreasonably large.
       - value: `"err." + "a".repeat(10000)` -> `validate(...)` should return the trimmed value (no exception)

Notes:
- Do NOT include `"12345678"` as a test value because it fails `CODE_LIKE` and would not isolate LONG_DIGITS behavior.
- Each test value above is crafted to be syntactically valid under `CODE_LIKE` (dot-separated segments starting with a lowercase letter) except where the intention is to show pre-existing rejection.

11) Exact compile and test-compile gates (do NOT run in investigation)

- `mvn -DskipTests compile`
- `mvn -DskipTests test-compile`

12) Exact targeted-test commands (do NOT run in investigation)

- Existing validator tests:
  - `mvn -Dtest=WorkflowKeyValidatorLoginErrorPrefixTest,WorkflowKeyValidatorRegistrationErrorPrefixTest test`
- Suggested new focused test (if added):
  - `mvn -Dtest=WorkflowKeyValidatorLongDigitsTest test`

13) Regex complexity risk, behavior-preservation requirements, blocked conditions, and review checkpoints

- Risk: `".*\\d{8,}.*"` invites backtracking complexity; `"\\d{8,}"` with `find()` mitigates that risk.
- Behavior-preservation requirements (must be verified by focused tests):
  - For the four representative cases above, `".*\\d{8,}.*"`.matches(value) == `"\\d{8,}"`.find(value).
- Blocked conditions: if downstream code relies on matches() vs find() semantics beyond the contains predicate, pause and document; no such dependency was found in the scan limited to WorkflowKeyValidator.
- Review checkpoints before commit:
  1. Show the exact single-file diff for WorkflowKeyValidator.java (replace Pattern and matcher usage only).
  2. Run `mvn -DskipTests compile` and `mvn -DskipTests test-compile`.
  3. Run targeted existing tests.
  4. Add the focused long-digit tests and validate them.

14) Provisional conventional commit message

- `fix(workflow-key): simplify long-digit detection`

15) Decision

- VALIDATED

Justification: Replacing `Pattern.compile(".*\\d{8,}.*")` + `matches()` with `Pattern.compile("\\d{8,}")` + `find()` preserves the predicate "contains 8+ consecutive digits anywhere" for the required boundary cases, while removing the leading/trailing wildcard quantifiers that Sonar flagged. Focused boundary tests (exact cases above) will confirm equivalence across representative inputs.

---

Investigation author: Copilot CLI

Status: VALIDATED

## Implementation and validation record

Implemented files:

- `src/main/java/com/bct/ngtpa/apiservice/application/dto/workflow/WorkflowKeyValidator.java`
- `src/test/java/com/bct/ngtpa/apiservice/application/dto/workflow/WorkflowKeyValidatorLongDigitsTest.java`

Implemented correction:

```java
private static final Pattern LONG_DIGITS = Pattern.compile("\\d{8,}");
```

```java
LONG_DIGITS.matcher(value).find()
```

The focused regression tests cover:

- seven consecutive digits accepted;
- eight consecutive digits rejected;
- nine consecutive digits rejected;
- separated digit groups accepted when no run reaches eight;
- a bounded 10,000-character syntactically valid non-match accepted.

`RegistrationIdempotencyKey` remained out of scope and was not modified.

Validation commands and results:

```bash
mvn -DskipTests compile
mvn -DskipTests test-compile
mvn -Dtest=WorkflowKeyValidatorLongDigitsTest test
mvn -Dtest=WorkflowKeyValidatorLoginErrorPrefixTest,WorkflowKeyValidatorRegistrationErrorPrefixTest test
```

```text
Compile: SUCCESS
Test-compile: SUCCESS
Focused long-digit tests: 5 run, 0 failures, 0 errors, 0 skipped
Related existing validator tests: 5 run, 0 failures, 0 errors, 0 skipped
git diff --check: SUCCESS
```

## Final decision

**VALIDATED**

Unit 5 is ready for independent pre-commit approval. The commit hash remains empty until the approved commit exists.

Approved commit message:

```text
fix(workflow-key): simplify long-digit detection
```
