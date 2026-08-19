package com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.HttpStatusClassification;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public final class CaptchaHttpResponses {
  public <T> ResponseEntity<MutationResponse<T>> toResponse(MutationResponse<T> body) {
    Objects.requireNonNull(body, "body must not be null");
    if (isIneligibleVerification(body)) {
      return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(body);
    }
    return ResponseEntity.status(HttpStatusClassification.classify(body.status())).body(body);
  }

  private boolean isIneligibleVerification(MutationResponse<?> body) {
    return !body.success()
        && body.status() == ApiStatus.VALIDATION_FAILED
        && firstCode(body.errors()).equals(ErrorCodes.CAPTCHA_VERIFICATION_INVALID);
  }

  private String firstCode(List<ApiError> errors) {
    return errors == null
            || errors.isEmpty()
            || errors.getFirst() == null
            || errors.getFirst().code() == null
        ? ""
        : errors.getFirst().code();
  }
}
