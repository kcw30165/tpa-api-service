## Account Balance E2E Pack

Account Balance HTTP validation is owned by the shared `scripts/e2e` area.

### Migrated local-stubbed suite

This suite preserves the former functional scenarios against an already-running local BFF and
operator-controlled APIM/Redis stubs.

```bash
ACCOUNT_BALANCE_BASE_URL=http://localhost:8080 \
ACCOUNT_BALANCE_PORTAL_SID='<runtime-only>' \
ACCOUNT_BALANCE_ACCOUNT_REF='<runtime-only>' \
bash scripts/e2e/run-account-balance-dev.sh provided-session
```

Stub hooks retain their existing `ACCOUNT_BALANCE_SETUP_*` names. Missing hooks still produce
`SKIPPED`; CI must not treat those cases as completed coverage.

### Authenticated live journey

`authenticated-live` fails closed until a shared Login -> OTP verify -> portal session/account
selection helper is extracted from the existing E2E code. Do not duplicate the Contributions
Login implementation in this pack.

Successful payloads can contain financial data. Keep runtime values uncommitted and remove local
response evidence after review. The next completion stage must keep raw success bodies only in
private runtime state and retain redacted summaries.

#### Offline regression

The deterministic no-network journey validates Login start, OTP send and verification, local
Account-Ref selection, one Account Balance request, and authenticated logout with five mocked
requests, zero human prompts, and zero automatic retries.

```bash
bash scripts/e2e/run-account-balance-regression.sh offline
```
