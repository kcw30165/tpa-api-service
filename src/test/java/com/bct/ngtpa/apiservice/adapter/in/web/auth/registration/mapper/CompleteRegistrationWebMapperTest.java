package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration.RegistrationSuccessPageMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResultStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompleteRegistrationWebMapperTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final CompleteRegistrationWebMapper mapper =
      new CompleteRegistrationWebMapper(
          Duration.ofMinutes(5), new RegistrationSuccessPageMapper(new BffPagesProperties()));

  @Test
  void registeredWithNoRemainingTrustsMapsToSuccessWithNoRemainingTrustsVariant() {
    var result =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.REGISTERED,
            WorkflowNextStep.REGISTRATION_SUCCESS,
            true,
            true,
            List.of(),
            List.of());
    var response = mapper.toResponse(result, "en");
    assertThat(response.success()).isTrue();
    assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
    JsonNode json = objectMapper.valueToTree(response);
    assertThat(json.at("/result/nextStep").asText()).isEqualTo("REGISTRATION_SUCCESS");
    assertThat(json.at("/result/data/actions/0/id").asText()).isEqualTo("returnToLogin");
    assertThat(json.at("/result/data/actions/0/type").asText()).isEqualTo("PRIMARY");
  }

  @Test
  void registeredWithRemainingTrustsMapsToSuccessWithRemainingTrustsVariant() {
    var result =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.REGISTERED_WITH_REMAINING_TRUSTS,
            WorkflowNextStep.REGISTRATION_SUCCESS,
            true,
            false,
            List.of(new RegistrationLookupAccount(null, null, "ENV1", "TL")),
            List.of());
    var response = mapper.toResponse(result, "en");
    assertThat(response.success()).isTrue();
    JsonNode json = objectMapper.valueToTree(response);
    assertThat(json.at("/result/data/unregisteredTrusts/0/accountEnv").asText()).isEqualTo("ENV1");
    assertThat(json.at("/result/data/actions/0/id").asText()).isEqualTo("registerAnotherTrust");
    assertThat(json.at("/result/data/actions/0/nextStep").asText()).isEqualTo("SELECT_TRUST");
  }

  @Test
  void completedReplayMapsToSuccessEvenWhenNotRegistered() {
    var result =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.COMPLETED_REPLAY,
            WorkflowNextStep.REGISTRATION_SUCCESS,
            false,
            true,
            List.of(),
            List.of());
    var response = mapper.toResponse(result, "en");
    assertThat(response.success()).isTrue();
    assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
  }

  @Test
  void validationFailedMapsToValidationFailedStatus() {
    var result =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.VALIDATION_FAILED,
            WorkflowNextStep.NONE,
            false,
            false,
            List.of(),
            List.of(error("err.registration.complete.validationFailed")));
    var response = mapper.toResponse(result, "en");
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
  }

  @Test
  void idempotencyKeyReusedMapsToValidationFailedStatus() {
    var result =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.IDEMPOTENCY_KEY_REUSED,
            WorkflowNextStep.NONE,
            false,
            false,
            List.of(),
            List.of(error("err.workflow.idempotency.keyReused")));
    var response = mapper.toResponse(result, "en");
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
  }

  @Test
  void downstreamRejectedMapsToBusinessRejectedStatus() {
    var result =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.DOWNSTREAM_REJECTED,
            WorkflowNextStep.NONE,
            false,
            false,
            List.of(),
            List.of(error("err.registration.complete.rejected")));
    var response = mapper.toResponse(result, "en");
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
  }

  @Test
  void downstreamFailedMapsToSystemErrorStatus() {
    var result =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.DOWNSTREAM_FAILED,
            WorkflowNextStep.NONE,
            false,
            false,
            List.of(),
            List.of(error("err.workflow.downstream.unavailable")));
    var response = mapper.toResponse(result, "en");
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
  }

  @Test
  void continuationLookupFailedMapsToSystemErrorStatus() {
    var result =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.CONTINUATION_LOOKUP_FAILED,
            WorkflowNextStep.NONE,
            false,
            false,
            List.of(),
            List.of(error("err.system.unexpected")));
    var response = mapper.toResponse(result, "en");
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
  }

  @Test
  void unknownOutcomeFallsThroughToSystemErrorDefaultBranch() {
    var result =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.UNKNOWN_OUTCOME,
            WorkflowNextStep.NONE,
            false,
            false,
            List.of(),
            List.of(error("err.system.unexpected")));
    var response = mapper.toResponse(result, "en");
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
  }

  @Test
  void missingReturnsValidationFailedFieldError() {
    var response = mapper.missing("err.registration.complete.passwordRequired", "password", "en");
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    JsonNode json = objectMapper.valueToTree(response);
    assertThat(json.at("/errors/0/code").asText())
        .isEqualTo("err.registration.complete.passwordRequired");
    assertThat(json.at("/errors/0/targets/0").asText()).isEqualTo("password");
  }

  private static WorkflowErrorDescriptor error(String code) {
    return new WorkflowErrorDescriptor(
        WorkflowErrorType.FORM, code, null, List.of("PAGE"), WorkflowErrorSeverity.ERROR, "SERVER");
  }
}
