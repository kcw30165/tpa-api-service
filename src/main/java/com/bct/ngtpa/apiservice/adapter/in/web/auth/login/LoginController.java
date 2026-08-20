package com.bct.ngtpa.apiservice.adapter.in.web.auth.login;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.cancel.CancelLoginDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.cancel.CancelLoginWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.*;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper.*;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.response.CsrfHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.LoginErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.port.in.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.cancel.CancelLoginCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.SendLoginOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend.ResendLoginOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.StartLoginStatus;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/login")
public final class LoginController {
  private final StartLoginUseCase startLoginUseCase;
  private final LoginStartWebMapper loginStartMapper;
  private final AuthFlowCookieWriter authFlowCookieWriter;
  private final CancelLoginUseCase cancelLoginUseCase;
  private final CancelLoginWebMapper cancelLoginMapper;
  private final SendLoginOtpUseCase sendLoginOtpUseCase;
  private final LoginOtpSendWebMapper loginOtpSendMapper;
  private final ResendLoginOtpUseCase resendLoginOtpUseCase;
  private final LoginOtpResendWebMapper loginOtpResendMapper;
  private final VerifyLoginOtpUseCase verifyLoginOtpUseCase;
  private final LoginOtpVerifyWebMapper loginOtpVerifyMapper;
  private final AuthenticatedSessionCookieWriter authenticatedSessionCookieWriter;

