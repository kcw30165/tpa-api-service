# Notice Board - Notifications Public Contract and Downstream Request Alignment

| Item | Value |
|---|---|
| Branch | `refactor/notice-board` |
| Base | `origin/dev` at `c9aa7987a68f939d4170f1c78a8fc4763ed26a57` |
| Owner | Nick (solution design and approval) |
| Status | Implemented on the branch (ST-2 to ST-4); ST-6 (single msgContent) designed; milestone gates pending |

Labels: **Confirmed** means read from the source at the base commit (investigation scripts 001, 002,
004 and 005). **Unverified** means not read yet; the named gate settles it.

## 1. Goal

1. `GET /api/v1/notifications` and `PATCH /api/v1/notifications` return their success body in the
   shared `MutationResponse` envelope with the payload under `result.data`:

   ```json
   {
     "success": true,
     "status": "SUCCESS",
     "result": { "data": {} },
     "messages": [],
     "errors": []
   }
   ```

2. The downstream requests `TRPGetMsgBoard` and `TRPUpdMsgRead` no longer carry `env`.

## 2. Scope

In scope: the success response schema of the two endpoints, the downstream request payloads of the
two APIM operations, and the tests, guards and docs that pin either.

Out of scope (unchanged): the public request contracts (query parameters, PATCH body), use cases,
ports, `accountEnv` inside the commands and services, domain model, field names inside the payload
(including `msgCode` and `sequence`), error responses, security, request logging, and the
partial-success semantics of PATCH.

## 3. Label rule (standing)

Java has no label responsibility. Text that a person reads, in `messages` and in `errors`, comes
from the downstream system or is rendered by the frontend from a code that Java passes. Java returns
codes and targets only.

Consequences for this contract:

- Success responses of both endpoints carry `messages: []` and `errors: []` (D5).
- Java adds no message text for these endpoints.
- Confirmed: the shared model still has text arguments, for example
  `ApiMessage.success(code, text, source)` and `ApiError.field(code, text, targets, source)`
  (`MutationResponseContractTest`).
- Unverified: whether the shared `ApiExceptionHandler` fills error text for the notification
  exceptions (U4). Script 006 records the handler source as evidence. The handler is a shared,
  deferred workstream (Central `ApiExceptionHandler` consolidation); this branch does not change it.

## 4. Baseline (Confirmed)

| Endpoint | Controller return type | Success body today | Status today |
|---|---|---|---|
| GET | `Mono<NotificationListResponse>` | `{ "notifications": [ ... ] }` | none (bare body) |
| PATCH | `Mono<MutationResponse<UpdateNotificationsReadStatusResponse>>` | `{ success, status, result: { notifications: [ { msgCode, isRead } ] }, messages, errors }` | `UPDATED` |

- PATCH already uses `MutationResponse` but its `result` is the payload itself; this refactor adds the
  `data` level so that both endpoints follow the same shape.
- Failure responses of both endpoints already use `MutationResponse.failure(...)` through
  `ApiExceptionHandler`. Only the success path changes.
- `ApiResponseExceptionHandlingBoundaryTest` pins GET as unwrapped
  (`Mono<NotificationListResponse> getNotifications`) and PATCH as
  `Mono<MutationResponse<UpdateNotificationsReadStatusResponse>> updateNotificationsReadStatus`.
  Both assertions must change.
- Existing convention for the `data` level: `FundFactSheetResultResponse`,
  `FundPriceModeResultResponse` and `FundPriceQueryResultResponse` are
  `record XxxResultResponse(XxxResponse data)` with `Objects.requireNonNull(data, ...)`;
  `AccountSummaryResultResponse`, `AccountGainLossResultResponse`, `AccountResultResponse` and
  `AccountsResultResponse` have the same shape without the null check.
- `NotificationDto` declares `@JsonProperty("isRead")`; `NotificationReadStatusDto` is
  `record NotificationReadStatusDto(String msgCode, boolean isRead)` without it.

## 5. Target public contract

GET success:

```json
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "data": {
      "notifications": [
        {
          "msgCode": "MSG-1-LONG",
          "sequence": "1",
          "category": "GENERAL",
          "msgTitle": "Title",
          "msgContent": "Eng",
          "isRead": false,
          "startDateTime": "04/05/2026 08:30"
        }
      ]
    }
  },
  "messages": [],
  "errors": []
}
```

