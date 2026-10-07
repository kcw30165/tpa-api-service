# Unit 07 — ExecutionLoggingAspect (java:S6218) Investigation

Date: 2026-10-06T14:08:23.654+08:00

Scope: investigate the java:S6218 finding in
src/main/java/com/bct/ngtpa/apiservice/infrastructure/logging/ExecutionLoggingAspect.java

Goal: produce an evidence-backed, minimal, behavior-preserving remediation plan (or BLOCKED decision) addressing Sonar's warning that a nested record contains an array field and therefore generated equals/hashCode semantics are likely incorrect for value equality.

---

1) Exact Sonar issue key

- bcff7d71-9a43-4913-a078-6fec5bd883f4 (java:S6218)

2) Exact source path, nested record, component, line, and Sonar message

- File: src/main/java/com/bct/ngtpa/apiservice/infrastructure/logging/ExecutionLoggingAspect.java
- Nested record: private record InvocationContext(..., Object[] args)
- Approx. location: inside ExecutionLoggingAspect; the record declaration begins near the end of the file (method `InvocationContext` declaration block). See the `InvocationContext` declaration for exact line numbers in your editor.
- Sonar message (semantics): "Fields of array type must not be used in generated equals() or hashCode() methods" (S6218). The rule warns that the automatically-generated record equals/hashCode will treat the Object[] field by reference rather than by element-wise content, which can make equality/hashing surprising when array contents should be compared by value.

3) Complete ExecutionLoggingAspect class inspected

- The class is an AspectJ `@Aspect` + `@Component`, with two @Around pointcuts logging annotated executions and package-level use-case `execute(..)` methods.
- Core flow: builds an `InvocationContext` from the join point and either logs synchronous invocations or decorates Reactor `Mono`/`Flux` results to log start/success/error events.
- Logging uses `LoggingSanitizer` and `ObjectMapper` to produce JSON-safe log events. start/success/error events are built via `buildEventBase(...)` and may include sanitized arguments (`loggingSanitizer.sanitizeArguments(context.args())`) and sanitized result (`loggingSanitizer.sanitizeValue(result)`).
- The class never exposes the `InvocationContext` outside the class; it is only created and passed into private helper methods inside the same class.

4) InvocationContext record inspected (every component)

Declaration (paraphrased):

```java
private record InvocationContext(
    String className,
    String methodName,
    String label,
    boolean logArgs,
    boolean logResult,
    Object[] args) {

  static InvocationContext from(ProceedingJoinPoint joinPoint, LogExecution logExecution) { ... }

  static InvocationContext fromJoinPoint(ProceedingJoinPoint joinPoint) { ... }
}
```

Details:
- Components (record components): className, methodName, label, logArgs, logResult, args (Object[]).
- Constructor usage: two static factory methods `from(...)` and `fromJoinPoint(...)` create instances from joinPoint.getArgs() (the recorded `args` array is the join point argument array, not copied).
- Accessor usage in class: `context.className()`, `context.methodName()`, `context.label()`, `context.logArgs()`, `context.logResult()`, and where `args()` is read only to pass into `loggingSanitizer.sanitizeArguments(context.args())` when `logArgs()` is true.
- Generated-method implications:
  - Java records auto-generate `equals`, `hashCode`, `toString`, and accessors. For array-typed components, the generated `equals`/`hashCode` will compare arrays by reference (==) and compute hash from array identity rather than content — which is often incorrect for arrays intended as value components.
- Visibility: the record is private and nested inside ExecutionLoggingAspect; therefore not part of the public API surface.
- Lifetime: an InvocationContext instance is created per intercepted invocation and is short-lived — created, passed into helper methods for logging, and then eligible for GC after method/stream lifecycle completes. It is not persisted or cached by the aspect.

5) Find every InvocationContext usage in repository and classification

Search results: `InvocationContext` only appears in ExecutionLoggingAspect.java and docs/sonarqube/sonarqube-bug-remediation-plan.md. No other source file references `InvocationContext`.

