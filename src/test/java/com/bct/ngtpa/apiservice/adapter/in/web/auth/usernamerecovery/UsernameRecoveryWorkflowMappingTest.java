package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiMessage;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.HttpStatusClassificationOverride;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for the shared Task 023 mapping helpers: the committed message-label constants, approved
 * targets/sources, the typed policy wrapper, the failure envelope builder, and the
 * ApiMessage/ApiError factories that keep the same public keys and envelope shapes the shared
 * transport helpers own.
 */
class UsernameRecoveryWorkflowMappingTest {

  @Test
  void committedMessageLabelsAreExactTypedConstants() {
    assertThat(UsernameRecoveryWorkflowMapping.MESSAGE_COMPLETE_REGISTRATION.value())
        .isEqualTo("label.usernamerecovery.complete.registration");
    assertThat(UsernameRecoveryWorkflowMapping.MESSAGE_REQUEST_ACCEPTED.value())
        .isEqualTo("label.usernamerecovery.request.accepted");
    assertThat(UsernameRecoveryWorkflowMapping.MESSAGE_RETURN_TO_LOGIN.value())
        .isEqualTo("label.return.to.login");
  }

  @Test
  void approvedTargetsAndSourcesAreExact() {
    assertThat(UsernameRecoveryWorkflowMapping.TARGET_IDEMPOTENCY_KEY).isEqualTo("Idempotency-Key");
    assertThat(UsernameRecoveryWorkflowMapping.TARGET_SCHEME_REF).isEqualTo("Scheme-Ref");
    assertThat(UsernameRecoveryWorkflowMapping.TARGET_PAGE).isEqualTo("PAGE");
    assertThat(UsernameRecoveryWorkflowMapping.SOURCE_BFF).isEqualTo("BFF");
    assertThat(UsernameRecoveryWorkflowMapping.SOURCE_SERVER).isEqualTo("SERVER");
  }

  @Test
  void policyFactoryWrapsBodyWithoutOverride() {
    MutationResponse<String> body =
        MutationResponse.success(ApiStatus.SUCCESS, "payload", List.of());

    UsernameRecoveryResponsePolicy<String> policy = UsernameRecoveryWorkflowMapping.policy(body);

    assertThat(policy.body()).isSameAs(body);
    assertThat(policy.httpOverride()).isNull();
  }

  @Test
  void policyFactoryWrapsBodyWithTypedOverride() {
    MutationResponse<String> body =
        MutationResponse.success(ApiStatus.SUCCESS, "payload", List.of());

    UsernameRecoveryResponsePolicy<String> policy =
        UsernameRecoveryWorkflowMapping.policy(body, HttpStatusClassificationOverride.CONFLICT);

    assertThat(policy.body()).isSameAs(body);
    assertThat(policy.httpOverride()).isEqualTo(HttpStatusClassificationOverride.CONFLICT);
  }

  @Test
  void policyFactoryRejectsNullBody() {
    assertThatThrownBy(() -> UsernameRecoveryWorkflowMapping.policy(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                UsernameRecoveryWorkflowMapping.policy(
                    null, HttpStatusClassificationOverride.FORBIDDEN))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void infoMessageProducesInfoLabelWithNoRenderedTextOrTarget() {
    ApiMessage message =
        UsernameRecoveryWorkflowMapping.infoMessage(
            UsernameRecoveryWorkflowMapping.MESSAGE_REQUEST_ACCEPTED);

    assertThat(message.type()).isEqualTo("INFO");
    assertThat(message.code()).isEqualTo("label.usernamerecovery.request.accepted");
    assertThat(message.message()).isNull();
    assertThat(message.target()).isNull();
  }

  @Test
  void failureResponseBuildsFalseFailureEnvelopeWithNextStepPayloadAndErrors() {
    MutationResponse<UsernameRecoveryWorkflowResultResponse<Void>> response =
        UsernameRecoveryWorkflowMapping.failureResponse(
            ApiStatus.BUSINESS_REJECTED,
            WorkflowNextStep.WAIT,
            List.of(UsernameRecoveryWorkflowMapping.formError("err.test.code", List.of(), "BFF")));

    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(response.result()).isNotNull();
    assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.WAIT);
    assertThat(response.result().data()).isNull();
    assertThat(response.messages()).isEmpty();
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.code()).isEqualTo("err.test.code");
              assertThat(error.source()).isEqualTo("BFF");
            });
  }

  @Test
  void formErrorProducesFormTypeErrorWithDefaultSeverity() {
    ApiError error =
        UsernameRecoveryWorkflowMapping.formError("err.test.code", List.of("PAGE"), "BFF");

    assertThat(error.type()).isEqualTo("FORM");
    assertThat(error.code()).isEqualTo("err.test.code");
    assertThat(error.targets()).containsExactly("PAGE");
    assertThat(error.source()).isEqualTo("BFF");
    assertThat(error.severity()).isEqualTo("ERROR");
    assertThat(error.message()).isNull();
  }

  @Test
  void businessErrorProducesBusinessTypeError() {
    ApiError error =
        UsernameRecoveryWorkflowMapping.businessError("err.test.code", List.of(), "BFF");

    assertThat(error.type()).isEqualTo("BUSINESS");
    assertThat(error.code()).isEqualTo("err.test.code");
    assertThat(error.targets()).isEmpty();
    assertThat(error.source()).isEqualTo("BFF");
    assertThat(error.severity()).isEqualTo("ERROR");
    assertThat(error.message()).isNull();
  }

  @Test
  void systemErrorProducesSystemTypeErrorWithNoTargets() {
    ApiError error = UsernameRecoveryWorkflowMapping.systemError("err.test.code", "SERVER");

    assertThat(error.type()).isEqualTo("SYSTEM");
    assertThat(error.code()).isEqualTo("err.test.code");
    assertThat(error.targets()).isEmpty();
    assertThat(error.source()).isEqualTo("SERVER");
    assertThat(error.severity()).isEqualTo("ERROR");
    assertThat(error.message()).isNull();
  }

  @Test
  void authenticationRequiredErrorMatchesSharedCookieAndCsrfEnvelope() {
    ApiError error = UsernameRecoveryWorkflowMapping.authenticationRequiredError();

    assertThat(error.type()).isEqualTo("AUTHENTICATION_REQUIRED");
    assertThat(error.code()).isEqualTo(ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED);
    assertThat(error.targets()).containsExactly(UsernameRecoveryWorkflowMapping.TARGET_PAGE);
    assertThat(error.source()).isEqualTo(UsernameRecoveryWorkflowMapping.SOURCE_SERVER);
    assertThat(error.severity()).isEqualTo("ERROR");
    assertThat(error.message()).isNull();
  }
}
