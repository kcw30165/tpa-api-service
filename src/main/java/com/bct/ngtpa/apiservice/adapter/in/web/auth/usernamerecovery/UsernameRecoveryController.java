package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoveryRequestDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoveryStartDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoveryStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoveryWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryRequestWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryCancelResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryRequestResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryStartResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.response.CookieHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.response.CsrfHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.response.IdempotencyHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.RequestHeaderHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.response.WorkflowHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.session.cookie.HttpSessionCookieAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.in.CancelUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RequestUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryCancelException;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryRequestException;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartException;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Thin reactive controllers for Username Recovery {@code /start}, {@code /request}, and {@code
 * /cancel}.
 *
 * <p>Each endpoint binds transport inputs, delegates application work to the committed inbound
 * ports, maps typed results/exceptions through the Task 023 response policies, translates completed
 * policies through {@link WorkflowHttpResponses}, and creates or clears cookies only through the
 * committed {@link UsernameRecoveryCookieWriter} + {@link HttpSessionCookieAdapter}.
 *
 * <p>Guards: this controller never constructs repeated failure envelopes, never inspects error-code
 * strings to choose a status, never catches broad exceptions, keeps raw CSRF/Origin/ Referer as
 * transient method inputs (never stored in a command DTO), and never logs or exposes session ids,
 * cookies, idempotency keys, Scheme-Refs, identity, DOB, or destinations. Only the typed
 * Start/Request/Cancel application exceptions are translated into Task 023 policies; unexpected
 * defects propagate to the central exception boundary.
 */
@RestController
@RequestMapping("/api/v1/auth/username-recovery")
@RequiredArgsConstructor
public final class UsernameRecoveryController {

  private static final String SCHEME_REF_HEADER = "Scheme-Ref";

  private final StartUsernameRecoveryUseCase startUseCase;
  private final UsernameRecoveryStartWebMapper startMapper;
  private final UsernameRecoveryStartResponsePolicy startPolicy;
  private final RequestUsernameRecoveryUseCase requestUseCase;
  private final UsernameRecoveryRequestWebMapper requestMapper;
  private final UsernameRecoveryRequestResponsePolicy requestPolicy;
  private final CancelUsernameRecoveryUseCase cancelUseCase;
  private final UsernameRecoveryCancelWebMapper cancelMapper;
  private final UsernameRecoveryCancelResponsePolicy cancelPolicy;
  private final UsernameRecoveryCookieWriter cookieWriter;
  private final HttpSessionCookieAdapter cookieAdapter;