Classification of usage:
- compared with equals: NO occurrences found in repo.
- hashed: NO occurrences found.
- used as a Map key: NO.
- stored in a Set: NO.
- logged or converted using toString: NO direct uses of `context.toString()` in code (the record's toString is not invoked by the class; instead `buildEventBase` uses explicit accessors and `loggingSanitizer`).
- asserted directly in tests: NO tests reference the record type or its accessors directly.
- used only as an internal request-scoped carrier: YES — it is created per-invocation and used only within ExecutionLoggingAspect for logging; it is not retained beyond the scope of the invocation.

Conclusion: InvocationContext is private, short-lived, and not used as a key or compared or hashed anywhere in the repository.

6) Inspect every place where its Object[] args component is created, copied, read, sanitized, logged, passed, or retained

- Creation: `joinPoint.getArgs()` is used as the `args` array in `InvocationContext.from(...)` and `fromJoinPoint(...)`. No defensive copy is performed; the record stores the joinPoint array reference.
- Copies: No explicit array copies are made in ExecutionLoggingAspect; the joinPoint args array reference is forwarded to the record.
- Reads: Only read in `logStart(...)` when `context.logArgs()` is true; there the code calls `loggingSanitizer.sanitizeArguments(context.args())`.
- Sanitized: `LoggingSanitizer.sanitizeArguments(Object[] args)` iterates the array and sanitizes each element via `sanitizeValue`, handling objects, maps, collections, arrays (including nested and primitive arrays), and applying masking for sensitive keys. It returns a List<Object> ready for JSON serialization.
- Logged: The sanitized `args` list is inserted into the event map and serialized to JSON by Jackson in `toJson(event)` and logged at INFO.
- Passed to another method: `loggingSanitizer.sanitizeArguments(...)` is the main consumer. No other component receives raw `context.args()` directly.
- Retained beyond one invocation: NO evidence — the sanitized arguments are logged and not retained in any long-lived structure.

7) Inspect LoggingSanitizer and related logging/redaction components

- `LoggingSanitizer` handles primitive arrays via `Array.getLength`/`Array.get`, and recursively sanitizes nested arrays and collections. It also sanitizes Map keys and values and masks sensitive tokens when field names match configured sensitive tokens. `toSafeString` and `sanitizeText` exist for throwable messages and free-text values.
- `MaskingService` provides masking placeholders and a configurable list of `sensitiveTokens` used by `isSensitiveKey`.
- Tests `ExecutionLoggingAspectTest`, `LoggingSanitizerTest`, and `LoggingSanitizerPropertiesBindingTest` exercise sanitization behavior and ensure tokens like `authorization` are masked.

8) Confirm whether raw args can contain sensitive types

Based on code inspection and tests, raw args may contain:
- nested arrays (handled by sanitizeArray)
- primitive arrays (handled by Array.get)
- request DTOs (sanitized via Jackson valueToTree and sanitizeJsonNode)
- encrypted payloads (treated as object or CharSequence; sensitive tokens masking applies by field/key names but cannot detect the payload is "encrypted")
- credentials/session data: possible when a Map contains keys like `authorization` — LoggingSanitizer masks known sensitive keys by configuration
- other sensitive values: possible; LoggingSanitizer attempts to mask by key/field names but cannot guarantee content-level secret detection for arbitrary object fields

Conclusion: LoggingSanitizer does a thorough job of sanitizing and masking common sensitive shapes and array types before logging. It converts arrays to Lists during sanitization so JSON serialization sees sanitized lists rather than raw arrays.

9) Inspect ExecutionLoggingAspect tests and related logging tests

- `ExecutionLoggingAspectTest` exercises logging for sync, Mono, and Flux paths. Tests assert that sanitized tokens (e.g., `authorization`) appear masked in the logged JSON, that `result` is included/omitted per `logResult`, and that `args` is included/omitted per `logArgs`.
- None of the tests reference `InvocationContext` directly, nor assert equals/hashCode semantics for it.
- `LoggingSanitizerTest` ensures array and nested structures are sanitized and masking placeholder is used for sensitive keys.

10) Inspect Git history for InvocationContext introduction and related decisions

