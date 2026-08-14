package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.HttpStatusClassification;
import com.bct.ngtpa.apiservice.adapter.in.web.response.HttpStatusClassificationOverride;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult.Outcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.UsernameRecoveryErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryCancelException;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryCancelException.Reason;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exhaustive Cancel response-policy tests: every Cancel result outcome, every
 * Cancel exception reason, exact public status/nextStep/messages/errors/
 * targets/source/severity, typed HTTP classification, no downstream statuses,
 * and no sensitive leakage. Cookie clearing remains a Task 024 concern and is
 * never modeled here.
 */
class UsernameRecoveryCancelResponsePolicyTest {

    private final UsernameRecoveryCancelResponsePolicy policy = new UsernameRecoveryCancelResponsePolicy();
    private final UsernameRecoveryCancelWebMapper mapper = new UsernameRecoveryCancelWebMapper();

    @Test
    void successMapsToSuccessReturnToLoginWithNullData() {
        var responsePolicy = policy.toPolicy(new UsernameRecoveryCancelResult(Outcome.SUCCESS), mapper);
        var body = responsePolicy.body();

        assertThat(body.success()).isTrue();
        assertThat(body.status()).isEqualTo(ApiStatus.SUCCESS);
        assertThat(body.result().nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
        assertThat(body.result().data()).isNull();
        assertThat(body.messages()).isEmpty();
        assertThat(body.errors()).isEmpty();
        assertThat(responsePolicy.httpOverride()).isNull();
        assertThat(classifiedHttp(body.status(), responsePolicy.httpOverride())).isEqualTo(200);
    }

    @Test
    void systemFailedMapsToSystemErrorReturnToLoginWithSanitizedSystemCode() {
        var responsePolicy = policy.toPolicy(new UsernameRecoveryCancelResult(Outcome.SYSTEM_FAILED), mapper);
        var body = responsePolicy.body();

        assertThat(body.success()).isFalse();
        assertThat(body.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
        assertThat(body.result().nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
        assertThat(body.result().data()).isNull();
        assertThat(body.messages()).isEmpty();
        assertThat(body.errors()).singleElement().satisfies(error -> {
            assertThat(error.type()).isEqualTo("SYSTEM");
            assertThat(error.code()).isEqualTo(SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value());
            assertThat(error.targets()).isEmpty();
            assertThat(error.source()).isEqualTo("SERVER");
            assertThat(error.severity()).isEqualTo("ERROR");
            assertThat(error.message()).isNull();
        });
        assertThat(responsePolicy.httpOverride()).isNull();
        assertThat(classifiedHttp(body.status(), responsePolicy.httpOverride())).isEqualTo(503);
    }
    @Test
    void everyCancelExceptionReasonMapsToExactTypedPolicy() {
        for (Reason reason : Reason.values()) {
            UsernameRecoveryResponsePolicy<UsernameRecoveryWorkflowResultResponse<Void>> responsePolicy =
                    policy.toPolicy(exceptionFor(reason));
            var body = responsePolicy.body();
            Expected expected = expectedFor(reason);

            assertThat(body.success()).as("success for %s", reason).isFalse();
            assertThat(body.status()).as("status for %s", reason).isEqualTo(expected.status);
            assertThat(body.result()).as("result for %s", reason).isNotNull();
            assertThat(body.result().nextStep()).as("nextStep for %s", reason).isEqualTo(expected.nextStep);
            assertThat(body.result().data()).as("data for %s", reason).isNull();
            assertThat(body.messages()).as("messages for %s", reason).isEmpty();
            assertThat(body.errors()).as("errors for %s", reason).singleElement().satisfies(error -> {
                assertThat(error.code()).isEqualTo(expected.code);
                assertThat(error.targets()).containsExactlyElementsOf(expected.targets);
                assertThat(error.source()).isEqualTo(expected.source);
                assertThat(error.severity()).isEqualTo("ERROR");
                assertThat(error.message()).isNull();
            });
            assertThat(responsePolicy.httpOverride()).as("override for %s", reason).isEqualTo(expected.override);
            assertThat(classifiedHttp(body.status(), responsePolicy.httpOverride()))
                    .as("http for %s", reason).isEqualTo(expected.http);
        }
    }

    @Test
    void everyCancelPolicyNeverEmitsDownstreamStatuses() {
        List<MutationResponse<?>> bodies = new ArrayList<>();
        bodies.add(policy.toPolicy(new UsernameRecoveryCancelResult(Outcome.SUCCESS), mapper).body());
        bodies.add(policy.toPolicy(new UsernameRecoveryCancelResult(Outcome.SYSTEM_FAILED), mapper).body());
        for (Reason reason : Reason.values()) {
            bodies.add(policy.toPolicy(exceptionFor(reason)).body());
        }

        assertThat(bodies).allSatisfy(body -> {
            assertThat(body.status()).isNotEqualTo(ApiStatus.DOWNSTREAM_REJECTED);
            assertThat(body.status()).isNotEqualTo(ApiStatus.DOWNSTREAM_ERROR);
        });
    }

    @Test
    void cancelPoliciesNeverLeakRawExceptionTextOrSensitiveValues() {
        for (Reason reason : Reason.values()) {
            var responsePolicy = policy.toPolicy(exceptionFor(reason));
            assertThat(responsePolicy.body().errors()).allSatisfy(error -> {
                assertThat(error.message()).isNull();
                assertThat(error.code()).doesNotContain(reason.name());
            });
            assertThat(responsePolicy.body().messages()).allSatisfy(message ->
                    assertThat(message.message()).isNull());
        }
    }

    @Test
    void nullCancelInputsFailClosed() {
        assertThatThrownBy(() -> policy.toPolicy(null, mapper)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> policy.toPolicy(new UsernameRecoveryCancelResult(Outcome.SUCCESS), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> policy.toPolicy((UsernameRecoveryCancelException) null))
                .isInstanceOf(NullPointerException.class);
    }

    private static int classifiedHttp(ApiStatus status, HttpStatusClassificationOverride override) {
        return override == null
                ? HttpStatusClassification.classify(status).value()
                : HttpStatusClassification.classify(status, override).value();
    }

    private static UsernameRecoveryCancelException exceptionFor(Reason reason) {
        return switch (reason) {
            case VALIDATION_FAILED -> UsernameRecoveryCancelException.validationFailed();
            case SECURITY_CONTEXT_REQUIRED -> UsernameRecoveryCancelException.securityContextRequired();
            case SECURITY_INVALID -> UsernameRecoveryCancelException.securityInvalid();
            case IDEMPOTENCY_IN_PROGRESS -> UsernameRecoveryCancelException.idempotencyInProgress();
            case IDEMPOTENCY_KEY_REUSED -> UsernameRecoveryCancelException.keyReused();
            case UNKNOWN_OUTCOME_EXISTING -> UsernameRecoveryCancelException.unknownOutcomeExisting();
            case REVISION_CONFLICT -> UsernameRecoveryCancelException.revisionConflict();
            case SYSTEM_FAILURE -> UsernameRecoveryCancelException.systemFailure();
        };
    }

    private static Expected expectedFor(Reason reason) {
        return switch (reason) {
            case VALIDATION_FAILED -> new Expected(
                    ApiStatus.VALIDATION_FAILED, WorkflowNextStep.RETURN_TO_LOGIN,
                    ErrorCodes.REQUEST_VALIDATION_FAILED,
                    List.of(), "BFF", null, 400);
            case SECURITY_CONTEXT_REQUIRED -> new Expected(
                    ApiStatus.BUSINESS_REJECTED, WorkflowNextStep.RETURN_TO_LOGIN,
                    ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED,
                    List.of("PAGE"), "SERVER", null, 422);
            case SECURITY_INVALID -> new Expected(
                    ApiStatus.VALIDATION_FAILED, WorkflowNextStep.RETURN_TO_LOGIN,
                    UsernameRecoveryErrorKey.CSRF_INVALID.value(),
                    List.of(), "SERVER", HttpStatusClassificationOverride.FORBIDDEN, 403);
            case IDEMPOTENCY_IN_PROGRESS -> new Expected(
                    ApiStatus.BUSINESS_REJECTED, WorkflowNextStep.RETURN_TO_LOGIN,
                    SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS.value(),
                    List.of(), "BFF", HttpStatusClassificationOverride.CONFLICT, 409);
            case IDEMPOTENCY_KEY_REUSED -> new Expected(
                    ApiStatus.VALIDATION_FAILED, WorkflowNextStep.RETURN_TO_LOGIN,
                    SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value(),
                    List.of("Idempotency-Key"), "BFF", HttpStatusClassificationOverride.CONFLICT, 409);
            case UNKNOWN_OUTCOME_EXISTING -> new Expected(
                    ApiStatus.SYSTEM_ERROR, WorkflowNextStep.RETURN_TO_LOGIN,
                    SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value(),
                    List.of(), "SERVER", null, 503);
            case REVISION_CONFLICT -> new Expected(
                    ApiStatus.VALIDATION_FAILED, WorkflowNextStep.RETURN_TO_LOGIN,
                    UsernameRecoveryErrorKey.AUTH_FLOW_INVALID.value(),
                    List.of(), "BFF", HttpStatusClassificationOverride.CONFLICT, 409);
            case SYSTEM_FAILURE -> new Expected(
                    ApiStatus.SYSTEM_ERROR, WorkflowNextStep.RETURN_TO_LOGIN,
                    SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value(),
                    List.of(), "SERVER", null, 503);
        };
    }

    private record Expected(
            ApiStatus status,
            WorkflowNextStep nextStep,
            String code,
            List<String> targets,
            String source,
            HttpStatusClassificationOverride override,
            int http) {
    }
}