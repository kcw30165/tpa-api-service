package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoveryStartDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoveryWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryStartResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.HttpStatusClassification;
import com.bct.ngtpa.apiservice.adapter.in.web.response.HttpStatusClassificationOverride;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoverySchemeProjection;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult.Outcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.UsernameRecoveryErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartException;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartException.Reason;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartValidator.Error;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartValidator.Kind;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exhaustive Start response-policy tests: every Start result outcome, every Start exception reason,
 * exact public status/nextStep/messages/errors/targets/ source/severity, typed HTTP classification,
 * no downstream statuses, and no sensitive leakage.
 */
class UsernameRecoveryStartResponsePolicyTest {

  private final UsernameRecoveryStartResponsePolicy policy =
      new UsernameRecoveryStartResponsePolicy();
  private final UsernameRecoveryStartWebMapper mapper = new UsernameRecoveryStartWebMapper();

  @Test
  void sessionCreatedMapsToSuccessSelectAccountWithTask022Projection() {
    var result =
        new UsernameRecoveryStartResult(
            Outcome.SESSION_CREATED,
            null,
            "csrf-value",
            600,
            List.of(
                new UsernameRecoverySchemeProjection(
                    new UsernameRecoverySchemeRef("sch_opaque-1"), "DB", "DBA", true)));

    var responsePolicy = policy.toPolicy(result, mapper);
    var body = responsePolicy.body();

    assertThat(body.success()).isTrue();
    assertThat(body.status()).isEqualTo(ApiStatus.SUCCESS);
    assertThat(body.result().nextStep()).isEqualTo(WorkflowNextStep.SELECT_ACCOUNT);
    assertThat(body.result().data()).isNotNull();
    assertThat(body.result().data().csrfToken()).isEqualTo("csrf-value");
    assertThat(body.result().data().expiresInSeconds()).isEqualTo(600);
    assertThat(body.result().data().schemes())
        .singleElement()
        .satisfies(scheme -> assertThat(scheme.schemeRef()).isEqualTo("sch_opaque-1"));
    assertThat(body.messages()).isEmpty();
    assertThat(body.errors()).isEmpty();
    assertThat(responsePolicy.httpOverride()).isNull();
    assertThat(classifiedHttp(body.status(), responsePolicy.httpOverride())).isEqualTo(200);
  }

