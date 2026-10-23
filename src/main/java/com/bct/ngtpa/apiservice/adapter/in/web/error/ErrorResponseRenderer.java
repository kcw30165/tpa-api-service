package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Builds the final {@link ResponseEntity} for a failed request: the {@link
 * ResponseEntity.BodyBuilder} construction, the conditional {@code X-Request-Id} header, and the
 * {@link MutationResponse} body.
 */
@Component
public final class ErrorResponseRenderer {

  private static final String SOURCE_SERVER = "SERVER";

  public ResponseEntity<MutationResponse<Void>> render(
      HttpStatusCode status, ApiStatus apiStatus, List<ApiError> errors, String requestId) {
    ResponseEntity.BodyBuilder builder =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON);
    if (requestId != null) {
      builder.header(RequestCorrelation.REQUEST_ID_HEADER, requestId);
    }
    return builder.body(MutationResponse.failure(apiStatus, errors));
  }

  public ResponseEntity<MutationResponse<Void>> render(
      ErrorDescriptor descriptor, List<String> messages, ErrorContext context) {
    List<ErrorEntry> entries = descriptor.errors();
    List<ApiError> errors = new ArrayList<>(entries.size());
    for (int i = 0; i < entries.size(); i++) {
      ErrorEntry entry = entries.get(i);
      errors.add(
          new ApiError(
              entry.type(),
              entry.code(),
              messages.get(i),
              entry.targets(),
              "ERROR",
              SOURCE_SERVER));
    }
    return render(
        descriptor.httpStatus(), descriptor.apiStatus(), List.copyOf(errors), context.requestId());
  }
}
