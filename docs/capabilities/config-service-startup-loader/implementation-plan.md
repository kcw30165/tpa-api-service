# Config Service Startup Loader — Implementation Plan

**Status:** Proposed. Not yet committed.
**Baseline:** `23941a92e87ff6991d2205d9ab337d579e01b52d`, branch `features/impl-fund-price`
**Task classification:** Behavior change → fail-first TDD applies.
**Commits:** Three, in order. (1) the plan. (2) loader foundation. (3) Fund Price export headers as first consumer.

---

## 1. Purpose

Introduce a startup-time property loader that fetches key/value configuration from the
NGTPA Config Service and registers it as a Spring `PropertySource`, so that any
`@ConfigurationProperties` class in the application can be backed by Config Service
without per-request I/O.

This is **foundation infrastructure**, not a Fund Price feature. Fund Price is only its
first consumer.

### Why not Spring Cloud Config Client

`spring-cloud-starter-config` is declared in `pom.xml` but completely inert: no
`spring.config.import`, no bootstrap wiring, no registered `PropertySource`, and an empty
`META-INF`. It cannot be activated, because the NGTPA Config Service does not implement
the Spring Cloud Config Server contract:

| | Spring Cloud Config Server | NGTPA Config Service |
|---|---|---|
| Path | `GET /{application}/{profile}/{label}` | `GET /api/configs?application=&profile=&label=&configKey=` |
| Response | `{ propertySources: [ { name, source: {...} } ] }` | flat `[ { application, profile, label, configKey, configValue } ]` |

Adapting the starter means overriding its locator internals — more code than writing the
loader, and fragile across Spring Cloud upgrades. It also cannot express the required
fail-soft policy (§4), which is neither the starter's `fail-fast=true` nor its silent
`fail-fast=false`.

**Decision: bespoke loader; remove the starter.**

---

## 2. Ratified decisions

| # | Decision | Value |
|---|---|---|
| D1 | Startup failure policy | Boot with YAML defaults, log a warning. Never block startup. |
| D2 | Precedence | Two tiers: Config Service -> `application.yml` -> code defaults (`addFirst`). No OS-env override tier. |
| D3 | `application` | `ngtpa-api-service` |
| D4 | `label` | Constant `main`; namespacing via dotted key prefixes |
| D5 | `profile` | Dedicated `CONFIG_SERVICE_PROFILE`, defaulting to the lowercased first active Spring profile. DR uses `main`. |
| D6 | Key shape | One row per leaf property, kebab-case, dotted path |
| D7 | Bootstrap timeouts | 2000 ms connect, 3000 ms read, 2 attempts, 500 ms backoff (~7 s worst case) |
| D8 | Starter | Remove `spring-cloud-starter-config` + `spring.cloud` YAML block + `SPRING_CLOUD_CONFIG_ENABLED` |
| D9 | Dead variables | Delete the three `REFERENCE_DATE_REFRESH_CONFIG_SERVICE_*` entries |
| D10 | Commits | Two: loader foundation, then Fund Price consumer |

### Why a dedicated `CONFIG_SERVICE_PROFILE`

`DeploymentEnvironmentPolicy` already treats the first active Spring profile as the
deployment-environment signal, so it *could* supply the value. But it normalizes with
`toUpperCase` before comparing, so the live casing is unknown from source alone, and the
deployment set includes `DR`, which has no Config Service counterpart.

A dedicated variable decouples *which config partition* from *is this production-like* —
two questions that share an input today but need not forever — and makes DR→`main` an
explicit deployment decision rather than hidden Java mapping.

---

## 3. Bootstrap constraint

An `EnvironmentPostProcessor` runs **before the Spring context exists**. Therefore the
loader must not use:

- `ConfigServiceProperties` (a `@ConfigurationProperties` bean)
- `ConfigServicePort` / `ConfigServiceWebClientAdapter` (Spring beans)
- the shared `ObjectMapper` bean
- any injected `Clock`, logger bean, or `WebClient` bean

It reads settings directly from the raw `ConfigurableEnvironment` and builds its own
HTTP client and `ObjectMapper` inline. This duplication is deliberate and must be
commented as such, so a future reader does not "fix" it by injecting the existing beans.

