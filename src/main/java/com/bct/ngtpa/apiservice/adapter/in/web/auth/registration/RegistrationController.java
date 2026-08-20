package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.RegistrationIdempotencyProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.XsrfCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.CancelRegistrationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.CompleteRegistrationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.CompleteRegistrationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationOtpResendResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationOtpVerifyRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationOtpVerifyResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartWorkflowDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationUsernameCheckRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationUsernameCheckResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.CancelRegistrationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.CompleteRegistrationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpResendWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationUsernameCheckWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.CsrfHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.port.in.CancelRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.CheckRegistrationUsernameUseCase;
import com.bct.ngtpa.apiservice.application.port.in.CompleteRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.ResendRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.VerifyRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResultStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth/registration")
@RequiredArgsConstructor
public final class RegistrationController {
  private final StartRegistrationUseCase useCase;
  private final RegistrationStartWebMapper mapper;
  private final AuthFlowCookieWriter cookieWriter;
  private final CompleteRegistrationUseCase completeRegistrationUseCase;
  private final CompleteRegistrationWebMapper completeRegistrationWebMapper;
  private final CancelRegistrationUseCase cancelRegistrationUseCase;
  private final CancelRegistrationWebMapper cancelRegistrationWebMapper;
  private final XsrfCookieWriter xsrfCookieWriter;
  private final ResendRegistrationOtpUseCase resendRegistrationOtpUseCase;
  private final RegistrationOtpResendWebMapper registrationOtpResendMapper;
  private final VerifyRegistrationOtpUseCase verifyRegistrationOtpUseCase;
  private final RegistrationOtpVerifyWebMapper registrationOtpVerifyMapper;
  private final RegistrationIdempotencyProperties idempotencyProperties;
  private final CheckRegistrationUsernameUseCase checkRegistrationUsernameUseCase;
  private final RegistrationUsernameCheckWebMapper registrationUsernameCheckWebMapper;

  @PostMapping(path = "/start")
  public Mono<
          ResponseEntity<
              MutationResponse<
                  RegistrationWorkflowResultResponse<RegistrationStartWorkflowDataResponse>>>>
      startRegistration(
          @RequestBody RegistrationStartRequest request,
          @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
          @RequestHeader(name = "X-Request-Id", required = false) String requestId,
          @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage,
          @CookieValue(name = "auth_flow_sid", required = false) String authFlowSessionIdCookie,
          ServerHttpResponse response) {
    if (idempotencyKey == null || idempotencyKey.isBlank())
      return Mono.just(
          RegistrationHttpResponses.toResponse(
              mapper.missingIdempotencyKeyResponse(acceptLanguage)));
    return useCase
        .start(
            mapper.toCommand(
                request, idempotencyKey, requestId, acceptLanguage, authFlowSessionIdCookie))
        .map(
            result -> {
              if (result.status() == StartRegistrationResultStatus.STARTED)
                cookieWriter.writeAuthFlowCookie(response, result.authFlowSessionId());
              return RegistrationHttpResponses.toResponse(
                  mapper.toMutationResponse(result, acceptLanguage));
            });
  }

