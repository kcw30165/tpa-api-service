package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationSelectedTrustRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationUsernameCheckRequest;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameResultStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;

class RegistrationUsernameCheckWebMapperTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final RegistrationUsernameCheckWebMapper mapper =
      new RegistrationUsernameCheckWebMapper();

  @Test
  void mapsRequestToCommandWithoutInternalFields() {
    var command =
        mapper.toCommand(
            new RegistrationUsernameCheckRequest(
                new RegistrationSelectedTrustRequest("TL", "ENV1"), "abc1@"),
            "afs_abc",
            "csrf_abc",
            "req_abc",
            "en",
            "https://portal.example",
            "https://portal.example/page");

    assertThat(command.authFlowSessionId().value()).isEqualTo("afs_abc");
    assertThat(command.csrfToken()).isEqualTo("csrf_abc");
    assertThat(command.selectedTrust().trustCode()).isEqualTo("TL");
    assertThat(command.selectedTrust().accountEnv()).isEqualTo("ENV1");
    assertThat(command.username()).isEqualTo("abc1@");
    assertThat(command.toString()).doesNotContain("afs_abc", "csrf_abc", "abc1@");
  }

  @Test
  void mapsDownstreamRejectedStatusToContractText() throws Exception {
    var response =
        mapper.toMutationResponse(
            new CheckRegistrationUsernameResult(
                CheckRegistrationUsernameResultStatus.DOWNSTREAM_REJECTED,
                WorkflowNextStep.USERNAME_CHECK,
                List.of(),
                List.of(error("err.registration.username.isRegistered"))));

    JsonNode json = objectMapper.valueToTree(response);
    assertThat(json.get("status").asText()).isEqualTo("BUSINESS_REJECTED");
    assertThat(json.at("/errors/0/code").asText())
        .isEqualTo("err.registration.username.isRegistered");
    assertThat(json.toString()).doesNotContain("err.tpa.username.taken");
  }

  @Test
  void requestDtoDoesNotExposePolicyNoCertNoAndRedactsUsername() throws Exception {
    var request =
        new RegistrationUsernameCheckRequest(
            new RegistrationSelectedTrustRequest("TL", "ENV1"), "abc1@");
    JsonNode json = objectMapper.valueToTree(request);

    assertThat(json.toString()).doesNotContain("policyNo", "certNo");
    assertThat(request.toString()).doesNotContain("abc1@");
  }

  private static WorkflowErrorDescriptor error(String code) {
    return new WorkflowErrorDescriptor(
        WorkflowErrorType.FORM, code, null, List.of("PAGE"), WorkflowErrorSeverity.ERROR, "SERVER");
  }

  @Test
  void requestWithoutSelectedTrustLeavesSelectedTrustNull() {
    var command =
        mapper.toCommand(
            new RegistrationUsernameCheckRequest(null, "abc1@"),
            "afs_abc",
            "csrf_abc",
            "req_abc",
            "en",
            "https://portal.example",
            "https://portal.example/page");
    assertThat(command.selectedTrust()).isNull();
    assertThat(command.username()).isEqualTo("abc1@");
  }

  @Test
  void nullRequestShortCircuitsSelectedTrustAndUsername() {
    var command =
        mapper.toCommand(
            null,
            "afs_abc",
            "csrf_abc",
            "req_abc",
            "en",
            "https://portal.example",
            "https://portal.example/page");
    assertThat(command.selectedTrust()).isNull();
    assertThat(command.username()).isNull();
  }

  @Test
  void blankAndNullOptionalFieldsDoNotThrowAndCsrfTokenIsNormalizedToNull() {
    var command =
        mapper.toCommand(
            new RegistrationUsernameCheckRequest(
                new RegistrationSelectedTrustRequest("TL", "ENV1"), "abc1@"),
            "afs_abc",
            null,
            "",
            null,
            null,
            "   ");
    assertThat(command).isNotNull();
    assertThat(command.csrfToken()).isNull();
    assertThat(command.authFlowSessionId().value()).isEqualTo("afs_abc");
  }

  @Test
  void usernameAvailableWithNullNextStepReturnsSuccessAndDefaultsNextStep() {
    var response =
        mapper.toMutationResponse(
            new CheckRegistrationUsernameResult(
                CheckRegistrationUsernameResultStatus.USERNAME_AVAILABLE,
                null,
                List.of(),
                List.of()));
    assertThat(response.success()).isTrue();
    assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
    assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.USERNAME_CHECK);
  }

  @Test
  void downstreamFailedWithEmptyErrorsMapsToSystemErrorAndSynthesizesDownstreamUnavailableCode() {
    var response =
        mapper.toMutationResponse(
            new CheckRegistrationUsernameResult(
                CheckRegistrationUsernameResultStatus.DOWNSTREAM_FAILED,
                WorkflowNextStep.USERNAME_CHECK,
                List.of(),
                List.of()));
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            errorEntry ->
                assertThat(errorEntry.code()).isEqualTo("err.workflow.downstream.unavailable"));
  }

  @Test
  void invalidAuthFlowWithEmptyErrorsMapsToValidationFailedAndSynthesizesAuthFlowInvalidCode() {
    var response =
        mapper.toMutationResponse(
            new CheckRegistrationUsernameResult(
                CheckRegistrationUsernameResultStatus.INVALID_AUTH_FLOW,
                WorkflowNextStep.USERNAME_CHECK,
                List.of(),
                List.of()));
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            errorEntry ->
                assertThat(errorEntry.code()).isEqualTo("err.registration.authFlow.invalid"));
  }

  @Test
  void csrfInvalidWithEmptyErrorsMapsToValidationFailedAndSynthesizesCsrfInvalidCode() {
    var response =
        mapper.toMutationResponse(
            new CheckRegistrationUsernameResult(
                CheckRegistrationUsernameResultStatus.CSRF_INVALID,
                WorkflowNextStep.USERNAME_CHECK,
                List.of(),
                List.of()));
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            errorEntry -> assertThat(errorEntry.code()).isEqualTo("err.registration.csrf.invalid"));
  }

  @Test
  void downstreamRejectedWithEmptyErrorsSynthesizesUsernameCheckFailedCode() {
    var response =
        mapper.toMutationResponse(
            new CheckRegistrationUsernameResult(
                CheckRegistrationUsernameResultStatus.DOWNSTREAM_REJECTED,
                WorkflowNextStep.USERNAME_CHECK,
                List.of(),
                List.of()));
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            errorEntry ->
                assertThat(errorEntry.code()).isEqualTo("err.registration.username.checkFailed"));
  }

  @Test
  void systemFailedWithEmptyErrorsMapsToSystemErrorAndSynthesizesSystemUnavailableCode() {
    var response =
        mapper.toMutationResponse(
            new CheckRegistrationUsernameResult(
                CheckRegistrationUsernameResultStatus.SYSTEM_FAILED,
                WorkflowNextStep.USERNAME_CHECK,
                List.of(),
                List.of()));
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            errorEntry -> assertThat(errorEntry.code()).isEqualTo("err.workflow.system.unavailable"));
  }

  @Test
  void invalidStateWithEmptyErrorsMapsToValidationFailedAndSynthesizesStateInvalidCode() {
    var response =
        mapper.toMutationResponse(
            new CheckRegistrationUsernameResult(
                CheckRegistrationUsernameResultStatus.INVALID_STATE,
                WorkflowNextStep.USERNAME_CHECK,
                List.of(),
                List.of()));
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            errorEntry -> assertThat(errorEntry.code()).isEqualTo("err.registration.state.invalid"));
  }

  @Test
  void usernameUnavailableWithEmptyErrorsHitsDefaultArmAndSynthesizesUsernameRequestInvalidCode() {
    var response =
        mapper.toMutationResponse(
            new CheckRegistrationUsernameResult(
                CheckRegistrationUsernameResultStatus.USERNAME_UNAVAILABLE,
                WorkflowNextStep.USERNAME_CHECK,
                List.of(),
                List.of()));
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            errorEntry ->
                assertThat(errorEntry.code()).isEqualTo("err.registration.username.request.invalid"));
  }

  @Test
  void suppliedErrorWithNullTargetsFallsBackToPageTarget() throws Exception {
    var errorWithNullTargets =
        new WorkflowErrorDescriptor(
            WorkflowErrorType.FORM,
            "err.registration.username.isRegistered",
            null,
            null,
            WorkflowErrorSeverity.ERROR,
            "SERVER");
    var response =
        mapper.toMutationResponse(
            new CheckRegistrationUsernameResult(
                CheckRegistrationUsernameResultStatus.DOWNSTREAM_REJECTED,
                WorkflowNextStep.USERNAME_CHECK,
                List.of(),
                List.of(errorWithNullTargets)));
    JsonNode json = objectMapper.valueToTree(response);
    assertThat(json.at("/errors/0/targets/0").asText()).isEqualTo("PAGE");
  }

  @Test
  void suppliedErrorWithEmptyTargetsFallsBackToPageTarget() throws Exception {
    var errorWithEmptyTargets =
        new WorkflowErrorDescriptor(
            WorkflowErrorType.FORM,
            "err.registration.username.isRegistered",
            null,
            List.of(),
            WorkflowErrorSeverity.ERROR,
            "SERVER");
    var response =
        mapper.toMutationResponse(
            new CheckRegistrationUsernameResult(
                CheckRegistrationUsernameResultStatus.DOWNSTREAM_REJECTED,
                WorkflowNextStep.USERNAME_CHECK,
                List.of(),
                List.of(errorWithEmptyTargets)));
    JsonNode json = objectMapper.valueToTree(response);
    assertThat(json.at("/errors/0/targets/0").asText()).isEqualTo("PAGE");
  }

  @Test
  void suppliedErrorWithBlankTargetFallsBackToPageTarget() throws Exception {
    var errorWithBlankTarget =
        new WorkflowErrorDescriptor(
            WorkflowErrorType.FORM,
            "err.registration.username.isRegistered",
            null,
            List.of("   "),
            WorkflowErrorSeverity.ERROR,
            "SERVER");
    var response =
        mapper.toMutationResponse(
            new CheckRegistrationUsernameResult(
                CheckRegistrationUsernameResultStatus.DOWNSTREAM_REJECTED,
                WorkflowNextStep.USERNAME_CHECK,
                List.of(),
                List.of(errorWithBlankTarget)));
    JsonNode json = objectMapper.valueToTree(response);
    assertThat(json.at("/errors/0/targets/0").asText()).isEqualTo("PAGE");
  }


  @Test
  void suppliedErrorWithNullFirstTargetElementFallsBackToPageTarget() throws Exception {
    var errorWithNullFirstTarget =
        new WorkflowErrorDescriptor(
            WorkflowErrorType.FORM,
            "err.registration.username.isRegistered",
            null,
            java.util.Arrays.asList((String) null),
            WorkflowErrorSeverity.ERROR,
            "SERVER");
    var response =
        mapper.toMutationResponse(
            new CheckRegistrationUsernameResult(
                CheckRegistrationUsernameResultStatus.DOWNSTREAM_REJECTED,
                WorkflowNextStep.USERNAME_CHECK,
                List.of(),
                List.of(errorWithNullFirstTarget)));
    JsonNode json = objectMapper.valueToTree(response);
    assertThat(json.at("/errors/0/targets/0").asText()).isEqualTo("PAGE");
  }

}
