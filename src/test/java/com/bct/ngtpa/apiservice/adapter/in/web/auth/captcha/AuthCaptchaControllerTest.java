package com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.dto.CaptchaRefreshRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.dto.CaptchaVerifyRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.mapper.CaptchaWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthCaptchaController;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaImage;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaVerificationOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CreateCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CreateCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.RefreshCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.RefreshCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.VerifyCaptchaChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.CreateCaptchaChallengeUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RefreshCaptchaUseCase;
import com.bct.ngtpa.apiservice.application.port.in.VerifyCaptchaUseCase;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class AuthCaptchaControllerTest {

        private static final String CHALLENGE_PATH = "/api/v1/auth/captcha/challenge";
        private static final String VERIFY_PATH = "/api/v1/auth/captcha/verify";
        private static final String REFRESH_PATH = "/api/v1/auth/captcha/refresh";
        private static final CaptchaChallengeId CHALLENGE_ID = new CaptchaChallengeId("cap_abcdefghijklmnop");
        private static final CaptchaChallengeId NEW_CHALLENGE_ID = new CaptchaChallengeId("cap_bcdefghijklmnopq");

        @Test
        void captchaChallengeEndpointExists() {
                webClient(stubCreateUseCase(), stubVerifyUseCase(), stubRefreshUseCase())
                                .post()
                                .uri(CHALLENGE_PATH)
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody()
                                .jsonPath("$.success").isEqualTo(true);
        }

        @Test
        void captchaVerifyEndpointExists() {
                webClient(stubCreateUseCase(), stubVerifyUseCase(), stubRefreshUseCase())
                                .post()
                                .uri(VERIFY_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("captchaChallengeId", CHALLENGE_ID.value(), "answer", "AbC123x"))
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody()
                                .jsonPath("$.success").isEqualTo(true);
        }

        @Test
        void captchaRefreshEndpointExists() {
                webClient(stubCreateUseCase(), stubVerifyUseCase(), stubRefreshUseCase())
                                .post()
                                .uri(REFRESH_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("captchaChallengeId", CHALLENGE_ID.value()))
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody()
                                .jsonPath("$.success").isEqualTo(true);
        }

        @Test
        void captchaEndpointsDoNotRequireAuthFlowCookieCsrfIdempotencyOrAccountRef() {
                AtomicBoolean createCalled = new AtomicBoolean();
                AtomicBoolean verifyCalled = new AtomicBoolean();
                AtomicBoolean refreshCalled = new AtomicBoolean();

                CreateCaptchaChallengeUseCase createUseCase = command -> {
                        createCalled.set(true);
                        return Mono.just(
                                        new CreateCaptchaChallengeResult(CHALLENGE_ID,
                                                        new CaptchaImage("base64-image", "image/png"), 300));
                };
                VerifyCaptchaUseCase verifyUseCase = command -> {
                        verifyCalled.set(true);
                        return Mono
                                        .just(new VerifyCaptchaChallengeResult(CaptchaVerificationOutcome.VERIFIED,
                                                        true, CHALLENGE_ID, 5));
                };
                RefreshCaptchaUseCase refreshUseCase = command -> {
                        refreshCalled.set(true);
                        return Mono.just(new RefreshCaptchaChallengeResult(NEW_CHALLENGE_ID,
                                        new CaptchaImage("base64-refresh", "image/png"), 300));
                };

                WebTestClient client = webClient(createUseCase, verifyUseCase, refreshUseCase);

                client.post().uri(CHALLENGE_PATH).exchange().expectStatus().isOk();
                client.post().uri(VERIFY_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("captchaChallengeId", CHALLENGE_ID.value(), "answer", "AbC123x"))
                                .exchange()
                                .expectStatus().isOk();
                client.post().uri(REFRESH_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of())
                                .exchange()
                                .expectStatus().isOk();

                assertThat(createCalled).isTrue();
                assertThat(verifyCalled).isTrue();
                assertThat(refreshCalled).isTrue();
        }

        @Test
        void captchaEndpointsDoNotUseLangQueryParameter() {
                AtomicReference<CreateCaptchaChallengeCommand> captured = new AtomicReference<>();
                CreateCaptchaChallengeUseCase createUseCase = command -> {
                        captured.set(command);
                        return Mono.just(
                                        new CreateCaptchaChallengeResult(CHALLENGE_ID,
                                                        new CaptchaImage("base64-image", "image/png"), 300));
                };

                webClient(createUseCase, stubVerifyUseCase(), stubRefreshUseCase())
                                .post()
                                .uri(CHALLENGE_PATH + "?lang=zh_HK")
                                .header("Accept-Language", "en-HK")
                                .exchange()
                                .expectStatus().isOk();

                assertThat(captured.get().acceptLanguage()).isEqualTo("en-HK");
        }

        @Test
        void captchaChallengeMapsUseCaseResultToMutationResponse() {
                AtomicReference<CreateCaptchaChallengeCommand> captured = new AtomicReference<>();
                CreateCaptchaChallengeUseCase createUseCase = command -> {
                        captured.set(command);
                        return Mono.just(
                                        new CreateCaptchaChallengeResult(CHALLENGE_ID,
                                                        new CaptchaImage("base64-image", "image/png"), 300));
                };

                webClient(createUseCase, stubVerifyUseCase(), stubRefreshUseCase())
                                .post()
                                .uri(CHALLENGE_PATH)
                                .header(RequestCorrelation.REQUEST_ID_HEADER, "req-123")
                                .header("Accept-Language", "zh-HK")
                                .exchange()
                                .expectStatus().isOk()
                                .expectHeader().valueEquals(RequestCorrelation.REQUEST_ID_HEADER, "req-123")
                                .expectBody()
                                .jsonPath("$.success").isEqualTo(true)
                                .jsonPath("$.status").isEqualTo("SUCCESS")
                                .jsonPath("$.result.nextStep").isEqualTo(WorkflowNextStep.NONE.name())
                                .jsonPath("$.result.data.captchaChallengeId").isEqualTo(CHALLENGE_ID.value())
                                .jsonPath("$.result.data.imageBase64").isEqualTo("base64-image")
                                .jsonPath("$.result.data.mimeType").isEqualTo("image/png")
                                .jsonPath("$.result.data.expiresInSeconds").isEqualTo(300)
                                .jsonPath("$.messages").isArray()
                                .jsonPath("$.errors").isArray();

                assertThat(captured.get().correlationId()).isEqualTo("req-123");
                assertThat(captured.get().acceptLanguage()).isEqualTo("zh-HK");
        }

        @Test
        void captchaChallengeResponseDoesNotIncludeWorkflowFields() {
                webClient(stubCreateUseCase(), stubVerifyUseCase(), stubRefreshUseCase())
                                .post()
                                .uri(CHALLENGE_PATH)
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody()
                                .jsonPath("$.result.nextStep").isEqualTo(WorkflowNextStep.NONE.name())
                                .jsonPath("$.result.pageRef").doesNotExist()
                                .jsonPath("$.result.data").exists();
        }

        @Test
        void captchaChallengeResponseDoesNotExposeAnswerOrHash() {
                webClient(stubCreateUseCase(), stubVerifyUseCase(), stubRefreshUseCase())
                                .post()
                                .uri(CHALLENGE_PATH)
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(String.class)
                                .value(body -> assertThat(body)
                                                .doesNotContain("answer")
                                                .doesNotContain("answerHash")
                                                .doesNotContain("expectedAnswer")
                                                .doesNotContain("captchaText")
                                                .doesNotContain("captchaCode")
                                                .doesNotContain("ngtpa:auth:captcha:"));
        }

        @Test
        void captchaVerifyMapsSuccessToVerifiedTrue() {
                webClient(stubCreateUseCase(), stubVerifyUseCase(), stubRefreshUseCase())
                                .post()
                                .uri(VERIFY_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("captchaChallengeId", CHALLENGE_ID.value(), "answer", "AbC123x"))
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody()
                                .jsonPath("$.success").isEqualTo(true)
                                .jsonPath("$.status").isEqualTo("SUCCESS")
                                .jsonPath("$.result.nextStep").isEqualTo(WorkflowNextStep.NONE.name())
                                .jsonPath("$.result.data.verified").isEqualTo(true)
                                .jsonPath("$.result.data.captchaChallengeId").isEqualTo(CHALLENGE_ID.value());
        }

        @Test
        void captchaVerifyMapsInvalidAnswerToValidationFailure() {
                VerifyCaptchaUseCase verifyUseCase = command -> Mono
                                .just(new VerifyCaptchaChallengeResult(CaptchaVerificationOutcome.INVALID, false, null,
                                                4));

                webClient(stubCreateUseCase(), verifyUseCase, stubRefreshUseCase())
                                .post()
                                .uri(VERIFY_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("captchaChallengeId", CHALLENGE_ID.value(), "answer", "wrong"))
                                .exchange()
                                .expectStatus().isEqualTo(422)
                                .expectBody()
                                .jsonPath("$.success").isEqualTo(false)
                                .jsonPath("$.status").isEqualTo("VALIDATION_FAILED")
                                .jsonPath("$.result.nextStep").isEqualTo(WorkflowNextStep.NONE.name())
                                .jsonPath("$.result.data.verified").isEqualTo(false)
                                .jsonPath("$.result.data.remainingAttempts").isEqualTo(4)
                                .jsonPath("$.errors[0].code").isEqualTo("err.captcha.verification.invalid")
                                .jsonPath("$.errors[0].targets[0]").isEqualTo("captchaAnswer");
        }

        @Test
        void captchaVerifyRejectsAlreadyVerifiedChallengeForCorrectAnswerUsingApprovedHttpContract() {
                assertAlreadyVerifiedHttpFailure("aBc123");
        }

        @Test
        void captchaVerifyRejectsAlreadyVerifiedChallengeForIncorrectAnswerUsingSameApprovedHttpContract() {
                assertAlreadyVerifiedHttpFailure("wrong");
        }

        private void assertAlreadyVerifiedHttpFailure(String submittedAnswer) {
                VerifyCaptchaUseCase verifyUseCase = command -> Mono
                                .just(new VerifyCaptchaChallengeResult(CaptchaVerificationOutcome.INVALID, false, null,
                                                0));
                webClient(stubCreateUseCase(), verifyUseCase, stubRefreshUseCase())
                                .post()
                                .uri(VERIFY_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("captchaChallengeId", CHALLENGE_ID.value(), "answer",
                                                submittedAnswer))
                                .exchange()
                                .expectStatus().isEqualTo(422)
                                .expectBody(String.class)
                                .value(body -> assertThat(body)
                                                .contains("\"success\":false")
                                                .contains("\"status\":\"VALIDATION_FAILED\"")
                                                .contains("err.captcha.verification.invalid")
                                                .contains("captchaAnswer")
                                                .contains("\"nextStep\":\"NONE\"")
                                                .doesNotContain("VERIFIED", submittedAnswer, "answerHash", "Redis",
                                                                "Exception"));
        }

        @Test
        void captchaVerifyMapsExpiredToValidationFailure() {
                assertVerifyFailureOutcome(CaptchaVerificationOutcome.EXPIRED, "err.captcha.verification.invalid",
                                "captchaAnswer");
        }

        @Test
        void captchaVerifyMapsLockedToValidationFailure() {
                assertVerifyFailureOutcome(CaptchaVerificationOutcome.LOCKED, "err.captcha.verification.invalid",
                                "captchaAnswer");
        }

        @Test
        void captchaVerifyMapsConsumedToValidationFailure() {
                assertVerifyFailureOutcome(CaptchaVerificationOutcome.CONSUMED, "err.captcha.verification.invalid",
                                "captchaAnswer");
        }

        @Test
        void captchaVerifyMapsNotFoundToValidationFailure() {
                assertVerifyFailureOutcome(CaptchaVerificationOutcome.NOT_FOUND, "err.captcha.verification.invalid",
                                "captchaAnswer");
        }

        @Test
        void captchaVerifyRequestToStringDoesNotExposeAnswer() {
                assertThat(new CaptchaVerifyRequest(CHALLENGE_ID.value(), "AbC123x").toString())
                                .doesNotContain("AbC123x")
                                .doesNotContain(CHALLENGE_ID.value());
        }

        @Test
        void captchaVerifyRequestRejectsMissingAnswer() {
                AtomicBoolean called = new AtomicBoolean();
                VerifyCaptchaUseCase verifyUseCase = command -> {
                        called.set(true);
                        return Mono
                                        .just(new VerifyCaptchaChallengeResult(CaptchaVerificationOutcome.VERIFIED,
                                                        true, CHALLENGE_ID, 5));
                };

                webClient(stubCreateUseCase(), verifyUseCase, stubRefreshUseCase())
                                .post()
                                .uri(VERIFY_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("captchaChallengeId", CHALLENGE_ID.value(), "answer", "   "))
                                .exchange()
                                .expectStatus().isBadRequest()
                                .expectBody()
                                .jsonPath("$.success").isEqualTo(false)
                                .jsonPath("$.status").isEqualTo("VALIDATION_FAILED")
                                .jsonPath("$.errors[0].code").isEqualTo("err.request.validation.failed")
                                .jsonPath("$.errors[0].targets[0]").isEqualTo("captchaAnswer");

                assertThat(called).isFalse();
        }

        @Test
        void captchaVerifyRequestRejectsMissingChallengeId() {
                AtomicBoolean called = new AtomicBoolean();
                VerifyCaptchaUseCase verifyUseCase = command -> {
                        called.set(true);
                        return Mono
                                        .just(new VerifyCaptchaChallengeResult(CaptchaVerificationOutcome.VERIFIED,
                                                        true, CHALLENGE_ID, 5));
                };

                webClient(stubCreateUseCase(), verifyUseCase, stubRefreshUseCase())
                                .post()
                                .uri(VERIFY_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("captchaChallengeId", " ", "answer", "AbC123x"))
                                .exchange()
                                .expectStatus().isBadRequest()
                                .expectBody()
                                .jsonPath("$.success").isEqualTo(false)
                                .jsonPath("$.status").isEqualTo("VALIDATION_FAILED")
                                .jsonPath("$.errors[0].code").isEqualTo("err.request.validation.failed")
                                .jsonPath("$.errors[0].targets[0]").isEqualTo("captchaChallengeId");

                assertThat(called).isFalse();
        }

        @Test
        void captchaVerifyRequestRejectsMalformedChallengeId() {
                AtomicBoolean called = new AtomicBoolean();
                VerifyCaptchaUseCase verifyUseCase = command -> {
                        called.set(true);
                        return Mono
                                        .just(new VerifyCaptchaChallengeResult(CaptchaVerificationOutcome.VERIFIED,
                                                        true, CHALLENGE_ID, 5));
                };

                webClient(stubCreateUseCase(), verifyUseCase, stubRefreshUseCase())
                                .post()
                                .uri(VERIFY_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("captchaChallengeId", "bad-id", "answer", "AbC123x"))
                                .exchange()
                                .expectStatus().isBadRequest()
                                .expectBody()
                                .jsonPath("$.success").isEqualTo(false)
                                .jsonPath("$.status").isEqualTo("VALIDATION_FAILED")
                                .jsonPath("$.errors[0].code").isEqualTo("err.request.validation.failed")
                                .jsonPath("$.errors[0].targets[0]").isEqualTo("captchaChallengeId");

                assertThat(called).isFalse();
        }

        @Test
        void captchaRefreshMapsUseCaseResultToMutationResponse() {
                AtomicReference<RefreshCaptchaChallengeCommand> captured = new AtomicReference<>();
                RefreshCaptchaUseCase refreshUseCase = command -> {
                        captured.set(command);
                        return Mono.just(new RefreshCaptchaChallengeResult(NEW_CHALLENGE_ID,
                                        new CaptchaImage("base64-refresh", "image/png"), 300));
                };

                webClient(stubCreateUseCase(), stubVerifyUseCase(), refreshUseCase)
                                .post()
                                .uri(REFRESH_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("captchaChallengeId", CHALLENGE_ID.value()))
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody()
                                .jsonPath("$.success").isEqualTo(true)
                                .jsonPath("$.status").isEqualTo("SUCCESS")
                                .jsonPath("$.result.nextStep").isEqualTo(WorkflowNextStep.NONE.name())
                                .jsonPath("$.result.data.captchaChallengeId").isEqualTo(NEW_CHALLENGE_ID.value())
                                .jsonPath("$.result.data.imageBase64").isEqualTo("base64-refresh")
                                .jsonPath("$.result.data.mimeType").isEqualTo("image/png")
                                .jsonPath("$.result.data.expiresInSeconds").isEqualTo(300);

                assertThat(captured.get().oldCaptchaChallengeId()).isEqualTo(CHALLENGE_ID);
        }

        @Test
        void captchaRefreshResponseDoesNotIncludeOldChallengeState() {
                webClient(stubCreateUseCase(), stubVerifyUseCase(), stubRefreshUseCase())
                                .post()
                                .uri(REFRESH_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("captchaChallengeId", CHALLENGE_ID.value()))
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(String.class)
                                .value(body -> assertThat(body)
                                                .doesNotContain("oldCaptchaChallengeId")
                                                .doesNotContain("answer")
                                                .doesNotContain("answerHash")
                                                .doesNotContain("oldStatus")
                                                .doesNotContain("pageRef"));
        }

        @Test
        void captchaRefreshAllowsMissingOldChallengeIdIfUseCaseSupportsIt() {
                AtomicReference<RefreshCaptchaChallengeCommand> captured = new AtomicReference<>();
                RefreshCaptchaUseCase refreshUseCase = command -> {
                        captured.set(command);
                        return Mono.just(new RefreshCaptchaChallengeResult(NEW_CHALLENGE_ID,
                                        new CaptchaImage("base64-refresh", "image/png"), 300));
                };

                webClient(stubCreateUseCase(), stubVerifyUseCase(), refreshUseCase)
                                .post()
                                .uri(REFRESH_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new CaptchaRefreshRequest(null))
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody()
                                .jsonPath("$.result.nextStep").isEqualTo(WorkflowNextStep.NONE.name())
                                .jsonPath("$.result.data.captchaChallengeId").isEqualTo(NEW_CHALLENGE_ID.value());

                assertThat(captured.get().oldCaptchaChallengeId()).isNull();
        }

        @Test
        void captchaRefreshRequestRejectsMalformedChallengeId() {
                AtomicBoolean called = new AtomicBoolean();
                RefreshCaptchaUseCase refreshUseCase = command -> {
                        called.set(true);
                        return Mono.just(new RefreshCaptchaChallengeResult(NEW_CHALLENGE_ID,
                                        new CaptchaImage("base64-refresh", "image/png"), 300));
                };

                webClient(stubCreateUseCase(), stubVerifyUseCase(), refreshUseCase)
                                .post()
                                .uri(REFRESH_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("captchaChallengeId", "bad-id"))
                                .exchange()
                                .expectStatus().isBadRequest()
                                .expectBody()
                                .jsonPath("$.success").isEqualTo(false)
                                .jsonPath("$.status").isEqualTo("VALIDATION_FAILED")
                                .jsonPath("$.errors[0].code").isEqualTo("err.request.validation.failed")
                                .jsonPath("$.errors[0].targets[0]").isEqualTo("captchaChallengeId");

                assertThat(called).isFalse();
        }

        private void assertVerifyFailureOutcome(CaptchaVerificationOutcome outcome, String code, String target) {
                VerifyCaptchaUseCase verifyUseCase = command -> Mono
                                .just(new VerifyCaptchaChallengeResult(outcome, false, null, 0));

                webClient(stubCreateUseCase(), verifyUseCase, stubRefreshUseCase())
                                .post()
                                .uri(VERIFY_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("captchaChallengeId", CHALLENGE_ID.value(), "answer", "wrong"))
                                .exchange()
                                .expectStatus().isEqualTo(422)
                                .expectBody()
                                .jsonPath("$.success").isEqualTo(false)
                                .jsonPath("$.status").isEqualTo("VALIDATION_FAILED")
                                .jsonPath("$.errors[0].code").isEqualTo(code)
                                .jsonPath("$.errors[0].targets[0]").isEqualTo(target);
        }

        private WebTestClient webClient(
                        CreateCaptchaChallengeUseCase createUseCase,
                        VerifyCaptchaUseCase verifyUseCase,
                        RefreshCaptchaUseCase refreshUseCase) {
                return WebTestClient.bindToController(new AuthCaptchaController(
                                createUseCase,
                                verifyUseCase,
                                refreshUseCase,
                                new CaptchaWebMapper((code, locale, accountEnv, trustCode, schemeType) -> "resolved-"
                                                + code),
                                new CaptchaHttpResponses()))
                                .build();
        }

        private CreateCaptchaChallengeUseCase stubCreateUseCase() {
                return command -> Mono.just(
                                new CreateCaptchaChallengeResult(CHALLENGE_ID,
                                                new CaptchaImage("base64-image", "image/png"), 300));
        }

        private VerifyCaptchaUseCase stubVerifyUseCase() {
                return command -> Mono
                                .just(new VerifyCaptchaChallengeResult(CaptchaVerificationOutcome.VERIFIED, true,
                                                CHALLENGE_ID, 5));
        }

        private RefreshCaptchaUseCase stubRefreshUseCase() {
                return command -> Mono.just(new RefreshCaptchaChallengeResult(NEW_CHALLENGE_ID,
                                new CaptchaImage("base64-refresh", "image/png"), 300));
        }
}