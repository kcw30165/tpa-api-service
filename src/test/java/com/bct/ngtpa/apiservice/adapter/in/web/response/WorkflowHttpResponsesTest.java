package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

/**
 * Focused tests for the shared typed {@code MutationResponse<T>} to {@link ResponseEntity}
 * translation.
 *
 * <p>Contract (derived from the committed Task 002 typed classification policy):
 *
 * <ul>
 *   <li>every normalized public {@link ApiStatus} classifies through {@link
 *       HttpStatusClassification} — success-like statuses to 200 OK, {@code VALIDATION_FAILED} to
 *       400, {@code BUSINESS_REJECTED} to 422, {@code SYSTEM_ERROR} to 503;
 *   <li>an explicit {@link HttpStatusClassificationOverride} selects the HTTP status only after
 *       downstream-status normalization is proven;
 *   <li>the exact body instance and generic type are preserved;
 *   <li>the typed policy carries no headers, so the response adds none;
 *   <li>unnormalized downstream statuses fail closed with {@link IllegalArgumentException};
 *   <li>null required inputs are rejected with {@link NullPointerException}.
 * </ul>
 */
class WorkflowHttpResponsesTest {

  @Test
  void shouldMapSuccessLikeStatusesToOk() {
    assertThat(status(WorkflowHttpResponses.toResponse(success(ApiStatus.SUCCESS))))
        .isEqualTo(HttpStatus.OK);
    assertThat(status(WorkflowHttpResponses.toResponse(success(ApiStatus.UPDATED))))
        .isEqualTo(HttpStatus.OK);
    assertThat(status(WorkflowHttpResponses.toResponse(success(ApiStatus.SUBMITTED))))
        .isEqualTo(HttpStatus.OK);
    assertThat(status(WorkflowHttpResponses.toResponse(success(ApiStatus.NO_CHANGE))))
        .isEqualTo(HttpStatus.OK);
    assertThat(status(WorkflowHttpResponses.toResponse(success(ApiStatus.PENDING_APPROVAL))))
        .isEqualTo(HttpStatus.OK);
    assertThat(status(WorkflowHttpResponses.toResponse(success(ApiStatus.PARTIAL_SUCCESS))))
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void shouldMapValidationFailedToBadRequest() {
    assertThat(status(WorkflowHttpResponses.toResponse(failure(ApiStatus.VALIDATION_FAILED))))
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void shouldMapBusinessRejectedToUnprocessableEntity() {
    assertThat(status(WorkflowHttpResponses.toResponse(failure(ApiStatus.BUSINESS_REJECTED))))
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void shouldMapSystemErrorToServiceUnavailable() {
    assertThat(status(WorkflowHttpResponses.toResponse(failure(ApiStatus.SYSTEM_ERROR))))
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void shouldRejectUnnormalizedDownstreamRejected() {
    assertThatThrownBy(
            () -> WorkflowHttpResponses.toResponse(failure(ApiStatus.DOWNSTREAM_REJECTED)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectUnnormalizedDownstreamError() {
    assertThatThrownBy(() -> WorkflowHttpResponses.toResponse(failure(ApiStatus.DOWNSTREAM_ERROR)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectNullBody() {
    assertThatThrownBy(() -> WorkflowHttpResponses.toResponse(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldPreserveExactBodyInstanceAndGenericType() {
    MutationResponse<String> body =
        MutationResponse.success(ApiStatus.SUCCESS, "payload", List.of());
    ResponseEntity<MutationResponse<String>> entity = WorkflowHttpResponses.toResponse(body);
    assertThat(entity.getBody()).isSameAs(body);
    assertThat(entity.getBody().result()).isEqualTo("payload");
    assertThat(entity.getBody().success()).isTrue();
  }

  @Test
  void shouldPreserveTypedPayloadResultAcrossOverrideOverload() {
    record Payload(String value) {}
    MutationResponse<Payload> body =
        MutationResponse.success(ApiStatus.SUCCESS, new Payload("x"), List.of());
    ResponseEntity<MutationResponse<Payload>> entity =
        WorkflowHttpResponses.toResponse(body, HttpStatusClassificationOverride.CONFLICT);
    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(entity.getBody()).isSameAs(body);
    assertThat(entity.getBody().result()).isEqualTo(new Payload("x"));
  }

  @Test
  void shouldCarryNoHeadersBecauseTypedPolicyRepresentsNone() {
    assertThat(WorkflowHttpResponses.toResponse(success(ApiStatus.SUCCESS)).getHeaders().isEmpty())
        .isTrue();
    assertThat(
            WorkflowHttpResponses.toResponse(failure(ApiStatus.BUSINESS_REJECTED))
                .getHeaders()
                .isEmpty())
        .isTrue();
    assertThat(
            WorkflowHttpResponses.toResponse(
                    failure(ApiStatus.BUSINESS_REJECTED),
                    HttpStatusClassificationOverride.TOO_MANY_REQUESTS)
                .getHeaders()
                .isEmpty())
        .isTrue();
  }

  @Test
  void shouldApplyConflictOverrideToBusinessRejected() {
    assertThat(
            status(
                WorkflowHttpResponses.toResponse(
                    failure(ApiStatus.BUSINESS_REJECTED),
                    HttpStatusClassificationOverride.CONFLICT)))
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void shouldApplyTooManyRequestsOverrideToBusinessRejected() {
    assertThat(
            status(
                WorkflowHttpResponses.toResponse(
                    failure(ApiStatus.BUSINESS_REJECTED),
                    HttpStatusClassificationOverride.TOO_MANY_REQUESTS)))
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void shouldApplyForbiddenOverrideToValidationFailed() {
    assertThat(
            status(
                WorkflowHttpResponses.toResponse(
                    failure(ApiStatus.VALIDATION_FAILED),
                    HttpStatusClassificationOverride.FORBIDDEN)))
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void shouldApplyConflictOverrideToSystemError() {
    assertThat(
            status(
                WorkflowHttpResponses.toResponse(
                    failure(ApiStatus.SYSTEM_ERROR), HttpStatusClassificationOverride.CONFLICT)))
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void shouldRejectNullOverride() {
    assertThatThrownBy(
            () -> WorkflowHttpResponses.toResponse(failure(ApiStatus.BUSINESS_REJECTED), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldRejectNullBodyEvenWithOverride() {
    assertThatThrownBy(
            () -> WorkflowHttpResponses.toResponse(null, HttpStatusClassificationOverride.CONFLICT))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldRejectDownstreamStatusesEvenWithOverride() {
    assertThatThrownBy(
            () ->
                WorkflowHttpResponses.toResponse(
                    failure(ApiStatus.DOWNSTREAM_REJECTED),
                    HttpStatusClassificationOverride.CONFLICT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                WorkflowHttpResponses.toResponse(
                    failure(ApiStatus.DOWNSTREAM_ERROR), HttpStatusClassificationOverride.CONFLICT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static MutationResponse<String> success(ApiStatus status) {
    return MutationResponse.success(status, "ok", List.of());
  }

  private static MutationResponse<String> failure(ApiStatus status) {
    return MutationResponse.failure(status, List.of());
  }

  private static HttpStatusCode status(ResponseEntity<MutationResponse<String>> entity) {
    return entity.getStatusCode();
  }
}
