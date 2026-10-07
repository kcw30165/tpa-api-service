# Unit 02 — ApimAdapterReflectionBranchProbeTest (java:S1751) Investigation

Date: 2026-10-05T18:08:35.535+08:00

Scope: investigate the java:S1751 finding in
`src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimAdapterReflectionBranchProbeTest.java`.

Summary
- Sonar issue key: 4b516506 (java:S1751) — "Loop with at most one iteration" reported in a test helper.
- Flagged method: private static Object updateReadStatusCommand() (in the test file).

1) Exact flagged method (source excerpt)

The test file contains this method (excerpted exactly as in source):

```java
private static Object updateReadStatusCommand() throws Exception {
  Class<?> commandClass =
      Class.forName(
          "com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand");
  for (Constructor<?> constructor : commandClass.getDeclaredConstructors()) {
    constructor.setAccessible(true);
    Object[] args = new Object[constructor.getParameterCount()];
    Class<?>[] types = constructor.getParameterTypes();
    for (int i = 0; i < types.length; i++) {
      if (types[i].equals(String.class)) {
        args[i] =
            switch (i) {
              case 0 -> "JP";
              case 1 -> "MBR";
              default -> "value-" + i;
            };
      } else if (List.class.isAssignableFrom(types[i])) {
        args[i] = List.of("MSG-1");
      } else {
        args[i] = null;
      }
    }
    return constructor.newInstance(args);
  }
  return null;
}
```

2) Why Sonar flags this method
- The loop `for (Constructor<?> constructor : commandClass.getDeclaredConstructors()) { ... return constructor.newInstance(args); }` contains an unconditional `return` inside the loop body. That means at runtime the loop can execute at most one iteration — either it returns on the first constructor, or the method exits earlier via exception. Sonar S1751 flags this as confusing control flow and suggests replacing the loop with a direct access or clarifying intent.

3) Inspection of the helper and surrounding logic
- The helper builds an args[] tailored to the constructor's parameter types, then immediately invokes that constructor and returns the result. There is no conditional check or exception handling around `constructor.newInstance(args)` within the loop body.
- The method therefore always attempts only the first constructor in the declaredConstructors() iteration order (which is stable but not guaranteed to be the no-arg constructor). If that invocation succeeds, that instance is returned; otherwise an exception propagates out of the method and the test will fail.

4) Actual constructor-selection intent — evidence and interpretation
- Evidence from code:
  - The method deliberately inspects each constructor's parameter types and builds argument values accordingly. That suggests the author expected to construct a plausible argument array for the constructor selected in the current iteration.
  - There is no code that tests whether the generated args are "sensible" for the constructor prior to invoking it; the code simply attempts the invocation and returns the result.
- Possible intents (interpretations):
  a) The helper intends to pick the first declared constructor and supply synthetic args for its parameters, returning that instance — i.e., a simple "take the first constructor" probe.
  b) The helper intends to try constructors in sequence until one successfully constructs (i.e., try/catch around newInstance and continue on failure).
  c) The helper intended to select a constructor by predicate (e.g., prefer a constructor with specific parameter counts or types), but the predicate logic is missing.

- Which is most likely?
  - Given the simple unconditional `return` after newInstance, the code as written implements (a): it effectively picks the first declared constructor and returns an instance (or fails). There is no attempt to fall back to other constructors.
  - The presence of logic that inspects types and sets args shows care to provide plausible values, but not enough to conclude (b) or (c) were intended — there is no try/catch to continue on failure, and no predicate logic to select a specific constructor.

5) Usages of the helper in this test class
- `updateReadStatusCommand()` is used exactly once in this file:
  - Called in the test `notificationReadStatusAdapterToApimRequestCoversNullAndPopulatedCommandBranches()` as a second probe case:

```java
calls += invokeIgnoringFailures(adapter, method, new Object[] {null});
calls += invokeIgnoringFailures(adapter, method, updateReadStatusCommand());
```

- No other production code references this test helper — it is test-local only.

6) Git history for the helper and test class
- Recent commits touching the test file include formatting and coverage-increase commits. The file's history shows the helper and related probes were added/updated as part of test-coverage improvements. (Log excerpts available in repo history.)
- No evidence in the local commits indicates the helper previously had a try/catch or a different loop structure; the current code has been in place in the recent commits that increased coverage.

7) Smallest behavior-preserving correction expected to close java:S1751
- Two safe minimal options (both close S1751):
  Option A — Preserve exact runtime behavior (no semantics change): replace the loop with a direct access to the first declared constructor and invoke it. This removes the misleading loop and is the smallest, most conservative change.

  Exact replacement (intent-preserving):

