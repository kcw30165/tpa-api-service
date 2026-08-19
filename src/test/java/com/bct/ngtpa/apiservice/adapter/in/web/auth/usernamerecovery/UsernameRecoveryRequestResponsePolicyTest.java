package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.HttpStatusClassification;
import com.bct.ngtpa.apiservice.adapter.in.web.response.HttpStatusClassificationOverride;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryDestinationType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult.Outcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.UsernameRecoveryErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryRequestException;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryRequestException.Reason;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exhaustive Request response-policy tests: every Request result outcome (including the
 * PARTIALLY_REQUESTED/COMPLETED accepted branches), every Request exception reason, exact public
 * status/nextStep/messages/errors/ targets/source/severity, typed HTTP classification, no
 * downstream statuses, and no sensitive leakage.
 */
class UsernameRecoveryRequestResponsePolicyTest {

  private final UsernameRecoveryRequestResponsePolicy policy =
      new UsernameRecoveryRequestResponsePolicy();
  private final UsernameRecoveryRequestWebMapper mapper = new UsernameRecoveryRequestWebMapper();

  @Test
  void partiallyRequestedAcceptedMapsToSuccessSelectAccountWithRequestAcceptedMessage() {
    var result = accepted(UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED);

    var responsePolicy = policy.toPolicy(result, mapper);
    var body = responsePolicy.body();

    assertThat(body.success()).isTrue();
    assertThat(body.status()).isEqualTo(ApiStatus.SUCCESS);
    assertThat(body.result().nextStep()).isEqualTo(WorkflowNextStep.SELECT_ACCOUNT);
    assertThat(body.result().data()).isNotNull();
    assertThat(body.result().data().status()).isEqualTo("PARTIALLY_REQUESTED");
    assertThat(body.result().data().schemeRef()).isEqualTo("sch_opaque-1");
    assertThat(body.result().data().maskedDestination()).isEqualTo("p****c@ah.ozk");
    assertThat(body.result().data().destinationType()).isEqualTo("EMAIL");
    assertThat(body.result().data().requestAccepted()).isTrue();
    assertThat(body.messages())
        .singleElement()
        .satisfies(
            message -> {
              assertThat(message.type()).isEqualTo("INFO");
              assertThat(message.code()).isEqualTo("label.usernamerecovery.request.accepted");
              assertThat(message.message()).isNull();
            });
    assertThat(body.errors()).isEmpty();
    assertThat(responsePolicy.httpOverride()).isNull();
    assertThat(classifiedHttp(body.status(), responsePolicy.httpOverride())).isEqualTo(200);
  }

