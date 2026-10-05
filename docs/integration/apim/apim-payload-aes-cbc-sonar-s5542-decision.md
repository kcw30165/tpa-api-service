# APIM Payload AES-CBC Wire Contract and SonarQube java:S5542

- **Status:** Proposed. Implementation is staged in section 7; approvals and open items are in section 9.
- **Date:** 2026-10-05
- **Baseline:** `dev` at `965fdd40e4769990110ddf245a87e55eb923f26a`
- **Implementation branch:** `fix/sonar-s5542-apim-payload-aes-cbc`
- **Scope:** the two `java:S5542` VULNERABILITY findings in `ApimPayloadCryptoService`. No other SonarQube finding is in scope.

Related documents: [APIM Operations, Testing, and Production Runbook](operations-and-testing.md) (section 3.3, payload protection) and [APIM Exchange Executor Architecture and Developer Guide](apim-exchange-executor-architecture-and-developer-guide.md).

---

## 1. Summary

Both findings flag `AES/CBC/PKCS5Padding`, the field-encryption transformation used by the APIM payload-protection pipeline. Nick confirmed on 2026-10-05 that APIM will not change its payload cipher. Changing the transformation in this repository alone would therefore break every encrypted-field exchange with APIM while `apim.encryption.enabled` is true.

Decision:

1. Leave the cryptographic behaviour and the wire format unchanged.
2. Pin the wire format with characterization tests, so that the findings cannot later be "fixed" by silently changing the cipher.
3. Raise `ApimPayloadCryptoService` to the agreed 95% instruction coverage (section 3.5).
4. Record the accepted risk in code with a rule-specific `@SuppressWarnings("java:S5542")` on the two methods that call `Cipher.getInstance`, and in this document (sections 5 and 6).

---

## 2. SonarQube findings

Source: SonarQube issues export uploaded on 2026-10-05 (total 2, effort 20 min each). Both are type VULNERABILITY, severity CRITICAL, tags `cwe` and `privacy`, status OPEN, message "Use another cipher mode or disable padding."

| Issue key | Rule | File | Primary location | Secondary location |
| --- | --- | --- | --- | --- |
| `8143a368-e3f8-4d20-b263-f490856bcd39` | `java:S5542` | `ApimPayloadCryptoService.java` | line 180 | line 34, "Transformation definition" |
| `c0206d3e-7dd7-433d-8de1-77dd1e85efcd` | `java:S5542` | `ApimPayloadCryptoService.java` | line 199 | line 34, "Transformation definition" |

---

## 3. Investigation findings

Each statement below was established from evidence (scripts 001 to 005, section 10), not from memory.

### 3.1 Where the transformation is defined and used

`ApimPayloadCryptoService` (package `adapter.out.apim`):

| Line | Code |
| --- | --- |
| 34 | `private static final String AES_TRANSFORMATION = "AES/CBC/PKCS5Padding";` |
| 176 to 190 | `encryptAes(...)`, `Cipher.getInstance(AES_TRANSFORMATION)` at line 180 |
| 192 to 205 | `decryptAes(...)`, `Cipher.getInstance(AES_TRANSFORMATION)` at line 199 |

The transformation string has been present since the first commit of the class (`0d7c9fa3`, 2026-04-23). No other class in `src/main` uses CBC or PKCS5 padding. All other AES uses in the repository are `AES/GCM/NoPadding`, and RSA uses `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`.

### 3.2 Wire format

Request field, BFF to APIM, for every field named in `apim.encryption.request-fields`:

1. The AES key is 32 random ASCII alphanumeric characters (`ApimAesKeyFactory`), i.e. a 32-byte AES key.
2. The clear value is wrapped in a JWT (`{"alg":"RS256","typ":"JWT"}`, payload `{"value": <field value>}`) signed with the BFF application private key (`SHA256withRSA`).
3. The JWT is encrypted with `AES/CBC/PKCS5Padding` using a fresh random 16-byte IV. The output is `Base64(IV || ciphertext)`.
4. The AES key bytes are encrypted with the APIM public key using `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` (OAEP SHA-256, MGF1 SHA-256) and Base64-encoded.
5. The field value on the wire is `<rsa-encrypted-key>:<aes-payload>`.

