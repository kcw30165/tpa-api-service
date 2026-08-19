package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

/**
 * Shared idempotency-key transport responses.
 *
 * <p>Owns common missing-key, in-progress, key-reused, and completed-replay transport semantics
 * only. Does NOT know about Scheme-Ref, candidate state, SELECT_ACCOUNT, RESTART_FLOW, or any
 * feature-owned semantics.
 */
public final class IdempotencyHttpResponses {
  private IdempotencyHttpResponses() {}

  /** 400 response used when a required {@code Idempotency-Key} is missing. */
  public static <T> Mono<ResponseEntity<MutationResponse<T>>> missingKey() {
    MutationResponse<T> body =
        MutationResponse.failure(
            ApiStatus.VALIDATION_FAILED,
            List.of(
                ApiError.form(
                    SharedWorkflowErrorKey.IDEMPOTENCY_MISSING.value(),
                    null,
                    List.of("Idempotency-Key"),
                    "SERVER")));
    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body));
  }

  /**
   * 409 response used when an operation with the same key is still in progress, including a typed
   * {@code Retry-After} header in seconds.
   */
  public static <T> Mono<ResponseEntity<MutationResponse<T>>> inProgress(int retryAfterSeconds) {
    MutationResponse<T> body =
        MutationResponse.failure(
            ApiStatus.BUSINESS_REJECTED,
            List.of(
                ApiError.business(
                    SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS.value(),
                    null,
                    List.of(),
                    "SERVER")));
    return Mono.just(
        ResponseEntity.status(HttpStatus.CONFLICT)
            .header(HttpHeaders.RETRY_AFTER, Integer.toString(retryAfterSeconds))
            .body(body));
  }

  /** 409 response used when a key is reused with a different semantic request. */
  public static <T> Mono<ResponseEntity<MutationResponse<T>>> keyReused() {
    MutationResponse<T> body =
        MutationResponse.failure(
            ApiStatus.VALIDATION_FAILED,
            List.of(
                ApiError.form(
                    SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value(),
                    null,
                    List.of("Idempotency-Key"),
                    "SERVER")));
    return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(body));
  }

  /**
   * 200 replay of an already-completed operation body. The original {@link MutationResponse} is
   * replayed as-is with no extra headers.
   */
  public static <T> Mono<ResponseEntity<MutationResponse<T>>> completedReplay(
      MutationResponse<T> original) {
    return Mono.just(ResponseEntity.ok(original));
  }
}