  @Test
  void completedAcceptedMapsToSuccessReturnToLoginWithAcceptedAndReturnToLoginMessages() {
    var result = accepted(UsernameRecoveryWorkflowStatus.COMPLETED);

    var responsePolicy = policy.toPolicy(result, mapper);
    var body = responsePolicy.body();

    assertThat(body.success()).isTrue();
    assertThat(body.status()).isEqualTo(ApiStatus.SUCCESS);
    assertThat(body.result().nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
    assertThat(body.result().data().status()).isEqualTo("COMPLETED");
    assertThat(body.messages())
        .extracting(m -> m.code())
        .containsExactly("label.usernamerecovery.request.accepted", "label.return.to.login");
    assertThat(body.messages())
        .allSatisfy(
            message -> {
              assertThat(message.type()).isEqualTo("INFO");
              assertThat(message.message()).isNull();
            });
    assertThat(body.errors()).isEmpty();
    assertThat(responsePolicy.httpOverride()).isNull();
    assertThat(classifiedHttp(body.status(), responsePolicy.httpOverride())).isEqualTo(200);
  }

  @Test
  void everyNonAcceptedRequestResultMapsToExactTypedPolicy() {
    for (Outcome outcome : nonAcceptedOutcomes()) {
      var responsePolicy = policy.toPolicy(outcome(outcome), mapper);
      var body = responsePolicy.body();
      Expected expected = expectedFor(outcome);

      assertThat(body.success()).as("success for %s", outcome).isFalse();
      assertThat(body.status()).as("status for %s", outcome).isEqualTo(expected.status);
      assertThat(body.result()).as("result for %s", outcome).isNotNull();
      assertThat(body.result().nextStep())
          .as("nextStep for %s", outcome)
          .isEqualTo(expected.nextStep);
      assertThat(body.result().data()).as("data for %s", outcome).isNull();
      assertThat(body.messages()).as("messages for %s", outcome).isEmpty();
      assertThat(body.errors())
          .as("errors for %s", outcome)
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
          .as("override for %s", outcome)
          .isEqualTo(expected.override);
      assertThat(classifiedHttp(body.status(), responsePolicy.httpOverride()))
          .as("http for %s", outcome)
          .isEqualTo(expected.http);
    }
  }

  @Test
  void everyRequestExceptionReasonMapsToExactTypedPolicy() {
    for (Reason reason : Reason.values()) {
      UsernameRecoveryResponsePolicy<
              UsernameRecoveryWorkflowResultResponse<UsernameRecoveryRequestDataResponse>>
          responsePolicy = policy.toPolicy(exceptionFor(reason));
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
  void everyRequestPolicyNeverEmitsDownstreamStatuses() {
    List<MutationResponse<?>> bodies = new ArrayList<>();
    for (Outcome outcome : nonAcceptedOutcomes()) {
      bodies.add(policy.toPolicy(outcome(outcome), mapper).body());
    }
    bodies.add(
        policy
            .toPolicy(accepted(UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED), mapper)
            .body());
    bodies.add(policy.toPolicy(accepted(UsernameRecoveryWorkflowStatus.COMPLETED), mapper).body());
    for (Reason reason : Reason.values()) {
      bodies.add(policy.toPolicy(exceptionFor(reason)).body());
    }

    assertThat(bodies)
        .allSatisfy(
            body -> {
              assertThat(body.status()).isNotEqualTo(ApiStatus.DOWNSTREAM_REJECTED);
              assertThat(body.status()).isNotEqualTo(ApiStatus.DOWNSTREAM_ERROR);
            });
  }

  @Test
  void requestPoliciesNeverLeakRawExceptionTextOrSensitiveValues() {
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
  void unknownOutcomeRemainsDistinctFromOrdinarySystemFailure() {
    var system = policy.toPolicy(outcome(Outcome.SYSTEM_FAILED), mapper).body();
    var unknown = policy.toPolicy(outcome(Outcome.UNKNOWN_OUTCOME), mapper).body();

    assertThat(system.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(unknown.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(system.result().nextStep()).isEqualTo(WorkflowNextStep.SELECT_ACCOUNT);
    assertThat(unknown.result().nextStep()).isEqualTo(WorkflowNextStep.WAIT);
    assertThat(system.errors())
        .extracting(ApiError::code)
        .containsExactly(SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value());
    assertThat(unknown.errors())
        .extracting(ApiError::code)
        .containsExactly(SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value());
  }

  @Test
  void nullRequestInputsFailClosed() {
    assertThatThrownBy(() -> policy.toPolicy(null, mapper))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                policy.toPolicy(accepted(UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> policy.toPolicy((UsernameRecoveryRequestException) null))
        .isInstanceOf(NullPointerException.class);
  }

  private static int classifiedHttp(ApiStatus status, HttpStatusClassificationOverride override) {
    return override == null
        ? HttpStatusClassification.classify(status).value()
        : HttpStatusClassification.classify(status, override).value();
  }

  private static List<Outcome> nonAcceptedOutcomes() {
    return List.of(
        Outcome.ALREADY_REQUESTED,
        Outcome.WORKFLOW_COMPLETED,
        Outcome.UNREGISTERED_CANDIDATE,
        Outcome.INVALID_SCHEME_REF,
        Outcome.SYSTEM_FAILED,
        Outcome.UNKNOWN_OUTCOME);
  }

  private static UsernameRecoveryRequestResult accepted(UsernameRecoveryWorkflowStatus status) {
    return new UsernameRecoveryRequestResult(
        Outcome.REQUEST_ACCEPTED,
        status,
        new UsernameRecoverySchemeRef("sch_opaque-1"),
        "p****c@ah.ozk",
        UsernameRecoveryDestinationType.EMAIL);
  }

  private static UsernameRecoveryRequestResult outcome(Outcome outcome) {
    return new UsernameRecoveryRequestResult(outcome, null, null, null, null);
  }

  private static UsernameRecoveryRequestException exceptionFor(Reason reason) {
    return switch (reason) {
      case VALIDATION_FAILED -> UsernameRecoveryRequestException.validationFailed();
      case SECURITY_CONTEXT_REQUIRED -> UsernameRecoveryRequestException.securityContextRequired();
      case IDEMPOTENCY_IN_PROGRESS -> UsernameRecoveryRequestException.idempotencyInProgress();
      case IDEMPOTENCY_KEY_REUSED -> UsernameRecoveryRequestException.keyReused();
      case UNKNOWN_OUTCOME_EXISTING -> UsernameRecoveryRequestException.unknownOutcomeExisting();
      case SESSION_ABSENT -> UsernameRecoveryRequestException.sessionAbsent();
      case SESSION_EXPIRED -> UsernameRecoveryRequestException.sessionExpired();
      case SECURITY_INVALID -> UsernameRecoveryRequestException.securityInvalid();
      case CANDIDATE_NOT_AVAILABLE -> UsernameRecoveryRequestException.candidateNotAvailable();
      case REVISION_CONFLICT -> UsernameRecoveryRequestException.revisionConflict();
      case SYSTEM_FAILURE -> UsernameRecoveryRequestException.systemFailure();
    };
  }

  private static Expected expectedFor(Outcome outcome) {
    return switch (outcome) {
      case ALREADY_REQUESTED ->
          new Expected(
              ApiStatus.BUSINESS_REJECTED,
              WorkflowNextStep.SELECT_ACCOUNT,
              UsernameRecoveryErrorKey.REQUEST_ALREADY_REQUESTED.value(),
              List.of(),
              "BFF",
              HttpStatusClassificationOverride.CONFLICT,
              409);
      case WORKFLOW_COMPLETED ->
          new Expected(
              ApiStatus.BUSINESS_REJECTED,
              WorkflowNextStep.RETURN_TO_LOGIN,
              UsernameRecoveryErrorKey.WORKFLOW_COMPLETED.value(),
              List.of(),
              "BFF",
              HttpStatusClassificationOverride.CONFLICT,
              409);
      case UNREGISTERED_CANDIDATE ->
          new Expected(
              ApiStatus.BUSINESS_REJECTED,
              WorkflowNextStep.SELECT_ACCOUNT,
              UsernameRecoveryErrorKey.REGISTRATION_REQUIRED.value(),
              List.of(),
              "BFF",
              null,
              422);
      case INVALID_SCHEME_REF ->
          new Expected(
              ApiStatus.VALIDATION_FAILED,
              WorkflowNextStep.SELECT_ACCOUNT,
              UsernameRecoveryErrorKey.SCHEME_REF_INVALID.value(),
              List.of("Scheme-Ref"),
              "BFF",
              null,
              400);
      case SYSTEM_FAILED ->
          new Expected(
              ApiStatus.SYSTEM_ERROR,
              WorkflowNextStep.SELECT_ACCOUNT,
              SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value(),
              List.of(),
              "SERVER",
              null,
              503);
      case UNKNOWN_OUTCOME ->
          new Expected(
              ApiStatus.SYSTEM_ERROR,
              WorkflowNextStep.WAIT,
              SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value(),
              List.of(),
              "SERVER",
              null,
              503);
      case REQUEST_ACCEPTED ->
          throw new IllegalArgumentException("accepted outcome is not failure policy data");
    };
  }

  private static Expected expectedFor(Reason reason) {
    return switch (reason) {
      case VALIDATION_FAILED ->
          new Expected(
              ApiStatus.VALIDATION_FAILED,
              WorkflowNextStep.NONE,
              ErrorCodes.REQUEST_VALIDATION_FAILED,
              List.of(),
              "BFF",
              null,
              400);
      case SECURITY_CONTEXT_REQUIRED ->
          new Expected(
              ApiStatus.BUSINESS_REJECTED,
              WorkflowNextStep.RESTART_FLOW,
              ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED,
              List.of("PAGE"),
              "SERVER",
              null,
              422);
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
      case SESSION_ABSENT ->
          new Expected(
              ApiStatus.VALIDATION_FAILED,
              WorkflowNextStep.RESTART_FLOW,
              UsernameRecoveryErrorKey.AUTH_FLOW_INVALID.value(),
              List.of(),
              "SERVER",
              null,
              400);
      case SESSION_EXPIRED ->
          new Expected(
              ApiStatus.VALIDATION_FAILED,
              WorkflowNextStep.RESTART_FLOW,
              UsernameRecoveryErrorKey.AUTH_FLOW_INVALID.value(),
              List.of(),
              "SERVER",
              null,
              400);
      case SECURITY_INVALID ->
          new Expected(
              ApiStatus.VALIDATION_FAILED,
              WorkflowNextStep.NONE,
              UsernameRecoveryErrorKey.CSRF_INVALID.value(),
              List.of(),
              "SERVER",
              HttpStatusClassificationOverride.FORBIDDEN,
              403);
      case CANDIDATE_NOT_AVAILABLE ->
          new Expected(
              ApiStatus.BUSINESS_REJECTED,
              WorkflowNextStep.SELECT_ACCOUNT,
              UsernameRecoveryErrorKey.REGISTRATION_REQUIRED.value(),
              List.of(),
              "BFF",
              null,
              422);
      case REVISION_CONFLICT ->
          new Expected(
              ApiStatus.VALIDATION_FAILED,
              WorkflowNextStep.RESTART_FLOW,
              UsernameRecoveryErrorKey.AUTH_FLOW_INVALID.value(),
              List.of(),
              "SERVER",
              HttpStatusClassificationOverride.CONFLICT,
              409);
      case SYSTEM_FAILURE ->
          new Expected(
              ApiStatus.SYSTEM_ERROR,
              WorkflowNextStep.SELECT_ACCOUNT,
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
