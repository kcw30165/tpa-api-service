package com.bct.ngtpa.apiservice.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationOtpVerifyRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpResendWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationOtpController;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.ResendRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.in.VerifyRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import reactor.core.publisher.Mono;

class P7RegistrationOtpIntegrationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void p7OtpEndpointsExistAndOnlyExpectedOtpEndpointsExist() throws Exception {
        Method resend = AuthRegistrationOtpController.class.getDeclaredMethod(
                "resendOtp",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class);

        Method verify = AuthRegistrationOtpController.class.getDeclaredMethod(
                "verifyOtp",
                RegistrationOtpVerifyRequest.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class);

        assertThat(AuthRegistrationOtpController.class.getAnnotation(RequestMapping.class).value())
                .contains("/api/v1/auth/registration/otp");
        assertThat(resend.getAnnotation(PostMapping.class).path()).contains("/resend");
        assertThat(verify.getAnnotation(PostMapping.class).path()).contains("/verify");
    }

    @Test
    void p7OtpEndpointsRequireAuthFlowCookieXsrfAndIdempotencyKey() throws Exception {
        var resendUseCase = new RecordingResendUseCase();
        var resend = otpController(resendUseCase, failVerifyUseCase());
        assertThat(json(resend.resendOtp(null, "csrf", "idem", "req", "en", null, null).block()))
                .contains("registration.authFlow.invalid");
        assertThat(json(resend.resendOtp("afs", null, "idem", "req", "en", null, null).block()))
                .contains("registration.csrf.invalid");
        assertThat(json(resend.resendOtp("afs", "csrf", null, "req", "en", null, null).block()))
                .contains("workflow.idempotency.missing");
        assertThat(resendUseCase.called).isFalse();

        var verifyUseCase = new RecordingVerifyUseCase();
        var verify = otpController(failResendUseCase(), verifyUseCase);
        assertThat(json(verify
                .verifyOtp(new RegistrationOtpVerifyRequest("123456"), null, "csrf", "idem", "req", "en", null, null)
                .block())).contains("registration.authFlow.invalid");
        assertThat(json(verify
                .verifyOtp(new RegistrationOtpVerifyRequest("123456"), "afs", null, "idem", "req", "en", null, null)
                .block())).contains("registration.csrf.invalid");
        assertThat(json(verify
                .verifyOtp(new RegistrationOtpVerifyRequest("123456"), "afs", "csrf", null, "req", "en", null, null)
                .block())).contains("workflow.idempotency.missing");
        assertThat(verifyUseCase.called).isFalse();
    }

    @Test
    void p7OtpEndpointsDoNotUseRequestIdAsIdempotencyKeyAndDoNotRequireAccountRef() throws Exception {
        var resendUseCase = new RecordingResendUseCase();
        var resend = otpController(resendUseCase, failVerifyUseCase());
        assertThat(json(
                resend.resendOtp(validAuthFlowSessionId(), "csrf", null, "x-request-id", "zh-HK", null, null).block()))
                .contains("workflow.idempotency.missing");
        resend.resendOtp(validAuthFlowSessionId(), "csrf", validIdempotencyKey(), "req", "zh-HK", null, null).block();
        assertThat(resendUseCase.called).isTrue();
        assertThat(resendUseCase.command.acceptLanguage()).isEqualTo("zh-HK");

        var verifyUseCase = new RecordingVerifyUseCase();
        var verify = otpController(failResendUseCase(), verifyUseCase);
        assertThat(json(verify.verifyOtp(new RegistrationOtpVerifyRequest("123456"), validAuthFlowSessionId(), "csrf",
                null, "x-request-id", "zh-HK", null, null).block())).contains("workflow.idempotency.missing");
        verify.verifyOtp(new RegistrationOtpVerifyRequest("123456"), validAuthFlowSessionId(), "csrf",
                validIdempotencyKey(), "req", "zh-HK", null, null).block();
        assertThat(verifyUseCase.called).isTrue();
        assertThat(verifyUseCase.command.acceptLanguage()).isEqualTo("zh-HK");
    }

    @Test
    void p7OtpVerifyRequestShapeContainsOnlyCode() {
        var fields = List.of(RegistrationOtpVerifyRequest.class.getRecordComponents()).stream()
                .map(component -> component.getName())
                .toList();
        assertThat(fields).containsExactly("code");
        assertThat(fields).doesNotContain("otpChallengeId", "authFlowSessionId", "email", "mobile", "identityType",
                "identityValue", "dateOfBirth", "lang");
        assertThat(new RegistrationOtpVerifyRequest("123456").toString()).doesNotContain("123456");
    }

    @Test
    void p7OtpResendAndVerifySuccessUseWorkflowContractAndDoNotExposeSecrets() throws Exception {
        var resend = otpController(new RecordingResendUseCase(), failVerifyUseCase());
        String resendJson = json(resend
                .resendOtp(validAuthFlowSessionId(), "csrf_secret", validIdempotencyKey(), "req", "en", null, null)
                .block());
        assertThat(resendJson).contains("success", "status", "result", "messages", "errors", "VERIFY_OTP",
                "registration-otp-verification", "maskedDestination", "expiresInSeconds", "resendCooldownSeconds",
                "remainingResendAttempts");
        assertThat(resendJson).doesNotContain("afs_secret", "auth_flow_sid", "csrf_secret", "csrfToken",
                "csrfTokenHash", "otpChallengeId", "activeOtpChallengeId", "123456", "member@example.com", "3024102",
                "04/04/1982");

        var verify = otpController(failResendUseCase(), new RecordingVerifyUseCase());
        String verifyJson = json(verify.verifyOtp(new RegistrationOtpVerifyRequest("123456"), validAuthFlowSessionId(),
                "csrf_secret", validIdempotencyKey(), "req", "en", null, null).block());
        assertThat(verifyJson).contains("success", "status", "result", "messages", "errors", "USERNAME_CHECK",
                "registration-username-check", "unregisteredTrusts", "accountEnv", "policyNo", "certNo", "trustCode");
        assertThat(verifyJson).doesNotContain("afs_secret", "auth_flow_sid", "csrf_secret", "csrfToken",
                "csrfTokenHash", "otpChallengeId", "activeOtpChallengeId", "123456", "member@example.com", "3024102",
                "04/04/1982", "apimEmail", "apimMobile");
    }

    @Test
    void p7OtpVerifyMultipleTrustsReturnsSelectTrust() throws Exception {
        var verifyUseCase = new RecordingVerifyUseCase();
        verifyUseCase.multipleTrusts = true;
        var verify = otpController(failResendUseCase(), verifyUseCase);
        String json = json(verify.verifyOtp(new RegistrationOtpVerifyRequest("123456"), validAuthFlowSessionId(),
                "csrf", validIdempotencyKey(), "req", "en", null, null).block());
        assertThat(json).contains("SELECT_TRUST", "registration-trust-selection", "00000008802", "00000008803");
        assertThat(json).doesNotContain("apimEmail", "apimMobile", "member@example.com");
    }

    private static AuthRegistrationOtpController otpController(
            ResendRegistrationOtpUseCase resendUseCase,
            VerifyRegistrationOtpUseCase verifyUseCase) {
        return new AuthRegistrationOtpController(
                resendUseCase,
                new RegistrationOtpResendWebMapper(null),
                verifyUseCase,
                new RegistrationOtpVerifyWebMapper(null));
    }

    private static ResendRegistrationOtpUseCase failResendUseCase() {
        return command -> Mono.error(new AssertionError("resend use case must not be called"));
    }

    private static VerifyRegistrationOtpUseCase failVerifyUseCase() {
        return command -> Mono.error(new AssertionError("verify use case must not be called"));
    }

    private static String validAuthFlowSessionId() {
        for (String candidate : List.of(
                "sid_0123456789abcdef",
                "sid_0123456789abcdef0123456789abcdef",
                "afs_0123456789abcdef",
                "afs_0123456789abcdef0123456789abcdef",
                "01234567-89ab-cdef-0123-456789abcdef",
                "opaqueAuthFlowSessionId0123456789")) {
            try {
                return new AuthFlowSessionId(candidate).value();
            } catch (RuntimeException ignored) {
                // Try the next candidate; this helper follows current value-object rules.
            }
        }
        throw new AssertionError("No valid AuthFlowSessionId test fixture candidate matched current validation rules");
    }

    private static String validIdempotencyKey() {
        for (String candidate : List.of(
                "idem_0123456789abcdef",
                "idem_0123456789abcdef0123456789abcdef",
                "01234567-89ab-cdef-0123-456789abcdef",
                "opaqueIdempotencyKey0123456789")) {
            try {
                return new RegistrationIdempotencyKey(candidate).value();
            } catch (RuntimeException ignored) {
                // Try the next candidate; this helper follows current value-object rules.
            }
        }
        throw new AssertionError(
                "No valid RegistrationIdempotencyKey test fixture candidate matched current validation rules");
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static final class RecordingResendUseCase implements ResendRegistrationOtpUseCase {
        boolean called;
        ResendRegistrationOtpCommand command;

        @Override
        public Mono<ResendRegistrationOtpResult> resend(ResendRegistrationOtpCommand command) {
            called = true;
            this.command = command;
            return Mono.just(new ResendRegistrationOtpResult(
                    ResendRegistrationOtpResultStatus.RESENT,
                    WorkflowNextStep.VERIFY_OTP,
                    "m****r@example.com",
                    300,
                    60,
                    4,
                    List.of(),
                    List.of(),
                    RegistrationIdempotencyStartOutcome.STARTED,
                    null));
        }
    }

    private static final class RecordingVerifyUseCase implements VerifyRegistrationOtpUseCase {
        boolean called;
        boolean multipleTrusts;
        VerifyRegistrationOtpCommand command;

        @Override
        public Mono<VerifyRegistrationOtpResult> verify(VerifyRegistrationOtpCommand command) {
            called = true;
            this.command = command;
            List<RegistrationUnregisteredTrust> trusts = multipleTrusts
                    ? List.of(new RegistrationUnregisteredTrust("OI", "00000008802", "2", "JPM"),
                            new RegistrationUnregisteredTrust("OI", "00000008803", "3", "BCT"))
                    : List.of(new RegistrationUnregisteredTrust("OI", "00000008802", "2", "JPM"));
            return Mono.just(new VerifyRegistrationOtpResult(
                    VerifyRegistrationOtpResultStatus.VERIFIED,
                    multipleTrusts ? WorkflowNextStep.SELECT_TRUST : WorkflowNextStep.USERNAME_CHECK,
                    trusts,
                    null,
                    List.of(),
                    List.of(),
                    RegistrationIdempotencyStartOutcome.STARTED,
                    null));
        }
    }
}
