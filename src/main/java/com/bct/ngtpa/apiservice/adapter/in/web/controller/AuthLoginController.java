package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.LoginHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.LoginStartDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.LoginStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.LoginWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper.LoginStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.port.in.StartLoginUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.StartLoginStatus;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthLoginController {
  private final StartLoginUseCase useCase;
  private final LoginStartWebMapper mapper;
  private final AuthFlowCookieWriter cookieWriter;

  @PostMapping("/login")
  public Mono<ResponseEntity<MutationResponse<LoginWorkflowResultResponse<LoginStartDataResponse>>>>
      startLogin(
          @RequestBody(required = false) LoginStartRequest request,
          @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
          @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false)
              String requestId,
          @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage,
          ServerHttpResponse response) {
    echoRequestId(response, requestId);
    if (!hasText(requestId)) {
      return Mono.just(LoginHttpResponses.toResponse(mapper.missingRequestId()));
    }
    if (!hasText(idempotencyKey)) {
      return Mono.just(LoginHttpResponses.toResponse(mapper.missingIdempotencyKey()));
    }
    if (request == null) {
      return Mono.just(LoginHttpResponses.toResponse(mapper.invalidRequest()));
    }

    try {
      return useCase
          .start(mapper.toCommand(request, idempotencyKey.trim(), requestId.trim(), acceptLanguage))
          .map(
              result -> {
                if (result.status() == StartLoginStatus.STARTED
                    || result.status() == StartLoginStatus.COMPLETED_REPLAY) {
                  cookieWriter.writeAuthFlowCookie(response, result.authFlowSessionId());
                }
                return LoginHttpResponses.toResponse(
                    mapper.toMutationResponse(result, acceptLanguage));
              })
          .onErrorResume(
              exception -> Mono.just(LoginHttpResponses.toResponse(mapper.systemUnavailable())));
    } catch (RuntimeException exception) {
      return Mono.just(LoginHttpResponses.toResponse(mapper.invalidRequest()));
    }
  }

  private void echoRequestId(ServerHttpResponse response, String requestId) {
    if (hasText(requestId)) {
      response.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER, requestId.trim());
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