  @Test
  void memberNotFoundMapsToBusinessRejectedRestartFlowWithVerificationFailedError() {
    var result = UsernameRecoveryStartResult.withoutSession(Outcome.MEMBER_NOT_FOUND, List.of());

    var responsePolicy = policy.toPolicy(result, mapper);
    var body = responsePolicy.body();

    assertThat(body.success()).isFalse();
    assertThat(body.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(body.result().nextStep()).isEqualTo(WorkflowNextStep.RESTART_FLOW);
    assertThat(body.result().data()).isNull();
    assertThat(body.messages()).isEmpty();
    assertThat(body.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.type()).isEqualTo("FORM");
              assertThat(error.code())
                  .isEqualTo(UsernameRecoveryErrorKey.VERIFICATION_FAILED.value());
              assertThat(error.targets()).isEmpty();
              assertThat(error.source()).isEqualTo("BFF");
              assertThat(error.severity()).isEqualTo("ERROR");
              assertThat(error.message()).isNull();
            });
    assertThat(responsePolicy.httpOverride()).isNull();
    assertThat(classifiedHttp(body.status(), responsePolicy.httpOverride())).isEqualTo(422);
  }

  @Test
  void zeroRegisteredMapsToBusinessRejectedRestartFlowWithRegistrationReminderAndSchemesOnlyData() {
    var result =
        UsernameRecoveryStartResult.withoutSession(
            Outcome.ZERO_REGISTERED,
            List.of(new UsernameRecoverySchemeProjection(null, "DB", "DBA", false)));

    var responsePolicy = policy.toPolicy(result, mapper);
    var body = responsePolicy.body();

    assertThat(body.success()).isFalse();
    assertThat(body.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(body.result().nextStep()).isEqualTo(WorkflowNextStep.RESTART_FLOW);
    assertThat(body.result().data()).isNull();
    assertThat(body.messages()).isEmpty();
    assertThat(body.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.type()).isEqualTo("BUSINESS");
              assertThat(error.code())
                  .isEqualTo(UsernameRecoveryErrorKey.REGISTRATION_REQUIRED.value());
              assertThat(error.targets()).isEmpty();
              assertThat(error.source()).isEqualTo("BFF");
              assertThat(error.severity()).isEqualTo("ERROR");
              assertThat(error.message()).isNull();
            });
    assertThat(responsePolicy.httpOverride()).isNull();
    assertThat(classifiedHttp(body.status(), responsePolicy.httpOverride())).isEqualTo(422);
  }

  @Test
  void everyStartExceptionReasonMapsToExactTypedPolicy() {
    for (Reason reason : Reason.values()) {
      UsernameRecoveryStartException exception = exceptionFor(reason);
      UsernameRecoveryResponsePolicy<
              UsernameRecoveryWorkflowResultResponse<UsernameRecoveryStartDataResponse>>
          responsePolicy = policy.toPolicy(exception);
      var body = responsePolicy.body();
      Expected expected = expectedFor(reason);

      assertThat(body.success()).as("success for %s", reason).isFalse();
      assertThat(body.status()).as("status for %s", reason).isEqualTo(expected.status);
      assertThat(body.result()).as("result for %s", reason).isNotNull();
      assertThat(body.result().nextStep())
          .as("nextStep for %s", reason)
          .isEqualTo(expected.nextStep);
      assertThat(body.result().data()).as("data for %s", reason).isNull();
      assertThat(body.messages()).as("messages for %s", reason).isEmpty();
      assertThat(body.errors())
          .as("errors for %s", reason)
          .singleElement()
          .satisfies(
              error -> {
                assertThat(error.code()).isEqualTo(expected.code);
                assertThat(error.targets()).containsExactlyElementsOf(expected.targets);
                assertThat(error.source()).isEqualTo(expected.source);
                assertThat(error.severity()).isEqualTo("ERROR");
                assertThat(error.message()).isNull();
              });
      assertThat(responsePolicy.httpOverride())
          .as("override for %s", reason)
          .isEqualTo(expected.override);
      assertThat(classifiedHttp(body.status(), responsePolicy.httpOverride()))
          .as("http for %s", reason)
          .isEqualTo(expected.http);
    }
  }

  @Test
  void validationFailedPreservesEveryFieldTargetWithItsCommittedKindCode() {
    var exception =
        UsernameRecoveryStartException.validationFailed(
            List.of(
                new Error("identityValue", Kind.IDENTITY_VALUE_INVALID),
                new Error("dateOfBirth", Kind.DATE_OF_BIRTH_INVALID),
                new Error("email", Kind.EMAIL_INVALID),
                new Error("mobile", Kind.MOBILE_INVALID)));

    var body = policy.toPolicy(exception).body();

    assertThat(body.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(body.result().nextStep()).isEqualTo(WorkflowNextStep.NONE);
    assertThat(body.errors())
        .extracting(ApiError::code)
        .containsExactly(
            RegistrationErrorKey.REGISTRATION_IDENTITYVALUE_INVALID.value(),
            RegistrationErrorKey.REGISTRATION_DATEOFBIRTH_INVALID.value(),
            RegistrationErrorKey.REGISTRATION_EMAIL_INVALID.value(),
            RegistrationErrorKey.REGISTRATION_MOBILE_INVALID.value());
    assertThat(body.errors())
        .extracting(ApiError::targets)
        .containsExactly(
            List.of("identityValue"), List.of("dateOfBirth"), List.of("email"), List.of("mobile"));
  }

  @Test
  void validationFailedWithNoErrorsFallsBackToSharedValidationCode() {
    var exception = UsernameRecoveryStartException.validationFailed(List.of());

    var body = policy.toPolicy(exception).body();

    assertThat(body.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(body.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.code()).isEqualTo(ErrorCodes.REQUEST_VALIDATION_FAILED);
              assertThat(error.targets()).isEmpty();
              assertThat(error.source()).isEqualTo("BFF");
            });
  }

  @Test
  void everyStartPolicyNeverEmitsDownstreamStatuses() {
    List<MutationResponse<?>> bodies = new ArrayList<>();
    for (Reason reason : Reason.values()) {
      bodies.add(policy.toPolicy(exceptionFor(reason)).body());
    }
    bodies.add(policy.toPolicy(sessionCreatedResult(), mapper).body());
    bodies.add(policy.toPolicy(memberNotFoundResult(), mapper).body());
    bodies.add(policy.toPolicy(zeroRegisteredResult(), mapper).body());

    assertThat(bodies)
        .allSatisfy(
            body -> {
              assertThat(body.status()).isNotEqualTo(ApiStatus.DOWNSTREAM_REJECTED);
              assertThat(body.status()).isNotEqualTo(ApiStatus.DOWNSTREAM_ERROR);
            });
  }

  @Test
  void startPoliciesNeverLeakRawExceptionTextOrSensitiveValues() {
    for (Reason reason : Reason.values()) {
      var responsePolicy = policy.toPolicy(exceptionFor(reason));
      assertThat(responsePolicy.body().errors())
          .allSatisfy(
              error -> {
                assertThat(error.message()).isNull();
                assertThat(error.code()).doesNotContain(reason.name());
              });
      assertThat(responsePolicy.body().messages())
          .allSatisfy(message -> assertThat(message.message()).isNull());
    }
  }

  @Test
  void nullStartInputsFailClosed() {
    assertThatThrownBy(() -> policy.toPolicy(null, mapper))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> policy.toPolicy(sessionCreatedResult(), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> policy.toPolicy((UsernameRecoveryStartException) null))
        .isInstanceOf(NullPointerException.class);
  }

  private static int classifiedHttp(ApiStatus status, HttpStatusClassificationOverride override) {
    return override == null
        ? HttpStatusClassification.classify(status).value()
        : HttpStatusClassification.classify(status, override).value();
  }

  private static UsernameRecoveryStartResult sessionCreatedResult() {
    return new UsernameRecoveryStartResult(
        Outcome.SESSION_CREATED,
        null,
        "csrf-value",
        600,
        List.of(
            new UsernameRecoverySchemeProjection(
                new UsernameRecoverySchemeRef("sch_opaque-1"), "DB", "DBA", true)));
  }

  private static UsernameRecoveryStartResult memberNotFoundResult() {
    return UsernameRecoveryStartResult.withoutSession(Outcome.MEMBER_NOT_FOUND, List.of());
  }

  private static UsernameRecoveryStartResult zeroRegisteredResult() {
    return UsernameRecoveryStartResult.withoutSession(
        Outcome.ZERO_REGISTERED,
        List.of(new UsernameRecoverySchemeProjection(null, "DB", "DBA", false)));
  }

  private static UsernameRecoveryStartException exceptionFor(Reason reason) {
    return switch (reason) {
      case VALIDATION_FAILED ->
          UsernameRecoveryStartException.validationFailed(
              List.of(new Error("identityValue", Kind.IDENTITY_VALUE_INVALID)));
      case IDEMPOTENCY_IN_PROGRESS -> UsernameRecoveryStartException.idempotencyInProgress();
      case IDEMPOTENCY_KEY_REUSED -> UsernameRecoveryStartException.keyReused();
      case UNKNOWN_OUTCOME_EXISTING -> UsernameRecoveryStartException.unknownOutcomeExisting();
      case CAPTCHA_INVALID -> UsernameRecoveryStartException.captchaInvalid();
      case CAPTCHA_NOT_FOUND -> UsernameRecoveryStartException.captchaNotFound();
      case CAPTCHA_NOT_VERIFIED -> UsernameRecoveryStartException.captchaNotVerified();
      case CAPTCHA_EXPIRED -> UsernameRecoveryStartException.captchaExpired();
      case CAPTCHA_CONFLICT -> UsernameRecoveryStartException.captchaConflict();
      case REPLAY_REVISION_CONFLICT -> UsernameRecoveryStartException.replayRevisionConflict();
      case SYSTEM_FAILURE -> UsernameRecoveryStartException.systemFailure();
    };
  }

  private static Expected expectedFor(Reason reason) {
    return switch (reason) {
      case VALIDATION_FAILED ->
          new Expected(
              ApiStatus.VALIDATION_FAILED,
              WorkflowNextStep.NONE,
              RegistrationErrorKey.REGISTRATION_IDENTITYVALUE_INVALID.value(),
              List.of("identityValue"),
              "BFF",
              null,
              400);
      case IDEMPOTENCY_IN_PROGRESS ->
          new Expected(
              ApiStatus.BUSINESS_REJECTED,
              WorkflowNextStep.WAIT,
              SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS.value(),
              List.of(),
              "BFF",
              HttpStatusClassificationOverride.CONFLICT,
              409);
      case IDEMPOTENCY_KEY_REUSED ->
          new Expected(
              ApiStatus.VALIDATION_FAILED,
              WorkflowNextStep.NONE,
              SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value(),
              List.of("Idempotency-Key"),
              "BFF",
              HttpStatusClassificationOverride.CONFLICT,
              409);
      case UNKNOWN_OUTCOME_EXISTING ->
          new Expected(
              ApiStatus.SYSTEM_ERROR,
              WorkflowNextStep.WAIT,
              SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value(),
              List.of(),
              "SERVER",
              null,
              503);
      case CAPTCHA_INVALID ->
          new Expected(
              ApiStatus.VALIDATION_FAILED,
              WorkflowNextStep.NONE,
              RegistrationErrorKey.REGISTRATION_CAPTCHA_INVALID.value(),
              List.of(),
              "BFF",
              null,
              400);
      case CAPTCHA_NOT_FOUND ->
          new Expected(
              ApiStatus.VALIDATION_FAILED,
              WorkflowNextStep.NONE,
              RegistrationErrorKey.REGISTRATION_CAPTCHA_NOTFOUND.value(),
              List.of(),
              "BFF",
              null,
              400);
      case CAPTCHA_NOT_VERIFIED ->
          new Expected(
              ApiStatus.VALIDATION_FAILED,
              WorkflowNextStep.NONE,
              RegistrationErrorKey.REGISTRATION_CAPTCHA_REQUIRED.value(),
              List.of(),
              "BFF",
              null,
              400);
      case CAPTCHA_EXPIRED ->
          new Expected(
              ApiStatus.VALIDATION_FAILED,
              WorkflowNextStep.NONE,
              RegistrationErrorKey.REGISTRATION_CAPTCHA_EXPIRED.value(),
              List.of(),
              "BFF",
              null,
              400);
      case CAPTCHA_CONFLICT ->
          new Expected(
              ApiStatus.BUSINESS_REJECTED,
              WorkflowNextStep.NONE,
              RegistrationErrorKey.REGISTRATION_CAPTCHA_CONSUMED.value(),
              List.of(),
              "BFF",
              HttpStatusClassificationOverride.CONFLICT,
              409);
      case REPLAY_REVISION_CONFLICT ->
          new Expected(
              ApiStatus.BUSINESS_REJECTED,
              WorkflowNextStep.NONE,
              SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value(),
              List.of(),
              "BFF",
              HttpStatusClassificationOverride.CONFLICT,
              409);
      case SYSTEM_FAILURE ->
          new Expected(
              ApiStatus.SYSTEM_ERROR,
              WorkflowNextStep.NONE,
              SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value(),
              List.of(),
              "SERVER",
              null,
              503);
    };
  }

  private record Expected(
      ApiStatus status,
      WorkflowNextStep nextStep,
      String code,
      List<String> targets,
      String source,
      HttpStatusClassificationOverride override,
      int http) {}
}
