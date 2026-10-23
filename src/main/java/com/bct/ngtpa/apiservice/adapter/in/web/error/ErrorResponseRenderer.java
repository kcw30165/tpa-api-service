package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.List;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Builds the final {@link ResponseEntity} for a failed request.
 *
 * <p>Sub-task 1.3: originally a verbatim move of the response-building half of {@code
 * ApiExceptionHandler.buildMutationErrorResponse(...)} (the {@code ResponseEntity.BodyBuilder}
 * construction, the conditional {@code X-Request-Id} header, and the {@link MutationResponse}
 * body).
 *
 * <p>Sub-task 1.8.3 (M1 closure, step 3 of 3): converted from a static-method utility to a Spring
 * {@code @Component} bean. Unlike {@link ErrorEventLogger} (sub-task 1.8.2), which kept both a
 * static and an instance method because its instance method's arity naturally differs (no {@code
 * LoggingSanitizer} parameter, since it became a constructor field), this class's {@code render}
 * method has no injectable dependency: an instance method with the identical 4-argument signature
 * cannot coexist with a static method of the same signature in the same class (same erasure, not
 * valid overloading). Since this sub-task already updates every one of this class's 6 known callers
 * (2 production, 4 test) at the same time it rewrites {@code ApiExceptionHandler}'s constructor for
 * A1, the static method is removed entirely rather than kept as a soon-to-be-redundant duplicate.
 */
@Component
public final class ErrorResponseRenderer {

  public ResponseEntity<MutationResponse<Void>> render(
      HttpStatusCode status, ApiStatus apiStatus, List<ApiError> errors, String requestId) {
    ResponseEntity.BodyBuilder builder =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON);
    if (requestId != null) {
      builder.header(RequestCorrelation.REQUEST_ID_HEADER, requestId);
    }
    return builder.body(MutationResponse.failure(apiStatus, errors));
  }
}