- `git log -S InvocationContext` shows a refactor commit that introduced the record: commit `d6a09160dd4a...` (message: `refactor for clean architecture`). That commit replaced earlier manual logging implementations with the current record-based approach. No earlier commits indicate that InvocationContext was intended to be stored or compared.
- No earlier commit indicates collection usage or deliberate equals/hashCode behavior.

11) Determine whether the Sonar finding represents a runtime correctness defect

- Actual runtime correctness defect: UNLIKELY. The InvocationContext is private, created per invocation, and not used in equality/hashing or stored as a key in any collection. Therefore the record's generated equals/hashCode using array reference semantics poses little practical runtime risk in the current codebase.

- Record-value semantics concern: VALID. Sonar is correct that generated equals/hashCode are inappropriate when a record contains array components and the component's value equality is meaningful. If in future the InvocationContext is ever compared or used as a key, the generated methods would be surprising.

- Best-handled-through-other-representation: POSSIBLE. Given the record is private and not used in sets/maps, the fastest mitigation is to provide explicit equals/hashCode that treat the args array by content semantics to align with developer expectation, or to convert args to an immutable List when constructing the record (but that changes the component type), or to explicitly exclude args from equality semantics with a documented contract.

12) Evaluate smallest behavior-preserving options (without pre-approving)

- Option A: Override equals and hashCode inside the private record using `Arrays.equals` / `Arrays.hashCode` on `args`.
  - Small change: implement `equals(Object o)` and `hashCode()` in the record body that call `Arrays.equals(this.args, other.args)` and `Arrays.hashCode(args)` with fallback to `Objects.equals` for other components (or call `super.equals` for non-array components).
  - Behavior-preserving: Does not change logging or sanitization. Only changes equality semantics should code later compare instances.
  - Complexity: Low.

- Option B: Override equals/hashCode using `Arrays.deepEquals` / `Arrays.deepHashCode`.
  - Pros: correct when `args` may contain nested object arrays (e.g., Object[] that itself contains another Object[] or primitive arrays). Handles nested arrays element-wise.
  - Cons: `deepEquals` treats arrays-of-arrays recursively and will treat an `Object[]` element specially; for object fields that themselves rely on custom equality semantics, deepEquals may be more expensive. Still low risk.

- Option C: Convert `Object[] args` into an `List<Object>` at construction time (e.g., `List.copyOf(Arrays.asList(joinPoint.getArgs()))`) and change component type to `List<Object>`.
  - Pros: `List` value equality behaves as expected, generated record equals/hashCode will use list equals semantics which are content-based. Also avoids array-specific pitfalls.
  - Cons: Requires changing the record component type (API-internal change), and will require updating any direct call to `args()` to accept/return List instead of Object[]. That would require edits where `args()` is used (`logStart` currently calls `context.args()` and passes to `loggingSanitizer.sanitizeArguments(context.args())`) — we'd need to add an overload or change sanitizer to accept List; slightly larger change but still small.

- Option D: Exclude `args` from equality/hashCode by implementing equals/hashCode that ignore the `args` component (documented contract).
  - Pros: minimal and explicit.
  - Cons: potential surprise if future code expects args to participate in equality.

- Option E: Convert the private record to a private static final class that implements content-aware equals/hashCode explicitly.
  - Pros: full control over behavior.
  - Cons: larger refactor; not necessary given record body can contain methods.

- BLOCKED: If tests or code paths in the wild rely on `InvocationContext` equality or hashing in external monitoring/metrics frameworks (none were found), further coordination would be required.

13) Arrays.equals vs Arrays.deepEquals — assessment and recommendation rationale

- Inspection of `LoggingSanitizer` shows `sanitizeArray` handles arrays of any component types (primitive and object) and recurses into nested arrays. Arguments passed to `args` may include object arrays, primitive arrays, nested arrays, and arbitrary DTOs.

- `Arrays.equals(Object[], Object[])` performs element-wise `Objects.equals` but does not recurse into elements that are themselves arrays — nested arrays will be compared by reference via Objects.equals which calls `equals()` on array objects (which uses reference equality).

- `Arrays.deepEquals(Object[], Object[])` performs a deep, recursive element-wise comparison treating nested arrays appropriately (including primitive array components).

