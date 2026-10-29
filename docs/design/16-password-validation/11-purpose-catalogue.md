# 11 Purpose catalogue

How `operationPayload` is defined for each purpose, and what it takes to add one. Sections 1 to 3 describe behaviour as built. Section 4 is design guidance that has not been implemented or reviewed.

## 1. The rule

`POST /api/v1/password-validation` carries a generic envelope:

```json
{ "password": "<entered password>", "purpose": "<PURPOSE>", "operationPayload": { } }
```

`operationPayload` is **exactly the JSON body of the protected request** for that purpose. The frontend freezes the body the member reviewed, sends it here with the password, and later sends the same body, unchanged, to the protected route together with the returned token. The server binds the token to the session, the selected account, the purpose and that exact body. It compares the raw JSON, so member order does not matter, but values, types, array order and the presence or absence of a member all do. See [08 Frontend integration](08-frontend-integration.md), section 3.

## 2. Supported purposes (as built)

| Purpose | Protected request | `operationPayload` |
|---|---|---|
| `PERSONAL_INFORMATION_UPDATE` | `PUT /api/v1/personal-information` | The PUT body: `formVersion`, `applyToAllAccounts`, `fields` |

Structure checked at issuance for `PERSONAL_INFORMATION_UPDATE`: `formVersion` is a non-blank string, `applyToAllAccounts` is a boolean and `fields` is a non-empty object. Other members are allowed and are bound, but not validated. This check only makes sure the request can be bound. The existing validation on the PUT remains authoritative. Any other purpose value is rejected with 400.

Apply to all accounts: TAS applies an apply-all update to every account bound to the username. The token binds the submitted flag and fields, not the list of accounts (decision D21).

## 3. Not supported yet

Change of investment and fund switch have no payload contract and are not accepted. Both return 400 as an unknown purpose.

## 4. Adding a purpose (design guidance, not built)

Four places are specific to Personal Information in the code reviewed in M3 and M4. Check the source again before starting.

1. The purpose list, `PasswordValidationPurpose`, and its parsing.
2. The structure check in the issuance request parser, where the three required members are fixed.
3. The token gate, which matches one route and method and consumes with one fixed purpose.
4. The confidential-route list in `PasswordValidationRouteMatcher`.

A new purpose needs:

- A name, and exactly one protected route and method.
- A structure check selected by purpose, replacing the fixed one.
- A token gate registration for its route.
- **Registration of its route in the confidential-route list.** This is a security requirement. Without it, a wildcard logging rule could log the body or the error text of that route.
- Tests for every status, for path variants of its route, and for logging, written in the same way as for Personal Information.

Rules for the payload:

- Binding covers the body only. A value in the path or the query string is not bound. Put every value that matters in the body, or bind it explicitly.
- Keep volatile values out of the body. A timestamp or generated identifier that changes between confirmation and submission would cause a mismatch every time. The frontend must freeze it, or the endpoint must not take it.
- Each purpose needs its own design and review. This list is not an approval.

Open decision O-12, owner Nick: whether each purpose validates its payload structure strictly at issuance, or only checks that it is a JSON object. Strict validation fails a frontend bug at confirmation, before the token is spent, but each purpose then needs its own validator. Decide this when the second purpose is built.