Response field, APIM to BFF, for every field named in `apim.encryption.response-fields`: split on the first `:`, RSA-decrypt the key with the BFF application private key, AES-CBC decrypt, verify the RS256 signature with the APIM public key, and extract `value`.

Field selection is global: `getRequestFields` and `getResponseFields` ignore their `apiName` parameter.

### 3.3 Call chain and activation

- The only production caller is `DefaultApimExchangeExecutor` (`encryptRequest` twice, `decryptResponseEnvelope` twice). Feature adapters do not call the crypto service.
- `apim.encryption.enabled` defaults to `${APIM_ENCRYPTION_ENABLED:true}` in `application.yml`.
- Helm renders found in the Jenkins repository (dated 2026-09-04) set `APIM_ENCRYPTION_ENABLED` to `"true"` for dev-shared, sit-slot-1, sit-slot-2, uat-slot-1 and uat-slot-2. No rendered PROD or DR output was found (open item O1).

### 3.4 Exposure analysis

- **Encrypt side (line 180).** The BFF produces ciphertext for APIM. It does not decrypt attacker-supplied data in this path. Any decryption oracle on the request direction would be inside APIM, outside this repository.
- **Decrypt side (line 199).** The input is a field value from the APIM response body. The browser cannot supply it. It arrives over the APIM channel, and `apim.base-url` and `apim.oauth.token-uri` render with the `https` scheme in all five rendered environments listed in 3.3.
- **Integrity check order.** The RS256 signature is verified after AES decryption (`decryptField`), so CBC without authentication has the theoretical shape of a padding oracle: an attacker would need to inject ciphertext into the APIM response channel and observe an outcome that differs between a padding failure and other failures.
- **What is observable.** All crypto failures are `ApimCryptoException` with fixed messages. For an unexpected exception, `ApiExceptionHandler.handleUnexpectedException` returns HTTP 500 with `ApiStatus.SYSTEM_ERROR`, the error code `err.system.unexpected` and a configured public message. The response body is built from the error code, not from the exception message. `ApimCryptoException` is not referenced by name in `adapter/in` or `application`; the only classification is `ApimCryptoFailureClassifier`, used by the Forgot Password completion adapter to return an unknown outcome. Exception text and stack traces reach server logs only (sanitized message, plus the cause in the stack trace); `LoginController` logs `x.getMessage()` unsanitized in the OTP-verify error path, which is a log line, not a response.

This is a risk-acceptance judgement based on that evidence, not a proof. See section 5.

### 3.5 Coverage baseline

Fresh targeted run (script 005: `ApimPayloadCryptoServiceTest`, `ApimPayloadCryptoServiceFieldSelectionTest`, `ApimAdapterReflectionBranchProbeTest`, 18 tests, all passing) for `ApimPayloadCryptoService`:

| Counter | Missed | Covered | Percent |
| --- | --- | --- | --- |
| Instruction | 41 | 469 | 91.96% |
| Branch | 1 | 25 | 96.15% |
| Line | 8 | 99 | 92.52% |

The 95% instruction target needs 485 covered of 510, i.e. at least 16 more instructions. The 41 missed instructions are:

| Method | Missed instructions | Notes |
| --- | --- | --- |
| `decryptResponse(String, String, JavaType)` | 7 | three-argument overload, no production caller |
| `decryptResponseEnvelope(String, String, Class)` | 7 | three-argument overload; see open item O4 |
| `transformObject` catch block (lines 140-141) | 7 | reached when a field transformation throws |
| `encryptAes` catch block (lines 187-188) | 7 | reached when AES encryption throws |
| `decryptAes` catch block (lines 202-203) | 7 | reached when AES decryption throws |
| `normalizeApiKey` | 6 | private, no direct caller; invoked only by reflection in `ApimAdapterReflectionBranchProbeTest` |

Covering the three catch blocks adds 21 instructions: 490 of 510, about 96.08% (expected, to be measured in stage 3). The branch figure is already above the 85 to 90% tier for orchestration classes and must not drop.