- Given the possible presence of nested arrays (and `LoggingSanitizer` explicitly handles nested arrays), `Arrays.deepEquals`/`deepHashCode` better matches the intuitive value-equality semantics of an argument array and is therefore safer and less surprising if equals/hashCode are ever used.

- Performance: `deepEquals` is slightly heavier but invocations are per-equality check only; since InvocationContext instances are short-lived and not used in hot code paths, the overhead is acceptable.

Recommendation: prefer `Arrays.deepEquals`/`Arrays.deepHashCode` if implementing content-aware equality.

14) toString and privacy

- Generated `record.toString()` will include array toString for `args` which uses array identity `[...]` from `Object[].toString()` and would not produce element JSON. However ExecutionLoggingAspect does not call `context.toString()` anywhere.

- The logging code explicitly sanitizes and serializes argument values via `loggingSanitizer` before putting them into event JSON. Therefore changing equals/hashCode (or even adding toString) is unnecessary for logging behavior.

- Do NOT add a new `toString()` that prints raw `args` or exposes sensitive values. If a custom `toString()` is added, it must either omit `args` entirely or include only a safe summary (e.g., count or masked summary). That change is outside the scope unless a stakeholder requests it.

15) Visibility and testing

- The record is private; do not widen visibility for tests.
- Existing public behavior is test-covered via `ExecutionLoggingAspectTest`. Those tests do not need to change for a content-aware equals/hashCode fix because they do not assert equals/hashCode semantics.
- If a focused regression test is desired to guard equals/hashCode semantics, it would require reflection to access the private record or making the record package-visible — both are undesirable. Given the low runtime impact and private scope, additional tests are not required.

16) Exact smallest implementation file set (for remediation)

- `src/main/java/com/bct/ngtpa/apiservice/infrastructure/logging/ExecutionLoggingAspect.java` — implement content-aware equals/hashCode in the nested `InvocationContext` record body using `Arrays.deepEquals`/`Arrays.deepHashCode` (or convert args to List at construction time and accept that small internal component-type change). No other files require edits.

17) Are existing tests sufficient or are focused regression tests required?

- Existing tests (`ExecutionLoggingAspectTest` and `LoggingSanitizerTest`) sufficiently exercise public logging behavior and sanitization and will catch privacy regressions. They do not exercise equals/hashCode semantics.

- Because custom equality and hashing were implemented, a narrow reflection-based regression test was added without widening production visibility.

- If the team prefers explicit protection of equals/hashCode semantics, add a narrowly-scoped reflection-based unit test that uses reflection to construct two `InvocationContext` instances with equivalent argument arrays and asserts `equals`/`hashCode` behave as expected. This is optional and not strictly necessary.

18) If focused tests required — exact assertions (if chosen)

- Using reflection (optional): construct two `InvocationContext` instances via the private static factory `fromJoinPoint` pattern or via a reflective constructor invocation passing two equivalent `Object[]` arrays (e.g., `new Object[]{1, new int[]{1,2}, List.of("x")}`), then assert `inv1.equals(inv2)` is true and `inv1.hashCode() == inv2.hashCode()`; also assert that `inv1.toString()` is not used by logging and no sensitive data appears in logs (this latter assertion duplicates existing tests).

- The test must never inspect raw sensitive values — compare only boolean equality and hash equality.

19) Compile and test-compile gates (for later implementation)

- mvn -DskipTests compile
- mvn -DskipTests test-compile

20) Exact targeted-test commands (for later implementation)

- mvn -Dtest=ExecutionLoggingAspectTest test
- mvn -Dtest=LoggingSanitizerTest test

21) Risks, blocked conditions, preserved behavior, prohibited changes, and review checkpoints

- Risks:
  - If future code begins to compare or store InvocationContext instances, failing to implement content-aware equals/hashCode would lead to subtle bugs. Sonar correctly warns about the potential hazard.
  - Changing equals/hashCode semantics may affect any hidden tests or monitoring that rely on identity semantics (none found).

- Blocked conditions:
  - BLOCKED if the private record is intentionally relied upon elsewhere (no evidence) or if changing component type (Object[] → List<Object>) would require modifications to `LoggingSanitizer` or other callers.

