package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.Objects;
import org.springframework.http.ResponseEntity;

/**
 * Shared typed conversion from an already-typed {@link MutationResponse} to a {@link
 * ResponseEntity}.
 *
 * <p>Translates {@code MutationResponse<T>} using the explicit typed HTTP-status classification
 * policy from {@link HttpStatusClassification}. It never infers transport behavior from error text,
 * the first error, arbitrary strings, result reflection, or maps.
 *
 * <p>The exact body instance and generic type are preserved. Null required inputs are rejected with
 * {@link Objects#requireNonNull} following the established shared-helper convention. The typed
 * classification policy represents HTTP status only, so this helper adds no response headers.
 */
public final class WorkflowHttpResponses {
  private WorkflowHttpResponses() {}

  /**
   * Wraps a typed mutation response with the HTTP status classified from its normalized public
   * {@link ApiStatus}.
   *
   * @param body typed mutation response; must not be null
   * @return response entity carrying the exact same body instance
   * @throws NullPointerException if body is null
   * @throws IllegalArgumentException if the body status is an unnormalized downstream status
   */
  public static <T> ResponseEntity<MutationResponse<T>> toResponse(MutationResponse<T> body) {
    Objects.requireNonNull(body, "body must not be null");
    return ResponseEntity.status(HttpStatusClassification.classify(body.status())).body(body);
  }

  /**
   * Wraps a typed mutation response with the HTTP status of an explicit typed override. The status
   * is validated as normalized before the override is applied.
   *
   * @param body typed mutation response; must not be null
   * @param override approved typed transport override; must not be null
   * @return response entity carrying the exact same body instance
   * @throws NullPointerException if body or override is null
   * @throws IllegalArgumentException if the body status is an unnormalized downstream status
   */
  public static <T> ResponseEntity<MutationResponse<T>> toResponse(
      MutationResponse<T> body, HttpStatusClassificationOverride override) {
    Objects.requireNonNull(body, "body must not be null");
    Objects.requireNonNull(override, "override must not be null");
    return ResponseEntity.status(HttpStatusClassification.classify(body.status(), override))
        .body(body);
  }
}
