# Shared UrlValidator Design

## Purpose

Provide one framework-free validator for downstream-supplied URLs. Fund Fact Sheet is the first consumer. Account Balance is not changed in this delivery; its future migration is documented below.

## Name

`UrlValidator`, method `validate(String)`. "Resolver" is avoided because URL resolution conventionally means resolving a relative URL against a base (`URI.resolve`), which this class does not do.

## Behaviour (identical to `AccountBalanceFactsheetResolver`)

| Input | Result | Event |
|---|---|---|
| null or blank | `Optional.empty()` | None |
| `URI.create(value)` throws, or result is not absolute | `Optional.empty()` | `onRejected.run()` |
| Otherwise | `Optional.of(value)` exactly as received | None |

No trimming, no scheme check, no host check (OQ-12). Values such as `javascript:` or `data:` URIs are absolute and pass through; FE is informed of this.

## Shape

```java
public final class UrlValidator {
  private final Runnable onRejected;

  public UrlValidator(Runnable onRejected) {
    this.onRejected = Objects.requireNonNull(onRejected, "onRejected must not be null");
  }

  public Optional<String> validate(String raw) { ... }
}
```

- The callback takes no arguments, so a URL can never reach a log through it.
- Each consumer supplies its own event method (Fund Fact Sheet: `FundFactSheetPresentationEventPort::malformedFactsheet`).
- No Spring annotations; wired as a bean per consumer in its configuration class.

## Extension (future)

When a second business rule is needed, add the Specification pattern by composition, not inheritance:

```java
@FunctionalInterface
public interface UrlRule {
  boolean isSatisfiedBy(URI uri);
}
// new UrlValidator(List.of(httpsOnly(), hostIn(allowList)), onRejected)
```

- The absolute-URI baseline stays built in and cannot be removed by a rule.
- Rules run after the baseline; any failure rejects the value.
- The existing one-argument constructor keeps working, so existing callers are unaffected.
- `UrlRule` is not introduced now because no current feature needs a second rule.

## Deferred refactor: Account Balance

Not executed in this delivery. Account Balance code and tests stay untouched.

### Current state

- `application.service.accountbalance.AccountBalanceFactsheetResolver` implements the same rule.
- Used by `DefaultAccountBalanceAssembler`; wired in `AccountBalanceApplicationConfig`.
- Event: `AccountBalancePresentationEventPort.malformedFactsheet()`, logged as `event=account-balance.presentation.factsheet.omitted reason=malformed`.
- Covered by `AccountBalanceFactsheetResolverTest`; `AccountBalanceCurrencyVisibilityBoundaryGuardTest` checks the port signature has no `String` and the logger has no URL.

### Target state

1. `DefaultAccountBalanceAssembler` depends on `UrlValidator` instead of `AccountBalanceFactsheetResolver`.
2. `AccountBalanceApplicationConfig` builds `new UrlValidator(events::malformedFactsheet)`.
3. `AccountBalanceFactsheetResolver` and its test are deleted; equivalent cases already live in `UrlValidatorTest`.
4. Add an Account Balance wiring test proving the malformed event still fires and the log line is unchanged.
5. Run all Account Balance and Account Summary tests and architecture guards; search guards for references to `AccountBalanceFactsheetResolver` and update only those.

### Acceptance

- No change to Account Balance or Account Summary JSON output, omission rules or log lines.
- No change to `AccountBalancePresentationEventPort`.
- Full Maven suite and startup smoke pass.

### Suggested branch and commit

`refactor/account-balance-url-validator` from `origin/dev`, after this feature is merged.

```text
refactor(account-balance): use shared UrlValidator for factsheet links
```