- Preserved behavior:
  - Logging and sanitization behavior must remain unchanged.
  - Visibility must remain private.
  - Do not expose raw args or change toString to reveal sensitive data.

- Prohibited changes in this unit:
  - Do not widen record visibility for tests.
  - Do not change logging formatting, sanitization, or masking.
  - Do not add argument summaries to logs.

- Review checkpoints (pre-commit for any remediation):
  1. Show the one-file diff (`ExecutionLoggingAspect.java`) implementing equals/hashCode using `Arrays.deepEquals`/`deepHashCode` in the record body, OR an alternative (List conversion) with minimal changes to sanitizer call sites.
  2. Run `mvn -DskipTests compile` and `mvn -DskipTests test-compile`.
  3. Run `mvn -Dtest=ExecutionLoggingAspectTest test` and `mvn -Dtest=LoggingSanitizerTest test` to ensure no logging regressions.
  4. Verify no sensitive value appears in logged JSON (existing tests already guard this).

22) Provisional conventional commit message

- `fix(logging): implement content-aware equals/hashCode for InvocationContext in ExecutionLoggingAspect`

23) Conclusion — one of READY_FOR_IMPLEMENTATION or BLOCKED

- Decision: READY_FOR_IMPLEMENTATION

Reasoning: Sonar S6218 correctly warns about array-typed fields in records. In this repository InvocationContext is private, short-lived, and not currently used in equality/hashCode-sensitive contexts; so the finding does not indicate an immediate runtime correctness bug. However it is low-risk and low-cost to make equals/hashCode content-aware in the private record body using `Arrays.deepEquals`/`Arrays.deepHashCode`. That change is behavior-preserving for logging and sanitization and prevents future surprises if InvocationContext is ever compared or used as a map key. Converting `args` to a `List<Object>` is an acceptable alternative but requires small additional call-site adjustments; overriding equals/hashCode in the record body is the smallest safe remediation.

---

Investigation author: Copilot CLI

Status: VALIDATED

Files changed: src/main/java/com/bct/ngtpa/apiservice/infrastructure/logging/ExecutionLoggingAspect.java, src/test/java/com/bct/ngtpa/apiservice/infrastructure/logging/ExecutionLoggingAspectInvocationContextTest.java

## Implementation and validation record

Implemented files:

- `src/main/java/com/bct/ngtpa/apiservice/infrastructure/logging/ExecutionLoggingAspect.java`
- `src/test/java/com/bct/ngtpa/apiservice/infrastructure/logging/ExecutionLoggingAspectInvocationContextTest.java`

Approved implementation:

- explicit `equals(Object)` compares all scalar record components and uses `Arrays.deepEquals(args(), other.args())`;
- explicit `hashCode()` includes the same scalar components and uses `Arrays.deepHashCode(args())`;
- the private record visibility, constructor, factories, accessors, and `toString()` remain unchanged;
- logging output, argument sanitization, result sanitization, masking, and event structure remain unchanged.

The focused reflection test verifies:

- equal simple argument arrays and equal hash codes;
- equal nested primitive arrays and equal hash codes;
- inequality when a scalar component differs;
- inequality when nested-array content differs;
- inequality against `null` and another object type.

The test does not invoke `toString()`, print contexts or arguments, widen production visibility, or use sensitive values. Deliberately cyclic arrays remain unsupported for equality/hash testing.

Validation commands and results:

```bash
mvn -DskipTests compile
mvn -DskipTests test-compile
mvn -Dtest=ExecutionLoggingAspectInvocationContextTest test
mvn -Dtest=ExecutionLoggingAspectTest test
mvn -Dtest=LoggingSanitizerTest test
```

```text
Compile: SUCCESS
Test-compile: SUCCESS
ExecutionLoggingAspectInvocationContextTest: SUCCESS
ExecutionLoggingAspectTest: SUCCESS
LoggingSanitizerTest: SUCCESS
git diff --check: SUCCESS
```

Exact test totals were not added because the supplied validation summary did not include the Surefire counts.

## Final decision

**VALIDATED**

Unit 7 is ready for independent pre-commit approval. The commit hash remains empty until the approved commit exists.

Approved commit message:

```text
fix(logging): define invocation context value semantics
```
