# Account Balance local functional validation

This package validates an already-running local BFF against operator-controlled local APIM and Redis stubs. It does not start services, seed authenticated sessions, or use Postman.

## Required environment

- `ACCOUNT_BALANCE_BASE_URL`, for example `http://localhost:8080`
- `ACCOUNT_BALANCE_PORTAL_SID`, a local synthetic authenticated-session value
- `ACCOUNT_BALANCE_ACCOUNT_REF`, a local synthetic selected-account reference

Do not commit or print these values. Optional variables include `ACCOUNT_BALANCE_EXPLICIT_CURRENCY`, `ACCOUNT_BALANCE_ENGLISH_LANGUAGE`, `ACCOUNT_BALANCE_EVIDENCE_DIR`, and `ACCOUNT_BALANCE_SCENARIOS`.

## Stub-dependent scenarios

Provide the matching setup variable as a shell command understood by your local stub system. The command output is discarded. Examples of variable names are `ACCOUNT_BALANCE_SETUP_ACCOUNT_INVALID`, `ACCOUNT_BALANCE_SETUP_METADATA_DEFAULT_ALL`, and `ACCOUNT_BALANCE_SETUP_MALFORMED_FACTSHEET`. A missing hook skips that scenario rather than inventing a stub product.

For metadata cache miss/hit, the hook must clear only the synthetic policy-scoped metadata entry, reset stub call counts, configure a complete metadata response, and make the subsequent request observe the same local scope. The operator must verify the metadata APIM call count increases on the first request and not on the second. For targeted fallback, configure the unfiltered response without a required item and the targeted response with that item. Use `policy-no=all` for `DEFAULT_ALL`, and the same synthetic requested policy for `POLICY_SPECIFIC`.

## Security

Fixtures contain only public error shapes. Real member, policy, certificate, Account-Ref, financial, cookie, token, actor, session, APIM credential, or metadata values are prohibited. Response files are mode 600 and should be deleted after review because a successful local response can contain synthetic financial values.
