package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
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
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthRegistrationControllerWorkflowContractTest {

  @Test
  void registrationStartUsesGenericMutationResponseContract() throws Exception {
    var method =
        RegistrationController.class.getDeclaredMethod(
            "startRegistration",
            RegistrationStartRequest.class,
            String.class,
            String.class,
            String.class,
            String.class,
            ServerWebExchange.class);

    assertThat(method.getGenericReturnType().getTypeName()).contains("MutationResponse");
    assertThat(method.getGenericReturnType().getTypeName())
        .doesNotContain("SimpleMutationResponse");
  }

  @Test
  void missingIdempotencyKeyUsesGenericMutationResponseAndDoesNotCallUseCase() {
    var useCase = new RecordingUseCase();
    var controller =
        new RegistrationController(
            useCase,
            new RegistrationStartWebMapper(Duration.ofMinutes(5)),
            AuthFlowCookieWriter.defaultWriter(),
            org.mockito.Mockito.mock(
                com.bct.ngtpa.apiservice.application.port.in.CompleteRegistrationUseCase.class),
            org.mockito.Mockito.mock(
                com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper
                    .CompleteRegistrationWebMapper.class),
            org.mockito.Mockito.mock(
                com.bct.ngtpa.apiservice.application.port.in.CancelRegistrationUseCase.class),
            org.mockito.Mockito.mock(
                com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper
                    .CancelRegistrationWebMapper.class),
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    StepVerifier.create(
            controller.startRegistration(
                new RegistrationStartRequest(
                    "GPFS", "id", "01/01/1980", "m@example.com", null, "cap"),
                null,
                "corr",
                "en",
                null,
                startExchange()))
        .assertNext(
            response -> {
              assertThat(response).isInstanceOf(org.springframework.http.ResponseEntity.class);
              assertThat(java.util.Objects.requireNonNull(response.getBody()))
                  .isInstanceOf(MutationResponse.class);
              assertThat(java.util.Objects.requireNonNull(response.getBody()).success()).isFalse();
              assertThat(java.util.Objects.requireNonNull(response.getBody()).status())
                  .isEqualTo(ApiStatus.VALIDATION_FAILED);
              assertThat(java.util.Objects.requireNonNull(response.getBody()).errors())
                  .isNotEmpty();
            })
        .verifyComplete();

    assertThat(useCase.called).isFalse();
  }

  @Test
  void workflowAlignedCookieBehaviourIsUnchanged() {
    var useCase = new RecordingUseCase();
    var controller =
        new RegistrationController(
            useCase,
            new RegistrationStartWebMapper(Duration.ofMinutes(5)),
            AuthFlowCookieWriter.defaultWriter(),
            org.mockito.Mockito.mock(
                com.bct.ngtpa.apiservice.application.port.in.CompleteRegistrationUseCase.class),
            org.mockito.Mockito.mock(
                com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper
                    .CompleteRegistrationWebMapper.class),
            org.mockito.Mockito.mock(
                com.bct.ngtpa.apiservice.application.port.in.CancelRegistrationUseCase.class),
            org.mockito.Mockito.mock(
                com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper
                    .CancelRegistrationWebMapper.class),
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    var exchange = startExchange();

    StepVerifier.create(
            controller.startRegistration(
                new RegistrationStartRequest(
                    "GPFS", "id", "01/01/1980", "m@example.com", null, "cap"),
                "idem_abc123",
                "corr",
                "en",
                null,
                exchange))
        .assertNext(
            body -> {
              assertThat(java.util.Objects.requireNonNull(body.getBody()).success()).isTrue();
              assertThat(java.util.Objects.requireNonNull(body.getBody()).result().nextStep())
                  .isEqualTo(WorkflowNextStep.VERIFY_OTP);
              assertThat(
                      java.util.Objects.requireNonNull(body.getBody()).result().data().csrfToken())
                  .isEqualTo("csrf_token_safe");
            })
        .verifyComplete();

    assertThat(exchange.getResponse().getCookies()).containsKey("auth_flow_sid");
    assertThat(exchange.getResponse().getCookies().getFirst("auth_flow_sid").getValue())
        .isEqualTo("afs_secret");
  }

  private static ServerWebExchange startExchange() {
    return MockServerWebExchange.from(
        MockServerHttpRequest.post("/api/v1/auth/registration/start"));
  }

  private static final class RecordingUseCase implements StartRegistrationUseCase {
    boolean called;

    @Override
    public Mono<StartRegistrationResult> start(StartRegistrationCommand command) {
      called = true;
      return Mono.just(
          new StartRegistrationResult(
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
