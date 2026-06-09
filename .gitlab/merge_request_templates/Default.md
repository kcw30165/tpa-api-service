# Pull Request

## Summary

<!-- Briefly describe what this PR changes and why. -->

-
-
-

## Change Type

<!-- Check all that apply. -->

- [ ] Feature / behavior change
- [ ] Bug fix
- [ ] Refactor with no intended behavior change
- [ ] Test-only change
- [ ] Documentation / governance-only change
- [ ] Configuration change
- [ ] Other:

## Scope And Repositories

<!-- Identify affected repository area(s). -->

- [ ] Main repository — Java BFF source, tests, runtime behavior
- [ ] `.github` repository — governance, prompts, skills, templates, workflows
- [ ] Docs repository — architecture, ADRs, runbooks, API/integration docs

Affected areas/files:

```text

```

## Current Direction / Guardrails Checked

Current source of truth:

```text
.github/instructions/current-project-direction.instructions.md
```

Confirm applicable guardrails:

- [ ] Clean Architecture / Hexagonal boundaries preserved
- [ ] `PortalAccessContext` used; no legacy `MemberContext` implementation types introduced
- [ ] `accountEnv` used internally; external `env` limited to boundary/APIM mapping
- [ ] `Accept-Language` is canonical; no `lang` query fallback introduced
- [ ] `Account-Ref` behavior preserved: optional globally, required only for selected-account APIs
- [ ] Generic/base response schema preserved where applicable
- [ ] Blocking errors belong in `errors[]`; non-blocking notifications belong in `messages[]`
- [ ] `X-Request-Id` returned in response header/context and not mixed into result/error payloads
- [ ] APIM DTOs/raw APIM names remain inside adapter/mapping boundaries
- [ ] No real APIM/Config Service/Redis/OAuth/certificate/external calls in automated tests
- [ ] Logging remains sanitized; no secrets, tokens, keys, policy numbers, certificate numbers, or user IDs exposed

## TDD Evidence

<!-- Required for every non-trivial production behavior change. -->

### Applicability

- [ ] TDD applies — non-trivial production behavior changed
- [ ] TDD does not apply — documentation/governance-only change
- [ ] TDD does not apply — mechanical/no behavior change
- [ ] TDD was impossible to apply first; justification provided below

Justification when TDD does not apply or was impossible:

```text

```

### Fail-First Evidence

- **Failing test added/updated first:**

```text

```

- **Expected failure reason:**

```text

```

- **Targeted test command:**

```bash

```

- **Targeted failure result before production change:**

```text

```

- **Production change made after failing test:**

```text

```

- **Targeted test pass result after production change:**

```text

```

- **Broader validation command/result:**

```text

```

- **Tests not run and why:**

```text

```

## Contract Impact

<!-- Be explicit. If no contract impact, say so. -->

- [ ] No public API contract impact
- [ ] Endpoint path/method changed
- [ ] Request headers/parameters/body changed
- [ ] Response schema changed
- [ ] Generic/base response behavior changed
- [ ] Error/message mapping changed
- [ ] XLSX/binary response behavior changed
- [ ] APIM request/response mapping changed
- [ ] Config keys/defaults changed

Details:

```text

```

## Tests Added / Updated

<!-- List test classes and behavior covered. -->

```text

```

## Validation Commands

Use Git Bash syntax and `mvn`, not `./mvnw`.

### Targeted Tests

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21" && export M2_HOME="/d/Tools/apache-maven-3.9.15" && export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH" && mvn test -Dtest=<TargetTestClassName>
```

Result:

```text

```

### Full Test Suite

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21" && export M2_HOME="/d/Tools/apache-maven-3.9.15" && export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH" && mvn test
```

Result:

```text

```

### Full Verification

```bash
export JAVA_HOME="C:/Java/OpenJDK/jdk-21" && export M2_HOME="/d/Tools/apache-maven-3.9.15" && export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH" && mvn clean verify
```

Result:

```text

```

## Documentation

- [ ] No documentation update needed
- [ ] README updated
- [ ] Architecture/docs repository update needed
- [ ] `.github` instructions/prompts/skills/templates updated
- [ ] Review report generated/updated

Details:

```text

```

## Risks / Follow-Ups

```text

```

## Reviewer Notes

```text

```