**This is the first bootstrap extension in the repository** — `META-INF` is empty and no
`EnvironmentPostProcessor`, `ApplicationContextInitializer`, or `MapPropertySource` exists
anywhere in `src/main`. There is no local precedent to copy; the design below is
first-of-kind and warrants its own review.

---

## 4. Failure policy (D1)

The existing startup gate, `AuthFlowSessionPersistenceCompatibilityStartupGate`, throws
`IllegalStateException` — **fail-fast**. The loader is deliberately the opposite.

| Condition | Behavior |
|---|---|
| Base URL blank / absent | Skip silently. Log at INFO. Not an error — local dev may not run Config Service. |
| Connection refused / DNS failure | Retry per D7, then warn and continue |
| Timeout | Retry per D7, then warn and continue |
| HTTP 4xx / 5xx | Warn and continue. Status code logged; response body **never** logged. |
| Malformed JSON | Warn and continue |
| Empty result list | Log at INFO. Valid — means no overrides for this partition. |
| Success | Register `PropertySource`, log count only |

The warning must state that the application is running on YAML defaults, so a degraded
boot is visible in logs rather than silently producing wrong behavior.

**Never logged:** Basic auth credentials, the `Authorization` header, any `configValue`,
or the response body. Config values may themselves be sensitive. Per the security-logging
rule, log the *count* of loaded keys, and key *names* only at DEBUG.

---

## 5. Components — Commit 1 (foundation)

```
adapter/out/configservice/bootstrap/
  ConfigServiceEnvironmentPostProcessor.java   # the loader
  ConfigServiceBootstrapSettings.java          # record, read from raw Environment
  ConfigServiceBootstrapClient.java            # inline HTTP + JSON, no Spring beans
  ConfigServicePropertySourceFactory.java      # List<entry> -> MapPropertySource

src/main/resources/META-INF/spring.factories  # registers the post-processor
```

### `ConfigServiceBootstrapSettings`

Read from the raw `Environment`, all with defaults:

| Property | Env var | Default |
|---|---|---|
| `config-service.base-url` | `CONFIG_SERVICE_BASE_URL` | `""` (disables loader) |
| `config-service.username` | `CONFIG_SERVICE_USERNAME` | `""` |
| `config-service.password` | `CONFIG_SERVICE_PASSWORD` | `""` |
| `config-service.application` | `CONFIG_SERVICE_APPLICATION` | `ngtpa-api-service` |
| `config-service.profile` | `CONFIG_SERVICE_PROFILE` | lowercased first active profile |
| `config-service.label` | `CONFIG_SERVICE_LABEL` | `main` |
| `config-service.bootstrap.connect-timeout-ms` | — | `2000` |
| `config-service.bootstrap.read-timeout-ms` | — | `3000` |
| `config-service.bootstrap.max-attempts` | — | `2` |
| `config-service.bootstrap.backoff-ms` | — | `500` |

Bootstrap timeouts are a **separate** block from the existing runtime
`config-service.timeout-milliseconds: 10000`, so the startup path and the runtime path
can be tuned independently. The runtime value is sized for "a slow response beats a
failure"; at boot that tradeoff inverts.

### Property source placement

The Config Service query itself is a pure filter: the loader reads
`CONFIG_SERVICE_PROFILE` from the OS environment and passes it, with
`application=ngtpa-api-service` and `label=main`, as query parameters. There is no
contest at that stage.

Precedence applies only to the *result*. A key such as
`fund-price.export.headers.fund-name` exists both in the Config Service response and as
an `application.yml` default (the default is what makes an outage degrade to a correct
workbook rather than blank headers). Two tiers, ratified:

```text
Config Service  ->  application.yml  ->  code defaults
```

Registered via `addFirst` (D2), so Config Service wins over YAML. No third tier: an OS
environment variable does **not** override a Config Service value. **This ordering must
be asserted by a test.**

---

## 6. Components — Commit 2 (Fund Price consumer)

```
adapter/in/web/fundprice/config/
  FundPriceExportProperties.java        # @ConfigurationProperties("fund-price")
```

