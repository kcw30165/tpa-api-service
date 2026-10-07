# Unit 06 — TrpGetAccountListAdapter (java:S3923) Investigation

Date: 2026-10-06T13:29:03.577+08:00

Scope: investigate the java:S3923 finding in
src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/login/account/TrpGetAccountListAdapter.java

Goal: produce an evidence-backed, smallest behavior-preserving correction that closes the Sonar S3923 (identical conditional branches) while preserving fail-closed behavior, configuration ownership of downstream messages, YAML compatibility, test and architecture contracts, and non-exposure of raw downstream messages.

---

1) Exact Sonar issue key

- f96fdb4f-8379-403c-9ea2-b7ebe80f67fa (java:S3923)

2) Exact source path, method, line, and flagged conditional

- File: src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/login/account/TrpGetAccountListAdapter.java
- Method: private LoginAccountAuthorizationResult mapEnvelope(ApimResponseEnvelope<TrpGetAccountListApimDataItem> envelope)
- Flagged conditional (exact code block):

```java
if (error != null) {
  if (!data.isEmpty()) return LoginAccountAuthorizationResult.unavailable();
  String configured = errorMappings.get(error);
  return "UNAVAILABLE".equals(configured)
      ? LoginAccountAuthorizationResult.unavailable()
      : LoginAccountAuthorizationResult.unavailable();
}
```

- Location: the conditional handling `if (error != null)` contains a ternary where both branches return the same expression; Sonar S3923 flags identical branch implementations as redundant.

3) Complete adapter and surrounding account-authorization flow inspected

- TrpGetAccountListAdapter implements `LoginAccountAuthorizationPort` and exposes `getAccounts(LoginAccountAuthorizationCommand)` which:
  - posts the command via `transport.post(command)`
  - maps the returned envelope via `mapEnvelope(...)`
  - on transport error returns `LoginAccountAuthorizationResult.unavailable()`
- `mapEnvelope(...)` normalizes `response.getErrMessage()` and `response.getData()` and enforces fail-closed semantics for null/empty/exception cases; success path maps data items via `mapper::toRecord` and returns `available(...)`.

4) Inspect errorMappings, its construction/config injection, properties, YAML, startup validation, callers and tests

- errorMappings field:
  - Declared in adapter: `private final Map<String, String> errorMappings;`
  - Assigned in constructor from properties: `this.errorMappings = Map.copyOf(properties.getErrorMappings());`
- TrpGetAccountListProperties (configuration class):
  - Prefix: `apim.login.get-account-list`
  - Fields: `path`, `operationName`, `errorMappings` (LinkedHashMap)
  - validate() enforces:
    - path and operationName non-blank
    - errorMappings non-null and non-empty
    - For each (code,outcome): code non-blank AND outcome must equal exactly `"UNAVAILABLE"`
      -> otherwise throws IllegalStateException("Invalid TRPGetAccountList error mapping")
- application.yml contains configuration under `apim.login.get-account-list.error-mappings` mapping known downstream messages to `UNAVAILABLE` (e.g. `err.tpa.no.active.account: UNAVAILABLE`).
- Startup validation: properties.validate() is called in adapter constructor, ensuring configuration presence and that all mapped values equal `UNAVAILABLE`.
- Callers: Adapter implements application port; callers use port.getAccounts; none in repo inspect or depend on adapter's internal mapping decision.
- Tests and architecture checks:
  - `Task0043TrpGetAccountListAdapterArchitectureTest` asserts:
    - adapter source contains `implements LoginAccountAuthorizationPort` and `errorMappings` (so adapter must retain that field)
    - adapter source does NOT contain raw downstream messages (e.g. `err.tpa.no.active.account`) — confirming non-exposure requirement
    - application.yml contains `get-account-list:` and those error-mapping lines (configuration owned)

5) What errorMappings owns today (evidence-backed)

- Configuration ownership: The exact downstream message strings are configuration-owned in application.yml under `apim.login.get-account-list.error-mappings`.
- Startup validation: `TrpGetAccountListProperties.validate()` enforces the presence of the map and that every mapping's value equals `UNAVAILABLE`.
- Runtime classification: The adapter reads `errorMappings.get(error)` at runtime, but given properties.validate() currently restricts outcomes to `UNAVAILABLE`, any configured outcome will be `UNAVAILABLE` at runtime.
- Known-message recognition: YAML lists the known downstream messages; adapter normalizes incoming `response.getErrMessage()` and looks up `errorMappings.get(error)` (string key lookup) to attempt classification.
- Observable runtime output: Today, regardless of configured outcome, the adapter returns `LoginAccountAuthorizationResult.unavailable()` for any error branch — i.e., errorMappings does not affect observable public output (both ternary branches return the same result). Therefore errorMappings currently functions as configuration ownership and intended future extension point rather than differing runtime behavior.