  @PostMapping
  public Mono<ResponseEntity<MutationResponse<LoginWorkflowResultResponse<LoginStartDataResponse>>>>
      startLogin(
          @RequestBody(required = false) LoginStartRequest request,
          @RequestHeader(name = "Idempotency-Key", required = false) String idem,
          @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false)
              String requestId,
          @RequestHeader(name = "Accept-Language", required = false) String lang,
          ServerHttpResponse response) {
    echo(response, requestId);
    if (!text(requestId))
      return Mono.just(LoginHttpResponses.toResponse(loginStartMapper.missingRequestId()));
    if (!text(idem))
      return Mono.just(LoginHttpResponses.toResponse(loginStartMapper.missingIdempotencyKey()));
    if (request == null)
      return Mono.just(LoginHttpResponses.toResponse(loginStartMapper.invalidRequest()));
    try {
      return startLoginUseCase
          .start(loginStartMapper.toCommand(request, idem.trim(), requestId.trim(), lang))
          .map(
              r -> {
                if (r.status() == StartLoginStatus.STARTED
                    || r.status() == StartLoginStatus.COMPLETED_REPLAY)
                  authFlowCookieWriter.writeAuthFlowCookie(response, r.authFlowSessionId());
                return LoginHttpResponses.toResponse(loginStartMapper.toMutationResponse(r, lang));
              })
          .onErrorResume(
              x -> Mono.just(LoginHttpResponses.toResponse(loginStartMapper.systemUnavailable())));
    } catch (RuntimeException x) {
      return Mono.just(LoginHttpResponses.toResponse(loginStartMapper.invalidRequest()));
    }
  }

  @PostMapping("/cancel")
  public Mono<
          ResponseEntity<MutationResponse<LoginWorkflowResultResponse<CancelLoginDataResponse>>>>
      cancel(
          @CookieValue(name = "auth_flow_sid", required = false) String session,
          @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrf,
          @RequestHeader(name = "X-Request-Id", required = false) String requestId,
          @RequestHeader(name = "Origin", required = false) String origin,
          @RequestHeader(name = "Referer", required = false) String referer,
          ServerHttpResponse response) {
    echo(response, requestId);
    if (!text(session)) {
      authFlowCookieWriter.clearAuthFlowCookie(response);
      return Mono.just(LoginHttpResponses.toResponse(cancelLoginMapper.missingSession()));
    }
    if (!text(csrf)) return CsrfHttpResponses.missingToken();
    try {
      log.info("csrf={} session={} requestId={} origin={} referer={}", csrf, session, requestId, origin, referer);
      return cancelLoginUseCase
          .cancel(
              new CancelLoginCommand(
                  new AuthFlowSessionId(session.trim()),
                  csrf.trim(),
                  clean(origin),
                  clean(referer),
                  clean(requestId)))
          .map(
              r -> {
                if (r.clearAuthFlowCookie()) authFlowCookieWriter.clearAuthFlowCookie(response);
                return LoginHttpResponses.toResponse(cancelLoginMapper.toMutationResponse(r));
              });
    } catch (RuntimeException x) {
      return Mono.just(LoginHttpResponses.toResponse(cancelLoginMapper.missingCsrf()));
    }
  }

  @PostMapping("/otp/send")
  public Mono<
          ResponseEntity<MutationResponse<LoginWorkflowResultResponse<LoginOtpSendDataResponse>>>>
      send(
          @RequestBody(required = false) LoginOtpSendRequest request,
          @CookieValue(name = "auth_flow_sid", required = false) String session,
          @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrf,
          @RequestHeader(name = "Idempotency-Key", required = false) String idem,
          @RequestHeader(name = "X-Request-Id", required = false) String req,
          @RequestHeader(name = "Accept-Language", required = false) String lang,
          @RequestHeader(name = "Origin", required = false) String origin,
          @RequestHeader(name = "Referer", required = false) String referer,
          ServerHttpResponse response) {
    echo(response, req);
    if (!text(session)) return sendMissing(LoginErrorKey.AUTH_FLOW_INVALID.value(), "PAGE");
    if (!text(csrf)) return CsrfHttpResponses.missingToken();
    if (!text(idem))
      return sendMissing(SharedWorkflowErrorKey.IDEMPOTENCY_MISSING.value(), "Idempotency-Key");
    if (!text(req)) return sendMissing("err.request.id.missing", "X-Request-Id");
    if (request == null || !text(request.destinationId()))
      return sendMissing(LoginErrorKey.OTP_DESTINATION_INVALID.value(), "destinationId");
    try {
      return sendLoginOtpUseCase
          .send(
              new SendLoginOtpCommand(
                  session.trim(),
                  csrf.trim(),
                  request.destinationId().trim(),
                  idem.trim(),
                  clean(origin),
                  clean(referer),
                  clean(lang),
                  req.trim()))
          .map(loginOtpSendMapper::toMutationResponse)
          .map(LoginHttpResponses::toResponse)
          .onErrorResume(
              x ->
                  Mono.just(
                      LoginHttpResponses.toResponse(
                          loginOtpSendMapper.missing(
                              SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value(), "PAGE"))));
    } catch (RuntimeException x) {
      return sendMissing(LoginErrorKey.REQUEST_INVALID.value(), "PAGE");
    }
  }

  @PostMapping("/otp/resend")
  public Mono<
          ResponseEntity<MutationResponse<LoginWorkflowResultResponse<LoginOtpSendDataResponse>>>>
      resend(
          @CookieValue(name = "auth_flow_sid", required = false) String session,
          @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrf,
          @RequestHeader(name = "Idempotency-Key", required = false) String idem,
          @RequestHeader(name = "X-Request-Id", required = false) String req,
          @RequestHeader(name = "Accept-Language", required = false) String lang,
          @RequestHeader(name = "Origin", required = false) String origin,
          @RequestHeader(name = "Referer", required = false) String referer,
          ServerHttpResponse response) {
    echo(response, req);
    if (!text(session)) return resendMissing(LoginErrorKey.AUTH_FLOW_INVALID.value(), "PAGE");
    if (!text(csrf)) return CsrfHttpResponses.missingToken();
    if (!text(idem))
      return resendMissing(SharedWorkflowErrorKey.IDEMPOTENCY_MISSING.value(), "Idempotency-Key");
    if (!text(req)) return resendMissing("err.request.id.missing", "X-Request-Id");
    try {
      return resendLoginOtpUseCase
          .resend(
              new ResendLoginOtpCommand(
                  session.trim(),
                  csrf.trim(),
                  idem.trim(),
                  clean(origin),
                  clean(referer),
                  clean(lang),
                  req.trim()))
          .map(loginOtpResendMapper::toMutationResponse)
          .map(LoginHttpResponses::toResponse)
          .map(
              e -> {
                Integer retry =
                    e.getBody() != null
                            && e.getBody().result() != null
                            && e.getBody().result().data() != null
                        ? e.getBody().result().data().retryAfterSeconds()
                        : null;
                return retry != null && retry > 0
                    ? ResponseEntity.status(e.getStatusCode())
                        .header(HttpHeaders.RETRY_AFTER, Integer.toString(retry))
                        .body(e.getBody())
                    : e;
              });
    } catch (RuntimeException x) {
      return resendMissing(LoginErrorKey.REQUEST_INVALID.value(), "PAGE");
    }
  }

  @PostMapping("/otp/verify")
  public Mono<
          ResponseEntity<MutationResponse<LoginWorkflowResultResponse<LoginOtpVerifyDataResponse>>>>
      verify(
          @RequestBody(required = false) LoginOtpVerifyRequest request,
          @CookieValue(name = "auth_flow_sid", required = false) String session,
          @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrf,
          @RequestHeader(name = "Idempotency-Key", required = false) String idem,
          @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false) String req,
          @RequestHeader(name = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false)
              String lang,
          @RequestHeader(name = "Origin", required = false) String origin,
          @RequestHeader(name = "Referer", required = false) String referer,
          ServerHttpResponse response) {
    echo(response, req);
    if (!text(session)) return verifyMissing(LoginErrorKey.AUTH_FLOW_INVALID.value(), "PAGE");
    if (!text(csrf)) return CsrfHttpResponses.missingToken();
    if (!text(idem))
      return verifyMissing(SharedWorkflowErrorKey.IDEMPOTENCY_MISSING.value(), "Idempotency-Key");
    if (request == null || !text(request.code()))
      return verifyMissing(LoginErrorKey.OTP_REQUIRED.value(), "otpCode");
    try {
      return verifyLoginOtpUseCase
          .verify(
              new VerifyLoginOtpCommand(
                  session.trim(),
                  csrf.trim(),
                  request.code().trim(),
                  idem.trim(),
                  clean(origin),
                  clean(referer),
                  clean(lang),
                  clean(req)))
          .map(
              r -> {
                if (r.status() == VerifyLoginOtpStatus.SESSION_CREATION_FAILED
                    || r.status() == VerifyLoginOtpStatus.SYSTEM_FAILED)
                  log.error(
                      "event=login.otp.verify.http.failure status={} requestId={}",
                      r.status(),
                      clean(req));
                if (r.status() == VerifyLoginOtpStatus.ACCOUNTS_READY
                    && r.authenticatedSessionId() != null
                    && text(r.authenticatedCsrfToken()))
                  authenticatedSessionCookieWriter.write(
                      response, r.authenticatedSessionId().value());
                if (r.clearAuthFlowCookie()) {
                  authFlowCookieWriter.clearAuthFlowCookie(response);
                }
                return LoginHttpResponses.toResponse(loginOtpVerifyMapper.toMutationResponse(r));
              })
          .doOnError(
              x ->
                  log.error(
                      "event=login.otp.verify.http.exception exceptionType={} message={} requestId={}",
                      x.getClass().getSimpleName(),
                      x.getMessage(),
                      clean(req),
                      x));
    } catch (RuntimeException x) {
      log.error("Synchronous login OTP verification failure: requestId={}", req, x);
      return verifyMissing(LoginErrorKey.REQUEST_INVALID.value(), "PAGE");
    }
  }

  private Mono<
          ResponseEntity<MutationResponse<LoginWorkflowResultResponse<LoginOtpSendDataResponse>>>>
      sendMissing(String c, String t) {
    return Mono.just(LoginHttpResponses.toResponse(loginOtpSendMapper.missing(c, t)));
  }

  private Mono<
          ResponseEntity<MutationResponse<LoginWorkflowResultResponse<LoginOtpSendDataResponse>>>>
      resendMissing(String c, String t) {
    return Mono.just(LoginHttpResponses.toResponse(loginOtpResendMapper.missing(c, t)));
  }

  private Mono<
          ResponseEntity<MutationResponse<LoginWorkflowResultResponse<LoginOtpVerifyDataResponse>>>>
      verifyMissing(String c, String t) {
    return Mono.just(LoginHttpResponses.toResponse(loginOtpVerifyMapper.missing(c, t)));
  }

  private void echo(ServerHttpResponse r, String id) {
    if (text(id)) r.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER, id.trim());
  }

  private boolean text(String v) {
    return v != null && !v.isBlank();
  }

  private String clean(String v) {
    return text(v) ? v.trim() : null;
  }
}