Structure mirrors the confirmed `ContributionSummaryProperties` precedent, which already
stores concatenated bilingual headers (`"Dealing date處理日期"`) — so this convention is
proven, not invented.

```
fund-price:
  export:
    headers:
      fund-price-date:     "Fund Price Date基金價格日期"
      fund-name:           "Fund Name"
      chinese-fund-name:   "基金名稱"
      unit-price:
        currency:          "Unit Price Currency 單位貨幣"
        price:             "Unit Price單位價格"
      bid-offer:
        bid-currency:      "Bid Price Currency買入單位貨幣"
        bid-price:         "Bid Unit Price單位價格"
        offer-currency:    "Offer Price Currency賣出單位貨幣"
        offer-price:       "Offer Price單位價格"
```

Every key carries a YAML default, so a Config Service outage degrades to a correct
workbook rather than blank headers.

Note: `unit-price.currency` has a space before the Chinese; the bid/offer keys do not.
This matches the ratified spec verbatim and is **not** to be normalized.

### SQL seed

Nine rows per profile (`dev`, `sit`, `uat`, `main`), `application = ngtpa-api-service`,
`label = main`. Delivered as a script against `[ngtpaConfig].[dbo].[config_kv]`.

---

## 7. Test plan (fail-first)

### Commit 1

| Test | Asserts |
|---|---|
| `ConfigServiceEnvironmentPostProcessorTest` | Blank base URL → no property source, no HTTP call |
| | Successful fetch → `MapPropertySource` registered with expected keys |
| | Remote value overrides YAML (D2) |

| | HTTP 500 → no source, app continues, warning logged |
| | Timeout → retried `max-attempts`, then continues |
| | Malformed JSON → continues |
| | Empty list → continues, no source or empty source |
| | Credentials and `configValue` never appear in log output |
| `ConfigServiceBootstrapSettingsTest` | Defaults; env-var override; profile lowercasing; DR→`main` |

HTTP is doubled with `ExchangeFunction` (the repository convention — `MockWebServer` is
not used here), or a plain stub client seam if the loader uses `RestClient`.

### Commit 2

| Test | Asserts |
|---|---|
| `FundPriceExportPropertiesBindingTest` | YAML defaults for all 9 keys; override binding; nested `unit-price`/`bid-offer` grouping |

Follows `ConfigServicePropertiesBindingTest` exactly: `ApplicationContextRunner` +
`YamlPropertySourceLoader` + `addLast`, with default / override / absent cases.

---

## 8. Validation

```
export JAVA_HOME="/home/nd2887/tools/jdk-21.0.12.1+1"
export M2_HOME="/home/nd2887/tools/apache-maven"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"
mvn -DskipTests compile
mvn -DskipTests test-compile
mvn -Dtest=ConfigServiceEnvironmentPostProcessorTest test
```

`spotless:apply` deferred to stabilization. Full `mvn test` run manually at the commit
gate. Because D8 removes a dependency, the commit gate additionally requires a **Spring
startup smoke test** confirming the application boots with Config Service unreachable.

---

## 9. Risks

| Risk | Mitigation |
|---|---|
| Wrong `application`/`profile`/`label` → silent YAML fallback | Log the exact query triple at INFO on every boot, and log loaded key count |
| Loader slows pod startup | D7 caps worst case ~7 s; readiness unaffected by design |
| Bootstrap code drifts from runtime adapter | Comment the duplication explicitly; both target the same `/api/configs` contract |
| Starter removal breaks a transitive dependency | Zero `org.springframework.cloud` imports confirmed; smoke test at gate |
| Config Service becomes a hidden hard dependency | D1 forbids it structurally; asserted by the HTTP-500 and timeout tests |

---

## 10. Out of scope

- Migrating existing YAML config (email/SMS templates, contribution headers) to Config
  Service — documented as a follow-up on a separate branch.
- Runtime config refresh / RabbitMQ propagation. The repository has no RabbitMQ
  integration, and business flows must not call Config Service on demand.
- Config Service write paths. `upsertConfig` / `deleteConfig` already exist on the
  runtime adapter and are untouched.
