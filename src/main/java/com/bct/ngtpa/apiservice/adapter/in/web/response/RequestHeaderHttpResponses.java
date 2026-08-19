package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

/**
 * Shared HTTP envelope for a missing required syntactic request header.
 *
 * <p>Contract:
 *
 * <ul>
 *   <li>Missing required syntactic header → 400 BAD_REQUEST with {@code
 *       err.request.validation.failed} and the caller-supplied canonical header target.
 * </ul>
 *
 * <p>This helper owns generic transport-presence failures only. It does NOT own:
 *
 * <ul>
 *   <li>missing or blank {@code X-Request-Id} — {@code RequiredRequestIdWebFilter} rejects those
 *       pre-controller;
 *   <li>missing {@code Scheme-Ref} feature semantics — Scheme-Ref validity, ownership, and
 *       eligibility remain Username Recovery feature behavior;
 *   <li>Username Recovery error codes, next steps, or feature results.
 * </ul>
 */
public final class RequestHeaderHttpResponses {
  private RequestHeaderHttpResponses() {}

  /**
   * 400 response used when a required syntactic request header is missing.
   *
   * @param target canonical public header target supplied by the caller; must not be null
   */
  public static <T> Mono<ResponseEntity<MutationResponse<T>>> missingRequiredHeader(String target) {
    Objects.requireNonNull(target, "target must not be null");
    MutationResponse<T> body =
        MutationResponse.failure(
            ApiStatus.VALIDATION_FAILED,
            List.of(
                ApiError.field(
                    ErrorCodes.REQUEST_VALIDATION_FAILED, null, List.of(target), "SERVER")));
    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body));
  }
}
