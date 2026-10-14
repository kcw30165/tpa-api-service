package com.bct.ngtpa.apiservice.adapter.in.web.auth.csrf;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.csrf.dto.CsrfTokenRefreshRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.csrf.dto.CsrfTokenRefreshResult;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import com.bct.ngtpa.apiservice.application.dto.auth.csrf.CsrfRefreshSessionType;
import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenCommand;
import com.bct.ngtpa.apiservice.application.port.in.RefreshCsrfTokenUseCase;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth/csrf-token")
@RequiredArgsConstructor
public final class CsrfTokenRefreshController {
  private final RefreshCsrfTokenUseCase useCase;
  private final CsrfTokenRefreshHttpResponses responses;
  private final AuthWorkflowProperties authWorkflowProperties;
  private final AuthFlowSessionProperties authFlowSessionProperties;

  /**
   * An explicit {@code sessionType} in the (optional) request body is authoritative when its
   * corresponding cookie is present: it bypasses the default "authenticated wins" precedence below.
   * When the body or the field is absent, behavior is byte-identical to before M4 - see the final
   * block of this method, which is untouched.
   */
  @PostMapping("/refresh")
  public Mono<ResponseEntity<MutationResponse<CsrfTokenRefreshResult>>> refresh(
      @RequestBody(required = false) CsrfTokenRefreshRequest request,
      @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin,
      @RequestHeader(name = HttpHeaders.REFERER, required = false) String referer,
      @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false)
          String requestId,
      ServerWebExchange exchange,
      ServerHttpResponse response) {
    nonCacheable(response, requestId);
    String authenticatedName = authWorkflowProperties.getLogin().getSession().getCookie().getName();
    String preAuthName = authFlowSessionProperties.getCookie().getName();
    List<org.springframework.http.HttpCookie> authenticated =
        exchange.getRequest().getCookies().get(authenticatedName);
    List<org.springframework.http.HttpCookie> preAuth =
        exchange.getRequest().getCookies().get(preAuthName);
    if (ambiguous(authenticated) || (empty(authenticated) && ambiguous(preAuth))) {
      return Mono.just(responses.ambiguousCookie());
    }
    String authenticatedValue = value(authenticated);
    String preAuthValue = value(preAuth);
    CsrfRefreshSessionType hint = request == null ? null : request.sessionType();

    if (hint == CsrfRefreshSessionType.AUTHENTICATED) {
      if (!hasText(authenticatedValue)) {
        return Mono.just(responses.sessionTypeCookieMissing());
      }
      return useCase
          .refresh(
              new RefreshCsrfTokenCommand(
                  clean(authenticatedValue), null, clean(origin), clean(referer), clean(requestId)))
          .map(responses::toResponse);
    }
    if (hint == CsrfRefreshSessionType.PRE_AUTH) {
      if (!hasText(preAuthValue)) {
        return Mono.just(responses.sessionTypeCookieMissing());
      }
      return useCase
          .refresh(
              new RefreshCsrfTokenCommand(
                  null, clean(preAuthValue), clean(origin), clean(referer), clean(requestId)))
          .map(responses::toResponse);
    }

    if (!empty(authenticated) && !hasText(authenticatedValue)) {
      return Mono.just(responses.unauthorized());
    }
    if (empty(authenticated) && !hasText(preAuthValue)) {
      return Mono.just(responses.unauthorized());
    }
    return useCase
        .refresh(
            new RefreshCsrfTokenCommand(
                clean(authenticatedValue),
                clean(preAuthValue),
                clean(origin),
                clean(referer),
                clean(requestId)))
        .map(responses::toResponse);
  }

  private static void nonCacheable(ServerHttpResponse response, String requestId) {
    response.getHeaders().setCacheControl(CacheControl.noStore());
    response.getHeaders().set(HttpHeaders.PRAGMA, "no-cache");
    if (hasText(requestId))
      response.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER, requestId.trim());
  }

  private static boolean ambiguous(List<?> cookies) {
    return cookies != null && cookies.size() != 1;
  }

  private static boolean empty(List<?> cookies) {
    return cookies == null || cookies.isEmpty();
  }

  private static String value(List<org.springframework.http.HttpCookie> cookies) {
    return empty(cookies) ? null : cookies.getFirst().getValue();
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static String clean(String value) {
    return hasText(value) ? value.trim() : null;
  }
}
