package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationError;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResultStatus;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartWorkflowDataResponse;

class RegistrationStartWebMapperTest {
  private final RegistrationStartWebMapper mapper =
      new RegistrationStartWebMapper(Duration.ofMinutes(5));

  @Test
  void registrationStartMapsRequestToUseCaseCommand() {
    var request =
        new RegistrationStartRequest(
            "GPFS", "1234567890", "31/01/1980", "member@example.com", null, "cap_secret");
    StartRegistrationCommand command = mapper.toCommand(request, "idem_abc123", "req-123", "en-HK");

    assertThat(command.idempotencyKey()).isEqualTo(new RegistrationIdempotencyKey("idem_abc123"));
    assertThat(command.identityType()).isEqualTo("GPFS");
    assertThat(command.identityValue()).isEqualTo("1234567890");
    assertThat(command.dateOfBirth()).isEqualTo("31/01/1980");
    assertThat(command.email()).isEqualTo("member@example.com");
    assertThat(command.mobile()).isNull();
    assertThat(command.acceptLanguage()).isEqualTo("en-HK");
    assertThat(command.correlationId()).isEqualTo("req-123");
    assertThat(command.toString())
        .doesNotContain("1234567890", "member@example.com", "cap_secret", "req-123", "idem_abc123");
  }

  @Test
  void responseToStringDoesNotExposeCsrfTokenOrSessionId() {
    var result =
        new StartRegistrationResult(
            StartRegistrationResultStatus.STARTED,
            WorkflowNextStep.VERIFY_OTP,
            "m****r@example.com",
            300,
            60,
            5,
            "csrf_secret",
            "afs_secret",
            List.of(),
            List.of(),
            RegistrationIdempotencyStartOutcome.STARTED,
            null);
    var response = mapper.toResponse(result);

    assertThat(response.csrfToken()).isEqualTo("csrf_secret");
    assertThat(response.toString())
        .doesNotContain(
            "csrf_secret", "afs_secret", "authFlowSessionId", "otpChallengeId", "otpCode");
  }

