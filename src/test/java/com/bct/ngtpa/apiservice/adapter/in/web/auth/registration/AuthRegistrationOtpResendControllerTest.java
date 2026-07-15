package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.RegistrationIdempotencyProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpResendWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationOtpController;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.ResendRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.in.VerifyRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

class AuthRegistrationOtpResendControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void otpResendEndpointExists() throws Exception {
        Method method = AuthRegistrationOtpController.class.getDeclaredMethod(
                "resendOtp", String.class, String.class, String.class, String.class, String.class, String.class, String.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.path()).contains("/resend");
    }

    @Test
    void otpResendRequiresAuthFlowCookie() throws Exception {
        var useCase = new RecordingUseCase();
        var controller = resendController(useCase);
        String json = objectMapper.writeValueAsString(controller.resendOtp(null, "csrf", "idem", "req", "en", null, null).block());
        assertThat(json).contains("err.registration.authFlow.invalid");
        assertThat(useCase.called).isFalse();
    }

    @Test
    void otpResendRequiresXsrfTokenHeader() throws Exception {
        var useCase = new RecordingUseCase();
        var controller = resendController(useCase);
        String json = objectMapper.writeValueAsString(controller.resendOtp("afs", null, "idem", "req", "en", null, null).block());
        assertThat(json).contains("err.registration.csrf.invalid");
        assertThat(useCase.called).isFalse();
    }

    @Test
    void otpResendRequiresIdempotencyKeyAndDoesNotUseRequestIdAsFallback() throws Exception {
        var useCase = new RecordingUseCase();
        var controller = resendController(useCase);
        String json = objectMapper.writeValueAsString(controller.resendOtp("afs", "csrf", null, "req", "en", null, null).block());
        assertThat(json).contains("err.workflow.idempotency.missing");
        assertThat(useCase.called).isFalse();
    }

    @Test
    void otpResendMapsHeadersAndCookieToUseCaseCommand() {
        var useCase = new RecordingUseCase();
        var controller = resendController(useCase);

        controller.resendOtp("afs_abc", "csrf_abc", "idem_abc", "req_abc", "zh-HK", "https://portal.example", "https://portal.example/page").block();

        assertThat(useCase.called).isTrue();
        assertThat(useCase.command.authFlowSessionId().value()).isEqualTo("afs_abc");
        assertThat(useCase.command.csrfToken()).isEqualTo("csrf_abc");
        assertThat(useCase.command.idempotencyKey().value()).isEqualTo("idem_abc");
        assertThat(useCase.command.correlationId()).isEqualTo("req_abc");
        assertThat(useCase.command.acceptLanguage()).isEqualTo("zh-HK");
        assertThat(useCase.command.origin()).isEqualTo("https://portal.example");
        assertThat(useCase.command.referer()).isEqualTo("https://portal.example/page");
    }

    @Test
    void otpResendSuccessDoesNotSetCookieValueInBody() throws Exception {
        var controller = resendController(new RecordingUseCase());
        String json = objectMapper.writeValueAsString(controller.resendOtp("afs_abc", "csrf_abc", "idem_abc", "req_abc", "en", null, null).block());
        assertThat(json).contains("VERIFY_OTP", "registration-otp-verification");
        assertThat(json).doesNotContain("afs_abc", "csrf_abc", "idem_abc", "otpChallengeId", "activeOtpChallengeId", "123456");
    }

    private static final class RecordingUseCase implements ResendRegistrationOtpUseCase {
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

    private static AuthRegistrationOtpController resendController(ResendRegistrationOtpUseCase resendUseCase) {
    return new AuthRegistrationOtpController(
            resendUseCase,
            new RegistrationOtpResendWebMapper(null),
            command -> Mono.error(new AssertionError("verify use case must not be called")),
            new RegistrationOtpVerifyWebMapper(null),
            new RegistrationIdempotencyProperties());
}
}