PATCH success:

```json
{
  "success": true,
  "status": "UPDATED",
  "result": {
    "data": {
      "notifications": [
        { "msgCode": "MSG-1-LONG", "isRead": true }
      ]
    }
  },
  "messages": [],
  "errors": []
}
```

An empty result is `"data": { "notifications": [] }` for both endpoints.

## 6. Downstream request alignment (env removal)

Confirmed from the sources:

- `GetMessageBoardApimRequest` and `UpdateNotificationReadStatusApimRequest` each declare
  `@JsonProperty("env") private String accountEnv`.
- Both adapters set it from `command.accountEnv()` and also build
  `new ApimCredentialResolutionContext(command.mbrType(), command.accountEnv())` with
  `ApimDatabaseScope.ACCOUNT_ENV`. A blank environment fails fast with `IllegalArgumentException`
  before the executor runs.
- `GetNotificationsService` and `UpdateNotificationsReadStatusService` resolve `ref-date` through
  `ReferenceDatePort.resolveReferenceDate(command.accountEnv())`.
- The APIM `request-fields` encryption list does not contain `env`, so the YAML needs no change.
  It contains `policy-no`, `cert-no`, `user-id`, `login-user-id`, `search-value`, `cust-id`, `email`
  and `mobile-number`.

Therefore `accountEnv` stays inside the BFF (routing and reference date) and only leaves the wire.

Request payloads after the change:

| Field | `TRPGetMsgBoard` | `TRPUpdMsgRead` | Source |
|---|---|---|---|
| `policy-no` | yes | yes | `AccountContext.policyNo` |
| `cert-no` | yes | yes | `AccountContext.certNo` |
| `ref-date` | yes | yes | resolved reference date |
| `login-user-id` | yes | yes | actor user id |
| `login-user-role` | yes | yes | actor role APIM value |
| `msg-code-long` | no | yes | comma-joined notification ids |
| `env` | removed | removed | not sent |
| `req-status` | no | no | not sent (see D9) |

`policy-no` and `cert-no` still come from the account resolved from `Account-Ref`.

## 7. Decisions

| ID | Decision | Reason |
|---|---|---|
| D1 | The inner payloads stay as they are: `NotificationListResponse` and `UpdateNotificationsReadStatusResponse` become the content of `data`. | Smallest change; field names unchanged; `GenericResponseTest`, `NotificationDtoTest` and `NotificationListResponseTest` stay valid. |
| D2 | Two new records follow the existing convention: `NotificationListResultResponse(NotificationListResponse data)` and `UpdateNotificationsReadStatusResultResponse(UpdateNotificationsReadStatusResponse data)`, in `adapter.in.web.response`, each with `Objects.requireNonNull(data, "data must not be null")`. | Same convention as Fund Fact Sheet and Fund Price. |
| D3 | Status: GET `SUCCESS`; PATCH `UPDATED` (as today, confirmed by Nick). | Keeps the status the PATCH already returns. |
| D4 | Both web mappers return `MutationResponse<...>`. Controller methods only chain `.map(mapper::toResponse)` and change their return types; the controller gains no header or context access. | `NotificationCurrentContextBoundaryGuardTest` forbids `Account-Ref`, `RequestHeaderContext` and `resolveRequiredAccountRef` in the controller. |
| D5 | `messages` and `errors` are `[]` on success for both endpoints. | Label rule (section 3). |
| D6 | PATCH partial failure is unchanged: the envelope stays `success: true` / `UPDATED`, and an item APIM did not update reports `isRead: false`. `PARTIAL_SUCCESS` is deferred. | A behaviour change outside this refactor. |
| D7 | The error path is unchanged. | Already a `MutationResponse` failure envelope. |
| D8 | The success schema change is breaking and is accepted. | The schema was not finalized. |
| D9 | `req-status` is not added to `UpdateNotificationReadStatusApimRequest`. | It is a development-time backdoor of the downstream developer for moving a message back from READ to UNREAD, not part of the BFF contract. |
| D10 | Remove the `accountEnv` field (`env`) from the two APIM request DTOs and the `.accountEnv(...)` call in the two adapters' `toApimRequest`. `GetNotificationsCommand`, `UpdateNotificationsReadStatusCommand`, both services, the controller, `ApimCredentialResolutionContext` and the reference-date lookup keep `accountEnv`. | Routing and `ref-date` still need it (section 6). |
| D11 | `cert-no` remains a JSON string as the DTOs declare it today. | Not part of this change; revisit only if the downstream rejects it. |

