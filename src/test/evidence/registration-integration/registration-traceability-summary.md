# Registration Integration Traceability Summary

## Final Result

- Catalogue cases: **82**
- Passed: **82**
- Failed, errored, skipped, or not run: **0**
- Evidence source: `target/registration-integration-evidence/registration-test-results.csv`

## Implementation Areas

- **Harness, captcha, and registration start:** TC-001 through TC-032 — 32/32 PASS
- **OTP lifecycle:** TC-033 through TC-052 — 20/20 PASS
- **Username, completion, and cancellation:** TC-053 through TC-072 — 20/20 PASS
- **Stabilization and security:** TC-073 through TC-082 — 10/10 PASS

## Surefire Traceability

- `com.bct.ngtpa.apiservice.adapter.out.redis.auth.RegistrationRedisSensitiveDataSafetyIntegrationTest` — 2 catalogue case(s)
- `com.bct.ngtpa.apiservice.adapter.out.registration.RegistrationDeliveryEnvironmentIntegrationTest` — 2 catalogue case(s)
- `com.bct.ngtpa.apiservice.architecture.RegistrationLoggingLookupStabilizationIntegrationTest` — 2 catalogue case(s)
- `com.bct.ngtpa.apiservice.integration.registration.RegistrationCancelLifecycleIntegrationTest` — 4 catalogue case(s)
- `com.bct.ngtpa.apiservice.integration.registration.RegistrationCaptchaAndStartIntegrationTest` — 11 catalogue case(s)
- `com.bct.ngtpa.apiservice.integration.registration.RegistrationCompletionPasswordIntegrationTest` — 1 catalogue case(s)
- `com.bct.ngtpa.apiservice.integration.registration.RegistrationCompletionPrerequisiteIntegrationTest` — 1 catalogue case(s)
- `com.bct.ngtpa.apiservice.integration.registration.RegistrationCompletionResilienceIntegrationTest` — 4 catalogue case(s)
- `com.bct.ngtpa.apiservice.integration.registration.RegistrationCompletionSuccessIntegrationTest` — 2 catalogue case(s)
- `com.bct.ngtpa.apiservice.integration.registration.RegistrationContractStabilizationIntegrationTest` — 2 catalogue case(s)
- `com.bct.ngtpa.apiservice.integration.registration.RegistrationIntegrationHarnessTest` — 1 catalogue case(s)
- `com.bct.ngtpa.apiservice.integration.registration.RegistrationLanguageCorrelationIntegrationTest` — 2 catalogue case(s)
- `com.bct.ngtpa.apiservice.integration.registration.RegistrationLookupProductionPathIntegrationTest` — 6 catalogue case(s)
- `com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpChallengeLifecycleIntegrationTest` — 6 catalogue case(s)
- `com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpResendServiceIntegrationTest` — 5 catalogue case(s)
- `com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpVerificationLifecycleIntegrationTest` — 9 catalogue case(s)
- `com.bct.ngtpa.apiservice.integration.registration.RegistrationStartWorkflowIntegrationTest` — 14 catalogue case(s)
- `com.bct.ngtpa.apiservice.integration.registration.RegistrationUsernameBoundaryIntegrationTest` — 1 catalogue case(s)
- `com.bct.ngtpa.apiservice.integration.registration.RegistrationUsernameLifecycleIntegrationTest` — 7 catalogue case(s)

## Catalogue Evidence

