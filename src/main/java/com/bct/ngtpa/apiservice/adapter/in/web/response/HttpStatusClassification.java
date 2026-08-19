package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * Stateless HTTP-status classification policy for normalized public {@link ApiStatus} values.
 *
 * <p>Nine normalized public statuses map to {@link HttpStatus}. The two unnormalized downstream
 * statuses fail closed with {@link IllegalArgumentException} because they must first pass through
 * {@code WorkflowPublicApiStatusNormalizer}. There is no default switch branch and no public HTTP
 * 502 mapping.
 *
 * <p>The typed override variant applies an approved transport override only after downstream-status
 * normalization is proven; it never bypasses normalization.
 */
public final class HttpStatusClassification {
  private HttpStatusClassification() {}

  /**
   * Classifies a normalized public {@link ApiStatus} into {@link HttpStatus}.
   *
   * @param status normalized public status; must not be null
   * @return the approved HTTP status
   * @throws NullPointerException if status is null
   * @throws IllegalArgumentException if an unnormalized downstream status is supplied
   */
  public static HttpStatus classify(ApiStatus status) {
    Objects.requireNonNull(status, "status must not be null");
    return switch (status) {
      case SUCCESS, UPDATED, SUBMITTED, NO_CHANGE, PENDING_APPROVAL, PARTIAL_SUCCESS ->
          HttpStatus.OK;
      case VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
      case BUSINESS_REJECTED -> HttpStatus.UNPROCESSABLE_ENTITY;
      case SYSTEM_ERROR -> HttpStatus.SERVICE_UNAVAILABLE;
      case DOWNSTREAM_REJECTED, DOWNSTREAM_ERROR ->
          throw new IllegalArgumentException(
              "status must be normalized before HTTP classification");
    };
  }

  /**
   * Classifies a normalized public {@link ApiStatus} and applies an approved typed override. The
   * status is validated as normalized before the override is applied.
   *
   * @param status normalized public status; must not be null
   * @param override approved transport override; must not be null
   * @return the override's approved HTTP status
   * @throws NullPointerException if status or override is null
   * @throws IllegalArgumentException if an unnormalized downstream status is supplied
   */
  public static HttpStatus classify(ApiStatus status, HttpStatusClassificationOverride override) {
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(override, "override must not be null");
    classify(status);
    return override.httpStatus();
  }
}
