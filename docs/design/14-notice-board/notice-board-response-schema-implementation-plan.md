# Notice Board - Implementation Plan

Design: `notice-board-response-schema-design.md` (same folder). Decision IDs D1-D11 and unverified
items U1-U5 refer to that document.

## 1. Milestone

One milestone, **M1: notifications use the MutationResponse data shape and the aligned downstream
requests**, split into six sub-tasks (ST-6 was added on 2026-10-20).

## 2. Sub-tasks

### ST-1 Design pack (docs only)

Adds this folder. No behaviour change, so no fail-first and no Maven.

Commit: `docs(notice-board): design MutationResponse schema and env removal for notifications endpoints`

### ST-2 Remove env from the TRPGetMsgBoard and TRPUpdMsgRead requests

Fail-first (tests only, JSON-level so they compile against the current types):

- `ApimNoticeMessageAdapterTest`: serialize the captured request; assert the key set is
  `policy-no`, `cert-no`, `login-user-id`, `login-user-role`, `ref-date` and that `env`, `page`, `size`
  are absent. It fails today on `env`.
- `ApimNotificationReadStatusAdapterTest`: same for `policy-no`, `cert-no`, `login-user-id`,
  `login-user-role`, `ref-date`, `msg-code-long`, with `env` and `req-status` absent.

Implementation: remove the `accountEnv` field from `GetMessageBoardApimRequest` and
`UpdateNotificationReadStatusApimRequest`; remove `.accountEnv(command.accountEnv())` from the two
`toApimRequest` methods; replace the two `body.getAccountEnv()` assertions and rename the test that
pins the `env` JSON key.

Tests to run in the gate: the two adapter tests, `GetMessageBoardApimRequestTest`,
`NotificationsAuthenticatedExecutorBoundaryTest`, `ApimRoutingLedgerCharacterizationTest`,
`ApimAdapterReflectionBranchProbeTest`, `ApimPayloadCryptoService*Test`, `GetNotificationsServiceTest`
and `UpdateNotificationsReadStatusServiceTest`.

Commit: `refactor(notice-board): drop env from TRPGetMsgBoard and TRPUpdMsgRead requests`

### ST-3 GET /api/v1/notifications

Fail-first (JSON-level):

- `NotificationWebMapperTest`: read `/result/data/notifications/0/...` from the serialized mapper
  output; add `success`, `status` (`SUCCESS`), `messages` and `errors` assertions.
- `NotificationControllerTest` (GET): use the real `NotificationWebMapper` and assert the serialized
  result.
- `NotificationReferenceDateFlowTest`: JSON paths move to `$.result.data.notifications[0]...`; add
  `$.success`, `$.status`, `$.messages`, `$.errors`.
- `ApiResponseExceptionHandlingBoundaryTest`: the GET assertion leaves the unwrapped list and a typed
  `Mono<MutationResponse<NotificationListResultResponse>>` assertion is added.
- `NotificationResponseSerializationContractTest`: GET part.

Implementation: `NotificationListResultResponse` (new), `NotificationWebMapper`,
`NotificationController` (GET), `NotificationListResultResponseTest`.

Commit: `refactor(notice-board): wrap GET /notifications in MutationResponse`

### ST-4 PATCH /api/v1/notifications

Fail-first (JSON-level):

- `NotificationReadStatusWebMapperTest`: `response.result().notifications()` becomes serialized
  `/result/data/notifications/...` paths; status stays `UPDATED`.
- `NotificationControllerTest` (PATCH): real `NotificationReadStatusWebMapper`; serialized assertion;
  the expected type changes from `MutationResponse<UpdateNotificationsReadStatusResponse>`.
- `ApiResponseExceptionHandlingBoundaryTest`: the PATCH type string changes to
  `Mono<MutationResponse<UpdateNotificationsReadStatusResultResponse>>`.
- `NotificationResponseSerializationContractTest`: PATCH part, which also settles U2.

Implementation: `UpdateNotificationsReadStatusResultResponse` (new),
`NotificationReadStatusWebMapper`, `NotificationController` (PATCH),
`UpdateNotificationsReadStatusResultResponseTest`, and `@JsonProperty("isRead")` on
`NotificationReadStatusDto` only if U2 shows the key differs.

Commit: `refactor(notice-board): move PATCH /notifications payload under result.data`

### ST-5 Docs

Update the two `/api/v1/notifications` sections of
`docs/refactor/2-controller/controller-refactoring-endpoint-inventory.md` (Java response types) and
set the design status to implemented.

Commit: `docs(notice-board): update notifications endpoint inventory and design status`

### ST-6 Single msgContent by Accept-Language (GET only)

Added after the live GET response showed both `msgContentChi` and `msgContentEng` (design section 13).

Fail-first (compiles against the current types and fails on assertions):

- `NotificationGetLanguageHttpContractTest` (new): the real controller and mapper through `WebTestClient` with
  different `Accept-Language` values; asserts `msgContent` and the absence of the two bilingual fields.
- `NotificationReferenceDateFlowTest`: asserts `msgContent` and the absence of the bilingual fields.
- `NotificationResponseSerializationContractTest`: the exact key set holds `msgContent`.

Implementation: `NotificationDto` (one content field), `NotificationWebMapper.toResponse(result, language)`,
`NotificationController` GET (`Accept-Language` header), and the callers in `NotificationDtoTest`,
`NotificationListResponseTest`, `NotificationWebMapperTest` (with new language tests),
`NotificationControllerTest` and `NotificationResponseSerializationContractTest`.

Commit: `refactor(notice-board): select single msgContent by Accept-Language on GET /notifications`

## 3. Sub-task gates (ST-2 to ST-6, in order, from the worktree root)

1. Fail-first (ST-2 to ST-4): write the new or changed tests and run only those with
   `mvn -Dtest=<classes> test`; they must fail on the missing behaviour, not on a compile error.
2. Implementation.
3. `mvn spotless:apply && mvn spotless:check`
4. `mvn -DskipTests compile`
5. `mvn -DskipTests test-compile`
6. `mvn -Dtest=<classes> test`: the new and changed tests plus every test whose source mentions a
   class changed in the sub-task. The selected classes are listed in the evidence.

Stop on a compile failure and repair all compile errors before the targeted tests.

## 4. Milestone gates (after ST-5, in order)

1. `mvn spotless:check`
2. `mvn test` (full suite)
3. Coverage from the JaCoCo report of gate 2. Targets: instruction at least 95 percent for every
   class added or changed on the branch; branch at least 95 percent for the new records, the request
   DTOs and the mappers; `NotificationController` and the two adapters branch 85-90 percent with a
   one-line justification per uncovered branch.
4. Smoke test on this worktree's `SERVER_PORT`.
5. SonarQube branch analysis with the quality gate.

## 5. Push and merge request

Only after the milestone gates pass. The MR description lists the two breaking JSON paths from
section 10 of the design so the frontend change ships in the same release.

## 6. Execution status

| Sub-task | Status | Commit |
|---|---|---|
| ST-1 design pack | Done | `2aff1c9f` |
| ST-2 env removal | Done | `f82bf7bd` |
| ST-3 GET `result.data` | Done | `f73e98c7` |
| ST-4 PATCH `result.data` | Done | `8f4fc5d5` |
| ST-5 docs | Done in this commit | |
| ST-6 msgContent by Accept-Language | Designed | |
| Milestone gates | Pending (re-run after ST-6) | |

Making the notification error responses label-free is not a sub-task of this branch: it is deferred to the central
`ApiExceptionHandler` consolidation (decision by Nick, 2026-10-20).