| Test ID | Feature | Scenario | Status | Surefire evidence |
|---|---|---|---|---|
| TC-001 | Captcha generation | Generate captcha successfully | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationIntegrationHarnessTest.xml` |
| TC-002 | Captcha generation | Request ID is echoed | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCaptchaAndStartIntegrationTest.xml` |
| TC-003 | Captcha generation | Missing or blank request ID is rejected | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCaptchaAndStartIntegrationTest.xml` |
| TC-004 | Captcha generation | Language fallback | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCaptchaAndStartIntegrationTest.xml` |
| TC-005 | Captcha generation | No registration credentials required | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCaptchaAndStartIntegrationTest.xml` |
| TC-006 | Captcha verify | Verify correct answer | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCaptchaAndStartIntegrationTest.xml` |
| TC-007 | Captcha verify | Wrong answer decrements attempts | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCaptchaAndStartIntegrationTest.xml` |
| TC-008 | Captcha verify | Lock after maximum failures | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCaptchaAndStartIntegrationTest.xml` |
| TC-009 | Captcha verify | Expired challenge | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCaptchaAndStartIntegrationTest.xml` |
| TC-010 | Captcha verify | Unknown challenge | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCaptchaAndStartIntegrationTest.xml` |
| TC-011 | Captcha refresh | Refresh active challenge | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCaptchaAndStartIntegrationTest.xml` |
| TC-012 | Captcha refresh | Refresh unknown/expired challenge | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCaptchaAndStartIntegrationTest.xml` |
| TC-013 | Registration start | Eligible member starts with email | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationStartWorkflowIntegrationTest.xml` |
| TC-014 | Registration start | Eligible member starts with mobile | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationStartWorkflowIntegrationTest.xml` |
| TC-015 | Registration start | Captcha required but missing | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationStartWorkflowIntegrationTest.xml` |
| TC-016 | Registration start | Captcha invalid or unverified | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationStartWorkflowIntegrationTest.xml` |
| TC-017 | Registration start | Captcha one-time consumption | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationStartWorkflowIntegrationTest.xml` |
| TC-018 | Registration start | Missing Idempotency-Key | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationStartWorkflowIntegrationTest.xml` |
| TC-019 | Registration start | Same key and same request replay | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationStartWorkflowIntegrationTest.xml` |
| TC-020 | Registration start | Same key with different request | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationStartWorkflowIntegrationTest.xml` |
| TC-021 | Registration start | Exactly one contact is required | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationStartWorkflowIntegrationTest.xml` |
| TC-022 | Registration start | Identity/date/contact format validation | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationStartWorkflowIntegrationTest.xml` |
| TC-023 | Registration start | STAFF_NO boundary mapping | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationLookupProductionPathIntegrationTest.xml` |
| TC-024 | Registration lookup | Eligible lookup has valid account data | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationStartWorkflowIntegrationTest.xml` |
| TC-025 | Registration lookup | Member not found | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationStartWorkflowIntegrationTest.xml` |
| TC-026 | Registration lookup | Unexpected APIM business-looking error | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationLookupProductionPathIntegrationTest.xml` |
| TC-027 | Registration lookup | Blank success with empty data | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationLookupProductionPathIntegrationTest.xml` |
| TC-028 | Registration lookup | Malformed/missing APIM envelope | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationLookupProductionPathIntegrationTest.xml` |
| TC-029 | Registration lookup | Eligible item missing required account field | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationLookupProductionPathIntegrationTest.xml` |
| TC-030 | Registration lookup | Contact mismatch | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationLookupProductionPathIntegrationTest.xml` |
| TC-031 | Registration start | No registration-start rate limit | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationStartWorkflowIntegrationTest.xml` |
| TC-032 | Registration start | Message delivery failure | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationStartWorkflowIntegrationTest.xml` |
| TC-033 | OTP resend | First resend succeeds | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpChallengeLifecycleIntegrationTest.xml` |
| TC-034 | OTP resend | Five resends are allowed | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpChallengeLifecycleIntegrationTest.xml` |
| TC-035 | OTP resend | Sixth resend is rejected | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpChallengeLifecycleIntegrationTest.xml` |
| TC-036 | OTP resend | Cooldown enforcement | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpChallengeLifecycleIntegrationTest.xml` |
| TC-037 | OTP resend | Initial send did not consume resend count | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpChallengeLifecycleIntegrationTest.xml` |
| TC-038 | OTP resend | Idempotent replay | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpResendServiceIntegrationTest.xml` |
| TC-039 | OTP resend | Missing/invalid session cookie | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpResendServiceIntegrationTest.xml` |
| TC-040 | OTP resend | Missing/invalid CSRF | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpResendServiceIntegrationTest.xml` |
| TC-041 | OTP resend | Origin/Referer rejected | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpResendServiceIntegrationTest.xml` |
| TC-042 | OTP resend | Expired/locked/verified challenge | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpChallengeLifecycleIntegrationTest.xml` |
| TC-043 | OTP resend | Provider failure does not leak content | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpResendServiceIntegrationTest.xml` |
| TC-044 | OTP verify | Correct OTP with one trust | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpVerificationLifecycleIntegrationTest.xml` |
| TC-045 | OTP verify | Correct OTP with multiple trusts | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpVerificationLifecycleIntegrationTest.xml` |
| TC-046 | OTP verify | No eligible trusts after verification | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpVerificationLifecycleIntegrationTest.xml` |
| TC-047 | OTP verify | Wrong OTP decrements per-challenge attempts | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpVerificationLifecycleIntegrationTest.xml` |
| TC-048 | OTP verify | Five attempts per send | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpVerificationLifecycleIntegrationTest.xml` |
| TC-049 | OTP verify | Resend creates fresh verify allowance | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpVerificationLifecycleIntegrationTest.xml` |
| TC-050 | OTP verify | Expired OTP | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpVerificationLifecycleIntegrationTest.xml` |
| TC-051 | OTP verify | Idempotent verified replay | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpVerificationLifecycleIntegrationTest.xml` |
| TC-052 | OTP verify | Session/CSRF/origin guards | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationOtpVerificationLifecycleIntegrationTest.xml` |
| TC-053 | Username check | Available username | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationUsernameLifecycleIntegrationTest.xml` |
| TC-054 | Username check | Username validation matrix | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationUsernameLifecycleIntegrationTest.xml` |
| TC-055 | Username check | Selected trust validation | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationUsernameLifecycleIntegrationTest.xml` |
| TC-056 | Username check | Taken username | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationUsernameLifecycleIntegrationTest.xml` |
| TC-057 | Username check | Unknown APIM username rejection | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationUsernameLifecycleIntegrationTest.xml` |
| TC-058 | Username check | Technical downstream failure | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationUsernameLifecycleIntegrationTest.xml` |
| TC-059 | Username check | Same trust and username repeat | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationUsernameLifecycleIntegrationTest.xml` |
| TC-060 | Username check | Idempotency-Key is ignored | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationUsernameBoundaryIntegrationTest.xml` |
| TC-061 | Registration complete | Complete final trust | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCompletionSuccessIntegrationTest.xml` |
| TC-062 | Registration complete | Complete with remaining trusts | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCompletionSuccessIntegrationTest.xml` |
| TC-063 | Registration complete | Password validation | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCompletionPasswordIntegrationTest.xml` |
| TC-064 | Registration complete | Missing username/selected trust/OTP verified state | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCompletionPrerequisiteIntegrationTest.xml` |
| TC-065 | Registration complete | Completion lock/concurrency | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCompletionResilienceIntegrationTest.xml` |
| TC-066 | Registration complete | Idempotent completion replay | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCompletionResilienceIntegrationTest.xml` |
| TC-067 | Registration complete | Possible committed downstream timeout | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCompletionResilienceIntegrationTest.xml` |
| TC-068 | Registration complete | APIM validation/business rejection | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCompletionResilienceIntegrationTest.xml` |
| TC-069 | Registration cancel | Cancel active flow | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCancelLifecycleIntegrationTest.xml` |
| TC-070 | Registration cancel | Cancel is safe to repeat | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCancelLifecycleIntegrationTest.xml` |
| TC-071 | Registration cancel | Missing/invalid CSRF or origin | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCancelLifecycleIntegrationTest.xml` |
| TC-072 | Registration cancel | Missing or expired session | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationCancelLifecycleIntegrationTest.xml` |
| TC-073 | MutationResponse contract | Envelope consistency | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationContractStabilizationIntegrationTest.xml` |
| TC-074 | Localization | Accept-Language source of truth | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationLanguageCorrelationIntegrationTest.xml` |
| TC-075 | Correlation | X-Request-Id propagation | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationLanguageCorrelationIntegrationTest.xml` |
| TC-076 | Sensitive-data safety | Response/log redaction | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.adapter.out.redis.auth.RegistrationRedisSensitiveDataSafetyIntegrationTest.xml` |
| TC-077 | Structured logging | Completed 4xx and 5xx levels | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.architecture.RegistrationLoggingLookupStabilizationIntegrationTest.xml` |
| TC-078 | Lookup stabilization | Only two lookup domain outcomes | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.architecture.RegistrationLoggingLookupStabilizationIntegrationTest.xml` |
| TC-079 | Error-key stabilization | Canonical public key inventory | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.integration.registration.RegistrationContractStabilizationIntegrationTest.xml` |
| TC-080 | Redis safety | Opaque key and persisted-data audit | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.adapter.out.redis.auth.RegistrationRedisSensitiveDataSafetyIntegrationTest.xml` |
| TC-081 | Non-production delivery override | Physical override boundary | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.adapter.out.registration.RegistrationDeliveryEnvironmentIntegrationTest.xml` |
| TC-082 | Production delivery behavior | No override in PROD/DR | PASS | `target/surefire-reports/TEST-com.bct.ngtpa.apiservice.adapter.out.registration.RegistrationDeliveryEnvironmentIntegrationTest.xml` |