### 3.6 Suppression conventions at this baseline

- No `NOSONAR` comments and no `@SuppressWarnings` with a Sonar rule key exist in `src`. The 9 `@SuppressWarnings` in `src/main` use javac keys (`unchecked`, `rawtypes`).
- The repository has no `sonar-project.properties`. The Jenkins scan script passes project settings and `sonar.exclusions`, with no issue-ignore settings.
- Earlier SonarQube fixes in this repository (`4da596dd`, S2187 and S2925) changed the code. There is no accepted-risk precedent.
- No test forbids `@SuppressWarnings` or `NOSONAR` in `src/main`. Two tests read `ApimPayloadCryptoService.java` by name (`ApimExecutionBoundaryCharacterizationTest`, `Stage021LegacyApimOrchestrationClosureFailFirstTest`); both treat it as an approved foundation file. Twenty-nine tests read `src/main` sources as text, so the full suite is part of validation (stage 5).

---

## 4. Options considered

| Option | Result |
| --- | --- |
| A. Change to `AES/GCM/NoPadding` | Rejected. Changes the wire contract; APIM will not change. |
| B. `AES/CBC/NoPadding` with hand-written PKCS#7 padding | Rejected. Same bytes on the wire and it silences the rule, but it only avoids the rule and adds home-grown padding code to a security component. |
| C. `@SuppressWarnings("java:S5542")` on `encryptAes` and `decryptAes` | Chosen. Rule-specific, method-scoped, lives in the code, so it survives rescans, branch changes and moves between SonarQube servers. |
| D. `// NOSONAR` on lines 180 and 199 | Not chosen. It hides every issue on the line, not only this rule. |
| E. Mark both issues Accepted in the SonarQube UI only | Fallback only (section 8). A UI status is lost if the analysis runs on another server or the code moves. |
| F. Project-level exclusion or ignore rules in the Jenkins scan | Rejected. Out of scope, and it would hide the rule for all other code. |

---

## 5. Residual risk (accepted)

- AES-CBC is unauthenticated: ciphertext is malleable. In this design integrity comes from the RS256 signature inside the encrypted payload, and that signature is checked after decryption.
- A padding-oracle attack needs an attacker who can both inject ciphertext into the APIM-to-BFF response channel (TLS-level or APIM-side compromise) and observe a distinguishable outcome. The public API does not expose one (section 3.4). Timing differences are not excluded.
- Eliminating the risk requires an APIM contract change. That is out of scope (confirmed by Nick, 2026-10-05).

---

## 6. Design of the change

### 6.1 Production code (stage 4)

- Add `@SuppressWarnings("java:S5542")` to `encryptAes` and `decryptAes` only.
- Add a one-line comment above each annotation and above the constant stating that `AES/CBC/PKCS5Padding` is mandated by the APIM payload contract and pointing to this document.
- No change to behaviour, signatures, the constant or the wire format.

### 6.2 Tests (stage 2)

New test class `ApimPayloadCryptoServiceWireContractTest` in `src/test/java/.../adapter/out/apim/`. Exact method names are settled during stage 2; the cases are:

1. Independent decryption: encrypt a field through `encryptRequest`, then decrypt the result with `javax.crypto` directly, using the literal `"AES/CBC/PKCS5Padding"` and the RSA-OAEP step. Assert the 16-byte IV prefix, the 32-byte key, and the RS256 JWT header and `value` claim.
2. Independent encryption: build a payload with `javax.crypto` (same layout), feed it to `decryptResponse`, and assert the clear value.
3. Encryption failure path: an `ApimAesKeyFactory` stub that returns a key of invalid length. Assert the `ApimCryptoException` chain (`Failed to encrypt APIM field payload.` inside `Failed to transform APIM payload.`). Covers the `encryptAes` and `transformObject` catch blocks.
4. Decryption failure path: a valid RSA-encrypted key with a ciphertext whose length is not a multiple of 16. Assert the `ApimCryptoException` chain (`Failed to decrypt APIM field payload.` inside `Failed to decrypt APIM response payload.`). Covers the `decryptAes` catch block. The failure is deterministic (illegal block size), not dependent on random padding.

