package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.response.*;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationIssuanceResult;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;

@Component
public final class PasswordValidationWebMapper {
  private final AuthenticatedSessionCookieWriter cookies;

  public PasswordValidationWebMapper(AuthenticatedSessionCookieWriter cookies) {
    this.cookies = cookies;
  }

  public ResponseEntity<MutationResponse<PasswordValidationResult>> toResponse(
      PasswordValidationIssuanceResult result, ServerHttpResponse response) {
    return switch (result.status()) {
      case ISSUED ->
          ResponseEntity.ok(
              MutationResponse.success(
                  ApiStatus.SUCCESS,
                  new PasswordValidationResult(
                      new PasswordValidationResult.Data(result.token(), result.expiresInSeconds())),
                  List.of()));
      case INCORRECT ->
          failure(
              HttpStatus.UNAUTHORIZED,
              ApiStatus.BUSINESS_REJECTED,
              "BUSINESS",
              ErrorCodes.PASSWORD_VALIDATION_PASSWORD_INVALID,
              List.of("password"));
      case LOCKED -> {
        cookies.clear(response);
        yield failure(
            HttpStatus.LOCKED,
            ApiStatus.BUSINESS_REJECTED,
            "BUSINESS",
            ErrorCodes.PASSWORD_VALIDATION_ACCOUNT_LOCKED,
            List.of("PAGE"));
      }
      case LOCK_CLEANUP_FAILED -> {
        cookies.clear(response);
        yield unavailable();
      }
      case THROTTLED -> {
        var failed =
            failure(
                HttpStatus.TOO_MANY_REQUESTS,
                ApiStatus.BUSINESS_REJECTED,
                "BUSINESS",
                ErrorCodes.PASSWORD_VALIDATION_RATE_LIMITED,
                List.of("PAGE"));
        yield ResponseEntity.status(failed.getStatusCode())
            .header(HttpHeaders.RETRY_AFTER, Integer.toString(result.retryAfterSeconds()))
            .body(failed.getBody());
      }
      case AUTHENTICATION_REQUIRED -> authenticationRequired();
      case UNAVAILABLE -> unavailable();
    };
  }

  public ResponseEntity<MutationResponse<PasswordValidationResult>> invalid(String target) {
    return failure(
        HttpStatus.BAD_REQUEST,
        ApiStatus.VALIDATION_FAILED,
        "FIELD",
        ErrorCodes.PASSWORD_VALIDATION_REQUEST_INVALID,
        List.of(target));
  }

  public ResponseEntity<MutationResponse<PasswordValidationResult>> authenticationRequired() {
    return failure(
        HttpStatus.UNAUTHORIZED,
        ApiStatus.BUSINESS_REJECTED,
        "AUTHENTICATION_REQUIRED",
        ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED,
        List.of("PAGE"));
  }

  public ResponseEntity<MutationResponse<PasswordValidationResult>> missingContext() {
    return failure(
        HttpStatus.BAD_REQUEST,
        ApiStatus.BUSINESS_REJECTED,
        "BUSINESS",
        ErrorCodes.MEMBER_CONTEXT_INVALID,
        List.of());
  }

  public ResponseEntity<MutationResponse<PasswordValidationResult>> csrfInvalid() {
    return failure(
        HttpStatus.FORBIDDEN,
        ApiStatus.BUSINESS_REJECTED,
        "BUSINESS",
        ErrorCodes.SECURITY_ACCESS_DENIED,
        List.of());
  }

  public ResponseEntity<MutationResponse<PasswordValidationResult>> unavailable() {
    return failure(
        HttpStatus.SERVICE_UNAVAILABLE,
        ApiStatus.SYSTEM_ERROR,
        "SYSTEM",
        ErrorCodes.PASSWORD_VALIDATION_UNAVAILABLE,
        List.of("PAGE"));
  }

  private ResponseEntity<MutationResponse<PasswordValidationResult>> failure(
      HttpStatus http, ApiStatus status, String type, String code, List<String> targets) {
    return ResponseEntity.status(http)
        .body(
            MutationResponse.failure(
                status, List.of(new ApiError(type, code, null, targets, "ERROR", "SERVER"))));
  }
}
