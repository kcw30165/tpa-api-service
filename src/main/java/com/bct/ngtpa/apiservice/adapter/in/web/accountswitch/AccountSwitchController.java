package com.bct.ngtpa.apiservice.adapter.in.web.accountswitch;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.HttpStatusClassification;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.usecase.accountswitch.AccountNotInSessionException;
import com.bct.ngtpa.apiservice.application.usecase.accountswitch.SwitchAccountCommand;
import com.bct.ngtpa.apiservice.application.usecase.accountswitch.SwitchAccountUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedCsrfValidationException;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.ValidateAuthenticatedSessionCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.ValidateAuthenticatedSessionUseCase;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Stateless account-switch endpoint: resolves display metadata for a different account within the
 * same authenticated session. No session mutation occurs; the frontend continues sending the {@code
 * Account-Ref} header on every subsequent authenticated call as today.
 *
 * <p>Sequencing mirrors {@code LogoutController}: read the raw session cookie, require CSRF,
 * validate the session, then perform the business action. Two distinct failure modes are mapped
 * separately: an invalid/expired session (401, via the existing {@code
 * AuthenticatedPortalContextResolutionException} contract) versus a valid session whose account
 * list does not contain the requested target (422 business rejection, via the existing {@code
 * BUSINESS_REJECTED -> UNPROCESSABLE_ENTITY} convention already used by other flows).
 */
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
public final class AccountSwitchController {

  private static final String SOURCE_SERVER = "SERVER";

  private final SwitchAccountUseCase useCase;
  private final AccountSwitchWebMapper mapper;
  private final ValidateAuthenticatedSessionUseCase sessionValidation;
  private final AuthenticatedSessionCookieReader sessionCookieReader;

  @PostMapping("/switch")
  public Mono<ResponseEntity<MutationResponse<AccountSwitchResponse>>> switchAccount(
      @RequestHeader(name = RequestHeaderContextKeys.XSRF_TOKEN_HEADER, required = false)
          String csrf,
      @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false)
          String requestId,
      @Valid @RequestBody AccountSwitchRequest request,
      ServerWebExchange exchange,
      ServerHttpResponse response) {
    response.getHeaders().setCacheControl(CacheControl.noStore());
    if (hasText(requestId)) {
      response.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER, requestId.trim());
    }

    Optional<AuthenticatedSessionId> sessionId = sessionCookieReader.read(exchange);
    if (sessionId.isEmpty() || !hasText(csrf)) {
      return unauthorized();
    }

    return sessionValidation
        .validate(new ValidateAuthenticatedSessionCommand(sessionId.get(), csrf, clean(requestId)))
        .flatMap(
            ignored ->
                useCase.switchAccount(
                    new SwitchAccountCommand(sessionId.get(), request.accountRef())))
        .map(account -> ResponseEntity.ok(mapper.toResponse(account)))
        .onErrorResume(AuthenticatedCsrfValidationException.class, ignored -> csrfInvalid())
        .onErrorResume(AccountNotInSessionException.class, ignored -> accountNotFound())
        .onErrorResume(
            AuthenticatedPortalContextResolutionException.class, ignored -> unauthorized());
  }

  private Mono<ResponseEntity<MutationResponse<AccountSwitchResponse>>> csrfInvalid() {
    return Mono.just(
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(
                MutationResponse.failure(
                    ApiStatus.VALIDATION_FAILED,
                    List.of(
                        ApiError.form(
                            AccountSwitchErrorKey.CSRF_INVALID.value(),
                            null,
                            List.of("PAGE"),
                            SOURCE_SERVER)))));
  }

  private Mono<ResponseEntity<MutationResponse<AccountSwitchResponse>>> accountNotFound() {
    MutationResponse<AccountSwitchResponse> body =
        MutationResponse.failure(
            ApiStatus.BUSINESS_REJECTED,
            List.of(
                ApiError.business(
                    AccountSwitchErrorKey.ACCOUNT_NOT_FOUND.value(),
                    null,
                    List.of("PAGE"),
                    SOURCE_SERVER)));
    return Mono.just(
        ResponseEntity.status(HttpStatusClassification.classify(body.status())).body(body));
  }

  private Mono<ResponseEntity<MutationResponse<AccountSwitchResponse>>> unauthorized() {
    return Mono.just(
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(
                MutationResponse.failure(
                    ApiStatus.BUSINESS_REJECTED,
                    List.of(
                        new ApiError(
                            "AUTHENTICATION_REQUIRED",
                            ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED,
                            null,
                            List.of("PAGE"),
                            "ERROR",
                            SOURCE_SERVER)))));
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String clean(String value) {
    return hasText(value) ? value.trim() : null;
  }
}