## 8. Class-level changes

| Class | Change |
|---|---|
| `NotificationListResultResponse` (new) | `record NotificationListResultResponse(NotificationListResponse data)` with a null check on `data`. |
| `UpdateNotificationsReadStatusResultResponse` (new) | `record UpdateNotificationsReadStatusResultResponse(UpdateNotificationsReadStatusResponse data)` with a null check on `data`. |
| `NotificationWebMapper` | `toResponse` returns `MutationResponse<NotificationListResultResponse>` with `ApiStatus.SUCCESS` and no messages. `toDto` is unchanged. |
| `NotificationReadStatusWebMapper` | `toResponse` returns `MutationResponse<UpdateNotificationsReadStatusResultResponse>` with `ApiStatus.UPDATED` and no messages. |
| `NotificationController` | `getNotifications` returns `Mono<MutationResponse<NotificationListResultResponse>>`; `updateNotificationsReadStatus` returns `Mono<MutationResponse<UpdateNotificationsReadStatusResultResponse>>`. |
| `GetMessageBoardApimRequest` | Remove the `env` / `accountEnv` field. |
| `UpdateNotificationReadStatusApimRequest` | Remove the `env` / `accountEnv` field. |
| `ApimNoticeMessageAdapter`, `ApimNotificationReadStatusAdapter` | Remove `.accountEnv(command.accountEnv())` from `toApimRequest`; the credential context call is unchanged. |

Unchanged: use cases, ports, commands and results, `UpdateNotificationsReadStatusRequest`,
`NotificationListResponse`, `UpdateNotificationsReadStatusResponse`, `NotificationDto`,
`NotificationReadStatusDto`, `application.yml`.

## 9. Tests affected

Changed:

- `NotificationWebMapperTest`, `NotificationReadStatusWebMapperTest`, `NotificationControllerTest`,
  `NotificationReferenceDateFlowTest` and `ApiResponseExceptionHandlingBoundaryTest` for the response
  schema.
- `ApimNoticeMessageAdapterTest` (the `body.getAccountEnv()` assertion and the test that pins the
  `env` JSON key) and `ApimNotificationReadStatusAdapterTest` (the `body.getAccountEnv()` assertion)
  for the request payloads.

New:

- `NotificationListResultResponseTest`, `UpdateNotificationsReadStatusResultResponseTest`.
- `NotificationResponseSerializationContractTest` (success bodies of both endpoints, including empty
  `messages` and `errors`).
- A request-payload test per operation that serializes the DTO and asserts the exact key set.

Must stay green unchanged: `GenericResponseTest`, `NotificationDtoTest`,
`NotificationListResponseTest`, `UpdateNotificationsReadStatusDtoTest`,
`NotificationCurrentContextBoundaryGuardTest`, `NotificationsAuthenticatedExecutorBoundaryTest`,
`ApimRoutingLedgerCharacterizationTest`, `ApimPayloadCryptoService*Test`, `ApiExceptionHandlerTest`,
`ApiExceptionHandlerMutationResponseContractTest`.

## 10. Consumer impact

| Endpoint | Old path | New path |
|---|---|---|
| GET | `$.notifications` | `$.result.data.notifications` |
| PATCH | `$.result.notifications` | `$.result.data.notifications` |

The frontend must change in the same release. No script, test resource or doc under `scripts/`,
`src/test/resources/` or `docs/` references the `notifications` JSON key (confirmed by script 002),
so there is no repository-side client to update. The downstream request change needs no frontend
change.

## 11. Unverified items and deferred work

