package com.bct.ngtpa.apiservice.adapter.in.web.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResultStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * E2 (2026-10-30): rendered-response guard confirming the uppercase {@code
 * WorkflowErrorType.DOWNSTREAM_BUSINESS}/{@code DOWNSTREAM_SYSTEM} internal classification values
 * are never surfaced verbatim as the public {@code ApiError.type} string.
 *
 * <p>These two values are live today - {@code RegistrationStartWebMapper.toWorkflowErrorType} maps
 * {@code DOWNSTREAM_REJECTED -> DOWNSTREAM_BUSINESS} and {@code MESSAGE_DELIVERY_FAILED ->
 * DOWNSTREAM_SYSTEM} - and exist to distinguish downstream-originated failures for internal
 * classification (see {@code WorkflowFailureCategory}, {@code FailureOrigin}); they are not meant
 * to be exposed as literal response field values. The rendered response must collapse them to the
 * same category the frontend already understands: BUSINESS or SYSTEM. This is a rendering-layer
 * collapse only - {@code WorkflowErrorType}, {@code FailureOrigin}, and the lowercase {@code
 * err.workflow.downstream.*} message keys (@code SharedWorkflowErrorKey}) are all unrelated and
 * unchanged by this guard.
 */
class WorkflowMutationResponseMapperRenderedTypeGuardTest {

  private final WorkflowMutationResponseMapper mapper = new WorkflowMutationResponseMapper();

  @Test
  void downstreamBusinessErrorTypeRendersAsPlainBusiness() {
    var error =
        new WorkflowErrorDescriptor(
            WorkflowErrorType.DOWNSTREAM_BUSINESS,
            "err.tpa.member.not.found",
            null,
            List.of("PAGE"),
            WorkflowErrorSeverity.ERROR,
            "BFF");
    var result =
        new WorkflowResult<Void>(
            WorkflowResultStatus.BUSINESS_FAILED, WorkflowNextStep.NONE, null, List.of(), List.of(error));

    var response = mapper.toMutationResponse(result);

    assertThat(response.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(response.errors()).hasSize(1);
    assertThat(response.errors().getFirst().type())
        .isEqualTo("BUSINESS")
        .doesNotContain("DOWNSTREAM");
  }

  @Test
  void downstreamSystemErrorTypeRendersAsPlainSystem() {
    var error =
        new WorkflowErrorDescriptor(
            WorkflowErrorType.DOWNSTREAM_SYSTEM,
            "err.registration.message.deliveryFailed",
            null,
            List.of("PAGE"),
            WorkflowErrorSeverity.ERROR,
            "BFF");
    var result =
        new WorkflowResult<Void>(
            WorkflowResultStatus.SYSTEM_FAILED, WorkflowNextStep.NONE, null, List.of(), List.of(error));

    var response = mapper.toMutationResponse(result);

    assertThat(response.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(response.errors()).hasSize(1);
    assertThat(response.errors().getFirst().type())
        .isEqualTo("SYSTEM")
        .doesNotContain("DOWNSTREAM");
  }

  @Test
  void nonDownstreamErrorTypesRenderUnchanged() {
    for (WorkflowErrorType type :
        List.of(
            WorkflowErrorType.FIELD,
            WorkflowErrorType.FORM,
            WorkflowErrorType.BUSINESS,
            WorkflowErrorType.SYSTEM)) {
      var error =
          new WorkflowErrorDescriptor(
              type, "err.some.code", null, List.of(), WorkflowErrorSeverity.ERROR, "BFF");
      var result =
          new WorkflowResult<Void>(
              WorkflowResultStatus.VALIDATION_FAILED,
              WorkflowNextStep.NONE,
              null,
              List.of(),
              List.of(error));

      var response = mapper.toMutationResponse(result);

      assertThat(response.errors().getFirst().type()).isEqualTo(type.name());
    }
  }
}
