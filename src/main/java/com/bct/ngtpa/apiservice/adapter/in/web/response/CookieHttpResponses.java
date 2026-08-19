package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

/**
 * Shared HTTP envelope for a syntactically missing or unusable required auth-flow cookie.
 *
 * <p>Contract:
 *
 * <ul>
 *   <li>Missing or unusable {@code auth_flow_sid} with no feature result to map → 401 UNAUTHORIZED
 *       with {@code err.security.authentication.required}.
 * </ul>
 *
 * <p>This helper owns the response envelope and status only. It never writes, clears, or composes
 * {@code Set-Cookie}, never calls {@code AuthFlowCookieWriter}, and knows nothing about Username
 * Recovery next steps or feature error keys. The approved generic authentication-required key is
 * the same one {@link CsrfHttpResponses} uses for a missing pre-auth transport artifact.
 */
public final class CookieHttpResponses {
  private CookieHttpResponses() {}

  /** 401 response used when a required auth-flow cookie is missing or unusable. */
  public static <T> Mono<ResponseEntity<MutationResponse<T>>> missingCookie() {
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