  @PostMapping("/start")
  public Mono<
          ResponseEntity<
              MutationResponse<
                  UsernameRecoveryWorkflowResultResponse<UsernameRecoveryStartDataResponse>>>>
      start(
          @RequestBody(required = false) UsernameRecoveryStartRequest request,
          @RequestHeader(name = "Accept-Language", required = false) String language,
          @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false)
              String requestId,
          @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
          ServerHttpResponse response) {
    echoRequestId(response, requestId);
    if (!hasText(idempotencyKey)) {
      return IdempotencyHttpResponses.missingKey();
    }
    return Mono.defer(
            () -> {
              UsernameRecoveryStartCommand command;
              try {
                command =
                    startMapper.toCommand(
                        request, resolveLanguage(language), idempotencyKey.trim());
              } catch (NullPointerException | IllegalArgumentException exception) {
                return Mono.just(
                    startPolicy.toPolicy(
                        UsernameRecoveryStartException.validationFailed(List.of())));
              }
              return startUseCase
                  .start(command)
                  .map(
                      result -> {
                        if (result.outcome()
                            == UsernameRecoveryStartResult.Outcome.SESSION_CREATED) {
                          cookieAdapter.apply(
                              response, cookieWriter.authFlowCookie(result.authFlowSessionId()));
                        }
                        return startPolicy.toPolicy(result, startMapper);
                      })
                  .onErrorResume(
                      UsernameRecoveryStartException.class,
                      exception -> Mono.just(startPolicy.toPolicy(exception)));
            })
        .map(this::toResponseEntity);
  }

  @PostMapping("/request")
  public Mono<
          ResponseEntity<
              MutationResponse<
                  UsernameRecoveryWorkflowResultResponse<UsernameRecoveryRequestDataResponse>>>>
      request(
          @CookieValue(name = "auth_flow_sid", required = false) String sessionId,
          @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
          @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrfToken,
          @RequestHeader(name = SCHEME_REF_HEADER, required = false) String schemeRef,
          @RequestHeader(name = "Origin", required = false) String origin,
          @RequestHeader(name = "Referer", required = false) String referer,
          @RequestHeader(name = "Accept-Language", required = false) String language,
          @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false)
              String requestId,
          ServerHttpResponse response) {
    echoRequestId(response, requestId);
    if (!hasText(sessionId)) {
      return CookieHttpResponses.missingCookie();
    }
    if (!hasText(idempotencyKey)) {
      return IdempotencyHttpResponses.missingKey();
    }
    if (!hasText(csrfToken)) {
      return CsrfHttpResponses.missingToken();
    }
    if (!hasText(schemeRef)) {
      return RequestHeaderHttpResponses.missingRequiredHeader(SCHEME_REF_HEADER);
    }
    return Mono.defer(
            () -> {
              UsernameRecoveryRequestCommand command;
              try {
                command =
                    convertRequest(
                        sessionId,
                        schemeRef,
                        idempotencyKey,
                        UsernameRecoveryRequestLanguageResolver.fromHeader(language));
              } catch (UsernameRecoveryRequestException exception) {
                return Mono.just(requestPolicy.toPolicy(exception));
              } catch (InvalidSchemeRefSignal exception) {
                return Mono.just(requestPolicy.toPolicy(invalidSchemeRefResult(), requestMapper));
              }
              return requestUseCase
                  .request(command, csrfToken.trim(), clean(origin), clean(referer))
                  .map(result -> requestPolicy.toPolicy(result, requestMapper))
                  .onErrorResume(
                      UsernameRecoveryRequestException.class,
                      exception -> Mono.just(requestPolicy.toPolicy(exception)));
            })
        .map(this::toResponseEntity);
  }

  @PostMapping("/cancel")
  public Mono<ResponseEntity<MutationResponse<UsernameRecoveryWorkflowResultResponse<Void>>>>
      cancel(
          @CookieValue(name = "auth_flow_sid", required = false) String sessionId,
          @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
          @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrfToken,
          @RequestHeader(name = "Origin", required = false) String origin,
          @RequestHeader(name = "Referer", required = false) String referer,
          @RequestHeader(name = "Accept-Language", required = false) String language,
          @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false)
              String requestId,
          ServerHttpResponse response) {
    echoRequestId(response, requestId);
    if (!hasText(sessionId)) {
      return CookieHttpResponses.missingCookie();
    }
    if (!hasText(idempotencyKey)) {
      return IdempotencyHttpResponses.missingKey();
    }
    if (!hasText(csrfToken)) {
      return CsrfHttpResponses.missingToken();
    }
    return Mono.defer(
            () -> {
              UsernameRecoveryCancelCommand command;
              try {
                command =
                    new UsernameRecoveryCancelCommand(
                        new AuthFlowSessionId(sessionId.trim()), idempotencyKey.trim());
              } catch (NullPointerException | IllegalArgumentException exception) {
                return Mono.just(
                    cancelPolicy.toPolicy(UsernameRecoveryCancelException.validationFailed()));
              }
              return cancelUseCase
                  .cancel(command, csrfToken.trim(), clean(origin), clean(referer))
                  .map(
                      result -> {
                        if (result.outcome() == UsernameRecoveryCancelResult.Outcome.SUCCESS) {
                          cookieAdapter.apply(response, cookieWriter.clearAuthFlowCookie());
                        }
                        return cancelPolicy.toPolicy(result, cancelMapper);
                      })
                  .onErrorResume(
                      UsernameRecoveryCancelException.class,
                      exception -> Mono.just(cancelPolicy.toPolicy(exception)));
            })
        .map(this::toResponseEntity);
  }

  /**
   * Converts the transport inputs into a {@link UsernameRecoveryRequestCommand} at the deferred
   * reactive boundary. Only the known conversion exception types (NullPointerException and
   * IllegalArgumentException from the value-object constructors) are translated: a malformed
   * session id becomes the approved session-restart signal and a malformed opaque Scheme-Ref
   * becomes the controller-local invalid-Scheme-Ref signal; the typed signals are translated
   * through the committed Task 023 Request policy by the endpoint. No exception message is ever
   * inspected and unexpected defects stay eligible for the central boundary.
   */
  private UsernameRecoveryRequestCommand convertRequest(
      String sessionId, String schemeRef, String idempotencyKey, Locale requestLanguage) {
    AuthFlowSessionId session = toSessionId(sessionId);
    UsernameRecoverySchemeRef opaqueSchemeRef = toSchemeRef(schemeRef);
    try {
      return new UsernameRecoveryRequestCommand(
          session, opaqueSchemeRef, idempotencyKey.trim(), requestLanguage);
    } catch (NullPointerException | IllegalArgumentException exception) {
      throw UsernameRecoveryRequestException.validationFailed();
    }
  }

  private AuthFlowSessionId toSessionId(String sessionId) {
    try {
      return new AuthFlowSessionId(sessionId.trim());
    } catch (NullPointerException | IllegalArgumentException exception) {
      throw UsernameRecoveryRequestException.sessionAbsent();
    }
  }

  private UsernameRecoverySchemeRef toSchemeRef(String schemeRef) {
    try {
      return new UsernameRecoverySchemeRef(schemeRef.trim());
    } catch (NullPointerException | IllegalArgumentException exception) {
      throw new InvalidSchemeRefSignal();
    }
  }

  private UsernameRecoveryRequestResult invalidSchemeRefResult() {
    return new UsernameRecoveryRequestResult(
        UsernameRecoveryRequestResult.Outcome.INVALID_SCHEME_REF, null, null, null, null);
  }

  /**
   * Controller-local typed signal for a malformed opaque Scheme-Ref at the transport-conversion
   * boundary. It is translated through the committed Task 023 Request policy {@code
   * INVALID_SCHEME_REF} result mapping and is never exposed outside the controller.
   */
  private static final class InvalidSchemeRefSignal extends RuntimeException {
    InvalidSchemeRefSignal() {
      super("username recovery request scheme-ref conversion failed");
    }
  }

  private <T> ResponseEntity<MutationResponse<T>> toResponseEntity(
      UsernameRecoveryResponsePolicy<T> policy) {
    if (policy.hasHttpOverride()) {
      return WorkflowHttpResponses.toResponse(policy.body(), policy.httpOverride());
    }
    return WorkflowHttpResponses.toResponse(policy.body());
  }

  private void echoRequestId(ServerHttpResponse response, String requestId) {
    if (hasText(requestId)) {
      response.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER, requestId.trim());
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String clean(String value) {
    return hasText(value) ? value.trim() : null;
  }

  private Locale resolveLanguage(String language) {
    return hasText(language) ? Locale.forLanguageTag(language.trim()) : Locale.ENGLISH;
  }
}
