package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration.RegistrationCancellationPageMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.cancel.CancelRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.cancel.CancelRegistrationResultStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class CancelRegistrationWebMapperTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final CancelRegistrationWebMapper mapper =
      new CancelRegistrationWebMapper(
          new RegistrationCancellationPageMapper(new BffPagesProperties()));

  @Test
  void cancelledMapsToSuccessfulCleanupResponse() {
    var result =
        new CancelRegistrationResult(CancelRegistrationResultStatus.CANCELLED, true, true, List.of());
    var response = mapper.toResponse(result, "en");
    assertThat(response.success()).isTrue();
    assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
    JsonNode json = objectMapper.valueToTree(response);
    assertThat(json.at("/result/nextStep").asText()).isEqualTo("RETURN_TO_LOGIN");
    assertThat(json.at("/result/data/actions/0/id").asText()).isEqualTo("returnToLogin");
  }

  @Test
  void sessionNotFoundIsTreatedAsSuccessfulCleanup() {
    var result =
        new CancelRegistrationResult(
            CancelRegistrationResultStatus.SESSION_NOT_FOUND, true, true, List.of());
    assertThat(mapper.toResponse(result, "en").success()).isTrue();
  }

  @Test
  void alreadyCancelledIsTreatedAsSuccessfulCleanup() {
    var result =
        new CancelRegistrationResult(
            CancelRegistrationResultStatus.ALREADY_CANCELLED, true, true, List.of());
    assertThat(mapper.toResponse(result, "en").success()).isTrue();
  }

  @Test
  void alreadyTerminalIsTreatedAsSuccessfulCleanup() {
    var result =
        new CancelRegistrationResult(
            CancelRegistrationResultStatus.ALREADY_TERMINAL, true, true, List.of());
    assertThat(mapper.toResponse(result, "en").success()).isTrue();
  }

  @Test
  void csrfInvalidMapsToValidationFailedStatus() {
    var result =
        new CancelRegistrationResult(
            CancelRegistrationResultStatus.CSRF_INVALID,
            false,
            false,
            List.of(error("err.registration.csrf.invalid")));
    var response = mapper.toResponse(result, "en");
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
  }

  @Test
  void invalidAuthFlowMapsToValidationFailedStatus() {
    var result =
        new CancelRegistrationResult(
            CancelRegistrationResultStatus.INVALID_AUTH_FLOW,
            false,
            false,
            List.of(error("err.registration.authFlow.invalid")));
    var response = mapper.toResponse(result, "en");
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
  }

  @Test
  void systemFailedMapsToSystemErrorStatusAndPropagatesSuppliedErrorCode() {
    var result =
        new CancelRegistrationResult(
            CancelRegistrationResultStatus.SYSTEM_FAILED,
            false,
            false,
            List.of(error("err.system.unexpected")));
    var response = mapper.toResponse(result, "en");
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    JsonNode json = objectMapper.valueToTree(response);
    assertThat(json.at("/errors/0/code").asText()).isEqualTo("err.system.unexpected");
  }

  @Test
  void missingSessionReturnsSuccessfulResponse() {
    var response = mapper.missingSession("en");
    assertThat(response.success()).isTrue();
    assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
  }

  @Test
  void missingCsrfReturnsValidationFailedWithCsrfRelatedCode() {
    var response = mapper.missingCsrf("en");
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    JsonNode json = objectMapper.valueToTree(response);
    assertThat(json.at("/errors/0/code").asText()).contains("csrf");
  }

  private static WorkflowErrorDescriptor error(String code) {
    return new WorkflowErrorDescriptor(
        WorkflowErrorType.FORM, code, null, List.of("PAGE"), WorkflowErrorSeverity.ERROR, "SERVER");
  }
}