  @Test
  void mapperIncludesLocalizedErrorMessage() {
    var result =
        new StartRegistrationResult(
            StartRegistrationResultStatus.VERIFICATION_FAILED,
            WorkflowNextStep.NONE,
            null,
            0,
            0,
            0,
            null,
            null,
            List.of(),
            List.of(
                new StartRegistrationError(
                    RegistrationErrorKey.START_VERIFICATION_FAILED,
                    "identityValue",
                    "We are unable to verify your details. Please review and try again.")),
            RegistrationIdempotencyStartOutcome.STARTED,
            null);

    var response = mapper.toMutationResponse(result);

    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.code()).isEqualTo("err.registration.start.verificationFailed");
            });
  }

  @Test
  void mapperResolvesRegistrationErrorMessageThroughResolver() {
    ErrorMessageResolver resolver =
        (code, locale, accountEnv, trustCode, schemeType) -> "resolved-message";
    var localMapper = new RegistrationStartWebMapper(Duration.ofMinutes(5), resolver);
    var result =
        new StartRegistrationResult(
            StartRegistrationResultStatus.VERIFICATION_FAILED,
            WorkflowNextStep.NONE,
            null,
            0,
            0,
            0,
            null,
            null,
            List.of(),
            List.of(
                new StartRegistrationError(
                    RegistrationErrorKey.START_VERIFICATION_FAILED, "identityValue")),
            RegistrationIdempotencyStartOutcome.STARTED,
            null);

    var response = localMapper.toMutationResponse(result, "zh-HK");

    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.code()).isEqualTo("err.registration.start.verificationFailed");
            });
  }

  @Test
  void messageDeliveryFailureMapsToDownstreamErrorStatus() {
    var result =
        new StartRegistrationResult(
            StartRegistrationResultStatus.MESSAGE_DELIVERY_FAILED,
            WorkflowNextStep.NONE,
            null,
            0,
            0,
            0,
            null,
            null,
            List.of(),
            List.of(
                new StartRegistrationError(RegistrationErrorKey.MESSAGE_DELIVERY_FAILED, "email")),
            RegistrationIdempotencyStartOutcome.STARTED,
            null);

    var response = mapper.toMutationResponse(result, "en");

    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(response.result()).isNotNull();
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error -> assertThat(error.code()).isEqualTo("err.registration.message.deliveryFailed"));
  }

  @Test
  void systemFailureMapsToSystemErrorStatus() {
    var result =
        new StartRegistrationResult(
            StartRegistrationResultStatus.SYSTEM_FAILED,
            WorkflowNextStep.NONE,
            null,
            0,
            0,
            0,
            null,
            null,
            List.of(),
            List.of(new StartRegistrationError(SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE, "PAGE")),
            RegistrationIdempotencyStartOutcome.STARTED,
            null);

    var response = mapper.toMutationResponse(result, "en");

    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(response.result()).isNotNull();
  }

  @Test
  void messageDeliveryFailedWithPageTargetHitsDownstreamSystemBranch() {
    var result =
        new StartRegistrationResult(
            StartRegistrationResultStatus.MESSAGE_DELIVERY_FAILED,
            WorkflowNextStep.NONE,
            null,
            0,
            0,
            0,
            null,
            null,
            List.of(),
            List.of(new StartRegistrationError(RegistrationErrorKey.MESSAGE_DELIVERY_FAILED, "PAGE")),
            RegistrationIdempotencyStartOutcome.STARTED,
            null);
    var response = mapper.toMutationResponse(result, "en");
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error -> assertThat(error.code()).isEqualTo("err.registration.message.deliveryFailed"));
  }

  @Test
  void downstreamRejectedWithPageTargetMapsToBusinessRejectedStatus() {
    var result =
        new StartRegistrationResult(
            StartRegistrationResultStatus.DOWNSTREAM_REJECTED,
            WorkflowNextStep.NONE,
            null,
            0,
            0,
            0,
            null,
            null,
            List.of(),
            List.of(
                new StartRegistrationError(
                    SharedWorkflowErrorKey.DOWNSTREAM_BUSINESS_REJECTED, "PAGE")),
            RegistrationIdempotencyStartOutcome.STARTED,
            null);
    var response = mapper.toMutationResponse(result, "en");
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error ->
                assertThat(error.code()).isEqualTo("err.workflow.downstream.businessRejected"));
  }

  @Test
  void verificationFailedWithPageTargetMapsToBusinessRejectedStatus() {
    var result =
        new StartRegistrationResult(
            StartRegistrationResultStatus.VERIFICATION_FAILED,
            WorkflowNextStep.NONE,
            null,
            0,
            0,
            0,
            null,
            null,
            List.of(),
            List.of(
                new StartRegistrationError(RegistrationErrorKey.START_VERIFICATION_FAILED, "PAGE")),
            RegistrationIdempotencyStartOutcome.STARTED,
            null);
    var response = mapper.toMutationResponse(result, "en");
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error -> assertThat(error.code()).isEqualTo("err.registration.start.verificationFailed"));
  }

  @Test
  void idempotencyKeyReusedWithPageTargetMapsToBusinessRejectedStatus() {
    var result =
        new StartRegistrationResult(
            StartRegistrationResultStatus.IDEMPOTENCY_KEY_REUSED,
            WorkflowNextStep.NONE,
            null,
            0,
            0,
            0,
            null,
            null,
            List.of(),
            List.of(new StartRegistrationError(SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED, "PAGE")),
            RegistrationIdempotencyStartOutcome.STARTED,
            null);
    var response = mapper.toMutationResponse(result, "en");
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error -> assertThat(error.code()).isEqualTo("err.workflow.idempotency.keyReused"));
  }

  @Test
  void validationFailedWithPageTargetHitsDefaultFormErrorTypeBranch() {
    var result =
        new StartRegistrationResult(
            StartRegistrationResultStatus.VALIDATION_FAILED,
            WorkflowNextStep.NONE,
            null,
            0,
            0,
            0,
            null,
            null,
            List.of(),
            List.of(new StartRegistrationError(RegistrationErrorKey.START_REQUEST_INVALID, "PAGE")),
            RegistrationIdempotencyStartOutcome.STARTED,
            null);
    var response = mapper.toMutationResponse(result, "en");
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error -> assertThat(error.code()).isEqualTo("err.registration.start.request.invalid"));
  }

  @Test
  void registrationCompletedShortCircuitsToSuccessWithReturnToLoginAndCompletionMessage() {
    var result =
        new StartRegistrationResult(
            StartRegistrationResultStatus.REGISTRATION_COMPLETED,
            WorkflowNextStep.NONE,
            null,
            0,
            0,
            0,
            null,
            null,
            List.of(),
            List.of(),
            RegistrationIdempotencyStartOutcome.STARTED,
            null);
    var response = mapper.toMutationResponse(result, "en");
    assertThat(response.success()).isTrue();
    assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
    assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
    assertThat(response.result().data()).isNull();
  }

  @Test
  void registrationCompletedIsTreatedAsSuccessByDirectResponseMapping() {
    var result =
        new StartRegistrationResult(
            StartRegistrationResultStatus.REGISTRATION_COMPLETED,
            WorkflowNextStep.RETURN_TO_LOGIN,
            null,
            0,
            0,
            0,
            null,
            null,
            List.of(),
            List.of(),
            RegistrationIdempotencyStartOutcome.STARTED,
            null);
    assertThat(mapper.toResponse(result)).isNotNull();
  }

  @Test
  void duplicateInProgressIsTreatedAsSuccessByDirectResponseMapping() {
    var result =
        new StartRegistrationResult(
            StartRegistrationResultStatus.DUPLICATE_IN_PROGRESS,
            WorkflowNextStep.WAIT,
            null,
            0,
            0,
            0,
            null,
            null,
            List.of(),
            List.of(),
            RegistrationIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS,
            null);
    assertThat(mapper.toResponse(result)).isNotNull();
  }

  @Test
  void completedReplayIsTreatedAsSuccessByDirectResponseMapping() {
    var result =
        new StartRegistrationResult(
            StartRegistrationResultStatus.COMPLETED_REPLAY,
            WorkflowNextStep.VERIFY_OTP,
            "m****r@example.com",
            300,
            60,
            5,
            "csrf_safe_token",
            "afs_secret",
            List.of(),
            List.of(),
            RegistrationIdempotencyStartOutcome.STARTED,
            null);
    assertThat(mapper.toResponse(result)).isNotNull();
    RegistrationStartWorkflowDataResponse data = mapper.toWorkflowData(result);
    assertThat(data).isNotNull();
  }

  @Test
  void zeroValuedOptionalFieldsStillProduceANonNullResponse() {
    var result =
        new StartRegistrationResult(
            StartRegistrationResultStatus.STARTED,
            WorkflowNextStep.VERIFY_OTP,
            "m****r@example.com",
            0,
            0,
            0,
            "csrf_safe_token",
            "afs_secret",
            List.of(),
            List.of(),
            RegistrationIdempotencyStartOutcome.STARTED,
            null);
    var response = mapper.toResponse(result);
    assertThat(response).isNotNull();
    assertThat(response.csrfToken()).isEqualTo("csrf_safe_token");
  }


  @Test
  void toMutationResponseWithNullResultReturnsSystemErrorAndDoesNotThrow() {
    var response = mapper.toMutationResponse(null, "en");
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
  }

  @Test
  void toResponseWithNullResultReturnsNull() {
    assertThat(mapper.toResponse(null)).isNull();
  }

  @Test
  void toResponseWithFailingStatusReturnsNull() {
    var result =
        new StartRegistrationResult(
            StartRegistrationResultStatus.VALIDATION_FAILED,
            WorkflowNextStep.NONE,
            null,
            0,
            0,
            0,
            null,
            null,
            List.of(),
            List.of(),
            RegistrationIdempotencyStartOutcome.STARTED,
            null);
    assertThat(mapper.toResponse(result)).isNull();
  }

  @Test
  void toWorkflowDataWithNullResultReturnsNull() {
    assertThat(mapper.toWorkflowData(null)).isNull();
  }

  @Test
  void toWorkflowDataWithFailingStatusReturnsNull() {
    var result =
        new StartRegistrationResult(
            StartRegistrationResultStatus.VALIDATION_FAILED,
            WorkflowNextStep.NONE,
            null,
            0,
            0,
            0,
            null,
            null,
            List.of(),
            List.of(),
            RegistrationIdempotencyStartOutcome.STARTED,
            null);
    assertThat(mapper.toWorkflowData(result)).isNull();
  }

  @Test
  void missingIdempotencyKeyResponseWithLanguageReturnsValidationFailedIdempotencyMissingError() {
    var response = mapper.missingIdempotencyKeyResponse("en");
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error -> assertThat(error.code()).isEqualTo("err.workflow.idempotency.missing"));
  }

  @Test
  void missingIdempotencyKeyResponseWithoutLanguageDelegatesToOverload() {
    var response = mapper.missingIdempotencyKeyResponse();
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
  }

}
