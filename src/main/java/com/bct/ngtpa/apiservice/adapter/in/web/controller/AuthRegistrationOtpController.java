package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.RegistrationHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.RegistrationIdempotencyProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationOtpResendResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationOtpVerifyRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationOtpVerifyResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpResendWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.CsrfHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.in.ResendRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.in.VerifyRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth/registration/otp")
@RequiredArgsConstructor
public class AuthRegistrationOtpController {

  private final AuthFlowCookieWriter cookieWriter;
  private final ResendRegistrationOtpUseCase resendRegistrationOtpUseCase;
  private final RegistrationOtpResendWebMapper registrationOtpResendMapper;
  private final VerifyRegistrationOtpUseCase verifyRegistrationOtpUseCase;
  private final RegistrationOtpVerifyWebMapper registrationOtpVerifyMapper;
  private final RegistrationIdempotencyProperties idempotencyProperties;

  @PostMapping(path = "/resend", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<
          ResponseEntity<
              MutationResponse<RegistrationWorkflowResultResponse<RegistrationOtpResendResponse>>>>
      resendOtp(
          @CookieValue(name = "auth_flow_sid", required = false) String authFlowSessionId,
          @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrfToken,
          @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
          @RequestHeader(name = "X-Request-Id", required = false) String requestId,
          @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage,
          @RequestHeader(name = "Origin", required = false) String origin,
          @RequestHeader(name = "Referer", required = false) String referer,
          ServerHttpResponse response) {
    if (!hasText(authFlowSessionId)) {
      return Mono.just(
          RegistrationHttpResponses.toResponse(
              registrationOtpResendMapper.missingAuthFlowCookieResponse()));
    }
    if (!hasText(csrfToken)) {
      return CsrfHttpResponses.missingToken();
    }
    if (!hasText(idempotencyKey)) {
      return Mono.just(
          RegistrationHttpResponses.toResponse(
              registrationOtpResendMapper.missingIdempotencyKeyResponse()));
    }

    ResendRegistrationOtpCommand command;
    try {
      command =
          new ResendRegistrationOtpCommand(
              new AuthFlowSessionId(authFlowSessionId.trim()),
              csrfToken.trim(),
              trimToNull(origin),
              trimToNull(referer),
              new RegistrationIdempotencyKey(idempotencyKey.trim()),
              trimToNull(acceptLanguage),
              trimToNull(requestId),
              idempotencyProperties.otpResendTtl());
    } catch (RuntimeException exception) {
      return Mono.just(
          RegistrationHttpResponses.toResponse(
              registrationOtpResendMapper.missingAuthFlowCookieResponse()));
    }

    return resendRegistrationOtpUseCase
        .resend(command)
        .map(
            result -> {
              if (result.status()
                      != com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp
                          .ResendRegistrationOtpResultStatus.INVALID_AUTH_FLOW
                  && result.status()
                      != com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp
                          .ResendRegistrationOtpResultStatus.CSRF_INVALID) {
                cookieWriter.writeAuthFlowCookie(response, authFlowSessionId);
              }
              return RegistrationHttpResponses.toResponse(
                  registrationOtpResendMapper.toMutationResponse(result, acceptLanguage));
            });
  }

  @PostMapping(
      path = "/verify",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<
          ResponseEntity<
              MutationResponse<RegistrationWorkflowResultResponse<RegistrationOtpVerifyResponse>>>>
      verifyOtp(
          @RequestBody(required = false) RegistrationOtpVerifyRequest request,
          @CookieValue(name = "auth_flow_sid", required = false) String authFlowSessionId,
          @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrfToken,
          @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
          @RequestHeader(name = "X-Request-Id", required = false) String requestId,
          @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage,
          @RequestHeader(name = "Origin", required = false) String origin,
          @RequestHeader(name = "Referer", required = false) String referer,
          ServerHttpResponse response) {
    if (!hasText(authFlowSessionId)) {
      return Mono.just(
          RegistrationHttpResponses.toResponse(
              registrationOtpVerifyMapper.missingAuthFlowCookieResponse()));
    }
    if (!hasText(csrfToken)) {
      return CsrfHttpResponses.missingToken();
    }
    if (!hasText(idempotencyKey)) {
      return Mono.just(
          RegistrationHttpResponses.toResponse(
              registrationOtpVerifyMapper.missingIdempotencyKeyResponse()));
    }
    if (request == null || !hasText(request.code())) {
      return Mono.just(
          RegistrationHttpResponses.toResponse(registrationOtpVerifyMapper.missingCodeResponse()));
    }

    VerifyRegistrationOtpCommand command;
    try {
      command =
          new VerifyRegistrationOtpCommand(
              new AuthFlowSessionId(authFlowSessionId.trim()),
              csrfToken.trim(),
              request.code().trim(),
              trimToNull(origin),
              trimToNull(referer),
              new RegistrationIdempotencyKey(idempotencyKey.trim()),
              trimToNull(acceptLanguage),
              trimToNull(requestId),
              idempotencyProperties.otpVerifyTtl());
    } catch (RuntimeException exception) {
      return Mono.just(
          RegistrationHttpResponses.toResponse(
              registrationOtpVerifyMapper.missingAuthFlowCookieResponse()));
    }

    return verifyRegistrationOtpUseCase
        .verify(command)
        .map(
            result -> {
              if (result.status()
                      != com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp
                          .VerifyRegistrationOtpResultStatus.INVALID_AUTH_FLOW
                  && result.status()
                      != com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp
                          .VerifyRegistrationOtpResultStatus.CSRF_INVALID) {
                cookieWriter.writeAuthFlowCookie(response, authFlowSessionId);
              }
              return RegistrationHttpResponses.toResponse(
                  registrationOtpVerifyMapper.toMutationResponse(result));
            });
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