| ID | Item | Settled by |
|---|---|---|
| U1 | Whether the architecture and boundary guards constrain the notification controller and adapters. | Settled for the guards by the ST-2 to ST-4 targeted runs: `InboundWebControllerArchitectureTest`, `StandaloneCapabilityControllerArchitectureTest`, `NotificationCurrentContextBoundaryGuardTest`, `NotificationsAuthenticatedExecutorBoundaryTest` and the `SecurityConfig*Test` classes passed. The full suite still runs at the milestone gate. |
| U2 | JSON key of `NotificationReadStatusDto.isRead`. | Settled by the ST-4 fail-first run: the key already serializes as `isRead`; no annotation was added. |
| U3 | Whether any test reads `controller-refactoring-endpoint-inventory.md`. | Full test run at the milestone gate. |
| U4 | Whether `ApiExceptionHandler` fills error text for the notification exceptions (label rule). | Settled: it does, through `ErrorMessageResolver` (evidence: script 006 `05-label-audit-readonly.txt`). Deferred by Nick (2026-10-20) to the central `ApiExceptionHandler` consolidation; no change on this branch. |
| U5 | Whether the downstream accepts `cert-no` as a JSON string (D11). | Local functional run against the downstream. |

Deferred, not part of this branch: `PARTIAL_SUCCESS` for PATCH, any rename of `msgCode`, a shared
response builder, and consolidation of `ApiExceptionHandler`.

## 12. Implementation record

| Sub-task | Commit | Result |
|---|---|---|
| ST-1 design pack | `2aff1c9f` | Docs only. |
| ST-2 env removed from `TRPGetMsgBoard` and `TRPUpdMsgRead` requests | `f82bf7bd` | Targeted run: 13 classes, 67 tests, 0 failures. |
| ST-3 GET `result.data` | `f73e98c7` | Targeted run: 23 classes, 132 tests, 0 failures. |
| ST-4 PATCH `result.data` | `8f4fc5d5` | Targeted run: 26 classes, 141 tests, 0 failures. |

Outcomes that settled earlier open points:

- U2: the PATCH response serializes the key `isRead` without an annotation; `NotificationReadStatusDto`
  was not changed.
- U4: `ApiExceptionHandler` fills `errors[].message` for the notification exceptions through
  `ErrorMessageResolver`. Making those error responses label-free is deferred by Nick (2026-10-20) to the
  central `ApiExceptionHandler` consolidation. There is no change on this branch.
- Success responses of both endpoints are label-free: `messages` and `errors` are `[]`.

Pending: the milestone gates (full `mvn test`, coverage, smoke test, SonarQube) and the frontend change for
the two breaking JSON paths in section 10.

## 13. Amendment: single `msgContent` selected by Accept-Language (ST-6)

Added on 2026-10-20 after a live GET response showed `msgContentChi` and `msgContentEng` on every
notification, which leaves the choice of language to the frontend. This amendment changes section 5 for the
GET response only. PATCH is not affected.

### 13.1 Confirmed (script 018)

- The downstream item `GetMessageBoardMessageItem` carries `msg-content-chi` and `msg-content-eng`. The
  adapter maps both to `NoticeMessage.msgContentChi` and `NoticeMessage.msgContentEng`, and
  `NotificationDto` exposes both.
- Existing precedent: `FundFactSheetWebMapper` chooses by language in the web mapper. For zh_HK it uses the
  Chinese text when it is not null or blank, otherwise the English text; for every other language it uses the
  English text. The command carries no language.
- `RequestLanguageResolver.isZhHk(language)` normalizes the value: `zh`, `zh-HK` and `zh_HK` select zh_HK;
  every other value, including null and blank, selects English (`en`, `en-US`, `fr` and `zh-TW` all give
  English).
- `NotificationCurrentContextBoundaryGuardTest` forbids the strings `RequestHeaderContext`, `Account-Ref` and
  `resolveRequiredAccountRef` in `NotificationController`. The controller therefore reads the plain header
  `@RequestHeader(name = "Accept-Language", required = false)`, as the Login and Forgot Password controllers
  do, and does not use `RequestHeaderContext` or `RequestHeaderContextKeys`.

### 13.2 Decisions

