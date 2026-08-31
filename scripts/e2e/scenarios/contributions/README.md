# Authenticated Contributions Gate D

Run the authenticated list journey with:

```bash
BFF_BASE_URL="https://ngtpa-api-service-dev.bcthk.info" \
CONTRIBUTIONS_FROM_DATE="20/03/2026" \
CONTRIBUTIONS_TO_DATE="29/06/2026" \
CONTRIBUTIONS_ACCOUNT_INDEX=0 \
bash scripts/e2e/run-contributions-dev.sh list
```

The runner performs Login start, one OTP send and verification, selects the indexed `Account-Ref`, calls `GET /api/v1/contributions`, and logs out. The private runtime directory holds cookies, XSRF tokens, OTP values, and Account-Ref. Evidence records only presence booleans and the selected account index.

### Shared authenticated journey

The Contributions scenario reuses `scripts/e2e/lib/authenticated-login-journey.sh` for Login,
OTP verification, local Account-Ref selection, private runtime state, and authenticated logout.
The scenario owns only the Contributions request and its public response assertions.
