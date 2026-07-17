package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.mapper.CaptchaWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.XsrfCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.CancelRegistrationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.CompleteRegistrationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthCaptchaController;
import com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationController;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaImage;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaVerificationOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CreateCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CreateCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.RefreshCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.VerifyCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.CancelRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.CompleteRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.CreateCaptchaChallengeUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RefreshCaptchaUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.VerifyCaptchaUseCase;
import com.bct.ngtpa.apiservice.application.service.auth.registration.RegistrationStartValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResultStatus;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestCase;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestResultExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(RegistrationTestResultExtension.class)
class RegistrationCaptchaAndStartIntegrationTest {

    private static final String CAPTCHA_PATH = "/api/v1/auth/captcha";
    private static final String START_PATH = "/api/v1/auth/registration/start";
    private static final CaptchaChallengeId CAPTCHA_ID =
            new CaptchaChallengeId("cap_abcdefghijklmnop");
    private static final CaptchaChallengeId REFRESHED_CAPTCHA_ID =
            new CaptchaChallengeId("cap_bcdefghijklmnopq");
    private static final String PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZQmcAAAAASUVORK5CYII=";

    @Test
    @RegistrationTestCase({"TC-002", "TC-003", "TC-004", "TC-005"})
    void captchaChallengeSupportsCorrelationLanguageFallbackAndAnonymousPreStartAccess() {
        AtomicReference<CreateCaptchaChallengeCommand> captured = new AtomicReference<>();
        CreateCaptchaChallengeUseCase create = command -> {
            captured.set(command);
            return Mono.just(new CreateCaptchaChallengeResult(
                    CAPTCHA_ID, new CaptchaImage(PNG_BASE64, "image/png"), 300));
        };
        WebTestClient client = captchaClient(create, verifiedCaptcha(), refreshedCaptcha());

        client.post()
                .uri(CAPTCHA_PATH + "/challenge?lang=zh_HK")
                .header("X-Request-Id", "0041-captcha-request")
                .header("Accept-Language", "en-HK")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-Id", "0041-captcha-request")
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.result.mimeType").isEqualTo("image/png")
                .jsonPath("$.result.expiresInSeconds").isEqualTo(300)
                .jsonPath("$.result.nextStep").doesNotExist();

        assertThat(captured.get().correlationId()).isEqualTo("0041-captcha-request");
        assertThat(captured.get().acceptLanguage()).isEqualTo("en-HK");

        client.post().uri(CAPTCHA_PATH + "/challenge")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .doesNotContain("auth_flow_sid", "X-XSRF-TOKEN", "Idempotency-Key", "Account-Ref"));
    }

    @Test
    @RegistrationTestCase({"TC-006", "TC-007", "TC-008", "TC-009", "TC-010", "TC-011", "TC-012"})
    void captchaVerifyAndRefreshExposeAllSupportedLifecycleOutcomes() {
        VerifyCaptchaUseCase verify = command -> Mono.just(switch (command.answer()) {
            case "correct" -> new VerifyCaptchaChallengeResult(
                    CaptchaVerificationOutcome.VERIFIED, true, CAPTCHA_ID, 5);
            case "locked" -> new VerifyCaptchaChallengeResult(
                    CaptchaVerificationOutcome.LOCKED, false, null, 0);
            case "expired" -> new VerifyCaptchaChallengeResult(
                    CaptchaVerificationOutcome.EXPIRED, false, null, 0);
            case "unknown" -> new VerifyCaptchaChallengeResult(
                    CaptchaVerificationOutcome.NOT_FOUND, false, null, 0);
            default -> new VerifyCaptchaChallengeResult(
                    CaptchaVerificationOutcome.INVALID, false, null, 4);
        });
        WebTestClient client = captchaClient(createdCaptcha(), verify, refreshedCaptcha());

        verify(client, "correct").jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.result.verified").isEqualTo(true);
        verify(client, "wrong").jsonPath("$.status").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.result.remainingAttempts").isEqualTo(4)
                .jsonPath("$.errors[0].code").isEqualTo("err.registration.captcha.invalid");
        verify(client, "locked").jsonPath("$.errors[0].code")
                .isEqualTo("err.registration.captcha.locked");
        verify(client, "expired").jsonPath("$.errors[0].code")
                .isEqualTo("err.registration.captcha.expired");
        verify(client, "unknown").jsonPath("$.errors[0].code")
                .isEqualTo("err.registration.captcha.notFound");

        client.post().uri(CAPTCHA_PATH + "/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("captchaChallengeId", CAPTCHA_ID.value()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.result.captchaChallengeId").isEqualTo(REFRESHED_CAPTCHA_ID.value())
                .jsonPath("$.result.imageBase64").isEqualTo(PNG_BASE64);

        client.post().uri(CAPTCHA_PATH + "/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("captchaChallengeId", "invalid id"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false);
    }

    @Test
    @RegistrationTestCase({
            "TC-013", "TC-014", "TC-015", "TC-016", "TC-017", "TC-018",
            "TC-019", "TC-020", "TC-021", "TC-022", "TC-023"
    })
    void registrationStartHttpBoundaryCoversChannelsValidationIdempotencyAndCookieContract() {
        AtomicReference<StartRegistrationCommand> captured = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        StartRegistrationUseCase useCase = command -> {
            calls.incrementAndGet();
            captured.set(command);
            return Mono.just(started(command.email() != null ? "m****r@example.com" : "9******7"));
        };
        WebTestClient client = startClient(useCase);

        client.post().uri(START_PATH)
                .header("Idempotency-Key", "idem_0123456789abcdef")
                .header("X-Request-Id", "0041-start-email")
                .header("Accept-Language", "en")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(startRequest("GPFS", "member@example.com", null, CAPTCHA_ID.value()))
                .exchange()
                .expectStatus().isOk()
                .expectCookie().httpOnly("auth_flow_sid", true)
                .expectCookie().secure("auth_flow_sid", true)
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.result.nextStep").isEqualTo("VERIFY_OTP")
                .jsonPath("$.result.data.remainingResendAttempts").isEqualTo(5)
                .jsonPath("$.result.data.csrfToken").isEqualTo("csrf_token_safe");

        assertThat(captured.get().email()).isEqualTo("member@example.com");
        assertThat(captured.get().mobile()).isNull();
        assertThat(captured.get().captchaChallengeId()).isEqualTo(CAPTCHA_ID.value());

        client.post().uri(START_PATH)
                .header("Idempotency-Key", "idem_abcdef0123456789")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(startRequest("GPFS", null, "91234567", CAPTCHA_ID.value()))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.result.data.maskedDestination").isEqualTo("9******7");
        assertThat(captured.get().mobile()).isEqualTo("91234567");
        assertThat(captured.get().email()).isNull();

        int beforeMissingKey = calls.get();
        client.post().uri(START_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(startRequest("GPFS", "member@example.com", null, CAPTCHA_ID.value()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.errors[0].code").isEqualTo("err.workflow.idempotency.missing");
        assertThat(calls).hasValue(beforeMissingKey);

        RegistrationStartValidator validator = new RegistrationStartValidator();
        assertThat(validator.validate(new com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartInput(
                "GPFS", "1234567890", "31/01/1980", null, null, CAPTCHA_ID.value())).valid()).isFalse();
        assertThat(validator.validate(new com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartInput(
                "GPFS", "1234567890", "31/01/1980", "member@example.com", "91234567", CAPTCHA_ID.value())).valid()).isFalse();
        assertThat(validator.validate(new com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartInput(
                "BAD", "1", "31/02/1980", "bad-email", null, null)).valid()).isFalse();

        // The command preserves the frontend identity vocabulary; APIM enrichment
        // owns STAFF_NO -> STAFF mapping and is guarded below.
        client.post().uri(START_PATH)
                .header("Idempotency-Key", "idem_staff_01234567")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(startRequest("STAFF_NO", "A12345", null, CAPTCHA_ID.value()))
                .exchange().expectStatus().isOk();
        assertThat(captured.get().identityType()).isEqualTo("STAFF_NO");
    }

    @Test
    @RegistrationTestCase({
            "TC-024", "TC-025", "TC-026", "TC-027", "TC-028",
            "TC-029", "TC-030", "TC-031", "TC-032"
    })
    void stabilizedLookupAndDeliveryContractsRemainWiredIntoRegistrationStart() throws Exception {
        String lookupAdapter = productionSource("ApimRegistrationLookupAdapter.java");
        String requestMapper = productionSource("TrpSearchUnregMemberApimMapper.java");
        String resultMapper = productionSource("TrpSearchUnregMemberApimResultMapper.java");
        String lookupResult = productionSource("RegistrationLookupResult.java");
        String startService = productionSource("StartRegistrationService.java");
        String otpAdapter = productionSource("RegistrationOtpChallengeAdapter.java");
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        String contract = Files.readString(Path.of(
                "src/test/resources/contracts/registration-lookup-apim-contract.csv"));

        // Ownership is intentionally split: the adapter owns transport and
        // structured failure logging, while the result mapper owns downstream
        // semantic classification and item validation.
        assertThat(lookupAdapter)
                .contains("TRPSearchUnregMember")
                .contains("withFailureLogging")
                .contains("registration.lookup.downstream_contract_failure")
                .contains("TrpSearchUnregMemberApimResultMapper")
                .doesNotContain("RegistrationLookupStatus.DOWNSTREAM_ERROR");
        assertThat(requestMapper)
                .contains("command.identityType().name()")
                .contains("RegistrationContactType.EMAIL")
                .contains("RegistrationContactType.MOBILE");
        assertThat(resultMapper)
                .contains("err.tpa.member.not.found")
                .contains("RegistrationLookupStatus.NOT_ELIGIBLE")
                .contains("RegistrationLookupStatus.ELIGIBLE")
                .contains("Reason.UNEXPECTED_ERROR")
                .contains("Reason.EMPTY_DATA")
                .contains("Reason.INVALID_ACCOUNT_DATA")
                .contains("Reason.CONTACT_MISMATCH")
                .contains("validateItem")
                .doesNotContain("RegistrationLookupStatus.DOWNSTREAM_ERROR");
        assertThat(lookupResult)
                .contains("eligible lookup requires at least one account");
        assertThat(contract)
                .contains("eligible-email")
                .contains("eligible-mobile")
                .contains("not-eligible,err.tpa.member.not.found")
                .contains("blank-error-empty-data")
                .contains("malformed-envelope")
                .contains("contact-mismatch")
                .contains("missing-env")
                .contains("missing-trust-code");
        assertThat(startService)
                .contains("RegistrationLookupPort")
                .contains("RegistrationMessagePort")
                .contains("RegistrationOtpPort");
        assertThat(otpAdapter)
                .contains("otpChallengePort.createChallenge(command)")
                .doesNotContain("OTP_SEND_BY_DESTINATION_HASH")
                .doesNotContain("enforceOtpSendRateLimit");
        assertThat(yaml)
                .doesNotContain("registration-start:")
                .doesNotContain("by-destination-hash:");

        WebTestClient deliveryFailure = startClient(command -> Mono.just(new StartRegistrationResult(
                StartRegistrationResultStatus.MESSAGE_DELIVERY_FAILED,
                WorkflowNextStep.NONE,
                null,
                0,
                0,
                0,
                null,
                null,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null)));
        deliveryFailure.post().uri(START_PATH)
                .header("Idempotency-Key", "idem_delivery_01234567")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(startRequest("GPFS", "member@example.com", null, CAPTCHA_ID.value()))
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .doesNotContain("member@example.com", "91234567", "captchaAnswer", "otpCode"));
    }

    private WebTestClient.BodyContentSpec verify(WebTestClient client, String answer) {
        return client.post().uri(CAPTCHA_PATH + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("captchaChallengeId", CAPTCHA_ID.value(), "answer", answer))
                .exchange()
                .expectStatus().isOk()
                .expectBody();
    }

    private WebTestClient captchaClient(
            CreateCaptchaChallengeUseCase create,
            VerifyCaptchaUseCase verify,
            RefreshCaptchaUseCase refresh) {
        return WebTestClient.bindToController(new AuthCaptchaController(
                        create,
                        verify,
                        refresh,
                        new CaptchaWebMapper(
                                (code, locale, accountEnv, trustCode, schemeType) -> "resolved-" + code)))
                .build();
    }

    private WebTestClient startClient(StartRegistrationUseCase useCase) {
        return WebTestClient.bindToController(new AuthRegistrationController(
                        useCase,
                        new RegistrationStartWebMapper(Duration.ofMinutes(5)),
                        AuthFlowCookieWriter.defaultWriter(),
                        Mockito.mock(CompleteRegistrationUseCase.class),
                        Mockito.mock(CompleteRegistrationWebMapper.class),
                        Mockito.mock(CancelRegistrationUseCase.class),
                        Mockito.mock(CancelRegistrationWebMapper.class),
                        Mockito.mock(XsrfCookieWriter.class)))
                .build();
    }

    private CreateCaptchaChallengeUseCase createdCaptcha() {
        return command -> Mono.just(new CreateCaptchaChallengeResult(
                CAPTCHA_ID, new CaptchaImage(PNG_BASE64, "image/png"), 300));
    }

    private VerifyCaptchaUseCase verifiedCaptcha() {
        return command -> Mono.just(new VerifyCaptchaChallengeResult(
                CaptchaVerificationOutcome.VERIFIED, true, CAPTCHA_ID, 5));
    }

    private RefreshCaptchaUseCase refreshedCaptcha() {
        return command -> Mono.just(new RefreshCaptchaChallengeResult(
                REFRESHED_CAPTCHA_ID, new CaptchaImage(PNG_BASE64, "image/png"), 300));
    }

    private RegistrationStartRequest startRequest(
            String identityType,
            String email,
            String mobile,
            String captchaChallengeId) {
        return new RegistrationStartRequest(
                identityType,
                "1234567890",
                "31/01/1980",
                email,
                mobile,
                captchaChallengeId);
    }

    private StartRegistrationResult started(String maskedDestination) {
        return new StartRegistrationResult(
                StartRegistrationResultStatus.STARTED,
                WorkflowNextStep.VERIFY_OTP,
                maskedDestination,
                300,
                60,
                5,
                "csrf_token_safe",
                "afs_0123456789abcdef",
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null);
    }

    private String productionSource(String fileName) throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            Path path = paths
                    .filter(candidate -> candidate.getFileName().toString().equals(fileName))
                    .findFirst()
                    .orElseThrow();
            return Files.readString(path);
        }
    }
}