6) Characterize current behavior (cases)

- configured known downstream messages (e.g. `err.tpa.no.active.account`):
  - If `error` equals known message and `data` empty -> adapter returns `LoginAccountAuthorizationResult.unavailable()` (via the ternary)
- unknown downstream messages (not present in YAML):
  - `configured = errorMappings.get(error)` returns null; ternary evaluates to false and returns `LoginAccountAuthorizationResult.unavailable()` (same result)
- null or blank downstream messages:
  - `normalize(...)` maps null/blank to null; code treats `error == null` as no error path; if data empty -> returns unavailable; otherwise available path may occur
- downstream success (no error, non-empty data):
  - adapter returns `available(...)` mapped from data
- malformed downstream responses (null envelope/response or data null):
  - adapter returns `unavailable()` (early fail-closed checks)
- technical failures (transport error or mapper runtime exception):
  - transport exceptions: `onErrorReturn(LoginAccountAuthorizationResult.unavailable())` ensures unavailable
  - mapping runtime exceptions: caught in try/catch and return unavailable

7) Confirm fail-closed behavior for known and unknown business errors

- Both known and unknown downstream business errors ultimately return `LoginAccountAuthorizationResult.unavailable()` as public observable output today. That is, the adapter is fail-closed for errors regardless of configuration mapping.

8) Does errorMappings affect any observable runtime output today?

- No. Because `TrpGetAccountListProperties.validate()` restricts outcomes to `UNAVAILABLE` and the adapter's ternary returns `LoginAccountAuthorizationResult.unavailable()` in both branches, `errorMappings` does not change public output. Its runtime read is effectively a no-op for public behavior; it only documents or validates known messages at startup and preserves a future extension point.

9) Do not propose a discarded read like `String configured = errorMappings.get(error); return unavailable();` — analysis

- The investigation verifies this read is present and harmless, but Sonar flags the identical-branch ternary as redundant. The smallest behavior-preserving correction is to remove the redundant conditional and return `LoginAccountAuthorizationResult.unavailable()` directly in that branch, while preserving the `errorMappings` field and properties validation so configuration ownership remains intact. This avoids leaving a dead/unread local variable and satisfies Sonar.

10) Does any test/architecture rule require runtime consumption of errorMappings by the adapter?

- `Task0043TrpGetAccountListAdapterArchitectureTest` requires the adapter source to contain the identifier `errorMappings` (string text) and that `application.yml` contains the configured messages. It does NOT require the adapter to perform any runtime logic based on the mapping values. No tests require adapter to expose or behave differently for mapped vs. unmapped messages.

11) Smallest evidence-backed correction to close java:S3923 while preserving requirements

- Change only inside `mapEnvelope(...)` `if (error != null)` branch to eliminate the redundant ternary and the unused local variable. Replace the block with a single fail-closed return. Example minimal replacement (pseudo-code):

```java
if (error != null) {
  return LoginAccountAuthorizationResult.unavailable();
}
```

- This preserves:
  - fail-closed public API behavior
  - non-exposure of raw downstream messages (adapter source still does not contain raw strings)
  - configuration ownership: `TrpGetAccountListProperties` and `application.yml` remain authoritative; `errorMappings` field remains present and validated at startup
  - YAML compatibility and architecture tests (field remains; YAML remains unchanged)
- This removes the redundant ternary and any dead local variable, resolving the Sonar S3923 flag.

12) Evaluate alternative possibilities (evidence and trade-offs)

- A: Direct return of unavailable (recommended smallest change)
  - Pros: minimal, behavior-preserving, passes architecture tests, removes Sonar warning
  - Cons: adapter no longer reads the mapping at runtime (but it already provides no runtime observable difference)
- B: Keep runtime read, but refactor to meaningful classification (e.g. distinguishing known vs unknown) while returning identical public output but recording metrics/internal logs
  - Pros: preserves runtime recognition; allows future extensions
  - Cons: requires additional code (metrics/logging), tests, and possibly design review; unnecessary given properties restrict outcomes to UNAVAILABLE
- C: Remove adapter-level consumption of errorMappings (delete field) and rely only on properties validation elsewhere
  - Pros: eliminates dead code
  - Cons: breaks `Task0043` architecture test which asserts adapter source contains `errorMappings` string; changes contract and is larger-impact
- D: BLOCKED — if historical commits or external integration require runtime mapping for audit/metrics, a larger design decision is needed
  - Evidence: Git history and current tests do not show such requirement; thus BLOCKED is not necessary.

13) Git history evidence

- application.yml contains `apim.login.get-account-list.error-mappings` since initial adapter wiring. `Task0043` architecture test asserts YAML presence and adapter presence of `errorMappings` as configuration ownership proof. `TrpGetAccountListProperties.validate()` enforces all mapped outcomes equal `UNAVAILABLE`. No historical commits were found that require adapter to expose or forward raw downstream messages.

