# 07 Implementor prompts (GitHub Copilot)

Common preamble - prepend to every prompt:

```text
You are the implementor for api-service feature "Change of Investment Choice" (CIM).
Worktree: /home/nd2887/projects/orso/ngtpa/api-service-worktrees/change-of-investment-choice
Branch: feature/change-of-investment-choice. Design pack: docs/design/17-change-of-investment-choice/ (authoritative).
Toolchain: Maven 3.9.15 /home/nd2887/tools/apache-maven, Java 21.0.12.1 /home/nd2887/tools/jdk-21.0.12.1+1.
Rules: do only this sub-task; no guesses - if code at HEAD contradicts the pack, STOP and report with file:line;
fail-first tests where behaviour changes; keep public contracts of other features unchanged;
use the existing MutationResponse / ApiStatus / ApiError types unchanged; no nextStep;
never log bodies, tokens, policy, certificate, user id or percentages; run spotless:check, compile,
test-compile and the targeted tests; produce one commit with the given message; do not push.
```

## 0.1 Read-only anchor check

```text
READ-ONLY. No source or test changes, no commit.
Produce a fact table with file:line at HEAD, each item CONFIRMED / DIFFERS (detail) / NOT FOUND:
1 session-scoped cache pattern (RedisCacheKeyFactory usage, TTL source, deletion on logout) usable for the CIM snapshot (01 s6);
2 HttpStatusClassification mapping for SUCCESS, SUBMITTED, VALIDATION_FAILED, BUSINESS_REJECTED, SYSTEM_ERROR;
3 how a non-workflow feature controller returns MutationResponse with result.data (Fund Fact Sheet as reference) and how the 503 is rendered via ExceptionMappings with ApiError.system;
4 the existing renderer path for a 400 VALIDATION_FAILED with FIELD errors and targets on a non-workflow endpoint, and the ErrorCodes constant for err.request.invalid;
5 shared APIM classifier entry point and how a feature adapter declares its single incident ERROR owner;
6 ApimRoutingLedgerCharacterizationTest entry count;
7 password-validation: token header name, purpose enum, issuance parser, token gate route matcher, confidential-route list (design 16 doc 11 s4);
8 Fund Price display-name selection and tie-break, and whether the application layer can reuse it without a web dependency;
9 ReferenceDatePort signature and the existing dd/MM/yyyy formatter.
Stop after the table.
```

## 1.1 - 1.4 Password validation second purpose

```text
Sub-task 1.<n> per 05-implementation-plan.md M1.
1.1 add CHANGE_INVESTMENT_CHOICE; regression tests proving PERSONAL_INFORMATION_UPDATE behaviour is unchanged first.
1.2 issuance structure check selected by purpose; CIM = strict V1-V8 of 04 s2; unknown members rejected.
1.3 token gate maps (method, route) -> purpose; add POST /api/v1/investment-choice/instructions; path-variant tests
    (trailing slash, case, encoded segments) as for Personal Information.
1.4 register the CIM route as confidential; logging tests prove no body/token/error text is logged.
Commit messages:
1.1 feat(password-validation): add change-of-investment-choice purpose
1.2 feat(password-validation): validate issuance payload structure per purpose
1.3 feat(password-validation): gate investment-choice instruction route
1.4 feat(password-validation): treat investment-choice instruction route as confidential
```

## 2.1 - 2.2 TRPGetCIMDtl

```text
Implement 03 section 1 exactly. SAFE_BUSINESS_CALL. Per-operation envelope interpretation, not ApimResponseValidator.
Tests first: every field rule, every integrity rule, strict dd/MM/yyyy and HH:mm:ss, empty data, >1 item,
each err-message row, empty eligibility slot, ledger entry. One incident ERROR owner via the shared classifier.
Commit messages:
2.1 feat(investment-choice): add TRPGetCIMDtl APIM contract types
2.2 feat(investment-choice): add TRPGetCIMDtl adapter
```

## 3.1 - 3.4 GET

```text
Implement 01 s5-s7 and 02 s0-s1. Reuse FundPriceRiskLevels.sortKey and the Fund Price display-name rule found in 0.1.
History fund names from the full SwitchFund list; available funds only avail-sw=true; pending joined by ref-no.
Snapshot = intervalPcnt, sources (code, avail-sw), funds (code, avail-sw) only; key scoped session+accountRef; overwrite.
Contract tests assert $.success, $.status, $.result.data..., $.messages, $.errors, and for 503
$.result null, $.errors[0].type SYSTEM, code, targets [], message absent/null, severity ERROR, source SERVER.
Commit messages:
3.1 feat(investment-choice): add fund ordering
3.2 feat(investment-choice): assemble investment choice details
3.3 feat(investment-choice): store submit snapshot per session and account
3.4 feat(investment-choice): expose GET /api/v1/investment-choice
```

## 4.1 - 4.4 Submit

```text
Implement 01 s8-s9, 02 s2, 03 s2, 04 s1-s3.
TRPCIMReq NON_RETRYABLE and never resubmitted; no success-flag check (D-14).
Outcomes: definite success / err-message (503, no reconciliation) / unknown (reconcile once with TRPGetCIMDtl,
today Asia/Hong_Kong, exact (switch-src, fund-code, pcnt) set; exactly one match -> 200 SUBMITTED).
Validation order, types, codes and targets exactly as 04 s1-s2; S2 rule pinned by one dedicated test (O-1).
Missing snapshot -> 503 + ERROR investment-choice.submission.snapshot.missing.
Commit messages:
4.1 feat(investment-choice): add TRPCIMReq adapter
4.2 feat(investment-choice): validate instruction against snapshot
4.3 feat(investment-choice): submit and reconcile instruction
4.4 feat(investment-choice): expose POST /api/v1/investment-choice/instructions
```
