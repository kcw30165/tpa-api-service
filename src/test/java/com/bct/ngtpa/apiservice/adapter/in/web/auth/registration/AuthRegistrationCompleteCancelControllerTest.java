package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.CompleteRegistrationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.CancelRegistrationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.CompleteRegistrationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration.RegistrationCancellationPageMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration.RegistrationSuccessPageMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.CancelRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.CompleteRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.cancel.CancelRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.cancel.CancelRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.cancel.CancelRegistrationResultStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResultStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Mono;

class AuthRegistrationCompleteCancelControllerTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void completeRegistrationSuccessUsesNextStepAndDataOnly() throws Exception {
    var controller =
        controller(
            new CompleteUseCaseStub(
                new CompleteRegistrationResult(
                    CompleteRegistrationResultStatus.REGISTERED_WITH_REMAINING_TRUSTS,
                    WorkflowNextStep.REGISTRATION_SUCCESS,
                    true,
                    false,
                    List.of(
                        new RegistrationLookupAccount(
                            "ENV1", "TL", null, null, null, null, null, Boolean.FALSE, null)),
                    List.of())),
            new CancelUseCaseStub(
                new CancelRegistrationResult(
                    CancelRegistrationResultStatus.CANCELLED, true, true, List.of())));

    JsonNode json =
        json(
            controller
                .completeRegistration(
                    new CompleteRegistrationRequest("Password123!", "Password123!"),
                    validAuthFlowSessionId(),
                    "csrf",
                    validIdempotencyKey(),
                    "req",
                    "en",
                    null,
                    null,
                    new MockServerHttpResponse())
                .block());

    assertThat(json.get("success").asBoolean()).isTrue();
    assertThat(json.at("/result/nextStep").asText()).isEqualTo("REGISTRATION_SUCCESS");
    assertThat(json.at("/result/data/unregisteredTrusts/0/accountEnv").asText()).isEqualTo("ENV1");
    assertThat(json.at("/result/data/actions/0/id").asText()).isEqualTo("registerAnotherTrust");
    assertThat(json.at("/result/data/actions/0/type").asText()).isEqualTo("PRIMARY");
    assertThat(json.at("/result/data/actions/0/nextStep").asText()).isEqualTo("SELECT_TRUST");
    assertThat(json.at("/result/page").isMissingNode()).isTrue();
    assertThat(json.at("/result/pageRef").isMissingNode()).isTrue();
  }

  @Test
  void cancelRegistrationSuccessUsesNextStepAndDataOnly() throws Exception {
    var controller =
        controller(
            new CompleteUseCaseStub(
                new CompleteRegistrationResult(
                    CompleteRegistrationResultStatus.REGISTERED,
                    WorkflowNextStep.REGISTRATION_SUCCESS,
                    true,
                    true,
                    List.of(),
                    List.of())),
            new CancelUseCaseStub(
                new CancelRegistrationResult(
                    CancelRegistrationResultStatus.CANCELLED, true, true, List.of())));

    JsonNode json =
        json(
            controller
                .cancelRegistration(
                    validAuthFlowSessionId(),
                    "csrf",
                    "req",
                    "en",
                    null,
                    null,
                    new MockServerHttpResponse())
                .block());

    assertThat(json.get("success").asBoolean()).isTrue();
    assertThat(json.at("/result/nextStep").asText()).isEqualTo("RETURN_TO_LOGIN");
    assertThat(json.at("/result/data/actions/0/id").asText()).isEqualTo("returnToLogin");
    assertThat(json.at("/result/data/actions/0/type").asText()).isEqualTo("PRIMARY");
    assertThat(json.at("/result/data/actions/0/nextStep").asText()).isEqualTo("RETURN_TO_LOGIN");
    assertThat(json.at("/result/page").isMissingNode()).isTrue();
    assertThat(json.at("/result/pageRef").isMissingNode()).isTrue();
  }

  private JsonNode json(Object value) {
    if (value instanceof org.springframework.http.ResponseEntity<?> response) {
      value = response.getBody();
    }
    return objectMapper.valueToTree(value);
  }

  private static RegistrationController controller(
      CompleteRegistrationUseCase completeUseCase, CancelRegistrationUseCase cancelUseCase) {
    var pages = new BffPagesProperties();
    return new RegistrationController(
        new StartUseCaseStub(),
        new com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper
            .RegistrationStartWebMapper(Duration.ofMinutes(5)),
        AuthFlowCookieWriter.defaultWriter(),
        completeUseCase,
        new CompleteRegistrationWebMapper(
            Duration.ofMinutes(5), new RegistrationSuccessPageMapper(pages)),
        cancelUseCase,
        new CancelRegistrationWebMapper(new RegistrationCancellationPageMapper(pages)),
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static String validAuthFlowSessionId() {
    for (String candidate :
        List.of(
            "sid_0123456789abcdef",
            "sid_0123456789abcdef0123456789abcdef",
            "afs_0123456789abcdef",
            "afs_0123456789abcdef0123456789abcdef",
            "01234567-89ab-cdef-0123-456789abcdef",
            "opaqueAuthFlowSessionId0123456789")) {
      try {
        return new AuthFlowSessionId(candidate).value();
      } catch (RuntimeException ignored) {
        // Follow current value-object validation rules.
      }
    }
    throw new AssertionError(
        "No valid AuthFlowSessionId test fixture candidate matched current validation rules");
  }

  private static String validIdempotencyKey() {
    for (String candidate :
        List.of(
            "idem_0123456789abcdef",
            "idem_0123456789abcdef0123456789abcdef",
            "01234567-89ab-cdef-0123-456789abcdef",
            "opaqueIdempotencyKey0123456789")) {
      try {
        return new RegistrationIdempotencyKey(candidate).value();
      } catch (RuntimeException ignored) {
        // Follow current value-object validation rules.
      }
    }
    throw new AssertionError(
        "No valid RegistrationIdempotencyKey test fixture candidate matched current validation rules");
  }

  private static final class StartUseCaseStub implements StartRegistrationUseCase {
    @Override
    public Mono<StartRegistrationResult> start(StartRegistrationCommand command) {
      return Mono.error(new AssertionError("start use case must not be called"));
    }
  }

  private record CompleteUseCaseStub(CompleteRegistrationResult result)
      implements CompleteRegistrationUseCase {
    @Override
    public Mono<CompleteRegistrationResult> complete(CompleteRegistrationCommand command) {
      return Mono.just(result);
    }
  }

  private record CancelUseCaseStub(CancelRegistrationResult result)
      implements CancelRegistrationUseCase {
    @Override
    public Mono<CancelRegistrationResult> cancel(CancelRegistrationCommand command) {
      return Mono.just(result);
    }
  }
}