14) Exact smallest implementation file set (for remediation)

- Production file to change: `src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/login/account/TrpGetAccountListAdapter.java` — only the `mapEnvelope(...)` branch handling `error != null` needs to be simplified.
- Tests: none required to change. Architecture test `Task0043TrpGetAccountListAdapterArchitectureTest` already enforces the field and YAML; it will remain green because the field is retained.

15) Are existing tests sufficient or are focused regression tests required?

- Existing tests and architecture checks are sufficient to protect the preserved behavior:
  - `Task0043TrpGetAccountListAdapterArchitectureTest` asserts presence of `errorMappings` and YAML entries
  - No unit tests expect the adapter to return anything other than `unavailable()` for error branches
- Therefore no new tests are required to validate the minimal change. If future behavior is desired (e.g. metrics for known vs unknown errors), add new tests at that time.

16) Compile and test-compile gates (for implementation later)

- `mvn -DskipTests compile`
- `mvn -DskipTests test-compile`

17) Exact targeted-test commands (for implementation later)

- `mvn -Dtest=Task0043TrpGetAccountListAdapterArchitectureTest test`
- Potentially `mvn -Dtest=TrpGetAccountListAdapterTest test` if adapter-level unit tests exist and are targeted

18) Risks, blocked conditions, preserved behavior, prohibited changes, and review checkpoints

- Risks:
  - If there exists an undocumented runtime expectation (metrics, telemetry) relying on adapter `errorMappings.get(error)` reads, removing that read could remove internal signals. No evidence of such signals was found in code or tests.
  - If future YAML aims to map to outcomes other than `UNAVAILABLE`, the current properties.validate() will reject such configuration; any extension requires coordinated updates to properties validation and architecture tests.
- Blocked conditions:
  - If product/architecture owners require runtime differing outcomes based on mapping, this unit should be BLOCKED pending design approval.
- Preserved behavior:
  - Fail-closed public API behavior remains identical.
  - No raw downstream strings are leaked or exposed.
  - Configuration ownership and YAML compatibility remain intact (properties and YAML unchanged).
- Prohibited changes in this unit:
  - Do not change application.yml, TrpGetAccountListProperties, or any tests.
  - Do not alter public API signatures or application behavior beyond removing redundant branches.
- Review checkpoints before committing the minimal fix:
  1. Show the exact single-file diff for TrpGetAccountListAdapter.java replacing the redundant ternary with a direct return.
  2. Confirm `Task0043TrpGetAccountListAdapterArchitectureTest` still sees `errorMappings` in adapter source.
  3. Run `mvn -DskipTests compile` and `mvn -DskipTests test-compile` (outside this investigation) and run the targeted architecture test.

19) Recommended provisional conventional commit message

- `fix(trp-get-account-list): remove redundant error-branch ternary while preserving fail-closed behavior`

20) Decision

- READY_FOR_IMPLEMENTATION

Rationale: The Sonar S3923 finding flags a redundant ternary where both branches return the same result. The current configuration and properties validation guarantee that any mapped outcome equals `UNAVAILABLE`, and the adapter already returns `unavailable()` for any error. The smallest, evidence-backed correction is to remove the redundant ternary and return `LoginAccountAuthorizationResult.unavailable()` directly. This preserves fail-closed behavior, configuration ownership, YAML compatibility, non-exposure of raw messages, and passes existing architecture tests which demand that the adapter retain the `errorMappings` field.

---

Investigation author: Copilot CLI

Status: VALIDATED

## Implementation and validation record

Implemented file:

- `src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/login/account/TrpGetAccountListAdapter.java`

Implemented correction:

```java
if (error != null) {
  return LoginAccountAuthorizationResult.unavailable();
}
```

The implementation removed the unused `errorMappings.get(error)` lookup and the identical-outcome ternary. The `errorMappings` field, constructor assignment, `properties.validate()`, `Map.copyOf(...)`, YAML configuration, and architecture ownership remain unchanged. No dummy read, logging, metrics, fallback behavior, public-result change, or raw downstream-message exposure was introduced.

Validation commands and results:

```bash
mvn -DskipTests compile
mvn -DskipTests test-compile
mvn -Dtest=Task0043TrpGetAccountListAdapterArchitectureTest test
mvn -Dtest=TrpGetAccountListAdapterTest test
```

```text
Compile: SUCCESS
Test-compile: SUCCESS
Architecture test class: SUCCESS
Adapter unit test class: SUCCESS
git diff --check: SUCCESS
```

Exact test totals were not added because the supplied validation summary did not include the Surefire counts.

## Final decision

**VALIDATED**

Unit 6 is ready for independent pre-commit approval. The commit hash remains empty until the approved commit exists.

Approved commit message:

```text
fix(login-account): simplify fail-closed error mapping
```
