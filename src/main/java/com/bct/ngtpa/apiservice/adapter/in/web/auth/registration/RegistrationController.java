package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.RegistrationIdempotencyProperties;
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
import com.bct.ngtpa.apiservice.adapter.in.web.filter.TerminalIncidentHttpObservation;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.CsrfHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.port.in.CancelRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.CheckRegistrationUsernameUseCase;
import com.bct.ngtpa.apiservice.application.port.in.CompleteRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.ResendRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.VerifyRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.observability.RequestIdLogSanitizer;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context
    .RegistrationProtectedAuthFlowContextStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResultStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResultStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpResultStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
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
  private final ResendRegistrationOtpUseCase resendRegistrationOtpUseCase;
  private final RegistrationOtpResendWebMapper registrationOtpResendMapper;
  private final VerifyRegistrationOtpUseCase verifyRegistrationOtpUseCase;
  private final RegistrationOtpVerifyWebMapper registrationOtpVerifyMapper;
  private final RegistrationIdempotencyProperties idempotencyProperties;
  private final CheckRegistrationUsernameUseCase checkRegistrationUsernameUseCase;
  private final RegistrationUsernameCheckWebMapper registrationUsernameCheckWebMapper;
  // Directly constructed controller tests bypass Spring's required injection.
  private FailureEventPort failureEvents = event -> {};

  @Autowired
  public void setFailureEventPort(FailureEventPort failureEvents) {
    this.failureEvents = failureEvents;
  }

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
          ServerWebExchange exchange) {
    ServerHttpResponse response = exchange.getResponse();
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
              var mapped = mapper.toMutationResponse(result, acceptLanguage);
              if (mapped.status() == ApiStatus.SYSTEM_ERROR
                  && (result.status() == StartRegistrationResultStatus.SYSTEM_FAILED
                      || result.status()
                          == StartRegistrationResultStatus.MESSAGE_DELIVERY_FAILED)) {
                TerminalIncidentHttpObservation.markOwned(exchange);
              }
              return RegistrationHttpResponses.toResponse(mapped);
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
          ServerWebExchange exchange,
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
                var mapped = completeRegistrationWebMapper.toResponse(result, acceptLanguage);
                var httpResponse = RegistrationHttpResponses.toResponse(mapped);
                if (result.status() == CompleteRegistrationResultStatus.CONTINUATION_LOOKUP_FAILED
                    && mapped.status() == ApiStatus.SYSTEM_ERROR) {
                  TerminalIncidentHttpObservation.markOwned(exchange);
                }
                return httpResponse;
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
          otpVerifyRejection(
              registrationOtpVerifyMapper.missingAuthFlowCookieResponse(),
              requestId,
              VerifyRegistrationOtpResultStatus.INVALID_AUTH_FLOW.name(),
              false));
    }
    if (!hasText(csrfToken)) {
      return CsrfHttpResponses.missingToken();
    }
    if (!hasText(idempotencyKey)) {
      return Mono.just(
          otpVerifyRejection(
              registrationOtpVerifyMapper.missingIdempotencyKeyResponse(),
              requestId,
              SharedWorkflowErrorKey.IDEMPOTENCY_MISSING.name(),
              false));
    }
    if (request == null || !hasText(request.code())) {
      return Mono.just(
          otpVerifyRejection(
              registrationOtpVerifyMapper.missingCodeResponse(),
              requestId,
              VerifyRegistrationOtpResultStatus.VALIDATION_FAILED.name(),
              false));
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
    } catch (IllegalArgumentException exception) {
      return Mono.just(
          otpVerifyRejection(
              registrationOtpVerifyMapper.missingAuthFlowCookieResponse(),
              requestId,
              VerifyRegistrationOtpResultStatus.INVALID_AUTH_FLOW.name(),
              false));
    } catch (RuntimeException exception) {
      return Mono.just(
          otpVerifyRejection(
              registrationOtpVerifyMapper.missingAuthFlowCookieResponse(),
              requestId,
              VerifyRegistrationOtpResultStatus.SYSTEM_FAILED.name(),
              true));
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
              var mapped = registrationOtpVerifyMapper.toMutationResponse(result);
              var httpResponse = RegistrationHttpResponses.toResponse(mapped);
              if (httpResponse.getStatusCode().value() == 400
                  && result.idempotencyOutcome()
                      != RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY) {
                RegistrationProtectedAuthFlowContextStatus contextStatus =
                    result.protectedContextStatus();
                logOtpVerifyRejection(
                    requestId,
                    contextStatus == null ? result.status().name() : contextStatus.name(),
                    httpResponse.getStatusCode().value(),
                    isTechnicalContextFailure(contextStatus));
              }
              return httpResponse;
            });
  }

  private ResponseEntity<
          MutationResponse<
              RegistrationWorkflowResultResponse<RegistrationOtpVerifyResponse>>>
      otpVerifyRejection(
          MutationResponse<RegistrationWorkflowResultResponse<RegistrationOtpVerifyResponse>>
              mutationResponse,
          String requestId,
          String reason,
          boolean technical) {
    var response = RegistrationHttpResponses.toResponse(mutationResponse);
    if (response.getStatusCode().value() == 400) {
      logOtpVerifyRejection(
          requestId, reason, response.getStatusCode().value(), technical);
    }
    return response;
  }

  private void logOtpVerifyRejection(
      String requestId, String reason, int httpStatus, boolean technical) {
    failureEvents.publish(
        new FailureEvent(
            "registration.otp_verify.rejected",
            RequestIdLogSanitizer.sanitize(requestId),
            "registration",
            "otp_verify",
            technical ? FailureOrigin.LOCAL_SYSTEM : FailureOrigin.LOCAL_VALIDATION,
            reason,
            null,
            httpStatus,
            false,
            java.util.Map.of("reason", reason)));
  }

  private boolean isTechnicalContextFailure(
      RegistrationProtectedAuthFlowContextStatus contextStatus) {
    return contextStatus == RegistrationProtectedAuthFlowContextStatus.MISSING_CONTEXT
        || contextStatus
            == RegistrationProtectedAuthFlowContextStatus.CONTEXT_DECRYPTION_FAILED;
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
          ServerHttpResponse response,
          ServerWebExchange exchange) {
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
              ResponseEntity<
                      MutationResponse<
                          RegistrationWorkflowResultResponse<RegistrationUsernameCheckResponse>>>
                  mapped =
                      RegistrationHttpResponses.toResponse(
                          registrationUsernameCheckWebMapper.toMutationResponse(result));
              if (result.status()
                  == com.bct.ngtpa.apiservice.application.usecase.auth.registration.username
                      .CheckRegistrationUsernameResultStatus.DOWNSTREAM_FAILED) {
                TerminalIncidentHttpObservation.markOwned(exchange);
              }
              return mapped;
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
