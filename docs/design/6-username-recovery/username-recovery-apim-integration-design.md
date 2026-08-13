# Username Recovery APIM Integration Design

## 1. Endpoint

```http
POST /ws/NGTPA/v1/TRPSearchMember
Content-Type: application/json
X-Request-Id: <propagated request ID>
```

Only `X-Request-Id` is propagated as request context. `Accept-Language` remains a Java/template concern unless APIM later requires it.

## 2. Request contract

```json
{
  "search-by": "STAFFNO",
  "search-value": "3011825",
  "dob": "15/07/1969",
  "email": "",
  "mobile-number": "66290597"
}
```

Proposed DTO:

```java
public record TrpSearchMemberApimRequest(
        @JsonProperty("search-by") String searchBy,
        @JsonProperty("search-value") String searchValue,
        String dob,
        String email,
        @JsonProperty("mobile-number") String mobileNumber) {
}
```

Exactly one contact value is populated. The unused field is sent as an empty string if required by APIM.

## 3. Response envelope reuse

Do not create `TrpSearchMemberApimResponseBody`.

Reuse:

```java
ApimResponseEnvelope<ApimResponseBody<TrpSearchMemberApimDataItem>>
```

or the exact existing transport generic shape used by `ApimWebClientFacade`.

Existing generic body:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApimResponseBody<T> {

    @JsonProperty("err-message")
    private String errMessage;

    @JsonProperty("data")
    private List<T> data;
}
```

Only the data-item DTO is new.

## 4. Data item

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TrpSearchMemberApimDataItem {
    private String env;

    @JsonProperty("trust-code")
    private String trustCode;

    @JsonProperty("sch-type")
    private String schemeType;

    @JsonProperty("policy-no")
    private String policyNo;

    @JsonProperty("cert-no")
    private String certNo;

    @JsonProperty("term-status")
    private String termStatus;

    @JsonProperty("term-complete-date")
    private String termCompleteDate;

    private Boolean isRegistered;

    @JsonProperty("cust-id")
    private String custId;
}
```

`cert-no` should be modeled as a string in Java even if a sample supplies a number. The final APIM JSON type must be confirmed because identifiers can require leading-zero preservation.

## 5. Mapping

### Java to APIM

| Java field    | APIM field    |
| ------------- | ------------- |
| identityType  | search-by     |
| identityValue | search-value  |
| dateOfBirth   | dob           |
| email         | email         |
| mobile        | mobile-number |

Identity mapping is direct:

```text
GPN -> GPN
GPFS -> GPFS
STAFFNO -> STAFFNO
ID -> ID
```

### APIM to Java

| APIM field         | Java domain field  |
| ------------------ | ------------------ |
| env                | accountEnv         |
| trust-code         | trustCode          |
| sch-type           | schemeType         |
| policy-no          | policyNo           |
| cert-no            | certNo             |
| term-status        | termStatus         |
| term-complete-date | termCompletionDate |
| isRegistered       | isRegistered       |
| cust-id            | userName           |

Only `accountEnv`, `trustCode`, `isRegistered`, and generated `schemeRef` are projected to FE. Other account data and username remain server-side.

## 6. Response examples

Success:

```json
{
  "response": {
    "err-message": "",
    "data": [
      {
        "env": "DB",
        "trust-code": "DBA",
        "sch-type": "OE",
        "policy-no": "00000008802",
        "cert-no": "5",
        "term-status": "",
        "term-complete-date": "",
        "isRegistered": true,
        "cust-id": "db02"
      }
    ]
  }
}
```

Member not found:

```json
{
  "response": {
    "err-message": "err.tpa.member.not.found"
  }
}
```

## 7. Invariants

The adapter must enforce:

```text
isRegistered=true  => cust-id is nonblank
isRegistered=false => cust-id is blank or absent
```

Additional required fields for every record:

- `env` nonblank
- `trust-code` nonblank
- `isRegistered` nonnull

Duplicate records with the same `env + trust-code` violate the confirmed one-to-one username mapping unless TAS confirms a deterministic deduplication rule. Default behavior is to treat duplicates as a downstream contract failure rather than silently choose one.

Blank success data is treated as member not found.

`term-complete-date` is parsed strictly as `dd/MM/yyyy` when nonblank. Invalid nonblank dates are contract failures.

## 8. Downstream mapping matrix

| ID          | APIM condition                                     | Adapter outcome             | Public status     | HTTP | Public code                              | Next step      |
| ----------- | -------------------------------------------------- | --------------------------- | ----------------- | ---: | ---------------------------------------- | -------------- |
| APIM-UR-001 | Blank err-message and valid nonempty data          | MATCHED                     | SUCCESS           |  200 | none                                     | SELECT_ACCOUNT |
| APIM-UR-002 | `err.tpa.member.not.found`                       | NOT_FOUND                   | BUSINESS_REJECTED |  422 | err.usernamerecovery.verification.failed | RESTART_FLOW   |
| APIM-UR-003 | Blank err-message and empty/missing data           | NOT_FOUND                   | BUSINESS_REJECTED |  422 | err.usernamerecovery.verification.failed | RESTART_FLOW   |
| APIM-UR-004 | Recognized business rejection other than not found | Classified business failure | BUSINESS_REJECTED |  422 | approved mapped code                     | RESTART_FLOW   |
| APIM-UR-005 | Registered with blank cust-id                      | CONTRACT_INVALID            | SYSTEM_ERROR      |  503 | err.system.unavailable                   | NONE           |
| APIM-UR-006 | Unregistered with nonblank cust-id                 | CONTRACT_INVALID            | SYSTEM_ERROR      |  503 | err.system.unavailable                   | NONE           |
| APIM-UR-007 | Missing env/trust-code/isRegistered                | CONTRACT_INVALID            | SYSTEM_ERROR      |  503 | err.system.unavailable                   | NONE           |
| APIM-UR-008 | Duplicate env + trust-code                         | CONTRACT_INVALID            | SYSTEM_ERROR      |  503 | err.system.unavailable                   | NONE           |
| APIM-UR-009 | Invalid date or identifier JSON type               | CONTRACT_INVALID            | SYSTEM_ERROR      |  503 | err.system.unavailable                   | NONE           |
| APIM-UR-010 | Timeout/connection/5xx                             | UNAVAILABLE                 | SYSTEM_ERROR      |  503 | err.system.unavailable                   | NONE           |
| APIM-UR-011 | Decryption/envelope failure                        | UNAVAILABLE                 | SYSTEM_ERROR      |  503 | err.system.unavailable                   | NONE           |
| APIM-UR-012 | Unknown outcome                                    | UNKNOWN_OUTCOME             | SYSTEM_ERROR      |  503 | err.system.unavailable                   | NONE           |

`DOWNSTREAM_REJECTED` must never appear in the public response.

## 9. Transport and security

- Follow existing APIM `SharedApim...Transport` and adapter conventions.
- Reuse OAuth, certificate, encryption, request ID, timeout, logging, and retry components.
- Do not log request values or `cust-id`.
- Retry only according to `ApimRetryEligibility` and idempotency safety. Lookup is read-only, but retries must remain bounded.
- Use the configured APIM path and credential profile rather than hardcoding them.

## 10. Proposed ports

```java
public interface UsernameRecoveryLookupPort {
    UsernameRecoveryLookupResult search(UsernameRecoveryLookupCommand command);
}
```

The application port must expose domain outcomes, not APIM envelopes or DTOs.
