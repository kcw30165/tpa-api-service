package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.RegistrationIdempotencyProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationOtpVerifyRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpResendWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationOtpController;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.VerifyRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

class AuthRegistrationOtpVerifyControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void otpVerifyEndpointExists() throws Exception {
        Method method = AuthRegistrationOtpController.class.getDeclaredMethod(
                "verifyOtp", RegistrationOtpVerifyRequest.class, String.class, String.class, String.class, String.class,
                String.class, String.class, String.class,
                org.springframework.http.server.reactive.ServerHttpResponse.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.path()).contains("/verify");
    }

    @Test
    void requestDtoExcludesInternalFieldsAndRedactsCode() {
        var fields = List.of(RegistrationOtpVerifyRequest.class.getRecordComponents()).stream()
                .map(component -> component.getName()).toList();
        assertThat(fields).containsExactly("code");
        assertThat(new RegistrationOtpVerifyRequest("123456").toString()).doesNotContain("123456");
    }

    @Test
    void otpVerifyRequiresAuthFlowCookie() throws Exception {
        var useCase = new RecordingUseCase();

        var controller = otpController(useCase);

        String json = objectMapper.writeValueAsString(controller
                .verifyOtp(new RegistrationOtpVerifyRequest("123456"), null, "csrf", "idem", "req", "en", null, null,
                new org.springframework.mock.http.server.reactive.MockServerHttpResponse())
                .block());
        assertThat(json).contains("err.registration.authFlow.invalid");
        assertThat(useCase.called).isFalse();
    }

    @Test
    void otpVerifyRequiresXsrfTokenHeader() throws Exception {
        var useCase = new RecordingUseCase();

        var controller = otpController(useCase);

        String json = objectMapper.writeValueAsString(controller
                .verifyOtp(new RegistrationOtpVerifyRequest("123456"), "afs", null, "idem", "req", "en", null, null,
                new org.springframework.mock.http.server.reactive.MockServerHttpResponse())
                .block());
        assertThat(json).contains("err.registration.csrf.invalid");
        assertThat(useCase.called).isFalse();
    }

    @Test
    void otpVerifyRequiresIdempotencyKeyAndDoesNotUseRequestIdAsFallback() throws Exception {
        var useCase = new RecordingUseCase();

        var controller = otpController(useCase);

        String json = objectMapper.writeValueAsString(controller
                .verifyOtp(new RegistrationOtpVerifyRequest("123456"), "afs", "csrf", null, "req", "en", null, null,
                new org.springframework.mock.http.server.reactive.MockServerHttpResponse())
                .block());
        assertThat(json).contains("err.workflow.idempotency.missing");
        assertThat(useCase.called).isFalse();
    }

    @Test
    void otpVerifyRequiresCode() throws Exception {
        var useCase = new RecordingUseCase();

        var controller = otpController(useCase);

        String json = objectMapper.writeValueAsString(controller
                .verifyOtp(new RegistrationOtpVerifyRequest(" "), "afs", "csrf", "idem", "req", "en", null, null,
                new org.springframework.mock.http.server.reactive.MockServerHttpResponse())
                .block());
        assertThat(json).contains("err.registration.otp.invalid");
        assertThat(useCase.called).isFalse();
    }

    @Test
    void otpVerifyMapsHeadersCookieAndBodyToUseCaseCommand() {
        var useCase = new RecordingUseCase();

        var controller = otpController(useCase);

        controller.verifyOtp(new RegistrationOtpVerifyRequest("123456"), "afs_abc", "csrf_abc", "idem_abc", "req_abc",
                "zh-HK", "https://portal.example", "https://portal.example/page",
                new org.springframework.mock.http.server.reactive.MockServerHttpResponse()).block();

        assertThat(useCase.called).isTrue();
        assertThat(useCase.command.authFlowSessionId().value()).isEqualTo("afs_abc");
        assertThat(useCase.command.csrfToken()).isEqualTo("csrf_abc");
        assertThat(useCase.command.otpCode()).isEqualTo("123456");
        assertThat(useCase.command.idempotencyKey().value()).isEqualTo("idem_abc");
        assertThat(useCase.command.correlationId()).isEqualTo("req_abc");
        assertThat(useCase.command.acceptLanguage()).isEqualTo("zh-HK");
    }

    @Test
    void otpVerifySuccessDoesNotReturnSecrets() throws Exception {

        var controller = otpController(new RecordingUseCase());

        com.fasterxml.jackson.databind.JsonNode json = objectMapper.valueToTree(controller.verifyOtp(new RegistrationOtpVerifyRequest("123456"),
            "afs_abc", "csrf_abc", "idem_abc", "req_abc", "en", null, null,
            new org.springframework.mock.http.server.reactive.MockServerHttpResponse()).block());
        assertThat(json.at("/body/result/nextStep").asText()).isEqualTo("USERNAME_CHECK");
        assertThat(json.at("/body/result/data/unregisteredTrusts/0/accountEnv").asText()).isEqualTo("OI");
        assertThat(json.at("/body/result/pageRef").isMissingNode()).isTrue();
        assertThat(json.toString()).doesNotContain("registration-username-check");
        assertThat(json.toString()).doesNotContain("afs_abc", "csrf_abc", "idem_abc", "123456", "otpChallengeId",
                "activeOtpChallengeId", "member@example.com", "3024102", "04/04/1982");
    }

    private static final class RecordingUseCase implements VerifyRegistrationOtpUseCase {
        boolean called;
        VerifyRegistrationOtpCommand command;

        @Override
        public Mono<VerifyRegistrationOtpResult> verify(VerifyRegistrationOtpCommand command) {
            called = true;
            this.command = command;
            return Mono.just(new VerifyRegistrationOtpResult(
                    VerifyRegistrationOtpResultStatus.VERIFIED,
                    WorkflowNextStep.USERNAME_CHECK,
                    List.of(new RegistrationUnregisteredTrust("OI", "JPM")),
                    null,
                    List.of(),
                    List.of(),
                    RegistrationIdempotencyStartOutcome.STARTED,
                    null));
        }
    }

    private static AuthRegistrationOtpController otpController(VerifyRegistrationOtpUseCase verifyUseCase) {
        return new AuthRegistrationOtpController(com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter.defaultWriter(),
                command -> Mono.error(new AssertionError("resend use case must not be called")),
                new RegistrationOtpResendWebMapper(null),
                verifyUseCase,
                new RegistrationOtpVerifyWebMapper(
                new com.bct.ngtpa.apiservice.adapter.in.web.workflow.WorkflowMutationResponseMapper()),
                new RegistrationIdempotencyProperties());
    }
}