```java
Constructor<?>[] ctors = commandClass.getDeclaredConstructors();
if (ctors.length == 0) {
  return null;
}
Constructor<?> constructor = ctors[0];
constructor.setAccessible(true);
Object[] args = new Object[constructor.getParameterCount()];
Class<?>[] types = constructor.getParameterTypes();
for (int i = 0; i < types.length; i++) {
  if (types[i].equals(String.class)) {
    args[i] = switch (i) {
      case 0 -> "JP";
      case 1 -> "MBR";
      default -> "value-" + i;
    };
  } else if (List.class.isAssignableFrom(types[i])) {
    args[i] = List.of("MSG-1");
  } else {
    args[i] = null;
  }
}
return constructor.newInstance(args);
```

  - Rationale: this removes the loop and preserves the observed behavior — the method will attempt the same constructor the original code used, returning the same result in successful runs. It addresses Sonar's complaint without changing semantics or adding complexity.

  Option B — Make the helper more robust (non-breaking but slightly behavior-changing in edge cases): attempt each declared constructor in order and return the first successful instantiation. That requires wrapping `constructor.newInstance(args)` in a try/catch and continuing on failure:

```java
for (Constructor<?> constructor : commandClass.getDeclaredConstructors()) {
  constructor.setAccessible(true);
  // build args as before
  try {
    return constructor.newInstance(args);
  } catch (Throwable ignored) {
    // continue to next constructor
  }
}
return null;
```

  - Rationale: this is more permissive (tries multiple constructors), which is likely desirable but may change which constructor is ultimately used (if the first constructor fails at runtime). That is a small behavioral change and should be validated by targeted tests.

- Approved choice: Option A (direct-first-constructor access). It is the smallest surgical change that removes the confusing loop and preserves the exact behavior the tests currently rely on. Option B is retained in this investigation only as a rejected alternative because it changes behavior by swallowing a first-constructor failure and trying later constructors.

8) Confirm whether only this test file needs modification
- Yes. The helper is test-local and the only usage is within this single test class. No production files require changes. Therefore only `src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimAdapterReflectionBranchProbeTest.java` needs modification.

9) Existing assertions and branch-probe behavior that must remain unchanged
- All tests in this file rely on `invokeIgnoringFailures(...)` to count successful branch probes. Any change must preserve the number and ordering of probes where possible.
- Specifically, the `notificationReadStatusAdapterToApimRequestCoversNullAndPopulatedCommandBranches()` test expects `assertThat(calls).isGreaterThanOrEqualTo(2);` after invoking with `null` and the command produced by `updateReadStatusCommand()`. The change must still produce a non-null command (when the DTO class exists) for the second probe.
- Do not modify `invokeIgnoringFailures`, `instantiate`, or the adapter reflection calls — only adjust the redundant loop.

10) Compile and test-compile gates (for implementation)
- mvn -DskipTests compile
- mvn -DskipTests test-compile

11) Exact targeted-test command (for implementation validation)
- mvn -Dtest=ApimAdapterReflectionBranchProbeTest test

12) Risks, blocked conditions, and review checkpoints
- Rejected alternative risk: Option B (try-each-constructor) can change which constructor is used, potentially altering the probe's runtime object shape and which defensive branches are hit. Option B was not implemented.
- Risk: Constructors order is not strictly specified across JVM implementations; tests relying on declaredConstructors()[0] ordering are using reflection-order that historically matches source declaration order but should not be relied upon for critical behavior. However this test is a probe for branch coverage, not production behavior — the acceptable tradeoff is conservative minimal change.
- Blocked if the DTO constructor ordering is intentionally relied upon elsewhere or if the team requires a deterministic predicate-based constructor selection (e.g., prefer no-arg constructor). If so, request clarification from adapter owners.
- Review checkpoints: after applying Option A, run compile gates and run the single test class above. Verify probe counts and ensure no test regressions in the file.

13) Approved conventional commit message
- `test(apim-adapter): replace single-iteration constructor loop`

14) Conclusion — decision
- Decision: VALIDATED.
- Option A was implemented exactly as approved: the method now accesses the first declared constructor explicitly while preserving the original empty-array, argument-building, invocation, and exception behavior.
- Option B was rejected and was not implemented because it would change behavior by trying later constructors after a failure.

---

Files examined:
- src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimAdapterReflectionBranchProbeTest.java (entire file)
- git history for the test file (recent coverage/formatting commits)

Implementation and validation record:

1. Modified only:
   - `src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimAdapterReflectionBranchProbeTest.java`
2. Replaced the single-iteration constructor loop in `updateReadStatusCommand()` with explicit first-constructor access.
3. Preserved `setAccessible(true)`, parameter inspection, synthetic argument values, `constructor.newInstance(args)`, and exception propagation.
4. Did not change `invokeIgnoringFailures`, `instantiate`, adapter reflection calls, probe counts, assertions, or production code.
5. Validation completed successfully:

```bash
mvn -DskipTests compile
mvn -DskipTests test-compile
mvn -Dtest=ApimAdapterReflectionBranchProbeTest test
```

Validation result:

```text
Compile: SUCCESS
Test-compile: SUCCESS
Tests run: 5, Failures: 0
```

Investigation author: Copilot CLI

Status: VALIDATED

Approved implementation: Option A (direct-first-constructor access)

Commit status: not yet committed

Approved commit message:

```text
test(apim-adapter): replace single-iteration constructor loop
```
