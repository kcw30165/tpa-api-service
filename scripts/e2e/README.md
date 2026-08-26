# BFF Scripted End-to-End Framework

This repository-owned curl framework replaces Postman as the authoritative local functional-validation mechanism. It is feature-neutral and supports scenario packs.

## Safe self-test

```bash
E2E_SCENARIO_PACK=platform E2E_SCENARIO_ID=controlled-assertion-self-test bash scripts/e2e/run.sh
```

## Readiness request

```bash
BFF_BASE_URL=http://localhost:8080 E2E_SCENARIO_PACK=platform E2E_SCENARIO_ID=readiness bash scripts/e2e/run.sh
```

Use runtime environment variables for sessions and account context. Never put real values in tracked files or command history. Mutations, OTP/SMS delivery, CAPTCHA automation, and other external effects are not implemented by the core framework.
