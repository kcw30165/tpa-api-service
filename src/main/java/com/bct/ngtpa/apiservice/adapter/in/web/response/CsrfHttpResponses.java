package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

/**
 * Shared HTTP envelope for CSRF-protected auth endpoints.
 *
 * <p>Contract:
 *
 * <ul>
 *   <li>Missing {@code X-XSRF-TOKEN} → 401 UNAUTHORIZED with {@code
 *       err.security.authentication.required}.
 *   <li>Incorrect/invalid token is proven by the application use case and is translated to 403
 *       FORBIDDEN by the workflow-specific response mapper (e.g. {@code
 *       err.forgot-password.csrf.invalid}).
 * </ul>
 */
public final class CsrfHttpResponses {
  private CsrfHttpResponses() {}

  /**
   * 401 response used when a CSRF-protected endpoint receives no {@code X-XSRF-TOKEN} header.
   * Mirrors the approved logout contract.
   */
  public static <T> Mono<ResponseEntity<MutationResponse<T>>> missingToken() {
    MutationResponse<T> body =
        MutationResponse.failure(
            ApiStatus.BUSINESS_REJECTED,
            List.of(
                new ApiError(
                    "AUTHENTICATION_REQUIRED",
                    ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED,
                    null,
                    List.of("PAGE"),
                    "ERROR",
                    "SERVER")));
    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body));
  }
}
