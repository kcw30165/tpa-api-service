package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCompleteWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordDetailsOtpWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordIdentityWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.XsrfCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.CancelForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel.CancelForgotPasswordCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel.CancelForgotPasswordResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel.CancelForgotPasswordStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthForgotPasswordCancelControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void cancelEndpointExists() throws Exception {
        Method method = AuthForgotPasswordController.class.getDeclaredMethod(
                "cancel",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                ServerHttpResponse.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.path()).contains("/cancel");
    }

    @Test
    void missingAuthFlowCookieReturnsSuccessfulCleanupAndClearsBothCookies() {
        var controller = controller(new RecordingUseCase(new CancelForgotPasswordResult(
                CancelForgotPasswordStatus.CANCELLED, true, true, List.of())));
        var response = new MockServerHttpResponse();

        StepVerifier.create(controller.cancel(
                null, "csrf_abc", "https://portal.example", "https://portal.example/fp", "req_abc", "en", response))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(entity.getBody().success()).isTrue();
                    assertThat(entity.getBody().result().nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
                    assertThat(entity.getBody().errors()).isEmpty();
                })
                .verifyComplete();

        assertThat(response.getCookies().getFirst("auth_flow_sid").getValue()).isEmpty();
        assertThat(response.getCookies().getFirst("XSRF-TOKEN").getValue()).isEmpty();
    }

    @Test
    void missingCsrfReturnsForbiddenWithoutCookieClearing() {
        var controller = controller(new RecordingUseCase(new CancelForgotPasswordResult(
                CancelForgotPasswordStatus.CANCELLED, true, true, List.of())));
        var response = new MockServerHttpResponse();

        StepVerifier.create(controller.cancel(
                "afs_abc", null, "https://portal.example", "https://portal.example/fp", "req_abc", "en", response))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(entity.getBody().success()).isFalse();
                    assertThat(entity.getBody().status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
                    assertThat(entity.getBody().result().nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
                    assertThat(entity.getBody().errors()).singleElement()
                            .extracting(error -> error.code())
                            .isEqualTo("err.forgot-password.csrf.invalid");
                })
                .verifyComplete();

        assertThat(response.getCookies().getFirst("auth_flow_sid")).isNull();
        assertThat(response.getCookies().getFirst("XSRF-TOKEN")).isNull();
    }

    @Test
    void successfulCancellationReturnsOkReturnToLoginClearsCookiesAndEchoesRequestId() {
        var controller = controller(new RecordingUseCase(new CancelForgotPasswordResult(
                CancelForgotPasswordStatus.CANCELLED, true, true, List.of())));
        var response = new MockServerHttpResponse();

        StepVerifier.create(controller.cancel(
                "afs_abc", "csrf_abc", "https://portal.example", "https://portal.example/fp", "req_abc", "en", response))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(entity.getBody().success()).isTrue();
                    assertThat(entity.getBody().result().nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
                    JsonNode json = objectMapper.valueToTree(entity.getBody());
                    assertThat(json.at("/result/data").isObject()).isTrue();
                    assertThat(json.at("/result/data").size()).isZero();
                    assertThat(entity.toString()).doesNotContain("afs_abc", "csrf_abc");
                })
                .verifyComplete();

        assertThat(response.getCookies().getFirst("auth_flow_sid").getValue()).isEmpty();
        assertThat(response.getCookies().getFirst("XSRF-TOKEN").getValue()).isEmpty();
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("req_abc");
    }

    @Test
    void invalidAuthFlowReturnsBadRequestWithoutCookieClearing() {
        List<WorkflowErrorDescriptor> errors = List.of(error(
                WorkflowErrorType.FORM, "err.forgot-password.authFlow.invalid"));
        var controller = controller(new RecordingUseCase(new CancelForgotPasswordResult(
                CancelForgotPasswordStatus.INVALID_AUTH_FLOW, false, false, errors)));
        var response = new MockServerHttpResponse();

        StepVerifier.create(controller.cancel(
                "afs_abc", "csrf_abc", "https://portal.example", "https://portal.example/fp", "req_abc", "en", response))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(entity.getBody().success()).isFalse();
                    assertThat(entity.getBody().result().nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
                    assertThat(entity.getBody().errors()).singleElement()
                            .extracting(error -> error.code())
                            .isEqualTo("err.forgot-password.authFlow.invalid");
                })
                .verifyComplete();

        assertThat(response.getCookies().getFirst("auth_flow_sid")).isNull();
        assertThat(response.getCookies().getFirst("XSRF-TOKEN")).isNull();
    }

    @Test
    void systemFailureReturnsServiceUnavailableWithoutCookieClearing() {
        List<WorkflowErrorDescriptor> errors = List.of(error(
                WorkflowErrorType.SYSTEM, "err.system.unavailable"));
        var controller = controller(new RecordingUseCase(new CancelForgotPasswordResult(
                CancelForgotPasswordStatus.SYSTEM_FAILED, false, false, errors)));
        var response = new MockServerHttpResponse();

        StepVerifier.create(controller.cancel(
                "afs_abc", "csrf_abc", "https://portal.example", "https://portal.example/fp", "req_abc", "en", response))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(entity.getBody().success()).isFalse();
                    assertThat(entity.getBody().result().nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
                    assertThat(entity.getBody().errors()).singleElement()
                            .extracting(error -> error.code())
                            .isEqualTo("err.system.unavailable");
                })
                .verifyComplete();

        assertThat(response.getCookies().getFirst("auth_flow_sid")).isNull();
        assertThat(response.getCookies().getFirst("XSRF-TOKEN")).isNull();
    }

    private static WorkflowErrorDescriptor error(WorkflowErrorType type, String code) {
        return new WorkflowErrorDescriptor(
                type, code, null, List.of("PAGE"), WorkflowErrorSeverity.ERROR, "SERVER");
    }

    private static final class RecordingUseCase implements CancelForgotPasswordUseCase {
        private final CancelForgotPasswordResult result;

        RecordingUseCase(CancelForgotPasswordResult result) {
            this.result = result;
        }

        @Override
        public Mono<CancelForgotPasswordResult> cancel(CancelForgotPasswordCommand command) {
            return Mono.just(result);
        }
    }

    private static AuthForgotPasswordController controller(CancelForgotPasswordUseCase cancelUseCase) {
        return new AuthForgotPasswordController(
                command -> Mono.error(new AssertionError("start use case must not be called")),
                new ForgotPasswordStartWebMapper(Duration.ofMinutes(5)),
                command -> Mono.error(new AssertionError("identity use case must not be called")),
                new ForgotPasswordIdentityWebMapper(),
                command -> Mono.error(new AssertionError("details use case must not be called")),
                new ForgotPasswordDetailsOtpWebMapper(),
                command -> Mono.error(new AssertionError("complete use case must not be called")),
                new ForgotPasswordCompleteWebMapper(),
                cancelUseCase,
                new ForgotPasswordCancelWebMapper(),
                AuthFlowCookieWriter.defaultWriter(),
                new XsrfCookieWriter(new com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties()));
    }
}