  @PostMapping(
      path = "/complete",
      consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
      produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
  public Mono<
          ResponseEntity<
              MutationResponse<RegistrationWorkflowResultResponse<CompleteRegistrationResponse>>>>
      completeRegistration(
          @RequestBody(required = false) CompleteRegistrationRequest request,
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
              completeRegistrationWebMapper.missing(
                  RegistrationErrorKey.AUTH_FLOW_INVALID.value(), "PAGE", acceptLanguage)));
    }
    if (!hasText(csrfToken)) {
      return CsrfHttpResponses.missingToken();
    }
    if (!hasText(idempotencyKey)) {
      return Mono.just(
          RegistrationHttpResponses.toResponse(
              completeRegistrationWebMapper.missing(
                  SharedWorkflowErrorKey.IDEMPOTENCY_MISSING.value(),
                  "Idempotency-Key",
                  acceptLanguage)));
    }
    if (request == null) {
      return Mono.just(
          RegistrationHttpResponses.toResponse(
              completeRegistrationWebMapper.missing(
                  RegistrationErrorKey.COMPLETE_REQUEST_INVALID.value(), "PAGE", acceptLanguage)));
    }

    try {
      return completeRegistrationUseCase
          .complete(
              completeRegistrationWebMapper.toCommand(
                  request,
                  authFlowSessionId,
                  csrfToken,
                  idempotencyKey,
                  origin,
                  referer,
                  acceptLanguage,
                  requestId))
          .map(
              result -> {
                if (result.clearAuthFlowCookie()) {
                  cookieWriter.clearAuthFlowCookie(response);
                } else {
                  cookieWriter.writeAuthFlowCookie(response, authFlowSessionId);
                }
                return RegistrationHttpResponses.toResponse(
                    completeRegistrationWebMapper.toResponse(result, acceptLanguage));
              });
    } catch (RuntimeException exception) {
      return Mono.just(
          RegistrationHttpResponses.toResponse(
              completeRegistrationWebMapper.missing(
                  RegistrationErrorKey.COMPLETE_REQUEST_INVALID.value(), "PAGE", acceptLanguage)));
    }
  }

  @PostMapping(
      path = "/cancel",
      produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
  public Mono<
          ResponseEntity<
              MutationResponse<RegistrationWorkflowResultResponse<CancelRegistrationResponse>>>>
      cancelRegistration(
          @CookieValue(name = "auth_flow_sid", required = false) String authFlowSessionId,
          @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrfToken,
          @RequestHeader(name = "X-Request-Id", required = false) String requestId,
          @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage,
          @RequestHeader(name = "Origin", required = false) String origin,
          @RequestHeader(name = "Referer", required = false) String referer,
          ServerHttpResponse response) {
    if (!hasText(authFlowSessionId)) {
      cookieWriter.clearAuthFlowCookie(response);
      xsrfCookieWriter.clearXsrfCookie(response);
      return Mono.just(
          RegistrationHttpResponses.toResponse(
              cancelRegistrationWebMapper.missingSession(acceptLanguage)));
    }
    if (!hasText(csrfToken)) {
      return CsrfHttpResponses.missingToken();
    }
    try {
      return cancelRegistrationUseCase
          .cancel(
              cancelRegistrationWebMapper.toCommand(
                  authFlowSessionId, csrfToken, origin, referer, requestId))
          .map(
              result -> {
                if (result.clearAuthFlowCookie()) {
                  cookieWriter.clearAuthFlowCookie(response);
                }
                if (result.clearXsrfCookie()) {
                  xsrfCookieWriter.clearXsrfCookie(response);
                }
                return RegistrationHttpResponses.toResponse(
                    cancelRegistrationWebMapper.toResponse(result, acceptLanguage));
              });
    } catch (RuntimeException exception) {
      return Mono.just(
          RegistrationHttpResponses.toResponse(
              cancelRegistrationWebMapper.missingCsrf(acceptLanguage)));
    }
  }

  @PostMapping(path = "/otp/resend", produces = MediaType.APPLICATION_JSON_VALUE)
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
      path = "/otp/verify",
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

  @PostMapping(
      path = "/username/check",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<
          ResponseEntity<
              MutationResponse<
                  RegistrationWorkflowResultResponse<RegistrationUsernameCheckResponse>>>>
      checkUsername(
          @RequestBody(required = false) RegistrationUsernameCheckRequest request,
          @CookieValue(name = "auth_flow_sid", required = false) String authFlowSessionId,
          @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrfToken,
          @RequestHeader(name = "X-Request-Id", required = false) String requestId,
          @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage,
          @RequestHeader(name = "Origin", required = false) String origin,
          @RequestHeader(name = "Referer", required = false) String referer,
          ServerHttpResponse response) {
    if (!hasText(authFlowSessionId)) {
      return Mono.just(
          RegistrationHttpResponses.toResponse(
              registrationUsernameCheckWebMapper.missingAuthFlowCookieResponse()));
    }
    if (!hasText(csrfToken)) {
      return CsrfHttpResponses.missingToken();
    }
    if (request == null) {
      return Mono.just(
          RegistrationHttpResponses.toResponse(
              registrationUsernameCheckWebMapper.missingRequestBodyResponse()));
    }

    CheckRegistrationUsernameCommand command;
    try {
      command =
          registrationUsernameCheckWebMapper.toCommand(
              request, authFlowSessionId, csrfToken, requestId, acceptLanguage, origin, referer);
    } catch (RuntimeException exception) {
      return Mono.just(
          RegistrationHttpResponses.toResponse(
              registrationUsernameCheckWebMapper.missingAuthFlowCookieResponse()));
    }

    return checkRegistrationUsernameUseCase
        .check(command)
        .map(
            result -> {
              if (result.status()
                      != com.bct.ngtpa.apiservice.application.usecase.auth.registration.username
                          .CheckRegistrationUsernameResultStatus.INVALID_AUTH_FLOW
                  && result.status()
                      != com.bct.ngtpa.apiservice.application.usecase.auth.registration.username
                          .CheckRegistrationUsernameResultStatus.CSRF_INVALID) {
                cookieWriter.writeAuthFlowCookie(response, authFlowSessionId);
              }
              return RegistrationHttpResponses.toResponse(
                  registrationUsernameCheckWebMapper.toMutationResponse(result));
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
