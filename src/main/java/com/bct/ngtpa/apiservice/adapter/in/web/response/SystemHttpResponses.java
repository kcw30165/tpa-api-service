package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

/**
 * Shared generic public {@code SYSTEM_ERROR} response for internal
 * {@code SYSTEM_FAILED}.
 *
 * <p>
 * Emits a fixed 503 SERVICE_UNAVAILABLE envelope with the approved generic
 * system code and no exception, downstream, or feature-owned detail.
 */
public final class SystemHttpResponses {
    private SystemHttpResponses() {
    }

    /** 503 response used when an internal failure maps to public {@code SYSTEM_ERROR}. */
    public static <T> Mono<ResponseEntity<MutationResponse<T>>> systemError() {
        MutationResponse<T> body = MutationResponse.failure(
                ApiStatus.SYSTEM_ERROR,
                List.of(ApiError.system(
                        SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value(),
                        null,
                        "SERVER")));
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }
}