### 6.3 Documentation

This document, plus one link in `README.md` of this folder. The status table in section 7 is updated as each stage completes. If the design changes during implementation, this document is updated in the same commit as the change.

### 6.4 Out of scope

- Removing dead code (`normalizeApiKey`, three-argument overloads without production callers).
- Consolidating `ApiExceptionHandler` (deferred as a separate future workstream).
- Any other SonarQube finding (BUG, CODE_SMELL).

---

## 7. Implementation plan and status

| Stage | Content | Validation | Status |
| --- | --- | --- | --- |
| 0 | Investigation (scripts 001 to 005) | Evidence artifacts reviewed | Done |
| 1 | This decision record and the README link | `mvn spotless:apply`; link check | Done |
| 2 | `ApimPayloadCryptoServiceWireContractTest` (section 6.2) | `mvn spotless:apply`; `mvn -DskipTests compile`; `mvn -DskipTests test-compile` | Not started |
| 3 | Targeted run of the crypto tests and a fresh class-level JaCoCo measurement | Instruction >= 95%; branch not below 85% | Not started |
| 4 | `@SuppressWarnings("java:S5542")` and comments (section 6.1) | `mvn spotless:apply`; compile; test-compile | Not started |
| 5 | Full validation | Full `mvn test` with JaCoCo report (run manually); `spotless:check` clean; application startup smoke test | Not started |
| 6 | SonarQube verification after merge | Both issue keys in section 2 no longer reported | Not started |

---

## 8. Acceptance, fallback and rollback

Acceptance:

- Behaviour is unchanged: the new wire-contract tests and all existing tests pass.
- `ApimPayloadCryptoService` instruction coverage is at least 95% and branch coverage does not fall below the 85 to 90% tier.
- `mvn spotless:apply` has been run last and `spotless:check` is clean.
- After merge, the Jenkins SonarQube scan no longer reports the two issue keys in section 2.

Fallback: if SonarQube still reports either issue after the scan, mark it Accepted in the SonarQube UI using the text of section 5 as the justification, and record that here.

Rollback: revert the commit. The change has no runtime or configuration effect.

---

## 9. Approvals and open items

| Approval | Owner | Status |
| --- | --- | --- |
| Solution design (this record and the staged plan) | Nick | Pending |
| Acceptance of the residual risk in section 5 | Security reviewer, to be named by Nick | Pending |

| Id | Open item | Resolution |
| --- | --- | --- |
| O1 | No rendered PROD or DR output was found, so the APIM URL scheme for those environments is not evidenced. | Confirm both use `https` before the security reviewer accepts section 5. |
| O2 | It has not been confirmed that this SonarQube server honours `@SuppressWarnings("java:S5542")` for these two issues. | Verified in stage 6; the fallback in section 8 applies otherwise. |
| O3 | Owner of the security risk acceptance is not named. | Nick to name the reviewer. |
| O4 | The three-argument `decryptResponseEnvelope` is reported uncovered although `ApimPayloadCryptoServiceTest` contains a three-argument call. | Re-measure in stage 3; investigate only if it affects the 95% target. |

---

## 10. Evidence index

| Script | Established |
| --- | --- |
| 001 | The two findings, the class source with line numbers, usages, tests, configuration keys, first coverage row, git history of the class |
| 002 | Wire-format collaborators (`ApimAesKeyFactory`, `ApimRsaPayloadCrypto`, `ApimPayloadFieldTransformer`), `ApimProperties`, encryption defaults and Helm renders, transformation unchanged on every branch, no peer specification in the repository |
| 003 | Callers of the crypto service, executor and facade source, global exception handler, status codes, logging calls, suppression convention |
| 004 | `ApimCryptoFailureClassifier` usage, error-code constants, handler tests, APIM URL schemes in configuration, `pom.xml`, CI validation script, documentation layout, commit-message convention |
| 005 | Deployed APIM URL scheme (Helm renders), failure surface in adapters and handler tests, method inventory, fresh class-level JaCoCo baseline |
