package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.List;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Builds the final {@link ResponseEntity} for a failed request.
 *
 * <p>Sub-task 1.3: this is a verbatim move of the response-building half of {@code
 * ApiExceptionHandler.buildMutationErrorResponse(...)} (the {@code ResponseEntity.BodyBuilder}
 * construction, the conditional {@code X-Request-Id} header, and the {@link MutationResponse}
 * body). The error-list fallback (substituting a system error when {@code errors} is null or empty)
 * stays in {@code ApiExceptionHandler} because it depends on {@code resolvePublicMessage}, which in
 * turn depends on handler-specific locale/account-context resolution; only the already-resolved,
 * non-empty error list is passed here.
 */
public final class ErrorResponseRenderer {

  private ErrorResponseRenderer() {}

  public static ResponseEntity<MutationResponse<Void>> render(
      HttpStatusCode status, ApiStatus apiStatus, List<ApiError> errors, String requestId) {
    ResponseEntity.BodyBuilder builder =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON);
    if (requestId != null) {
      builder.header(RequestCorrelation.REQUEST_ID_HEADER, requestId);
    }
    return builder.body(MutationResponse.<Void>failure(apiStatus, errors));
  }
}
