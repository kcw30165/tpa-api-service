package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationController;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.StartRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResultStatus;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthRegistrationControllerWorkflowContractTest {

    @Test
    void registrationStartUsesGenericMutationResponseContract() throws Exception {
        var method = AuthRegistrationController.class.getDeclaredMethod(
                "startRegistration",
                RegistrationStartRequest.class,
                String.class,
                String.class,
                String.class,
                String.class,
                ServerHttpResponse.class);

        assertThat(method.getGenericReturnType().getTypeName()).contains("MutationResponse");
        assertThat(method.getGenericReturnType().getTypeName()).doesNotContain("SimpleMutationResponse");
    }

    @Test
    void missingIdempotencyKeyUsesGenericMutationResponseAndDoesNotCallUseCase() {
        var useCase = new RecordingUseCase();
        var controller = new AuthRegistrationController(
                useCase,
                new RegistrationStartWebMapper(Duration.ofMinutes(5)),
                AuthFlowCookieWriter.defaultWriter(),
                        org.mockito.Mockito.mock(
                                com.bct.ngtpa.apiservice.application.port.in.CompleteRegistrationUseCase.class),
                        org.mockito.Mockito.mock(
                                com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.CompleteRegistrationWebMapper.class),
                        org.mockito.Mockito.mock(
                                com.bct.ngtpa.apiservice.application.port.in.CancelRegistrationUseCase.class),
                        org.mockito.Mockito.mock(
                                com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.CancelRegistrationWebMapper.class),
                        org.mockito.Mockito.mock(
                                com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.XsrfCookieWriter.class));

        StepVerifier.create(controller.startRegistration(
                        new RegistrationStartRequest("GPFS", "id", "01/01/1980", "m@example.com", null, "cap"),
                        null,
                        "corr",
                        "en",
                        null,
                        new MockServerHttpResponse()))
                .assertNext(response -> {
                    assertThat(response).isInstanceOf(org.springframework.http.ResponseEntity.class);
        assertThat(java.util.Objects.requireNonNull(response.getBody()))
                .isInstanceOf(MutationResponse.class);
                    assertThat(java.util.Objects.requireNonNull(response.getBody()).success()).isFalse();
                    assertThat(java.util.Objects.requireNonNull(response.getBody()).status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
                    assertThat(java.util.Objects.requireNonNull(response.getBody()).errors()).isNotEmpty();
                })
                .verifyComplete();

        assertThat(useCase.called).isFalse();
    }

    @Test
    void workflowAlignedCookieBehaviourIsUnchanged() {
        var useCase = new RecordingUseCase();
        var controller = new AuthRegistrationController(
                useCase,
                new RegistrationStartWebMapper(Duration.ofMinutes(5)),
                AuthFlowCookieWriter.defaultWriter(),
                        org.mockito.Mockito.mock(
                                com.bct.ngtpa.apiservice.application.port.in.CompleteRegistrationUseCase.class),
                        org.mockito.Mockito.mock(
                                com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.CompleteRegistrationWebMapper.class),
                        org.mockito.Mockito.mock(
                                com.bct.ngtpa.apiservice.application.port.in.CancelRegistrationUseCase.class),
                        org.mockito.Mockito.mock(
                                com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.CancelRegistrationWebMapper.class),
                        org.mockito.Mockito.mock(
                                com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.XsrfCookieWriter.class));
        var response = new MockServerHttpResponse();

        StepVerifier.create(controller.startRegistration(
                        new RegistrationStartRequest("GPFS", "id", "01/01/1980", "m@example.com", null, "cap"),
                        "idem_abc123",
                        "corr",
                        "en",
                        null,
                        response))
                .assertNext(body -> {
                    assertThat(java.util.Objects.requireNonNull(body.getBody()).success()).isTrue();
                    assertThat(java.util.Objects.requireNonNull(body.getBody()).result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
                    assertThat(java.util.Objects.requireNonNull(body.getBody()).result().data().csrfToken()).isEqualTo("csrf_token_safe");
                })
                .verifyComplete();

        assertThat(response.getCookies()).containsKey("auth_flow_sid");
        assertThat(response.getCookies().getFirst("auth_flow_sid").getValue()).isEqualTo("afs_secret");
    }

    private static final class RecordingUseCase implements StartRegistrationUseCase {
        boolean called;

        @Override
        public Mono<StartRegistrationResult> start(StartRegistrationCommand command) {
            called = true;
            return Mono.just(new StartRegistrationResult(
                    StartRegistrationResultStatus.STARTED,
                    WorkflowNextStep.VERIFY_OTP,
                    "m****r@example.com",
                    300,
                    60,
                    5,
                    "csrf_token_safe",
                    "afs_secret",
                    List.of(),
                    List.of(),
                    RegistrationIdempotencyStartOutcome.STARTED,
                    null));
        }
    }
}