| ID | Decision | Reason |
|---|---|---|
| D12 | Each notification in the GET response has one field `msgContent`; `msgContentChi` and `msgContentEng` are removed. | The frontend must not choose between two languages. |
| D13 | Selection happens in `NotificationWebMapper`: for zh_HK, `msgContent` is the Chinese content when it is not null or blank, otherwise the English content; for every other language, and for a missing or blank header, it is the English content. There is no fallback from English to Chinese. | Same rule as Fund Fact Sheet and Fund Price. |
| D14 | `NoticeMessage`, the APIM DTOs, the adapter, the commands, the use cases and the services are unchanged. The domain keeps both texts. | Language selection belongs to the web mapper; the command carries no language. |
| D15 | `NotificationWebMapper.toResponse(NotificationListResult, String language)` replaces the one-argument method; no overload is kept. `NotificationController.getNotifications` gains the last parameter `@RequestHeader(name = "Accept-Language", required = false) String acceptLanguage` and passes it through unchanged. | Nothing is released; the mapper normalizes the value. |
| D16 | Java adds no text. `msgContent` is downstream text; Java only chooses which of the two texts to return. | Label rule (section 3). |
| D17 | `msgTitle` is not changed by this amendment (see U6). | Out of scope until Nick decides. |

### 13.3 Target contract

One item of `GET /api/v1/notifications` for `Accept-Language: en`, or without the header:

```json
{
  "msgCode": "CPState-20260201",
  "sequence": "32",
  "category": "DOC_AVAIL",
  "msgTitle": "DOCUMENT AVAILABLE",
  "msgContent": "Your Contribution Payment Statement for Jan-Mar 2026 is ready.",
  "isRead": true,
  "startDateTime": "01/02/2026 00:00"
}
```

For `Accept-Language: zh_HK` (or `zh-HK`, or `zh`) the same item has the Chinese content in `msgContent`
when the downstream sent one; otherwise it has the English content.

### 13.4 Consumer impact

| Endpoint | Old path | New path |
|---|---|---|
| GET | `$.result.data.notifications[*].msgContentChi` and `$.result.data.notifications[*].msgContentEng` | `$.result.data.notifications[*].msgContent` |

The frontend must send `Accept-Language` on `GET /api/v1/notifications` and must show `msgContent` as
received. This is a second breaking change on the GET path, in the same release as section 10.

### 13.5 Class-level changes and tests

| Class | Change |
|---|---|
| `NotificationDto` | The two content components become one component `msgContent`. |
| `NotificationWebMapper` | `toResponse(result, language)`; `msgContent` is chosen as in D13. |
| `NotificationController` | `getNotifications` gets the `Accept-Language` header parameter, last. |

Tests changed: `NotificationDtoTest`, `NotificationListResponseTest`, `NotificationWebMapperTest` (call
signature, plus language tests), `NotificationControllerTest` (new argument), `NotificationReferenceDateFlowTest`
and `NotificationResponseSerializationContractTest`. New: `NotificationGetLanguageHttpContractTest`, which
sends real requests with different `Accept-Language` values. Unchanged: `ApimNoticeMessageAdapterTest` and
`NoticeMessageTest`, because the adapter and the domain keep both texts.

The milestone gates (full `mvn test`, coverage, smoke test, SonarQube) ran before this change and are re-run
after it.

### 13.6 Open and unverified

| ID | Item | Settled by |
|---|---|---|
| U6 | `msgTitle` is not the downstream `msg-title`: the adapter ignores that field and passes `MessageType.titleFor(category)` (for example "DOCUMENT AVAILABLE"), which is English text produced in Java. `ApimNoticeMessageAdapterTest` expects "IMPORTANT NOTICE" for category `IMP_NOTE` and the category itself for an unknown one. This conflicts with the label rule (section 3). The source of `MessageType.titleFor` has not been read. | Decision by Nick: keep it, return a code for the frontend to render, or use the downstream `msg-title`. Not part of ST-6. |
| U7 | Whether the frontend already sends `Accept-Language` on this request, and which values it sends. | Frontend confirmation before release. |
| U8 | Whether a Chinese variant other than `zh` and `zh-HK` (for example `zh-TW`) must select Chinese. The shared resolver gives English. | Decision by Nick; a change belongs in `RequestLanguageResolver` and affects every endpoint. |

Status: designed; implementation pending.
