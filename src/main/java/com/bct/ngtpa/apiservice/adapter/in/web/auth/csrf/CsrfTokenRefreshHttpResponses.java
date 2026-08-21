package com.bct.ngtpa.apiservice.adapter.in.web.auth.csrf;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.csrf.dto.CsrfTokenRefreshData;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.csrf.dto.CsrfTokenRefreshResult;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenResult;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public final class CsrfTokenRefreshHttpResponses {
  public ResponseEntity<MutationResponse<CsrfTokenRefreshResult>> toResponse(
      RefreshCsrfTokenResult result) {
    return switch (result.status()) {
      case SUCCESS ->
          ResponseEntity.ok(
              MutationResponse.success(
                  ApiStatus.SUCCESS,
                  new CsrfTokenRefreshResult(
                      new CsrfTokenRefreshData(result.sessionType(), result.csrfToken())),
                  List.of()));
      case ORIGIN_REJECTED ->
          failure(
              HttpStatus.FORBIDDEN,
              ApiStatus.VALIDATION_FAILED,
              "CSRF_REFRESH_ORIGIN_REJECTED",
              "err.security.origin.invalid");
      case CONFLICT ->
          failure(
              HttpStatus.CONFLICT,
              ApiStatus.BUSINESS_REJECTED,
              "CSRF_REFRESH_CONFLICT",
              "err.security.csrf.refresh-conflict");
      case SYSTEM_FAILURE ->
          failure(
              HttpStatus.SERVICE_UNAVAILABLE,
              ApiStatus.SYSTEM_ERROR,
              "CSRF_REFRESH_SYSTEM_ERROR",
              "err.system.unavailable");
      case SESSION_MISSING, SESSION_INVALID, SESSION_NOT_USABLE -> unauthorized();
    };
  }

  public ResponseEntity<MutationResponse<CsrfTokenRefreshResult>> unauthorized() {
    return failure(
        HttpStatus.UNAUTHORIZED,
        ApiStatus.BUSINESS_REJECTED,
        "AUTHENTICATION_REQUIRED",
        ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED);
  }

  public ResponseEntity<MutationResponse<CsrfTokenRefreshResult>> ambiguousCookie() {
    return failure(
        HttpStatus.BAD_REQUEST,
        ApiStatus.VALIDATION_FAILED,
        "AMBIGUOUS_SESSION_COOKIE",
        ErrorCodes.REQUEST_VALIDATION_FAILED);
  }

  private ResponseEntity<MutationResponse<CsrfTokenRefreshResult>> failure(
      HttpStatus httpStatus, ApiStatus status, String code, String key) {
    var error = new ApiError(code, key, null, List.of("PAGE"), "ERROR", "SERVER");
    return ResponseEntity.status(httpStatus).body(MutationResponse.failure(status, List.of(error)));
  }
}
